import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

export interface AuthResponse {
  token: string;
  username: string;
}

export interface GameStats {
  gamesPlayed: number;
  wins: number;
  second: number;
  third: number;
  fourth: number;
}

const STORAGE_KEY = 'card-games-auth';

@Injectable({ providedIn: 'root' })
export class AuthService {

  /** Përdoruesi i loguar aktualisht (null nëse je "mysafir") */
  readonly username = signal<string | null>(null);

  private token: string | null = null;

  constructor(private http: HttpClient) {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved) {
      try {
        const parsed: AuthResponse = JSON.parse(saved);
        this.token = parsed.token;
        this.username.set(parsed.username);
      } catch {
        localStorage.removeItem(STORAGE_KEY);
      }
    }
  }

  async register(username: string, password: string): Promise<void> {
    const res = await firstValueFrom(
      this.http.post<AuthResponse>(`${environment.apiBaseUrl}/auth/register`, { username, password }),
    );
    this.applySession(res);
  }

  async login(username: string, password: string): Promise<void> {
    const res = await firstValueFrom(
      this.http.post<AuthResponse>(`${environment.apiBaseUrl}/auth/login`, { username, password }),
    );
    this.applySession(res);
  }

  logout(): void {
    this.token = null;
    this.username.set(null);
    localStorage.removeItem(STORAGE_KEY);
  }

  getToken(): string | null {
    return this.token;
  }

  isLoggedIn(): boolean {
    return this.token !== null;
  }

  async getStats(gameType: 'PESEKATESH' | 'PESEQINDSH' | 'DERR'): Promise<GameStats> {
    return firstValueFrom(
      this.http.get<GameStats>(`${environment.apiBaseUrl}/stats/${gameType}`, {
        headers: { Authorization: `Bearer ${this.token}` },
      }),
    );
  }

  private applySession(res: AuthResponse): void {
    this.token = res.token;
    this.username.set(res.username);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(res));
  }
}
