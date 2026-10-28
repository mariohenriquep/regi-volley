import { ApiError } from '@/api/errors';

/** The API answers 403 to a member who is not an administrator; the admin screens then say so instead of showing an error. */
export function isForbidden(error: unknown): boolean {
  return error instanceof ApiError && error.status === 403;
}
