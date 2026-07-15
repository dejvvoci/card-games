import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { DerrWebSocketService } from '../../services/derr-websocket.service';
import { DerrStateView, DerrPlayerView } from '../../models/game-state.model';
import { Card, SUIT_SYMBOL, SUIT_COLOR, rankLabel, parseCardLabel } from '../../../../models/card.model';
import { PlayingCardComponent } from '../../../../shared/playing-card/playing-card.component';
import { AuthService } from '../../../../auth/auth.service';
import { VoiceChatService } from '../../../../voice/voice-chat.service';
import { saveGameSession, loadGameSession, clearGameSession } from '../../../../shared/game-session-store';

const SESSION_KEY = 'derr';

@Component({
  selector: 'app-derr-board',
  standalone: true,
  imports: [CommonModule, FormsModule, PlayingCardComponent],
  templateUrl: './derr-board.component.html',
  styleUrls: ['./derr-board.component.css'],
})
export class DerrBoardComponent implements OnInit, OnDestroy {

  state: DerrStateView | null = null;
  errorMessage: string | null = null;
  readonly suitSymbol = SUIT_SYMBOL;
  readonly suitColor = SUIT_COLOR;
  readonly rankLabel = rankLabel;

  // ---- Ekrani i hyrjes (para lidhjes WebSocket) ----
  joined = false;
  username = '';
  mode: 'solo' | 'multiplayer' = 'solo';
  roomId = '';

  /** true kur lidhja STOMP është aktive — përdoret për banerin "duke u rilidhur..." */
  wsConnected = true;
  /** "Ndaj lojën": kopjimi i linkut u konfirmua vizualisht për pak sekonda */
  linkCopied = false;

  /** Njoftimi "X shpëtoi!" kur dikush mbetet pa letra */
  escapeMessage: string | null = null;
  private lastSeenEscapeSeq = -1;
  private escapeMessageTimer: ReturnType<typeof setTimeout> | null = null;

  /** Letra "fluturuese": animacioni nga dora e kundërshtarit te dora ime, kur unë jam duke tërhequr */
  flyingDraw: { direction: 'top' | 'left' | 'right'; card: Card | null; arrived: boolean; burn: boolean; fadeOut: boolean } | null = null;

  /** true për një çast të shkurtër pas shpërndarjes fillestare — nxit animacionin e "hedhjes" së letrave në dorë */
  justDealt = false;

  private subs: Subscription[] = [];

  /** Numërim mbrapsht deri sa vendet bosh mbushen automatikisht me BOT (vetëm gjatë WAITING_FOR_PLAYERS) */
  lobbySecondsLeft = 0;
  private lobbyCountdownTimer: ReturnType<typeof setInterval> | null = null;

  constructor(private ws: DerrWebSocketService, private cdr: ChangeDetectorRef, public auth: AuthService,
              private router: Router, private route: ActivatedRoute, public voiceChat: VoiceChatService) {}

  ngOnInit(): void {
    if (this.auth.username()) {
      this.username = this.auth.username()!;
    }
    this.subs.push(
      this.ws.state$.subscribe((s) => {
        if (s) this.detectEscape(this.state, s);
        if (s) this.detectDrawAnimation(this.state, s);
        const wasWaiting = this.state?.phase === 'WAITING_FOR_PLAYERS';
        this.state = s;
        if (s) {
          this.syncLobbyCountdown(s);
          this.voiceChat.syncPeers(s.players.filter((p) => !p.bot).map((p) => p.id));
          if (wasWaiting && s.phase === 'PLAYING') this.triggerDealAnimation();
        }
        this.cdr.markForCheck();
      }),
      this.ws.errors$.subscribe((msg) => this.showError(msg)),
      this.ws.connectionStatus$.subscribe((connected) => {
        this.wsConnected = connected;
        this.cdr.markForCheck();
      }),
    );

    const roomFromLink = this.route.snapshot.queryParamMap.get('room')?.trim().toUpperCase() || null;
    const saved = loadGameSession(SESSION_KEY);

    // Rihyrje automatike: nëse faqja u rifreskua ndërkohë që isha në një lojë aktive (ose e rihapa
    // të njëjtin link ftese), rilidhu në të njëjtën dhomë me të njëjtin playerId — backend-i më njeh,
    // s'e humb vendin. Nëse linku ftese tregon një dhomë TJETËR, ai ka përparësi ndaj sesionit të vjetër.
    if (saved && (!roomFromLink || saved.roomId === roomFromLink)) {
      this.username = saved.username;
      this.mode = (saved['mode'] as 'solo' | 'multiplayer') ?? 'multiplayer';
      this.roomId = saved.roomId;
      this.ws.connect(saved.roomId, saved.playerId, this.username, this.mode === 'solo', this.auth.getToken());
      this.joined = true;
      return;
    }

    // Nëse erdhëm nga një link "Ndaj lojën" (?room=KODI): hyr direkt në dhomë, pa kërkuar rishkrim kodi
    if (roomFromLink) {
      this.mode = 'multiplayer';
      this.roomId = roomFromLink;
      if (this.canJoin) {
        this.onJoinSubmit();
      }
    } else {
      this.roomId = this.generateRoomCode();
    }
  }

