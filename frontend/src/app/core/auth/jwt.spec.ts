import { decodeJwt, expiresAt, isExpired } from './jwt';
import { fakeJwt, inSeconds } from './testing';

describe('jwt helpers', () => {
  it('reads the claims from the payload', () => {
    const exp = inSeconds(900);
    expect(decodeJwt(fakeJwt({ sub: 'user-1', exp }))).toEqual({ sub: 'user-1', exp });
  });

  it('converts exp (seconds) to milliseconds', () => {
    const exp = inSeconds(900);
    expect(expiresAt(fakeJwt({ exp }))).toBe(exp * 1000);
  });

  it('knows whether a token has expired', () => {
    expect(isExpired(fakeJwt({ exp: inSeconds(60) }))).toBe(false);
    expect(isExpired(fakeJwt({ exp: inSeconds(-60) }))).toBe(true);
  });

  it('treats unreadable or exp-less tokens as expired instead of throwing', () => {
    expect(decodeJwt('not-a-jwt')).toBeNull();
    expect(isExpired('not-a-jwt')).toBe(true);
    expect(isExpired(fakeJwt({ sub: 'user-1' }))).toBe(true);
  });
});
