import { useId } from 'react';
import { Button } from '@/components/ui/button';
import { formatDateTime } from '@/lib/lisbon';
import type { JoinRequest } from './model';

/** One person waiting for a decision: who they are, how to reach them, when they asked. */
export function JoinRequestCard({
  request,
  onApprove,
  onReject,
}: {
  request: JoinRequest;
  onApprove: (request: JoinRequest, opener: HTMLElement) => void;
  onReject: (request: JoinRequest, opener: HTMLElement) => void;
}) {
  const titleId = useId();
  return (
    <article
      aria-labelledby={titleId}
      className="grid gap-3 rounded-xl bg-card p-4 text-card-foreground ring-1 ring-foreground/10"
    >
      <div className="grid gap-0.5">
        <h2 id={titleId} className="text-base font-semibold">
          {request.name}
        </h2>
        <p className="text-sm break-all">{request.email}</p>
        <p className="text-sm">{request.phone}</p>
        <p className="text-sm text-muted-foreground">
          Requested {formatDateTime(request.requestedAt)}
        </p>
      </div>
      <div className="grid grid-cols-2 gap-2">
        <Button
          className="h-11 px-4 text-sm"
          onClick={(event) => {
            onApprove(request, event.currentTarget);
          }}
        >
          Approve<span className="sr-only"> {request.name}</span>
        </Button>
        <Button
          variant="outline"
          className="h-11 px-4 text-sm"
          onClick={(event) => {
            onReject(request, event.currentTarget);
          }}
        >
          Reject<span className="sr-only"> {request.name}</span>
        </Button>
      </div>
    </article>
  );
}
