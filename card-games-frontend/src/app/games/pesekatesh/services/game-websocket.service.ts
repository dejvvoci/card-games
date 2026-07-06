import { Injectable } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { GameStateView } from '../models/game-state.model';
import { Card } from '../../../models/card.model';
import { environment } from '../../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class GameWebSocketService {

  private client!: Client;
  private playerId!: string;
  private roomId!: string;
  private connected = false;

  /** Gjendja e plotë e lojës (e personalizuar për këtë lojtar) */
  private gameStateSubject = new BehaviorSubject<GameStateView | null>(null);
  public gameState$: Observable<GameStateView | null> = this.gameStateSubject.asObservable();

  /** Mesazhe gabimi private (p.sh. "lëvizje e pavlefshme") */
  private errorSubject = new Subject<string>();
  public errors$: Observable<string> = this.errorSubject.asObservable();

  /** true kur lidhja STOMP është aktive */
  private connectionStatus = new BehaviorSubject<boolean>(false);
  public connectionStatus$ = this.connectionStatus.asObservable();

  connect(roomId: string, playerId: string, username: string, soloVsBots: boolean, shtatatEveryRound: boolean,
          authToken: string | null = null): void {
    this.roomId = roomId;
    this.playerId = playerId;

    this.client = new Client({
      // WebSocket nativ i browser-it (jo SockJS) -> s'ka nevojë për asnjë polyfill
      brokerURL: `${environment.wsEndpoint}?playerId=${encodeURIComponent(playerId)}`,
      reconnectDelay: 3000,
      onConnect: () => {
        this.connected = true;
        this.connectionStatus.next(true);

        // Abonim privat: dora ime + gjendja e personalizuar
        this.client.subscribe(`/user/queue/game-state`, (msg: IMessage) => {
          this.gameStateSubject.next(JSON.parse(msg.body));
        });

        // Abonim privat: gabime validimi
        this.client.subscribe(`/user/queue/errors`, (msg: IMessage) => {
          this.errorSubject.next(msg.body);
        });

        // Hyrje në dhomë (authToken lidh Player-in me llogarinë e loguar, për historikun e statistikave)
        this.client.publish({
          destination: `/app/join/${roomId}`,
          body: JSON.stringify({ playerId, username, soloVsBots, shtatatEveryRound, authToken }),
        });
      },
      onStompError: (frame) => {
        console.error('STOMP error', frame);
      },
      onWebSocketClose: () => {
        this.connected = false;
        this.connectionStatus.next(false);
      },
    });

    this.client.activate();
  }

  disconnect(): void {
    this.client?.deactivate();
  }

  /** Hedh një letër (funksionon njësoj për Kate 1-4 dhe Katin 5 - Shtatat) */
  playCard(card: Card): void {
    this.client.publish({
      destination: `/app/play/${this.roomId}`,
      body: JSON.stringify({ playerId: this.playerId, suit: card.suit, rank: card.rank }),
    });
  }

  /** Konfirmon "Pass" te Shtatat kur s'ka lëvizje të vlefshme */
  passTurn(): void {
    this.client.publish({
      destination: `/app/pass/${this.roomId}`,
      body: JSON.stringify({ playerId: this.playerId }),
    });
  }

  /** Transferon një letër te lojtari i bllokuar (rregulli i ndarjes te Shtatat) */
  tradeCard(card: Card, targetPlayerId: string): void {
    this.client.publish({
      destination: `/app/trade/${this.roomId}`,
      body: JSON.stringify({
        playerId: this.playerId,
        suit: card.suit,
        rank: card.rank,
        targetPlayerId,
      }),
    });
  }

  /** Konfirmon gatishmërinë për raundin tjetër (pas ROUND_FINISHED) */
  markReady(): void {
    this.client.publish({
      destination: `/app/ready/${this.roomId}`,
      body: JSON.stringify({ playerId: this.playerId }),
    });
  }

  get myPlayerId(): string {
    return this.playerId;
  }
}