  ngOnDestroy(): void {
    this.subs.forEach((s) => s.unsubscribe());
    if (this.lobbyCountdownTimer) clearInterval(this.lobbyCountdownTimer);
    if (this.escapeMessageTimer) clearTimeout(this.escapeMessageTimer);
    this.voiceChat.leave();
    if (this.joined) this.ws.disconnect();
  }

  async onJoinVoiceChat(): Promise<void> {
    try {
      await this.voiceChat.join(this.ws.myPlayerId);
    } catch {
      this.showError('S\'u arrit qasja te mikrofoni. Kontrollo lejet e browser-it.');
    }
  }

  /** Zbulon nëse dikush sapo "shpëtoi" (i mbaruan letrat) dhe shfaq një njoftim të shkurtër */
  private detectEscape(prev: DerrStateView | null, next: DerrStateView): void {
    if (!prev || next.escapeSeq === prev.escapeSeq || next.lastEscapedSeat === -1) return;
    if (next.phase === 'GAME_OVER' && next.matchLoserSeat === next.lastEscapedSeat) return; // ky është "Derri", jo shpëtim
    const escaped = next.players.find((p) => p.seatIndex === next.lastEscapedSeat);
    if (!escaped) return;
    this.escapeMessage = `🎉 ${escaped.username} shpëtoi!`;
    if (this.escapeMessageTimer) clearTimeout(this.escapeMessageTimer);
    this.escapeMessageTimer = setTimeout(() => { this.escapeMessage = null; this.cdr.markForCheck(); }, 2500);
  }

  /** Zbulon nëse unë sapo tërhoqa një letër (blind draw) dhe nis animacionin "fluturues" drejt dorës sime */
  private detectDrawAnimation(prev: DerrStateView | null, next: DerrStateView): void {
    if (!prev || prev.phase !== 'PLAYING') return;
    const myself = this.me; // ende pasqyron `prev`, sepse this.state s'është rifreskuar akoma
    if (!myself || prev.drawerSeat !== myself.seatIndex) return;
    if (prev.holderSeat === next.holderSeat && prev.drawerSeat === next.drawerSeat) return;

    const seatOffset = (prev.holderSeat - myself.seatIndex + 4) % 4;
    const direction: 'top' | 'left' | 'right' = seatOffset === 2 ? 'top' : seatOffset === 1 ? 'left' : 'right';

    const paired = next.burnedPairs.length > prev.burnedPairs.length;
    let card: Card | null = null;
    if (paired) {
      card = parseCardLabel(next.burnedPairs[next.burnedPairs.length - 2]);
    } else {
      const nextMe = next.players.find((p) => p.id === myself.id);
      const labels = nextMe?.myHand ?? [];
      if (labels.length) card = parseCardLabel(labels[labels.length - 1]);
    }
    this.triggerFlyingDraw(direction, card, paired);
  }

  private triggerFlyingDraw(direction: 'top' | 'left' | 'right', card: Card | null, paired: boolean): void {
    this.flyingDraw = { direction, card, arrived: false, burn: false, fadeOut: false };
    this.cdr.markForCheck();
    setTimeout(() => {
      if (!this.flyingDraw) return;
      this.flyingDraw = { ...this.flyingDraw, arrived: true };
      this.cdr.markForCheck();
      setTimeout(() => {
        if (!this.flyingDraw) return;
        if (paired) {
          this.flyingDraw = { ...this.flyingDraw, burn: true };
          this.cdr.markForCheck();
          setTimeout(() => { this.flyingDraw = null; this.cdr.markForCheck(); }, 650);
        } else {
          this.flyingDraw = { ...this.flyingDraw, fadeOut: true };
          this.cdr.markForCheck();
          setTimeout(() => { this.flyingDraw = null; this.cdr.markForCheck(); }, 400);
        }
      }, 560);
    }, 20);
  }

  /** Nis animacionin e "hedhjes" së letrave në dorë pas shpërndarjes fillestare */
  private triggerDealAnimation(): void {
    this.justDealt = true;
    this.cdr.markForCheck();
    setTimeout(() => { this.justDealt = false; this.cdr.markForCheck(); }, 900);
  }

