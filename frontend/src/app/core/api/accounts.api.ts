import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { API_BASE } from './api.config';
import { Account } from './models';

@Injectable({ providedIn: 'root' })
export class AccountsApi {
  private readonly http = inject(HttpClient);

  /** The backend returns only the caller's account (one account per user). */
  me(): Observable<Account> {
    return this.http.get<Account[]>(`${API_BASE}/accounts`).pipe(map((accounts) => accounts[0]));
  }
}
