import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { PeseqindshWebSocketService } from '../../services/peseqindsh-websocket.service';
import { PeseqindshStateView, MeldView, PeseqindshPlayerView } from '../../models/game-state.model';
import { Card, SUIT_SYMBOL, SUIT_COLOR, rankLabel, parseCardLabel } from '../../../../models/card.model';
import { PlayingCardComponent } from '../../../../shared/playing-card/playing-card.component';
import { AuthService } from '../../../../auth/auth.service';
import { VoiceChatService } from '../../../../voice/voice-chat.service';
import { saveGameSession, loadGameSession, clearGameSession } from '../../../../shared/game-session-store';
import { SwipeUpDirective } from '../../../../shared/swipe-up.directive';

const SESSION_KEY = 'peseqindsh';

@Component({
  selector: 'app-peseqindsh-board',
  standalone: true,
  imports: [CommonModule, FormsModule, PlayingCardComponent, SwipeUpDirective],
  templateUrl: './peseqindsh-board.component.html',
  styleUrls: ['./peseqindsh-board.component.css'],
})
export class PeseqindshBoardComponent implements OnInit, OnDestroy {

  state: PeseqindshStateView | null = null;
  errorMessage: string | null = null;
  readonly suitSymbol = SUIT_SYMBOL;
  readonly suitColor = SUIT_COLOR;

  /** iOS Safari s'e mbështet HTML5 drag&drop me prekje — çaktivizojmë draggable atje, që të mos krijojë ambiguitet prekje-vs-tërheqje */
  readonly isTouchDevice = typeof window !== 'undefined' && ('ontouchstart' in window || navigator.maxTouchPoints > 0);
  readonly rankLabel = rankLabel;

  /** Letrat e zgjedhura aktualisht nga dora ime (për të formuar një kombinim) */
  selectedCards: Card[] = [];

  /** Kombinimet e "vendosura mënjanë" gjatë ndërtimit të hapjes 25-pikëshe */
  stagedGroups: Card[][] = [];

  /** Renditja ime personale e letrave në dorë (ruhet lokalisht — backend s'e di fare, s'ka ndikim në lojë) */
  private handOrder: string[] = [];
  dragFromIndex: number | null = null;
  dragOverIndex: number | null = null;

  /** Modal me të gjitha kombinimet e mia / të kundërshtarit në tokë (për të parë pikët e grumbulluara) */
  showMyMeldsModal = false;
  showOpponentMeldsModal = false;

  // ---- Animacione: qartësi vizuale kur tërhiqet/hidhet një letër ----
  talonPulse = false;
  justDiscardedPulse = false;
  justDrewCardIndex: number | null = null;
  private drawPulseTimer: ReturnType<typeof setTimeout> | null = null;
  private discardPulseTimer: ReturnType<typeof setTimeout> | null = null;
  private talonPulseTimer: ReturnType<typeof setTimeout> | null = null;

  // ---- Ekrani i hyrjes (para lidhjes WebSocket) ----
  joined = false;
  username = '';
  mode: 'solo' | 'multiplayer' = 'solo';
  roomId = '';

  private subs: Subscription[] = [];

  /** Numërim mbrapsht deri sa vendi bosh mbushet automatikisht me BOT (vetëm gjatë WAITING_FOR_PLAYERS) */
  lobbySecondsLeft = 0;
  private lobbyCountdownTimer: ReturnType<typeof setInterval> | null = null;

  /** true kur lidhja STOMP është aktive — përdoret për banerin "duke u rilidhur..." */
  wsConnected = true;

  /** "Ndaj lojën": kopjimi i linkut u konfirmua vizualisht për pak sekonda */
  linkCopied = false;

  constructor(private ws: PeseqindshWebSocketService, private cdr: ChangeDetectorRef, public auth: AuthService,
              private router: Router, private route: ActivatedRoute, public voiceChat: VoiceChatService) {}

