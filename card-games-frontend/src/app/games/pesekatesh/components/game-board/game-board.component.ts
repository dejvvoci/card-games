import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { GameWebSocketService } from '../../services/game-websocket.service';
import { GameStateView, PlayerView } from '../../models/game-state.model';
import { Card, SUIT_SYMBOL, SUIT_COLOR, rankLabel, parseCardLabel } from '../../../../models/card.model';
import { PlayingCardComponent } from '../../../../shared/playing-card/playing-card.component';
import { AuthService } from '../../../../auth/auth.service';
import { VoiceChatService } from '../../../../voice/voice-chat.service';
import { saveGameSession, loadGameSession, clearGameSession } from '../../../../shared/game-session-store';

const SESSION_KEY = 'pesekatesh';

@Component({
  selector: 'app-game-board',
  standalone: true,
  imports: [CommonModule, FormsModule, PlayingCardComponent],
  templateUrl: './game-board.component.html',
  styleUrls: ['./game-board.component.css'],
})
export class GameBoardComponent implements OnInit, OnDestroy {

  state: GameStateView | null = null;
  errorMessage: string | null = null;
  readonly suitSymbol = SUIT_SYMBOL;
  readonly suitColor = SUIT_COLOR;
  readonly rankLabel = rankLabel;

  /** Kur jam unë duke transferuar letër te lojtari i bllokuar (Kati 5) */
  selectingCardForTrade = false;

  /** Letra e zgjedhur në dorë — pret konfirmim me butonin "Luaj letrën" (më e sigurt se tap-i i drejtpërdrejtë në disa telefona) */
  selectedCard: Card | null = null;

  // ---- Ekrani i hyrjes (para lidhjes WebSocket) ----
  joined = false;
  username = '';
  mode: 'solo' | 'multiplayer' = 'solo';
  roomId = '';
  shtatatEveryRound = true;

  // ---- Historiku i marrjeve (trick-et e mia gjatë raundit aktual) ----
  showHistory = false;

  // ---- Animacioni "mbledhja e dorës" (letrat fluturojnë te fituesi i trick-ut) ----
  flyingTrickCards: { seatIndex: number; card: Card }[] | null = null;
  flyDirection: 'top' | 'bottom' | 'left' | 'right' | null = null;
  flyActive = false;
  private lastTrickSeq = -1;
  private firstStateSeen = false;
  private flyTimers: ReturnType<typeof setTimeout>[] = [];

  private subs: Subscription[] = [];

  /** Numërim mbrapsht deri sa vendet bosh mbushen automatikisht me BOT (vetëm gjatë WAITING_FOR_PLAYERS) */
  lobbySecondsLeft = 0;
  private lobbyCountdownTimer: ReturnType<typeof setInterval> | null = null;

  /** true kur lidhja STOMP është aktive — përdoret për banerin "duke u rilidhur..." */
  wsConnected = true;

  /** "Ndaj lojën": kopjimi i linkut u konfirmua vizualisht për pak sekonda */
  linkCopied = false;

  constructor(private ws: GameWebSocketService, private cdr: ChangeDetectorRef, public auth: AuthService,
              private router: Router, private route: ActivatedRoute, public voiceChat: VoiceChatService) {}

