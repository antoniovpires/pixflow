import { HttpErrorResponse } from '@angular/common/http';

import { ProblemDetail } from './models';

export interface ApiError {
  /** A message that is safe to show to the user. */
  message: string;
  /** Per-field messages (400 validation errors), keyed by request field name. */
  fields: Record<string, string>;
  status: number;
}

/** Turns whatever the HTTP layer threw into something a form can display. */
export function toApiError(error: unknown, fallback = 'Something went wrong. Please try again.'): ApiError {
  if (!(error instanceof HttpErrorResponse)) {
    return { message: fallback, fields: {}, status: -1 };
  }
  if (error.status === 0) {
    return { message: "We can't reach the server. Please try again in a moment.", fields: {}, status: 0 };
  }
  // Our backend always answers with a JSON problem. An HTML/plain-text body (or a 502/503/504)
  // means the request never reached it: the server is down or the dev proxy is not set up.
  const isProblem = typeof error.error === 'object' && error.error !== null;
  if (!isProblem || [502, 503, 504].includes(error.status)) {
    return { message: "We can't reach the server. Please try again in a moment.", fields: {}, status: 0 };
  }
  const problem = error.error as ProblemDetail;
  return {
    message: problem.detail || fallback,
    fields: problem.errors ?? {},
    status: error.status,
  };
}
