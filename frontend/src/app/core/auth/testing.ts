/** Builds an unsigned, JWT-shaped string for tests. The frontend only reads the payload. */
export function fakeJwt(payload: { sub?: string; exp?: number }): string {
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(payload)}.signature`;
}

/** Seconds since epoch, `offsetSeconds` from now. */
export function inSeconds(offsetSeconds: number): number {
  return Math.floor(Date.now() / 1000) + offsetSeconds;
}
