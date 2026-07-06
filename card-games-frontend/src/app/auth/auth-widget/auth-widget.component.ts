import { ChangeDetectorRef, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService, GameStats } from '../auth.service';

type GameTypeKey = 'PESEKATESH' | 'PESEQINDSH';

@Component({
  selector: 'app-auth-widget',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="auth-widget">
      <ng-container *ngIf="!auth.username(); else loggedIn">
        <div class="auth-form">
          <p class="eyebrow">{{ mode === 'login' ? 'Hyr në llogari' : 'Krijo llogari' }}</p>
          <input class="field-input" type="text" placeholder="Emri i përdoruesit" [(ngModel)]="username" maxlength="20" />
          <input class="field-input" type="password" placeholder="Fjalëkalimi" [(ngModel)]="password" maxlength="60" />
          <p class="error-text" *ngIf="errorMessage">{{ errorMessage }}</p>
          <div class="auth-actions">
            <button class="auth-btn primary" [disabled]="!canSubmit || loading" (click)="submit()">
              {{ mode === 'login' ? 'Hyr' : 'Regjistrohu' }}
            </button>
            <button class="auth-btn link" (click)="toggleMode()">
              {{ mode === 'login' ? 'Nuk ke llogari? Regjistrohu' : 'Ke tashmë llogari? Hyr' }}
            </button>
          </div>
        </div>
      </ng-container>

      <ng-template #loggedIn>
        <div class="account-panel">
          <p class="greeting">👤 {{ auth.username() }}</p>
          <div class="stats-tabs">
            <button [class.active]="statsGame==='PESEKATESH'" (click)="showStats('PESEKATESH')">Pesëkatësh</button>
            <button [class.active]="statsGame==='PESEQINDSH'" (click)="showStats('PESEQINDSH')">Peseqindsh</button>
          </div>
          <div class="stats-box" *ngIf="statsGame as g">
            <ng-container *ngIf="stats[g] as s; else statsLoading">
              <div class="stat"><span>{{ s.gamesPlayed }}</span>Ndeshje</div>
              <div class="stat"><span>{{ s.wins }}</span>Vend 1</div>
              <div class="stat" *ngIf="g === 'PESEKATESH'"><span>{{ s.second }}</span>Vend 2</div>
              <div class="stat" *ngIf="g === 'PESEKATESH'"><span>{{ s.third }}</span>Vend 3</div>
              <div class="stat" *ngIf="g === 'PESEKATESH'"><span>{{ s.fourth }}</span>Vend 4</div>
              <div class="stat" *ngIf="g === 'PESEQINDSH'"><span>{{ s.second }}</span>Vend 2</div>
            </ng-container>
            <ng-template #statsLoading><p class="hint">Duke ngarkuar...</p></ng-template>
          </div>
          <button class="auth-btn link" (click)="logout()">Dil</button>
        </div>
      </ng-template>
    </div>
  `,
  styles: [`
    .auth-widget {
      max-width: 320px;
      margin: 0 auto 24px;
      background: var(--color-surface);
      border: 1.5px solid var(--color-border-strong);
      border-radius: var(--radius-lg);
      padding: 16px 20px;
      box-shadow: var(--shadow-soft);
      font-family: var(--font-body);
    }
    .eyebrow {
      font-size: 12px;
      letter-spacing: 0.05em;
      text-transform: uppercase;
      color: var(--color-accent-deep);
      font-weight: var(--weight-semibold);
      margin: 0 0 10px;
      text-align: center;
    }
    .field-input {
      width: 100%;
      box-sizing: border-box;
      padding: 8px 12px;
      margin-bottom: 8px;
      border-radius: var(--radius-md, 8px);
      border: 1.5px solid var(--color-border-strong);
      font-family: var(--font-body);
      font-size: 13px;
    }
    .error-text { color: #b3261e; font-size: 12px; margin: 0 0 8px; }
    .auth-actions { display: flex; flex-direction: column; gap: 6px; }
    .auth-btn {
      font-family: var(--font-body);
      font-size: 13px;
      font-weight: var(--weight-semibold);
      border-radius: var(--radius-pill);
      padding: 8px 14px;
      cursor: pointer;
      border: none;
    }
    .auth-btn.primary { background: var(--color-primary); color: var(--color-text-on-primary); }
    .auth-btn.primary:disabled { opacity: 0.5; cursor: not-allowed; }
    .auth-btn.link { background: transparent; color: var(--color-text-muted); text-decoration: underline; }
    .greeting { text-align: center; font-weight: var(--weight-semibold); margin: 0 0 10px; }
    .stats-tabs { display: flex; gap: 6px; justify-content: center; margin-bottom: 10px; }
    .stats-tabs button {
      font-size: 12px;
      padding: 6px 10px;
      border-radius: var(--radius-pill);
      border: 1.5px solid var(--color-border-strong);
      background: transparent;
      cursor: pointer;
    }
    .stats-tabs button.active { background: var(--color-accent); color: var(--color-text-on-accent); border-color: var(--color-accent); }
    .stats-box { display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; margin-bottom: 12px; }
    .stat { text-align: center; font-size: 11px; color: var(--color-text-muted); }
    .stat span { display: block; font-family: var(--font-display); font-size: 18px; color: var(--color-primary-deep); }
    .hint { font-size: 12px; color: var(--color-text-muted); text-align: center; }
  `],
})
export class AuthWidgetComponent {

  mode: 'login' | 'register' = 'login';
  username = '';
  password = '';
  errorMessage: string | null = null;
  loading = false;

  statsGame: GameTypeKey | null = null;
  stats: Partial<Record<GameTypeKey, GameStats>> = {};

  constructor(public auth: AuthService, private cdr: ChangeDetectorRef) {}

  get canSubmit(): boolean {
    return this.username.trim().length > 0 && this.password.length > 0;
  }

  toggleMode(): void {
    this.mode = this.mode === 'login' ? 'register' : 'login';
    this.errorMessage = null;
  }

  async submit(): Promise<void> {
    if (!this.canSubmit) return;
    this.loading = true;
    this.errorMessage = null;
    try {
      if (this.mode === 'login') {
        await this.auth.login(this.username.trim(), this.password);
      } else {
        await this.auth.register(this.username.trim(), this.password);
      }
      this.username = '';
      this.password = '';
    } catch (err: any) {
      this.errorMessage = err?.error ?? 'Ndodhi një gabim. Provo sërish.';
    } finally {
      this.loading = false;
      this.cdr.markForCheck();
    }
  }

  logout(): void {
    this.auth.logout();
    this.statsGame = null;
    this.stats = {};
  }

  async showStats(game: GameTypeKey): Promise<void> {
    this.statsGame = game;
    if (this.stats[game]) return;
    this.stats[game] = await this.auth.getStats(game);
    this.cdr.markForCheck();
  }
}
