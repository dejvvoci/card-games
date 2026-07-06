import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthWidgetComponent } from '../auth/auth-widget/auth-widget.component';

interface GameOption {
  id: string;
  name: string;
  description: string;
  route: string;
  cornerLabel: string;   // p.sh. "4P" (si vlerë e një letre, por për numrin e lojtarëve)
  centerIcon: string;    // simboli i madh qendror i "letrës"
  accent: 'primary' | 'accent';
  tilt: number;          // rrotullim i lehtë si letra e shpërndara në tavolinë
}

@Component({
  selector: 'app-game-selector',
  standalone: true,
  imports: [CommonModule, AuthWidgetComponent],
  template: `
    <div class="lobby">
      <p class="eyebrow">Trashëgimi shqiptare, e rimenduar</p>
      <h1>Zgjidh letrën tënde</h1>
      <p class="subtitle">Çdo lojë është një tavolinë e vetën — kliko kartën për të hyrë.</p>

      <app-auth-widget></app-auth-widget>

      <div class="card-spread">
        <div
          class="game-card"
          [class.accent-primary]="g.accent==='primary'"
          [class.accent-accent]="g.accent==='accent'"
          [style.transform]="'rotate(' + g.tilt + 'deg)'"
          *ngFor="let g of games"
          (click)="play(g)">

          <div class="corner corner-top">
            <span class="corner-label">{{ g.cornerLabel }}</span>
            <span class="corner-icon">{{ g.centerIcon }}</span>
          </div>

          <div class="card-center">
            <span class="center-icon">{{ g.centerIcon }}</span>
            <h2>{{ g.name }}</h2>
            <p>{{ g.description }}</p>
          </div>

          <div class="corner corner-bottom">
            <span class="corner-label">{{ g.cornerLabel }}</span>
            <span class="corner-icon">{{ g.centerIcon }}</span>
          </div>

          <button class="cta-btn" type="button">Luaj tani →</button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .lobby {
      min-height: 100vh;
      padding: 64px 24px 80px;
      text-align: center;
      background: var(--color-bg);
      color: var(--color-text);
      font-family: var(--font-body);
      box-sizing: border-box;
    }
    .eyebrow {
      font-size: 13px;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      color: var(--color-accent-deep);
      font-weight: var(--weight-semibold);
      margin: 0 0 12px;
    }
    h1 {
      font-family: var(--font-display);
      font-weight: var(--weight-semibold);
      font-size: 42px;
      color: var(--color-primary-deep);
      margin: 0;
    }
    .subtitle {
      font-weight: var(--weight-regular);
      color: var(--color-text-muted);
      font-size: 16px;
      margin: 14px 0 0;
    }

    .card-spread {
      display: flex;
      justify-content: center;
      align-items: center;
      gap: 16px;
      max-width: 640px;
      margin: 72px auto 0;
      flex-wrap: wrap;
    }

    .game-card {
      position: relative;
      width: 250px;
      aspect-ratio: 5 / 7;
      background: var(--color-surface);
      border: 1.5px solid var(--color-border-strong);
      border-radius: var(--radius-lg);
      padding: 22px;
      cursor: pointer;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      box-shadow: var(--shadow-soft);
      transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
    }
    .game-card:hover {
      transform: rotate(0deg) translateY(-14px) !important;
      z-index: 2;
    }
    .accent-primary:hover { border-color: var(--color-primary); box-shadow: 0 18px 40px -10px rgba(15, 81, 50, 0.32); }
    .accent-accent:hover { border-color: var(--color-accent); box-shadow: 0 18px 40px -10px rgba(193, 110, 81, 0.36); }

    .corner {
      display: flex;
      flex-direction: column;
      align-items: center;
      line-height: 1;
      width: fit-content;
    }
    .corner-bottom { align-self: flex-end; transform: rotate(180deg); }
    .corner-label {
      font-family: var(--font-display);
      font-weight: var(--weight-semibold);
      font-size: 16px;
    }
    .corner-icon { font-size: 14px; margin-top: 3px; }
    .accent-primary .corner-label, .accent-primary .corner-icon { color: var(--color-primary-deep); }
    .accent-accent .corner-label, .accent-accent .corner-icon { color: var(--color-accent-deep); }

    .card-center {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      gap: 10px;
    }
    .center-icon {
      font-family: var(--font-display);
      font-size: 56px;
      line-height: 1;
    }
    .accent-primary .center-icon { color: var(--color-primary); }
    .accent-accent .center-icon { color: var(--color-accent); }

    h2 {
      font-family: var(--font-display);
      font-weight: var(--weight-semibold);
      font-size: 21px;
      margin: 0;
    }
    .accent-primary h2 { color: var(--color-primary-deep); }
    .accent-accent h2 { color: var(--color-accent-deep); }

    .game-card p {
      font-weight: var(--weight-regular);
      color: var(--color-text-muted);
      font-size: 13px;
      margin: 0;
      line-height: 1.5;
    }

    .cta-btn {
      align-self: center;
      font-family: var(--font-body);
      font-size: 13px;
      font-weight: var(--weight-semibold);
      border: none;
      padding: 9px 20px;
      border-radius: var(--radius-pill);
      cursor: pointer;
      transition: filter 0.15s ease;
    }
    .cta-btn:hover { filter: brightness(1.08); }
    .accent-primary .cta-btn { background: var(--color-primary); color: var(--color-text-on-primary); }
    .accent-accent .cta-btn { background: var(--color-accent); color: var(--color-text-on-accent); }
  `],
})
export class GameSelectorComponent {

  // Shto këtu çdo lojë të re: cornerLabel si "vlera" e letrës, centerIcon si simboli i saj.
  games: GameOption[] = [
    {
      id: 'pesekatesh',
      name: 'Pesëkatësh',
      description: 'Trick-taking klasik për 4 lojtarë, me Kate 1-4 dhe Shtatat.',
      route: '/pesekatesh',
      cornerLabel: '4P',
      centerIcon: '♠',
      accent: 'primary',
      tilt: -4,
    },
    {
      id: 'peseqindsh',
      name: 'Peseqindsh',
      description: 'Rami/Burako 1v1 me Dysha si Xhoker — deri në 500 pikë.',
      route: '/peseqindsh',
      cornerLabel: '2P',
      centerIcon: '♦',
      accent: 'accent',
      tilt: 4,
    },
  ];

  constructor(private router: Router) {}

  play(game: GameOption): void {
    this.router.navigateByUrl(game.route);
  }
}