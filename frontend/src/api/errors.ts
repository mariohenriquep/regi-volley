import type { components } from './schema.gen';

/** The one error body of the API: `{code, message, requestId[, fields]}` (docs/architecture.md section 11, "Errors"). */
export type ApiErrorBody = components['schemas']['ApiError'];

/** Why a booking or its cancellation was refused (`reason` of the error body); stable, unlike the message. */
export type RefusalReason = NonNullable<ApiErrorBody['reason']>;

export const GENERIC_ERROR_MESSAGE = 'Something went wrong. Please try again.';
export const NETWORK_ERROR_MESSAGE =
  'Cannot reach the server. Check your connection and try again.';

/**
 * A failed API call. `message` is safe to show: the API's own English message for a 4xx (its messages never quote
 * input, threat model section 7), generic text for a 5xx or anything that is not the API's error body. `requestId`
 * ties what the user saw to the server's log line.
 */
export class ApiError extends Error {
  override readonly name = 'ApiError';
  readonly status: number;
  readonly code: string;
  readonly fields: readonly string[];
  readonly requestId: string | undefined;
  readonly retryAfterSeconds: number | undefined;
  /**
   * Typed as the contract's enum, but it is whatever string the server sent: a reason added by a newer backend is
   * still a string here, so consumers must keep a fallback for values they do not know.
   */
  readonly reason: RefusalReason | undefined;

  constructor(
    status: number,
    code: string,
    message: string,
    fields: readonly string[] = [],
    requestId?: string,
    retryAfterSeconds?: number,
    reason?: RefusalReason,
  ) {
    super(message);
    this.status = status;
    this.code = code;
    this.fields = fields;
    this.requestId = requestId;
    this.retryAfterSeconds = retryAfterSeconds;
    this.reason = reason;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function asErrorBody(body: unknown): ApiErrorBody {
  if (!isRecord(body)) return {};
  const { code, message, fields, requestId, reason } = body;
  return {
    code: typeof code === 'string' ? code : undefined,
    message: typeof message === 'string' ? message : undefined,
    fields:
      Array.isArray(fields) && fields.every((field) => typeof field === 'string')
        ? fields
        : undefined,
    requestId: typeof requestId === 'string' ? requestId : undefined,
    // The contract's enum is not re-checked here (it would be a second copy of it); see ApiError.reason.
    reason: typeof reason === 'string' ? (reason as RefusalReason) : undefined,
  };
}

function retryAfter(response: Response): number | undefined {
  const value = Number.parseInt(response.headers.get('Retry-After') ?? '', 10);
  return Number.isFinite(value) && value >= 0 ? value : undefined;
}

/** Maps a non-2xx response and its parsed body to an {@link ApiError}. */
export function toApiError(response: Response, body: unknown): ApiError {
  const parsed = asErrorBody(body);
  const serverError = response.status >= 500;
  const message =
    !serverError && parsed.message && parsed.message.length > 0
      ? parsed.message
      : GENERIC_ERROR_MESSAGE;
  return new ApiError(
    response.status,
    parsed.code ?? `HTTP_${String(response.status)}`,
    message,
    parsed.fields ?? [],
    parsed.requestId,
    retryAfter(response),
    parsed.reason,
  );
}

/** The request never got an answer (offline, DNS, the proxy cannot reach the API...). */
export function networkError(cause: unknown): ApiError {
  const error = new ApiError(0, 'NETWORK_ERROR', NETWORK_ERROR_MESSAGE);
  error.cause = cause;
  return error;
}

interface FetchResult<T> {
  data?: T;
  error?: unknown;
  response: Response;
}

/** Awaits an openapi-fetch call: its data when the response is 2xx, otherwise throws an {@link ApiError}. */
export async function unwrap<T>(call: Promise<FetchResult<T>>): Promise<T> {
  let result: FetchResult<T>;
  try {
    result = await call;
  } catch (cause) {
    if (cause instanceof ApiError) throw cause;
    throw networkError(cause);
  }
  if (!result.response.ok) {
    throw toApiError(result.response, result.error);
  }
  // A 204 has no body: callers of such operations type T as undefined/never.
  return result.data as T;
}
