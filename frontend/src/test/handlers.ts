import { http, HttpResponse } from 'msw';

/** A recorded request, for assertions on what the client sent. */
export interface Seen {
  method: string;
  path: string;
  authorization: string | null;
  contentType: string | null;
  body: string;
}

export function recorder() {
  const seen: Seen[] = [];
  const record = async (request: Request) => {
    seen.push({
      method: request.method,
      path: new URL(request.url).pathname,
      authorization: request.headers.get('Authorization'),
      contentType: request.headers.get('Content-Type'),
      body: await request.clone().text(),
    });
  };
  return { seen, record };
}

export const tokenResponse = (accessToken: string) =>
  HttpResponse.json({ accessToken, tokenType: 'Bearer', expiresIn: 600 });

export const unauthenticated = () =>
  HttpResponse.json(
    { code: 'UNAUTHENTICATED', message: 'Authentication is required', requestId: 'req-401' },
    { status: 401 },
  );

/** Refresh answers 401: no cookie, or an expired / revoked one. */
export const refreshRejected = http.post('/api/v1/auth/refresh', () => unauthenticated());

export const me = {
  associationId: '11111111-1111-1111-1111-111111111111',
  memberId: '22222222-2222-2222-2222-222222222222',
};

/** The API's one error body, as MSW answers it. */
export const apiError = (
  status: number,
  code: string,
  message: string,
  options: { fields?: string[]; headers?: Record<string, string> } = {},
) =>
  HttpResponse.json(
    { code, message, requestId: `req-${String(status)}`, fields: options.fields },
    { status, headers: options.headers },
  );

export const rateLimited = (retryAfter: number) =>
  apiError(429, 'TOO_MANY_REQUESTS', 'Too many requests', {
    headers: { 'Retry-After': String(retryAfter) },
  });

export const publicAssociation = {
  name: 'Lisbon Spikers',
  shortName: 'lisbon-spikers',
  locality: 'Lisbon',
  contactEmail: 'info@lisbonspikers.example',
  levels: ['Beginner', 'Intermediate', 'Advanced'],
  groups: [
    {
      name: 'Tuesday evening',
      levels: ['Beginner', 'Intermediate'],
      venue: 'Pavilhão Central',
      schedule: [
        { dayOfWeek: 'TUESDAY', startTime: '20:30', durationMinutes: 90 },
        { dayOfWeek: 'THURSDAY', startTime: '21:00', durationMinutes: 60 },
      ],
    },
  ],
  venues: [{ name: 'Pavilhão Central', address: 'Rua da Bola 1, Lisbon', courts: 2 }],
};
