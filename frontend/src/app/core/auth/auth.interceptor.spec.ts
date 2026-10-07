import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { authInterceptor } from './auth.interceptor';
import { fakeJwt, inSeconds } from './testing';

const TOKEN = fakeJwt({ sub: 'user-1', exp: inSeconds(900) });

function setup(withSession = true) {
  sessionStorage.clear();
  if (withSession) sessionStorage.setItem('pixflow.access-token', TOKEN);
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(withInterceptors([authInterceptor])),
      provideHttpClientTesting(),
    ],
  });
  return {
    http: TestBed.inject(HttpClient),
    controller: TestBed.inject(HttpTestingController),
    router: TestBed.inject(Router),
  };
}

describe('authInterceptor', () => {
  it('adds the bearer token to our API calls', () => {
    const { http, controller } = setup();
    http.get('/api/accounts').subscribe();
    expect(controller.expectOne('/api/accounts').request.headers.get('Authorization')).toBe(`Bearer ${TOKEN}`);
  });

  it('does not send the token to login or register', () => {
    const { http, controller } = setup();
    http.post('/api/auth/login', {}).subscribe();
    expect(controller.expectOne('/api/auth/login').request.headers.has('Authorization')).toBe(false);
  });

  it('never leaks the token to another host', () => {
    const { http, controller } = setup();
    http.get('https://example.com/data').subscribe();
    expect(controller.expectOne('https://example.com/data').request.headers.has('Authorization')).toBe(false);
  });

  it('sends no header when signed out', () => {
    const { http, controller } = setup(false);
    http.get('/api/accounts').subscribe();
    expect(controller.expectOne('/api/accounts').request.headers.has('Authorization')).toBe(false);
  });

  it('signs the user out when a protected call returns 401', () => {
    const { http, controller, router } = setup();
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    http.get('/api/accounts').subscribe({ error: () => undefined });
    controller.expectOne('/api/accounts').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reason: 'expired' } });
  });

  it('does not sign out on a 401 from the login call itself (wrong password)', () => {
    const { http, controller, router } = setup(false);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    http.post('/api/auth/login', {}).subscribe({ error: () => undefined });
    controller.expectOne('/api/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(navigate).not.toHaveBeenCalled();
  });
});
