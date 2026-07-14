export type DerrPhase =
  | 'WAITING_FOR_PLAYERS'
  | 'PLAYING'
  | 'GAME_OVER';

export interface DerrPlayerView {
  id: string;
  username: string;
  bot: boolean;
  seatIndex: number;
  active: boolean;
  cardsInHand: number;
  myHand: string[] | null; // vetëm për "unë"
}

export interface DerrStateView {
  roomId: string;
  phase: DerrPhase;
  holderSeat: number;
  drawerSeat: number;
  lastEscapedSeat: number;
  escapeSeq: number;
  matchLoserSeat: number;
  burnedPairs: string[];
  players: DerrPlayerView[];
  /** Epoch ms kur vendet bosh mbushen automatikisht me BOT, nëse dhoma ende pret lojtarë */
  lobbyDeadlineEpochMs: number;
}