  ngOnInit(): void {
    if (this.auth.username()) {
      this.username = this.auth.username()!;
    }
    this.subs.push(
      this.ws.gameState$.subscribe((s) => {
        this.state = s;
        if (s) {
          this.handleTrickAnimation(s);
          this.syncLobbyCountdown(s);
          this.voiceChat.syncPeers(s.players.filter((p) => !p.bot).map((p) => p.id));
        }
        // Letra e zgjedhur s'është më relevante pas ndryshimit të radhës/gjendjes (p.sh. u luajt tashmë nga ne, ose radha kaloi)
        if (this.selectedCard && (!this.isMyTurn || !this.myHandCards.some((c) => c.suit === this.selectedCard!.suit && c.rank === this.selectedCard!.rank))) {
          this.selectedCard = null;
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
      this.shtatatEveryRound = (saved['shtatatEveryRound'] as boolean) ?? true;
      this.ws.connect(saved.roomId, saved.playerId, this.username, this.mode === 'solo', this.shtatatEveryRound,
        this.auth.getToken());
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
    this.flyTimers.forEach((t) => clearTimeout(t));
    if (this.lobbyCountdownTimer) clearInterval(this.lobbyCountdownTimer);
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

  /** Nis/ndal numërimin mbrapsht të lobby-t sipas fazës aktuale të lojës */
  private syncLobbyCountdown(s: GameStateView): void {
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

  // ============================================================
  //  ANIMACIONI I MBLEDHJES SË DORËS (TRICK-UT)
  // ============================================================

  /** Zbulon nëse backend-i sapo zgjodhi një trick të ri dhe nis animacionin drejt fituesit */
  private handleTrickAnimation(s: GameStateView): void {
    if (!this.firstStateSeen) {
      // Mos anima gjendjen fillestare kur lidhemi për herë të parë (mund të kemi hyrë në mes lojës)
      this.firstStateSeen = true;
      this.lastTrickSeq = s.trickSeq;
      return;
    }
    if (s.trickSeq === this.lastTrickSeq || s.lastTrickWinnerSeat === -1 || !s.lastTrick) return;
    this.lastTrickSeq = s.trickSeq;

    this.flyTimers.forEach((t) => clearTimeout(t));
    this.flyTimers = [];

    this.flyingTrickCards = Object.entries(s.lastTrick).map(([seat, label]) => ({
      seatIndex: Number(seat),
      card: parseCardLabel(label),
    }));
    this.flyDirection = this.seatDirection(s.lastTrickWinnerSeat);
    this.flyActive = false;

    // Vonesë e vogël para se të aktivizohet transformimi CSS, që letrat të shfaqen fillimisht në vendin e tyre origjinal
    this.flyTimers.push(setTimeout(() => {
      this.flyActive = true;
      this.cdr.markForCheck();
    }, 20));

    this.flyTimers.push(setTimeout(() => {
      this.flyingTrickCards = null;
      this.flyDirection = null;
      this.flyActive = false;
      this.cdr.markForCheck();
    }, 750));
  }

  /** Pozicioni (sipër/poshtë/majtas/djathtas) i një vendi ndaj meje, për vendosjen dhe animacionin e letrave */
  seatSlot(seatIndex: number): 'top' | 'bottom' | 'left' | 'right' {
    return this.seatDirection(seatIndex);
  }

  private seatDirection(seatIndex: number): 'top' | 'bottom' | 'left' | 'right' {
    if (!this.me) return 'bottom';
    if (seatIndex === this.me.seatIndex) return 'bottom';
    const offset = (seatIndex - this.me.seatIndex + 4) % 4;
    if (offset === 1) return 'left';
    if (offset === 2) return 'top';
    return 'right';
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
    saveGameSession(SESSION_KEY, { roomId: roomToJoin, playerId, username, mode: this.mode, shtatatEveryRound: this.shtatatEveryRound });
    this.ws.connect(roomToJoin, playerId, username, this.mode === 'solo', this.shtatatEveryRound,
      this.auth.getToken());
    this.joined = true;
  }

  /** Gjeneron linkun e dhomës dhe e ndan (Web Share API në mobile, ose kopjim në clipboard) */
  async onShareGame(): Promise<void> {
    const link = `${window.location.origin}/pesekatesh?room=${encodeURIComponent(this.roomId.trim().toUpperCase())}`;
    if (navigator.share) {
      try {
        await navigator.share({ title: 'Pesëkatësh', text: 'Eja të luajmë Pesëkatësh!', url: link });
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

  markReady(): void {
    this.ws.markReady();
  }

  toggleHistory(): void {
    this.showHistory = !this.showHistory;
  }

  backToSelector(): void {
    clearGameSession(SESSION_KEY);
    if (this.joined) this.ws.disconnect();
    this.router.navigateByUrl('/');
  }

  // ============================================================
  //  GETTERS PËR TEMPLATE (pozicionimi i 4 lojtarëve rreth tavolinës)
  // ============================================================

  get me(): PlayerView | undefined {
    return this.state?.players.find((p) => p.id === this.ws.myPlayerId);
  }

  get myHandCards(): Card[] {
    return (this.me?.myHand ?? []).map(parseCardLabel);
  }

  get myHandLabels(): string[] {
    return this.me?.myHand ?? [];
  }

  /** Kthen string letre nga trick-u (p.sh. "K♠") në {suit, rank} për <app-playing-card> */
  parseTrick(label: string): Card {
    return parseCardLabel(label);
  }

  /** Letra e hedhur nga një lojtar specifik në trick-un aktual (null nëse s'ka hedhur ende) */
  cardPlayedBy(seatIndex: number | undefined): Card | null {
    if (!this.state || seatIndex === undefined) return null;
    const label = this.state.currentTrick[seatIndex];
    return label ? parseCardLabel(label) : null;
  }

  /** Lojtarët e tjerë, radhitur relativisht ndaj meje: [majtas, përballë, djathtas] */
  get opponents(): PlayerView[] {
    if (!this.state || !this.me) return [];
    const mySeat = this.me.seatIndex;
    return [1, 2, 3].map((offset) =>
      this.state!.players.find((p) => p.seatIndex === (mySeat + offset) % 4)!,
    );
  }

  get isMyTurn(): boolean {
    return !!this.me && this.state?.currentPlayerSeat === this.me.seatIndex;
  }

  get amIBlocked(): boolean {
    if (!this.me || this.state?.phase !== 'KATI_5_SHTATAT') return false;
    return this.state.blockedPlayerSeat === this.me.seatIndex;
  }

  /** true nëse jam unë ai që duhet t'i japë letër lojtarit të bllokuar — vetëm te Shtatat */
  get mustGiveCardToBlocked(): boolean {
    if (!this.state || !this.me || this.state.phase !== 'KATI_5_SHTATAT' || this.state.blockedPlayerSeat === -1) return false;
    return this.me.seatIndex === this.state.blockedGiverSeat;
  }

  // ============================================================
  //  STRUKTURA E NDESHJES: RAUNDE, GATI-UP, HISTORIKU I MARRJEVE
  // ============================================================

  get iAmReady(): boolean {
    return !!this.me && (this.state?.readySeats.includes(this.me.seatIndex) ?? false);
  }

  get humanPlayersCount(): number {
    return this.state?.players.filter((p) => !p.bot).length ?? 0;
  }

  get readyCount(): number {
    return this.state?.readySeats.length ?? 0;
  }

  get matchWinner(): PlayerView | undefined {
    return this.state?.players.find((p) => p.seatIndex === this.state?.matchWinnerSeat);
  }

  /** Trick-et e mia (grupe prej 4 letrash) të marra gjatë raundit aktual, si karta {suit,rank} */
  get myCapturedTricks(): Card[][] {
    return (this.me?.myCapturedTricks ?? []).map((trick) => trick.map(parseCardLabel));
  }

  // ============================================================
  //  VALIDIMI I LËVIZJEVE (klient-side, para dërgimit te backend)
  //  Backend-i rivërteton gjithsesi, por kjo parandalon klikime të gabuara në UI
  // ============================================================

  isCardPlayable(card: Card): boolean {
    if (!this.state || !this.isMyTurn || this.amIBlocked) return false;

    if (this.state.phase === 'KATE_1_4') {
      return this.isValidKate1to4(card);
    }
    if (this.state.phase === 'KATI_5_SHTATAT') {
      return this.isValidKati5(card);
    }
    return false;
  }

  private isValidKate1to4(card: Card): boolean {
    const led = this.state!.ledSuit;
    if (!led) return true; // hap dorën, çdo letër lejohet
    const hand = this.myHandCards;
    const hasLedSuit = hand.some((c) => c.suit === led);
    if (hasLedSuit) {
      return card.suit === led; // OBLIGUAR të ndjekë shenjën
    }
    return true; // "shpresë" - lejohet çdo letër tjetër
  }

  private isValidKati5(card: Card): boolean {
    const bounds = this.state!.sevensBounds[card.suit];
    if (!bounds) {
      return card.rank === 7; // vetëm 7-a hap shenjë të re
    }
    const [low, high] = bounds;
    return card.rank === low - 1 || card.rank === high + 1;
  }

  /** A kam ndonjë lëvizje të vlefshme te Shtatat? (për të treguar butonin "Pass") */
  get hasAnyValidSevensMove(): boolean {
    if (this.state?.phase !== 'KATI_5_SHTATAT') return false;
    return this.myHandCards.some((c) => this.isValidKati5(c));
  }

  // ============================================================
  //  VEPRIME TË PËRDORUESIT
  // ============================================================

  isSelectedCard(card: Card): boolean {
    return !!this.selectedCard && this.selectedCard.suit === card.suit && this.selectedCard.rank === card.rank;
  }

  /** Tap mbi një letër: e zgjedh (ngrihet pak) ose e ç'zgjedh nëse ishte tashmë e zgjedhur — lojtaja konfirmohet me butonin "Luaj letrën" */
  onCardClick(card: Card): void {
    this.selectedCard = this.isSelectedCard(card) ? null : card;
  }

  get canPlaySelectedCard(): boolean {
    if (!this.selectedCard) return false;
    if (this.mustGiveCardToBlocked) return true;
    return this.isCardPlayable(this.selectedCard);
  }

  /** Buton "Luaj letrën" — konfirmon lojtaren e letrës së zgjedhur (më e sigurt se tap-i i drejtpërdrejtë në disa telefona) */
  onPlaySelectedCard(): void {
    const card = this.selectedCard;
    if (!card) return;

    if (this.mustGiveCardToBlocked) {
      const blockedPlayer = this.state!.players.find(
        (p) => p.seatIndex === this.state!.blockedPlayerSeat,
      );
      if (blockedPlayer) {
        this.ws.tradeCard(card, blockedPlayer.id);
      }
      this.selectedCard = null;
      return;
    }

    if (!this.isCardPlayable(card)) {
      this.showError('Nuk mund ta luash këtë letër — duhet të ndjekësh shenjën!');
      return;
    }
    this.ws.playCard(card);
    this.selectedCard = null;
  }

  onPassClick(): void {
    if (this.hasAnyValidSevensMove) {
      this.showError('Ke ende lëvizje të vlefshme, s\'mund të bësh Pass.');
      return;
    }
    this.ws.passTurn();
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