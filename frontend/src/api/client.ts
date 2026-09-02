import type { ApiErrorBody } from '../types/api';
import type { CsrfResponse } from '../types/auth';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');
let csrf: Pick<CsrfResponse, 'headerName' | 'token'> | null = null;
let gestionnaireExpirationSession: (() => void) | null = null;

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly validationErrors: Record<string, string> = {},
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export function effacerCsrf(): void {
  csrf = null;
}

export function apiUrl(path: string): string {
  return `${API_BASE_URL}${path}`;
}

export function definirGestionnaireExpirationSession(gestionnaire: (() => void) | null): void {
  gestionnaireExpirationSession = gestionnaire;
}

async function chargerCsrf(force = false): Promise<Pick<CsrfResponse, 'headerName' | 'token'>> {
  if (csrf && !force) return csrf;
  const response = await fetch(`${API_BASE_URL}/api/auth/csrf`, {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) throw await creerErreur(response);
  const data = (await response.json()) as CsrfResponse;
  csrf = { headerName: data.headerName, token: data.token };
  return csrf;
}

interface ApiOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  csrf?: boolean;
  ignorerExpirationSession?: boolean;
}

export async function apiRequest<T>(path: string, options: ApiOptions = {}): Promise<T> {
  return executer<T>(path, options, false);
}

export async function apiBlob(path: string, accept = 'application/pdf'): Promise<Blob> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: accept },
  });
  if (!response.ok) {
    if (response.status === 401) {
      effacerCsrf();
      gestionnaireExpirationSession?.();
    }
    throw await creerErreur(response);
  }
  return response.blob();
}

async function executer<T>(path: string, options: ApiOptions, dejaRetente: boolean): Promise<T> {
  const {
    body,
    csrf: protectionCsrf,
    ignorerExpirationSession,
    ...optionsRequete
  } = options;
  const headers = new Headers(optionsRequete.headers);
  headers.set('Accept', 'application/json');
  const formulaire = typeof FormData !== 'undefined' && body instanceof FormData;
  if (body !== undefined && !formulaire) headers.set('Content-Type', 'application/json');
  if (protectionCsrf) {
    const token = await chargerCsrf(dejaRetente);
    headers.set(token.headerName, token.token);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...optionsRequete,
    headers,
    credentials: 'include',
    body: body === undefined ? undefined : formulaire ? body : JSON.stringify(body),
  });

  if (!response.ok) {
    if (protectionCsrf && response.status === 403 && !dejaRetente) {
      effacerCsrf();
      return executer<T>(path, options, true);
    }
    if (response.status === 401 && !ignorerExpirationSession) {
      effacerCsrf();
      gestionnaireExpirationSession?.();
    }
    throw await creerErreur(response);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

async function creerErreur(response: Response): Promise<ApiError> {
  let body: ApiErrorBody = {};
  try { body = (await response.json()) as ApiErrorBody; } catch { /* réponse non JSON */ }
  return new ApiError(
    response.status,
    body.code ?? 'API_ERROR',
    body.message ?? 'Une erreur est survenue.',
    body.validationErrors ?? {},
  );
}
