import type { UseQueryResult } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { ErrorAlert } from '@/components/ErrorAlert';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';

/** Loading skeleton, or the error with a retry, or the data: the three states every member screen shares. */
export function QueryState<T>({
  query,
  loadingLabel,
  children,
}: {
  query: UseQueryResult<T>;
  /** Names the loading region for screen readers, e.g. "Loading your plan". */
  loadingLabel: string;
  children: (data: T) => ReactNode;
}) {
  if (query.isPending) {
    return (
      <div role="status" aria-label={loadingLabel} aria-busy="true" className="grid gap-3">
        <Skeleton className="h-28 w-full rounded-xl" />
        <Skeleton className="h-28 w-full rounded-xl" />
      </div>
    );
  }
  if (query.isError) {
    return (
      <div className="grid gap-3">
        <ErrorAlert error={query.error} />
        <Button
          variant="outline"
          className="h-11"
          onClick={() => {
            void query.refetch();
          }}
        >
          Try again
        </Button>
      </div>
    );
  }
  return <>{children(query.data)}</>;
}
