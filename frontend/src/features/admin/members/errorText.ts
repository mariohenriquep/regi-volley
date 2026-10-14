import { ApiError, GENERIC_ERROR_MESSAGE } from '@/api/errors';

/** An error as one sentence for a notice: the API's own message (safe to show) and the reference to quote to support. */
export function errorText(error: unknown): string {
  if (!(error instanceof ApiError)) return GENERIC_ERROR_MESSAGE;
  return error.requestId ? `${error.message} (Reference: ${error.requestId})` : error.message;
}
