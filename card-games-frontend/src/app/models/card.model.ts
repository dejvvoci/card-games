export type Suit = 'MAC' | 'ZEMER' | 'ROMB' | 'SPATHI';

export interface Card {
  suit: Suit;
  rank: number; // 2..14 (11=J,12=Q,13=K,14=A)
}

export const SUIT_SYMBOL: Record<string, string> = {
  MAC: '♠',
  ZEMER: '♥',
  ROMB: '♦',
  SPATHI: '♣',
};

export const SUIT_COLOR: Record<string, string> = {
  MAC: 'black',
  SPATHI: 'black',
  ZEMER: 'red',
  ROMB: 'red',
};

export function rankLabel(rank: number): string {
  switch (rank) {
    case 11: return 'J';
    case 12: return 'Q';
    case 13: return 'K';
    case 14: return 'A';
    default: return String(rank);
  }
}

const SYMBOL_TO_SUIT: Record<string, Suit> = {
  '♠': 'MAC',
  '♥': 'ZEMER',
  '♦': 'ROMB',
  '♣': 'SPATHI',
};

const LABEL_TO_RANK: Record<string, number> = { J: 11, Q: 12, K: 13, A: 14 };

/** Kthen një string letre nga backend, p.sh. "K♠" ose "10♥", sërish në objekt Card */
export function parseCardLabel(label: string): Card {
  const symbol = label.slice(-1);
  const rankPart = label.slice(0, -1);
  const suit = SYMBOL_TO_SUIT[symbol];
  const rank = LABEL_TO_RANK[rankPart] ?? parseInt(rankPart, 10);
  return { suit, rank };
}
