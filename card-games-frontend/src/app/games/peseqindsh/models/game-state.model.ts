export type PeseqindshPhase =
  | 'WAITING_FOR_PLAYERS'
  | 'CUTTING'
  | 'PLAYING'
  | 'ROUND_FINISHED'
  | 'GAME_OVER';

export interface MeldView {
  id: string;
  type: 'SET' | 'RUN';
  ownerSeat: number;
  cards: string[]; // p.sh. ["3♥","4♥","5♥"]
  points: number;
}

export interface PeseqindshPlayerView {
  id: string;
  username: string;
  bot: boolean;
  seatIndex: number;
  hasOpened: boolean;
  totalScore: number;
  cardsInHand: number;
  myHand: string[] | null;
}

export interface PeseqindshStateView {
  roomId: string;
  phase: PeseqindshPhase;
  currentPlayerSeat: number;
  cutterSeat: number;
  roundNumber: number;
  discardedThisTurn: boolean;
  tookOpenPileThisTurn: boolean;
  closedPileCount: number;
  openPile: string[];
  /** Historiku i PLOTË i letrave të hedhura këtë raund, që nga fillimi (s'zvogëlohet kurrë si openPile) */
  discardHistory: string[];
  /** Letrat e marra nga toka që ende duhen përdorur në kombinime para se radha të kalojë */
  pendingForcedCards: string[];
  melds: MeldView[];
  players: PeseqindshPlayerView[];
  /** Epoch ms kur vendi bosh mbushet automatikisht me BOT, nëse dhoma ende pret lojtar */
  lobbyDeadlineEpochMs: number;
}
