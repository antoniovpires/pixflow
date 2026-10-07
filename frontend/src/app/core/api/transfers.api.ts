import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE } from './api.config';
import { Transfer } from './models';

@Injectable({ providedIn: 'root' })
export class TransfersApi {
  private readonly http = inject(HttpClient);

  /** Transfers the caller sent or received, newest first. */
  list(): Observable<Transfer[]> {
    return this.http.get<Transfer[]>(`${API_BASE}/transfers`);
  }
}
