import { Injectable } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { DerrStateView } from '../models/game-state.model';

@Injectable({ providedIn: 'root' })
export class DerrWebSocketService {

  private client!: Client;
  private playerId!: string;
  private roomId!: string;

  private stateSubject = new BehaviorSubject<DerrStateView | null>(null);
  public state$: Observable<DerrStateView | null> = this.stateSubject.asObservable();

  private errorSubject = new Subject<string>();
  public errors$: Observable<string> = this.errorSubject.asObservable();

  /** true kur lidhja STOMP është aktive */
  private connectionStatus = new BehaviorSubject<boolean>(false);
  public connectionStatus$ = this.connectionStatus.asObservable();

  connect(roomId: string, playerId: string, username: string, soloVsBots: boolean, authToken: string | null = null): void {
    this.roomId = roomId;
    this.playerId = playerId;

    this.client = new Client({
      // WebSocket nativ i browser-it (jo SockJS) -> s'ka nevojë për asnjë polyfill
      brokerURL: `${environment.wsEndpoint}?playerId=${encodeURIComponent(playerId)}`,
      reconnectDelay: 3000,
      onConnect: () => {
        this.connectionStatus.next(true);
        this.client.subscribe('/user/queue/derr-state', (msg: IMessage) => {
          this.stateSubject.next(JSON.parse(msg.body));
        });
        this.client.subscribe('/user/queue/derr-errors', (msg: IMessage) => {
          this.errorSubject.next(msg.body);
        });
        // authToken lidh Player-in me llogarinë e loguar, për historikun e statistikave
        this.client.publish({
          destination: `/app/derr/join/${roomId}`,
          body: JSON.stringify({ playerId, username, soloVsBots, authToken }),
        });
      },
      onWebSocketClose: () => {
        this.connectionStatus.next(false);
      },
    });
    this.client.activate();
  }

  disconnect(): void {
    this.client?.deactivate();
  }

  /** Tërheq (vjedh) letrën në pozicionin e zgjedhur nga dora e verbër e holder-it */
  draw(cardIndex: number): void {
    this.client.publish({
      destination: `/app/derr/draw/${this.roomId}`,
      body: JSON.stringify({ playerId: this.playerId, cardIndex }),
    });
  }

  get myPlayerId(): string { return this.playerId; }
}
