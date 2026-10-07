import { HttpErrorResponse } from '@angular/common/http';

import { toApiError } from './api-error';

const http = (status: number, body: unknown) => new HttpErrorResponse({ status, error: body });

describe('toApiError', () => {
  it('uses the detail of a backend problem response', () => {
    const error = toApiError(http(401, { status: 401, detail: 'Invalid email or password' }));
    expect(error).toEqual({ message: 'Invalid email or password', fields: {}, status: 401 });
  });

  it('exposes per-field validation errors', () => {
    const error = toApiError(http(400, { detail: 'Validation failed', errors: { email: 'must be a well-formed email address' } }));
    expect(error.fields).toEqual({ email: 'must be a well-formed email address' });
  });

  it('says the server is unreachable when there is no response at all', () => {
    expect(toApiError(http(0, null)).message).toContain("can't reach the server");
  });

  it('says the server is unreachable for an HTML 404 (dev proxy missing) or a gateway error', () => {
    expect(toApiError(http(404, '<!DOCTYPE html><html></html>')).message).toContain("can't reach the server");
    expect(toApiError(http(502, '<html>Bad Gateway</html>')).message).toContain("can't reach the server");
    expect(toApiError(http(503, { detail: 'x' })).message).toContain("can't reach the server");
  });

  it('keeps a real backend 404 message (the body is a JSON problem)', () => {
    expect(toApiError(http(404, { detail: 'Pix key not found' })).message).toBe('Pix key not found');
  });

  it('falls back for non-HTTP errors', () => {
    expect(toApiError(new Error('boom'), 'Fallback').message).toBe('Fallback');
  });
});
