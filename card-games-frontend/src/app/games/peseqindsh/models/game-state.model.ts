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
  closedPileCount: number;
  openPile: string[];
  melds: MeldView[];
  players: PeseqindshPlayerView[];
  /** Epoch ms kur vendi bosh mbushet automatikisht me BOT, nëse dhoma ende pret lojtar */
  lobbyDeadlineEpochMs: number;
}
