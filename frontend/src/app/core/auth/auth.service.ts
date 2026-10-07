import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, switchMap, tap } from 'rxjs';

import { API_BASE } from '../api/api.config';
import { LoginRequest, RegisterRequest, TokenResponse } from '../api/models';
import { expiresAt, isExpired } from './jwt';

const STORAGE_KEY = 'pixflow.access-token';

/**
 * Holds the session. The access token lives in sessionStorage: it survives a page refresh
 * but dies with the tab. Trade-off: any script running on the page could read it (XSS), so
 * the app must never render untrusted HTML. A refresh-token flow (HttpOnly cookie) would be
 * the next hardening step, see docs/known-gaps.md (S1).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly _token = signal<string | null>(this.restore());
  private expiryTimer: ReturnType<typeof setTimeout> | undefined;

  /** The raw token, for the HTTP interceptor. */
  readonly token = this._token.asReadonly();

  constructor() {
    const token = this._token();
    if (token) this.scheduleExpiry(token);
  }

  isAuthenticated(): boolean {
    const token = this._token();
    return token !== null && !isExpired(token);
  }

  login(request: LoginRequest): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(`${API_BASE}/auth/login`, request)
      .pipe(tap((response) => this.start(response.accessToken)));
  }

  /** Creates the user and their account, then signs them in. */
  register(request: RegisterRequest): Observable<TokenResponse> {
    return this.http
      .post(`${API_BASE}/auth/register`, request)
      .pipe(switchMap(() => this.login({ email: request.email, password: request.password })));
  }

  logout(reason?: 'expired'): void {
    this.clear();
    void this.router.navigate(['/login'], reason ? { queryParams: { reason } } : {});
  }

  private start(token: string): void {
    this._token.set(token);
    this.persist(token);
    this.scheduleExpiry(token);
  }

  private clear(): void {
    clearTimeout(this.expiryTimer);
    this._token.set(null);
    this.persist(null);
  }

  // Signs the user out at the moment the token expires, instead of waiting for a 401.
  private scheduleExpiry(token: string): void {
    clearTimeout(this.expiryTimer);
    const at = expiresAt(token);
    if (at === null) return;
    const delay = at - Date.now();
    if (delay <= 0) {
      this.clear();
      return;
    }
    this.expiryTimer = setTimeout(() => this.logout('expired'), Math.min(delay, 2_147_000_000));
  }

  private restore(): string | null {
    try {
      const token = sessionStorage.getItem(STORAGE_KEY);
      return token && !isExpired(token) ? token : null;
    } catch {
      return null; // storage blocked (private mode, etc.)
    }
  }

  private persist(token: string | null): void {
    try {
      if (token) sessionStorage.setItem(STORAGE_KEY, token);
      else sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      /* ignore: the session just will not survive a refresh */
    }
  }
}
