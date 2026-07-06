import { Injectable } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { PeseqindshStateView } from '../models/game-state.model';
import { Card } from '../../../models/card.model';

@Injectable({ providedIn: 'root' })
export class PeseqindshWebSocketService {

  private client!: Client;
  private playerId!: string;
  private roomId!: string;

  private stateSubject = new BehaviorSubject<PeseqindshStateView | null>(null);
  public state$: Observable<PeseqindshStateView | null> = this.stateSubject.asObservable();

  private errorSubject = new Subject<string>();
  public errors$: Observable<string> = this.errorSubject.asObservable();

  connect(roomId: string, playerId: string, username: string, soloVsBots: boolean, authToken: string | null = null): void {
    this.roomId = roomId;
    this.playerId = playerId;

    this.client = new Client({
      // WebSocket nativ i browser-it (jo SockJS) -> s'ka nevojë për asnjë polyfill
      brokerURL: `${environment.wsEndpoint}?playerId=${encodeURIComponent(playerId)}`,
      reconnectDelay: 3000,
      onConnect: () => {
        this.client.subscribe('/user/queue/peseqindsh-state', (msg: IMessage) => {
          this.stateSubject.next(JSON.parse(msg.body));
        });
        this.client.subscribe('/user/queue/peseqindsh-errors', (msg: IMessage) => {
          this.errorSubject.next(msg.body);
        });
        // authToken lidh Player-in me llogarinë e loguar, për historikun e statistikave
        this.client.publish({
          destination: `/app/peseqindsh/join/${roomId}`,
          body: JSON.stringify({ playerId, username, soloVsBots, authToken }),
        });
      },
    });
    this.client.activate();
  }

  disconnect(): void {
    this.client?.deactivate();
  }

  private send(action: string, body: any): void {
    this.client.publish({
      destination: `/app/peseqindsh/${action}/${this.roomId}`,
      body: JSON.stringify({ playerId: this.playerId, ...body }),
    });
  }

  /** Hedh një letër drejt tokës (hapi 1 i radhës) */
  discard(card: Card): void {
    this.send('discard', { card });
  }

  /** Tërheq 1 letër nga grumbulli i mbyllur (hapi 2, variant A) */
  drawClosed(): void {
    this.send('drawClosed', {});
  }

  /** Merr GJITHË letrat e hapura (hapi 2, variant B - vetëm nëse je i shtruar) */
  takeOpenPile(): void {
    this.send('takeOpenPile', {});
  }

  /** Merr letrat nga toka duke filluar te një letër specifike (dhe të gjitha mbi të, deri në maja) */
  takeFromOpenPile(fromCard: Card): void {
    this.send('takeFromOpenPile', { card: fromCard });
  }

  /** Konfirmon që të gjitha letrat e marra nga toka u përdorën në kombinime */
  endForcedTurn(): void {
    this.send('endForcedTurn', {});
  }

  /** Hapja e parë: një ose disa kombinime njëkohësisht (>=25 pikë gjithsej) */
  openHand(meldGroups: Card[][]): void {
    this.send('openHand', { meldGroups });
  }

  /** Shton një kombinim të ri (vetëm pasi je i shtruar) */
  addMeld(cards: Card[]): void {
    this.send('addMeld', { cards });
  }

  /** Zgjat një kombinim ekzistues me 1 letër */
  extendMeld(meldId: string, card: Card): void {
    this.send('extendMeld', { meldId, card });
  }

  /** Kalon te raundi tjetër pasi njëri fitoi këtë raund */
  nextRound(): void {
    this.send('nextRound', {});
  }

  get myPlayerId(): string { return this.playerId; }
}