import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AuthService } from './auth.service';
import { fakeJwt, inSeconds } from './testing';

const STORAGE_KEY = 'pixflow.access-token';

function setup() {
  TestBed.configureTestingModule({
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });
  return {
    auth: TestBed.inject(AuthService),
    http: TestBed.inject(HttpTestingController),
    router: TestBed.inject(Router),
  };
}

describe('AuthService', () => {
  beforeEach(() => sessionStorage.clear());
  afterEach(() => vi.useRealTimers());

  it('starts signed out', () => {
    expect(setup().auth.isAuthenticated()).toBe(false);
  });

  it('stores the token after a successful login', () => {
    const { auth, http } = setup();
    const token = fakeJwt({ sub: 'user-1', exp: inSeconds(900) });

    auth.login({ email: 'ada@pixflow.dev', password: 'correct-horse-9' }).subscribe();
    const request = http.expectOne('/api/auth/login');
    expect(request.request.method).toBe('POST');
    request.flush({ accessToken: token, tokenType: 'Bearer', expiresIn: 900 });

    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.token()).toBe(token);
    expect(sessionStorage.getItem(STORAGE_KEY)).toBe(token);
  });

  it('stays signed out when the login is rejected', () => {
    const { auth, http } = setup();

    auth.login({ email: 'ada@pixflow.dev', password: 'wrong' }).subscribe({ error: () => undefined });
    http.expectOne('/api/auth/login').flush({ detail: 'Invalid email or password' }, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('registers, then logs in with the same credentials', () => {
    const { auth, http } = setup();
    const token = fakeJwt({ sub: 'user-1', exp: inSeconds(900) });

    auth.register({ name: 'Ada', email: 'ada@pixflow.dev', password: 'correct-horse-9' }).subscribe();
    http.expectOne('/api/auth/register').flush({ userId: 'u', accountId: 'a' }, { status: 201, statusText: 'Created' });
    const login = http.expectOne('/api/auth/login');
    expect(login.request.body).toEqual({ email: 'ada@pixflow.dev', password: 'correct-horse-9' });
    login.flush({ accessToken: token, tokenType: 'Bearer', expiresIn: 900 });

    expect(auth.isAuthenticated()).toBe(true);
  });

  it('restores a still-valid session after a page refresh', () => {
    sessionStorage.setItem(STORAGE_KEY, fakeJwt({ sub: 'user-1', exp: inSeconds(600) }));
    expect(setup().auth.isAuthenticated()).toBe(true);
  });

  it('ignores an expired token left in storage', () => {
    sessionStorage.setItem(STORAGE_KEY, fakeJwt({ sub: 'user-1', exp: inSeconds(-60) }));
    expect(setup().auth.isAuthenticated()).toBe(false);
  });

  it('logout clears the session and goes to /login', () => {
    sessionStorage.setItem(STORAGE_KEY, fakeJwt({ sub: 'user-1', exp: inSeconds(600) }));
    const { auth, router } = setup();
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    auth.logout();

    expect(auth.isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login'], {});
  });

  it('signs the user out automatically when the token expires', () => {
    vi.useFakeTimers();
    sessionStorage.setItem(STORAGE_KEY, fakeJwt({ sub: 'user-1', exp: inSeconds(30) }));
    const { auth, router } = setup();
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    vi.advanceTimersByTime(31_000);

    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reason: 'expired' } });
  });
});
