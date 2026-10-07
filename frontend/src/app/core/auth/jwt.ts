// The payload of a JWT is only base64url-encoded JSON: readable by anyone, which is why the
// server puts nothing sensitive in it. The browser reads it ONLY to know when the token
// expires. It never trusts it for security: the server verifies the signature on every request.

interface JwtPayload {
  sub?: string;
  exp?: number;
}

export function decodeJwt(token: string): JwtPayload | null {
  try {
    const payload = token.split('.')[1];
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    return JSON.parse(atob(padded)) as JwtPayload;
  } catch {
    return null;
  }
}

/** Milliseconds since epoch at which the token expires, or null if unreadable. */
export function expiresAt(token: string): number | null {
  const exp = decodeJwt(token)?.exp;
  return typeof exp === 'number' ? exp * 1000 : null;
}

export function isExpired(token: string, now = Date.now()): boolean {
  const at = expiresAt(token);
  return at === null || at <= now;
}
