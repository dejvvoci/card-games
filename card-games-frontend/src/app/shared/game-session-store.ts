/**
 * Ruajtje e thjeshtë lokale e identitetit të lojtarit (roomId + playerId + username, etj.)
 * për një lojë specifike. Qëllimi: nëse faqja rifreskohet ose lidhja humbet krejtësisht
 * (jo vetëm një ndërprerje e shkurtër që STOMP-i e rilidh vetë), lojtari mund të rihyjë
 * në të njëjtën dhomë me të njëjtin playerId — backend-i e njeh si "po ai lojtar që u rikthye",
 * jo si një lojtar i ri, kështu që s'e humb vendin dhe s'e prish lojën për të tjerët.
 */
export interface GameSession {
  roomId: string;
  playerId: string;
  username: string;
  [key: string]: unknown;
}

const PREFIX = 'card-games-session:';

export function saveGameSession(gameKey: string, session: GameSession): void {
  localStorage.setItem(PREFIX + gameKey, JSON.stringify(session));
}

export function loadGameSession(gameKey: string): GameSession | null {
  const raw = localStorage.getItem(PREFIX + gameKey);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as GameSession;
  } catch {
    return null;
  }
}

export function clearGameSession(gameKey: string): void {
  localStorage.removeItem(PREFIX + gameKey);
}
