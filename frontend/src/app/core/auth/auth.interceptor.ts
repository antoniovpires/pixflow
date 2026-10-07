import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { API_BASE } from '../api/api.config';
import { AuthService } from './auth.service';

/**
 * - Adds "Authorization: Bearer <token>" to calls to our own API (never to other hosts).
 * - Skips /auth/*: login and register are public.
 * - A 401 on a protected call means the token is expired or invalid: sign the user out.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const isProtectedApiCall = request.url.startsWith(API_BASE) && !request.url.startsWith(`${API_BASE}/auth/`);
  const token = auth.token();

  const outgoing =
    isProtectedApiCall && token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;

  return next(outgoing).pipe(
    catchError((error: unknown) => {
      if (isProtectedApiCall && error instanceof HttpErrorResponse && error.status === 401) {
        auth.logout('expired');
      }
      return throwError(() => error);
    }),
  );
};
