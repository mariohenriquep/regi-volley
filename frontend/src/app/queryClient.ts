import { QueryClient } from '@tanstack/react-query';
import { ApiError } from '@/api/errors';

/** A client error (4xx) will not change on a retry; network and server errors get two more attempts. */
const shouldRetry = (failureCount: number, error: unknown) =>
  !(error instanceof ApiError && error.status >= 400 && error.status < 500) && failureCount < 2;

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: shouldRetry, refetchOnWindowFocus: false },
      mutations: { retry: false },
    },
  });
}
