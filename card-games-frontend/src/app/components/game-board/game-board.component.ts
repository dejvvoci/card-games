import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { GameWebSocketService } from '../../services/game-websocket.service';
import { GameStateView, PlayerView } from '../../models/game-state.model';
import { Card, SUIT_SYMBOL, SUIT_COLOR, rankLabel, parseCardLabel } from '../../models/card.model';
import { PlayingCardComponent } from '../../shared/playing-card/playing-card.component';

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

  constructor(private ws: GameWebSocketService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.subs.push(
      this.ws.gameState$.subscribe((s) => {
        this.state = s;
        if (s) this.handleTrickAnimation(s);
        this.cdr.markForCheck();
      }),
      this.ws.errors$.subscribe((msg) => this.showError(msg)),
    );
    this.roomId = this.generateRoomCode();
  }

  ngOnDestroy(): void {
    this.subs.forEach((s) => s.unsubscribe());
    this.flyTimers.forEach((t) => clearTimeout(t));
    if (this.joined) this.ws.disconnect();
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
    this.ws.connect(roomToJoin, playerId, this.username.trim(), this.mode === 'solo', this.shtatatEveryRound);
    this.joined = true;
  }

  markReady(): void {
    this.ws.markReady();
  }

  toggleHistory(): void {
    this.showHistory = !this.showHistory;
  }

  backToSelector(): void {
    window.location.href = '/';
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

  onCardClick(card: Card): void {
    if (this.mustGiveCardToBlocked) {
      // Jam duke zgjedhur çfarë letre t'i jap lojtarit të bllokuar
      const blockedPlayer = this.state!.players.find(
        (p) => p.seatIndex === this.state!.blockedPlayerSeat,
      );
      if (blockedPlayer) {
        this.ws.tradeCard(card, blockedPlayer.id);
      }
      return;
    }

    if (!this.isCardPlayable(card)) {
      this.showError('Nuk mund ta luash këtë letër — duhet të ndjekësh shenjën!');
      return;
    }
    this.ws.playCard(card);
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