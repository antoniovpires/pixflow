// Types mirroring the backend DTOs (Java records). Keep them in sync by hand.

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
}

export interface RegisterResponse {
  userId: string;
  accountId: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  /** Lifetime in seconds. */
  expiresIn: number;
}

export interface Account {
  id: string;
  userId: string;
  balance: number;
  currency: string;
}

export type TransferStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'CANCELLED' | 'REVERSED';

export interface Transfer {
  id: string;
  status: TransferStatus;
  amount: number;
  sourceAccountId: string;
  targetAccountId: string;
}

/** RFC 7807 body returned by the backend for every error. */
export interface ProblemDetail {
  status?: number;
  detail?: string;
  /** Field name -> message, present on 400 validation errors. */
  errors?: Record<string, string>;
}

export type KeyType = 'CPF' | 'EMAIL' | 'PHONE_NUMBER';

export interface PixKey {
  id: string;
  accountId: string;
  keyValue: string;
  keyType: KeyType;
}
