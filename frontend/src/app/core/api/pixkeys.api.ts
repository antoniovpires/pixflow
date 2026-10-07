import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE } from './api.config';
import { KeyType, PixKey } from './models';

@Injectable({ providedIn: 'root' })
export class PixKeysApi {
  private readonly http = inject(HttpClient);

  list(): Observable<PixKey[]> {
    return this.http.get<PixKey[]>(`${API_BASE}/pixkeys`);
  }

  create(keyValue: string, keyType: KeyType): Observable<PixKey> {
    return this.http.post<PixKey>(`${API_BASE}/pixkeys`, { keyValue, keyType });
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/pixkeys/${id}`);
  }
}
