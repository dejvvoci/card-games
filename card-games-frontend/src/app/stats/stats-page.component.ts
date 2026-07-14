import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService, GameStats } from '../auth/auth.service';

type GameTypeKey = 'PESEKATESH' | 'PESEQINDSH' | 'DERR';

interface GameFilterOption {
  key: GameTypeKey;
  label: string;
}

@Component({
  selector: 'app-stats-page',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="stats-page">
      <p class="eyebrow">Historiku yt</p>
      <h2>Statistikat</h2>

      <div class="game-filter">
        <button *ngFor="let g of games" [class.active]="selectedGame===g.key" (click)="selectGame(g.key)">
          {{ g.label }}
        </button>
      </div>

      <div class="stats-grid" *ngIf="stats as s; else statsLoading">
        <div class="stat-card">
          <span class="stat-value">{{ s.gamesPlayed }}</span>
          <span class="stat-label">Ndeshje të luajtura</span>
        </div>
        <div class="stat-card highlight">
          <span class="stat-value">{{ s.wins }}</span>
          <span class="stat-label">Vend i 1-rë</span>
        </div>
        <div class="stat-card">
          <span class="stat-value">{{ s.second }}</span>
          <span class="stat-label">Vend i 2-të</span>
        </div>
        <div class="stat-card" *ngIf="selectedGame==='PESEKATESH' || selectedGame==='DERR'">
          <span class="stat-value">{{ s.third }}</span>
          <span class="stat-label">Vend i 3-të</span>
        </div>
        <div class="stat-card" *ngIf="selectedGame==='PESEKATESH' || selectedGame==='DERR'">
          <span class="stat-value">{{ s.fourth }}</span>
          <span class="stat-label">Vend i 4-t</span>
        </div>
      </div>
      <ng-template #statsLoading><p class="hint">Duke ngarkuar...</p></ng-template>
    </div>
  `,
  styles: [`
    .stats-page {
      max-width: 640px;
      margin: 0 auto;
      text-align: center;
    }
    .eyebrow {
      font-size: 13px;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      color: var(--color-accent-deep);
      font-weight: var(--weight-semibold);
      margin: 0 0 8px;
    }
    h2 {
      font-family: var(--font-display);
      font-weight: var(--weight-semibold);
      font-size: 30px;
      color: var(--color-primary-deep);
      margin: 0 0 24px;
    }
    .game-filter {
      display: flex;
      justify-content: center;
      gap: 10px;
      margin-bottom: 28px;
    }
    .game-filter button {
      font-family: var(--font-body);
      font-size: 13px;
      font-weight: var(--weight-semibold);
      padding: 9px 18px;
      border-radius: var(--radius-pill);
      border: 1.5px solid var(--color-border-strong);
      background: var(--color-surface);
      color: var(--color-text);
      cursor: pointer;
      transition: all 0.15s ease;
    }
    .game-filter button.active {
      background: var(--color-primary);
      color: var(--color-text-on-primary);
      border-color: var(--color-primary);
    }
    .stats-grid {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      justify-content: center;
    }
    .stat-card {
      background: var(--color-surface);
      border: 1.5px solid var(--color-border-strong);
      border-radius: var(--radius-lg);
      padding: 20px 26px;
      min-width: 120px;
      box-shadow: var(--shadow-soft);
    }
    .stat-card.highlight { border-color: var(--color-accent); }
    .stat-value {
      display: block;
      font-family: var(--font-display);
      font-weight: var(--weight-semibold);
      font-size: 34px;
      color: var(--color-primary-deep);
    }
    .stat-card.highlight .stat-value { color: var(--color-accent-deep); }
    .stat-label {
      display: block;
      font-size: 12px;
      color: var(--color-text-muted);
      margin-top: 4px;
    }
    .hint { font-size: 13px; color: var(--color-text-muted); }
  `],
})
export class StatsPageComponent implements OnInit {

  games: GameFilterOption[] = [
    { key: 'PESEKATESH', label: 'Pesëkatësh' },
    { key: 'PESEQINDSH', label: 'Peseqindsh' },
    { key: 'DERR', label: 'Derri në Dorë' },
  ];

  selectedGame: GameTypeKey = 'PESEKATESH';
  stats: GameStats | null = null;

  constructor(private auth: AuthService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.loadStats();
  }

  selectGame(game: GameTypeKey): void {
    if (this.selectedGame === game) return;
    this.selectedGame = game;
    this.loadStats();
  }

  private async loadStats(): Promise<void> {
    this.stats = null;
    const result = await this.auth.getStats(this.selectedGame);
    this.stats = result;
    this.cdr.markForCheck();
  }
}