  ngOnInit(): void {
    if (this.auth.username()) {
      this.username = this.auth.username()!;
    }
    this.subs.push(
      this.ws.state$.subscribe((s) => {
        if (s) this.detectDrawAndDiscard(this.state, s);
        this.state = s;
        if (s) {
          this.syncLobbyCountdown(s);
          this.reconcileHandOrder(s.players.find((p) => p.id === this.ws.myPlayerId)?.myHand ?? []);
          this.voiceChat.syncPeers(s.players.filter((p) => !p.bot).map((p) => p.id));
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
    if (this.drawPulseTimer) clearTimeout(this.drawPulseTimer);
    if (this.discardPulseTimer) clearTimeout(this.discardPulseTimer);
    if (this.talonPulseTimer) clearTimeout(this.talonPulseTimer);
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

  /** Krahason gjendjen e vjetër me të renë për të nxjerrë në pah vizualisht tërheqjen/hedhjen e një letre */
  private detectDrawAndDiscard(prev: PeseqindshStateView | null, next: PeseqindshStateView): void {
    if (!prev) return;

    if (next.closedPileCount < prev.closedPileCount) {
      this.talonPulse = true;
      if (this.talonPulseTimer) clearTimeout(this.talonPulseTimer);
      this.talonPulseTimer = setTimeout(() => { this.talonPulse = false; this.cdr.markForCheck(); }, 500);

      const prevMe = prev.players.find((p) => p.id === this.ws.myPlayerId);
      const nextMe = next.players.find((p) => p.id === this.ws.myPlayerId);
      if (prevMe && nextMe && nextMe.cardsInHand > prevMe.cardsInHand) {
        this.justDrewCardIndex = nextMe.cardsInHand - 1;
        if (this.drawPulseTimer) clearTimeout(this.drawPulseTimer);
        this.drawPulseTimer = setTimeout(() => { this.justDrewCardIndex = null; this.cdr.markForCheck(); }, 900);
      }
    }

    if (!prev.discardedThisTurn && next.discardedThisTurn) {
      this.justDiscardedPulse = true;
      if (this.discardPulseTimer) clearTimeout(this.discardPulseTimer);
      this.discardPulseTimer = setTimeout(() => { this.justDiscardedPulse = false; this.cdr.markForCheck(); }, 700);
    }
  }

  /** Nis/ndal numërimin mbrapsht të lobby-t sipas fazës aktuale të lojës */
  private syncLobbyCountdown(s: PeseqindshStateView): void {
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
    // Solo: dhomë private e gjeneruar automatikisht (bot-i plotësohet menjëherë nga backend)
    const roomToJoin = this.mode === 'solo' ? 'solo-' + playerId : this.roomId.trim().toUpperCase();
    const username = this.username.trim();
    saveGameSession(SESSION_KEY, { roomId: roomToJoin, playerId, username, mode: this.mode });
    this.ws.connect(roomToJoin, playerId, username, this.mode === 'solo', this.auth.getToken());
    this.joined = true;
  }

  /** Gjeneron linkun e dhomës dhe e ndan (Web Share API në mobile, ose kopjim në clipboard) */
  async onShareGame(): Promise<void> {
    const link = `${window.location.origin}/peseqindsh?room=${encodeURIComponent(this.roomId.trim().toUpperCase())}`;
    if (navigator.share) {
      try {
        await navigator.share({ title: 'Peseqindsh', text: 'Eja të luajmë Peseqindsh!', url: link });
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
  //  GETTERS
  // ============================================================

  get me(): PeseqindshPlayerView | undefined {
    return this.state?.players.find((p) => p.id === this.ws.myPlayerId);
  }

  get opponent(): PeseqindshPlayerView | undefined {
    return this.state?.players.find((p) => p.id !== this.ws.myPlayerId);
  }

  get myHandCards(): Card[] {
    return this.handOrder.map(parseCardLabel);
  }

  /** Ruaj renditjen e letrave t'i ekzistueshme, shto në fund letrat e reja, hiq ato që s'janë më në dorë */
  private reconcileHandOrder(newLabels: string[]): void {
    const stillPresent = this.handOrder.filter((l) => newLabels.includes(l));
    const newlyAdded = newLabels.filter((l) => !this.handOrder.includes(l));
    this.handOrder = [...stillPresent, ...newlyAdded];
  }

  get isMyTurn(): boolean {
    return !!this.me && this.state?.currentPlayerSeat === this.me.seatIndex;
  }

  /** Kthen string letre (nga meld ose openPile) në {suit, rank} për <app-playing-card> */
  parseLabel(label: string): Card {
    return parseCardLabel(label);
  }

  get myMelds(): MeldView[] {
    return (this.state?.melds ?? []).filter((m) => m.ownerSeat === this.me?.seatIndex);
  }

  get opponentMelds(): MeldView[] {
    return (this.state?.melds ?? []).filter((m) => m.ownerSeat === this.opponent?.seatIndex);
  }

  get myMeldsTotalPoints(): number {
    return this.myMelds.reduce((sum, m) => sum + m.points, 0);
  }

  get opponentMeldsTotalPoints(): number {
    return this.opponentMelds.reduce((sum, m) => sum + m.points, 0);
  }

  get stagedTotalPoints(): number {
    // Vlerësim orientues në frontend (backend rivalidon saktë);
    // ndjek të njëjtën shkallë si BE: 3-8=5, 9-K=10, A=15, 2=20.
    const val = (c: Card) => (c.rank === 2 ? 20 : c.rank === 14 ? 15 : c.rank >= 9 ? 10 : 5);
    return this.stagedGroups.flat().reduce((sum, c) => sum + val(c), 0);
  }

  get canDiscard(): boolean {
    return this.isMyTurn && !this.state?.discardedThisTurn && this.selectedCards.length === 1;
  }

  get canDrawClosed(): boolean {
    return this.isMyTurn && !!this.state?.discardedThisTurn;
  }

  get canTakeOpenPile(): boolean {
    return this.isMyTurn && !!this.state?.discardedThisTurn && !!this.me?.hasOpened
      && (this.state?.openPile.length ?? 0) > 0;
  }

  /** Njësoj si canTakeOpenPile, por për marrjen e pjesshme duke filluar te një letër specifike */
  get canTakeFromOpenPile(): boolean {
    return this.canTakeOpenPile;
  }

  get canAddSelectedAsMeld(): boolean {
    return this.isMyTurn && !!this.me?.hasOpened && this.selectedCards.length >= 3;
  }

  get canStageSelectedGroup(): boolean {
    return !this.me?.hasOpened && this.selectedCards.length >= 3;
  }

  /** Butoni "Mbaro radhën" vlen VETËM pasi ke marrë tokën këtë radhë dhe ke përdorur të gjitha letrat e detyruara */
  get canEndForcedTurn(): boolean {
    return this.isMyTurn && !!this.state?.tookOpenPileThisTurn
      && (this.state?.pendingForcedCards.length ?? 0) === 0;
  }

  // ============================================================
  //  NDËRVEPRIMI ME LETRAT
  // ============================================================

  isSelected(card: Card): boolean {
    return this.selectedCards.some((c) => c.suit === card.suit && c.rank === card.rank);
  }

  toggleSelect(card: Card): void {
    if (this.isSelected(card)) {
      this.selectedCards = this.selectedCards.filter((c) => !(c.suit === card.suit && c.rank === card.rank));
    } else {
      this.selectedCards = [...this.selectedCards, card];
    }
  }

  // ============================================================
  //  RIRENDITJA E DORËS (drag & drop, vetëm lokale — s'prek gjendjen e lojës)
  // ============================================================

  onHandDragStart(index: number, event: DragEvent): void {
    this.dragFromIndex = index;
    event.dataTransfer?.setData('text/plain', String(index));
    if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move';
  }

  onHandDragOver(index: number, event: DragEvent): void {
    event.preventDefault(); // e domosdoshme që të lejohet 'drop'
    if (this.dragOverIndex !== index) {
      this.dragOverIndex = index;
      this.cdr.markForCheck();
    }
  }

  onHandDrop(index: number, event: DragEvent): void {
    event.preventDefault();
    const from = this.dragFromIndex;
    this.dragFromIndex = null;
    this.dragOverIndex = null;
    if (from === null || from === index) {
      this.cdr.markForCheck();
      return;
    }
    const reordered = [...this.handOrder];
    const [moved] = reordered.splice(from, 1);
    reordered.splice(index, 0, moved);
    this.handOrder = reordered;
    this.cdr.markForCheck();
  }

  onHandDragEnd(): void {
    this.dragFromIndex = null;
    this.dragOverIndex = null;
    this.cdr.markForCheck();
  }

  // ============================================================
  //  VEPRIME
  // ============================================================

  onDiscard(): void {
    if (!this.canDiscard) return;
    this.ws.discard(this.selectedCards[0]);
    this.selectedCards = [];
  }

  /** Gjest touch: fshirja lart mbi një letër e hedh direkt në tokë, pa nevojën e zgjedhjes+butonit */
  onSwipeDiscard(card: Card): void {
    if (!this.isMyTurn || this.state?.discardedThisTurn) return;
    this.ws.discard(card);
    this.selectedCards = this.selectedCards.filter((c) => !(c.suit === card.suit && c.rank === card.rank));
  }

  onDrawClosed(): void {
    if (!this.canDrawClosed) return;
    this.ws.drawClosed();
  }

  onTakeOpenPile(): void {
    if (!this.canTakeOpenPile) return;
    this.ws.takeOpenPile();
  }

  /** Klikimi mbi një letër specifike në tokë: merr atë letër + të gjitha mbi të, deri në maja */
  onTakeFromOpenPile(card: Card): void {
    if (!this.canTakeFromOpenPile) return;
    this.ws.takeFromOpenPile(card);
  }

  /** Shton grupin e zgjedhur te lista e "hapjes" (mund të bësh disa grupe para se të dërgosh) */
  onStageGroup(): void {
    if (!this.canStageSelectedGroup) return;
    this.stagedGroups.push([...this.selectedCards]);
    this.selectedCards = [];
  }

  onRemoveStagedGroup(index: number): void {
    this.stagedGroups.splice(index, 1);
  }

  /** Dërgon të gjitha grupet e vendosura mënjanë si hapjen zyrtare (backend rivërteton >=25p) */
  onConfirmOpenHand(): void {
    if (this.stagedGroups.length === 0) {
      this.showError('Zgjidh të paktën një kombinim para se të hapësh lojën.');
      return;
    }
    this.ws.openHand(this.stagedGroups);
    this.stagedGroups = [];
  }

  /** Pasi je i shtruar: fut direkt kombinimin e zgjedhur si kombinim i ri në tokë */
  onAddMeld(): void {
    if (!this.canAddSelectedAsMeld) return;
    this.ws.addMeld(this.selectedCards);
    this.selectedCards = [];
  }

  /** Klikimi mbi një kombinim ekzistues në tokë: nëse kam 1 letër të zgjedhur, e ngjis atje */
  onMeldClick(meld: MeldView): void {
    if (!this.me?.hasOpened) return;
    if (this.selectedCards.length !== 1) {
      this.showError('Zgjidh saktësisht 1 letër nga dora për ta ngjitur te ky kombinim.');
      return;
    }
    this.ws.extendMeld(meld.id, this.selectedCards[0]);
    this.selectedCards = [];
  }

  onEndForcedTurn(): void {
    if (!this.canEndForcedTurn) return;
    this.ws.endForcedTurn();
  }

  onNextRound(): void {
    this.ws.nextRound();
  }

  toggleMyMeldsModal(): void {
    this.showMyMeldsModal = !this.showMyMeldsModal;
  }

  toggleOpponentMeldsModal(): void {
    this.showOpponentMeldsModal = !this.showOpponentMeldsModal;
  }

  private showError(msg: string): void {
    this.errorMessage = msg;
    this.cdr.markForCheck();
    setTimeout(() => {
      this.errorMessage = null;
      this.cdr.markForCheck();
    }, 3500);
  }
}