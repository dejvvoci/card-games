import { ChangeDetectorRef, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../auth.service';

/** Formulari i hyrjes/regjistrimit — shfaqet vetëm kur s'ka përdorues të loguar */
@Component({
  selector: 'app-auth-widget',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="auth-widget">
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
    </div>
  `,
  styles: [`
    .auth-widget {
      max-width: 320px;
      margin: 0 auto;
      background: var(--color-surface);
      border: 1.5px solid var(--color-border-strong);
      border-radius: var(--radius-lg);
      padding: 20px 24px;
      box-shadow: var(--shadow-soft);
      font-family: var(--font-body);
    }
    .eyebrow {
      font-size: 12px;
      letter-spacing: 0.05em;
      text-transform: uppercase;
      color: var(--color-accent-deep);
      font-weight: var(--weight-semibold);
      margin: 0 0 12px;
      text-align: center;
    }
    .field-input {
      width: 100%;
      box-sizing: border-box;
      padding: 9px 12px;
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
      padding: 9px 14px;
      cursor: pointer;
      border: none;
    }
    .auth-btn.primary { background: var(--color-primary); color: var(--color-text-on-primary); }
    .auth-btn.primary:disabled { opacity: 0.5; cursor: not-allowed; }
    .auth-btn.link { background: transparent; color: var(--color-text-muted); text-decoration: underline; }
  `],
})
export class AuthWidgetComponent {

  mode: 'login' | 'register' = 'login';
  username = '';
  password = '';
  errorMessage: string | null = null;
  loading = false;

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
}
