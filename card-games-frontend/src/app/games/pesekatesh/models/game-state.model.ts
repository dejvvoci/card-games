export type GamePhase =
  | 'WAITING_FOR_PLAYERS'
  | 'KATE_1_4'
  | 'KATI_5_SHTATAT'
  | 'ROUND_FINISHED'
  | 'GAME_OVER';

export interface PlayerView {
  id: string;
  username: string;
  bot: boolean;
  seatIndex: number;
  totalScore: number;
  cardsInHand: number;
  myHand: string[] | null; // plotësohet vetëm për "unë"
  /** Trick-et (grupe prej 4 letrash) e marra gjatë raundit aktual — vetëm për "unë" */
  myCapturedTricks: string[][] | null;
}

export interface GameStateView {
  roomId: string;
  phase: GamePhase;
  currentPlayerSeat: number;
  ledSuit: string | null;
  currentTrick: Record<number, string>;
  players: PlayerView[];
  sevensBounds: Record<string, [number, number]>;
  blockedPlayerSeat: number;
  blockedGiverSeat: number;
  finishOrder: number[];
  /** Pamja e trick-ut të fundit të zgjidhur — përdoret për animacionin e mbledhjes së letrave */
  lastTrick: Record<number, string>;
  lastTrickWinnerSeat: number;
  trickSeq: number;
  /** Struktura e ndeshjes: 5 raunde + gati-up + mënyra e Shtatave */
  roundNumber: number;
  totalRounds: number;
  shtatatEveryRound: boolean;
  tiebreakRound: boolean;
  readySeats: number[];
  matchWinnerSeat: number;
}