  /** Nis/ndal numërimin mbrapsht të lobby-t sipas fazës aktuale të lojës */
  private syncLobbyCountdown(s: DerrStateView): void {
    if (s.phase !== 'WAITING_FOR_PLAYERS') {
      if (this.lobbyCountdownTimer) {
        clearInterval(this.lobbyCountdownTimer);
        this.lobbyCountdownTimer = null;
      }
      return;
    }
    this.lobbySecondsLeft = Math.max(0, Math.round((s.lobbyDeadlineEpochMs - Date.now()) / 1000));
    if (!this.lobbyCountdownTimer) {
      this.lobbyCountdownTimer = setInterval(() => {
        this.lobbySecondsLeft = Math.max(0, Math.round((s.lobbyDeadlineEpochMs - Date.now()) / 1000));
        this.cdr.markForCheck();
      }, 1000);
    }
  }

  generateRoomCode(): string {
    return Math.random().toString(36).substring(2, 8).toUpperCase();
  }

  get canJoin(): boolean {
    if (!this.username.trim()) return false;
    if (this.mode === 'multiplayer' && !this.roomId.trim()) return false;
    return true;
  }

  onJoinSubmit(): void {
    if (!this.canJoin) return;
    const playerId = 'p-' + Math.random().toString(36).substring(2, 10);
    // Solo: dhomë private e gjeneruar automatikisht (bot-et plotësohen menjëherë nga backend)
    const roomToJoin = this.mode === 'solo' ? 'solo-' + playerId : this.roomId.trim().toUpperCase();
    const username = this.username.trim();
    saveGameSession(SESSION_KEY, { roomId: roomToJoin, playerId, username, mode: this.mode });
    this.ws.connect(roomToJoin, playerId, username, this.mode === 'solo', this.auth.getToken());
    this.joined = true;
  }

  /** Gjeneron linkun e dhomës dhe e ndan (Web Share API në mobile, ose kopjim në clipboard) */
  async onShareGame(): Promise<void> {
    const link = `${window.location.origin}/derr?room=${encodeURIComponent(this.roomId.trim().toUpperCase())}`;
    if (navigator.share) {
      try {
        await navigator.share({ title: 'Derri në Dorë', text: 'Eja të luajmë Derri në Dorë!', url: link });
        return;
      } catch {
        // përdoruesi anuloi ose dështoi share-i -> bie mbrapa te kopjimi
      }
    }
    await navigator.clipboard.writeText(link);
    this.linkCopied = true;
    this.cdr.markForCheck();
    setTimeout(() => { this.linkCopied = false; this.cdr.markForCheck(); }, 2500);
  }

  backToSelector(): void {
    clearGameSession(SESSION_KEY);
    if (this.joined) this.ws.disconnect();
    this.router.navigateByUrl('/');
  }

  // ============================================================
  //  GETTERS PËR TEMPLATE (pozicionimi i 4 lojtarëve rreth tavolinës)
  // ============================================================

  get me(): DerrPlayerView | undefined {
    return this.state?.players.find((p) => p.id === this.ws.myPlayerId);
  }

  get myHandCards(): Card[] {
    return (this.me?.myHand ?? []).map(parseCardLabel);
  }

  /** Lojtarët e tjerë, radhitur relativisht ndaj meje: [majtas, përballë, djathtas] */
  get opponents(): DerrPlayerView[] {
    if (!this.state || !this.me) return [];
    const mySeat = this.me.seatIndex;
    return [1, 2, 3].map((offset) =>
      this.state!.players.find((p) => p.seatIndex === (mySeat + offset) % 4)!,
    );
  }

  get isMyTurn(): boolean {
    return !!this.me && this.state?.drawerSeat === this.me.seatIndex;
  }

  get isMyHolding(): boolean {
    return !!this.me && this.state?.holderSeat === this.me.seatIndex;
  }

  get holderPlayer(): DerrPlayerView | undefined {
    return this.state?.players.find((p) => p.seatIndex === this.state?.holderSeat);
  }

  get drawerPlayer(): DerrPlayerView | undefined {
    return this.state?.players.find((p) => p.seatIndex === this.state?.drawerSeat);
  }

  get matchLoserPlayer(): DerrPlayerView | undefined {
    return this.state?.players.find((p) => p.seatIndex === this.state?.matchLoserSeat);
  }

  /** true nëse ky vend është holder-i AKTUAL nga i cili unë (radha ime) duhet të tërheq letër */
  isDrawableHolderSeat(seatIndex: number): boolean {
    return this.isMyTurn && this.state?.holderSeat === seatIndex;
  }

  /** Krijon një varg [0..count) për *ngFor kur duam N sllote letrash të kthyera */
  range(count: number): number[] {
    return Array.from({ length: count }, (_, i) => i);
  }

  onDrawCard(index: number): void {
    if (!this.isMyTurn) return;
    this.ws.draw(index);
  }

  private showError(msg: string): void {
    this.errorMessage = msg;
    this.cdr.markForCheck();
    setTimeout(() => {
      this.errorMessage = null;
      this.cdr.markForCheck();
    }, 3000);
  }
}
