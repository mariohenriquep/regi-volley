import { ArrowLeftIcon } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';
import { QueryState } from '@/components/QueryState';
import { usePageTitle } from '@/lib/usePageTitle';
import { ConfirmDialog } from '../ConfirmDialog';
import { isForbidden } from '../forbidden';
import { NotAdmin } from '../NotAdmin';
import { useAdminNotice } from '../useAdminNotice';
import { JoinRequestCard } from './JoinRequestCard';
import { type JoinRequest, MAX_PENDING_LISTED } from './model';
import { RejectDialog } from './RejectDialog';
import { useApproveJoinRequest, useJoinRequests, useRejectJoinRequest } from './useJoinRequests';

/** The requests waiting for a decision (US-06): approve (the member starts at the entry level) or reject. */
export function JoinRequestsPage() {
  usePageTitle('Join requests');
  const requests = useJoinRequests();
  const approve = useApproveJoinRequest();
  const reject = useRejectJoinRequest();
  const notice = useAdminNotice();
  const [approving, setApproving] = useState<JoinRequest | null>(null);
  const [rejecting, setRejecting] = useState<JoinRequest | null>(null);

  if (isForbidden(requests.error)) {
    return (
      <section className="grid gap-4">
        <h1 className="text-2xl font-semibold">Join requests</h1>
        <NotAdmin />
      </section>
    );
  }

  return (
    <section className="grid gap-4">
      <h1 className="text-2xl font-semibold">Join requests</h1>
      <Link
        to="/admin"
        className="flex w-fit items-center gap-1 text-sm text-primary underline underline-offset-4"
      >
        <ArrowLeftIcon className="size-4" aria-hidden="true" />
        Back to Admin
      </Link>

      {notice.notice}

      <QueryState query={requests} loadingLabel="Loading join requests">
        {(pending) =>
          pending.length === 0 ? (
            <div className="grid gap-1 py-8 text-center">
              <p className="font-medium">No requests are waiting</p>
              <p className="text-sm text-muted-foreground">
                New requests appear here when someone asks to join from your public page.
              </p>
            </div>
          ) : (
            <div className="grid gap-3">
              {pending.length >= MAX_PENDING_LISTED && (
                <p className="text-sm text-muted-foreground">
                  Showing the {MAX_PENDING_LISTED} oldest requests. Decide some to see the rest.
                </p>
              )}
              {pending.map((request) => (
                <JoinRequestCard
                  key={request.id}
                  request={request}
                  onApprove={(target, opener) => {
                    notice.remember(opener);
                    approve.reset();
                    setApproving(target);
                  }}
                  onReject={(target, opener) => {
                    notice.remember(opener);
                    reject.reset();
                    setRejecting(target);
                  }}
                />
              ))}
            </div>
          )
        }
      </QueryState>

      <ConfirmDialog
        open={approving !== null}
        title={`Approve ${approving?.name ?? ''}?`}
        description="Approving creates the member at the entry level and emails them an activation link."
        confirmLabel="Approve request"
        pendingLabel="Approving…"
        pending={approve.isPending}
        error={approve.error}
        onConfirm={() => {
          if (!approving) return;
          const { id, name } = approving;
          approve.mutate(id, {
            onSuccess: ({ memberId }) => {
              setApproving(null);
              notice.announce({
                text: (
                  <>
                    {name} is now a member. An activation link was emailed to them.
                    {memberId && (
                      <>
                        {' '}
                        <Link
                          to={`/admin/members/${memberId}`}
                          className="text-primary underline underline-offset-4"
                        >
                          Open member
                        </Link>
                      </>
                    )}
                  </>
                ),
              });
            },
          });
        }}
        onClose={() => {
          setApproving(null);
        }}
        onCloseAutoFocus={notice.onCloseAutoFocus}
      />

      <RejectDialog
        request={rejecting}
        pending={reject.isPending}
        error={reject.error}
        onReject={(reason) => {
          if (!rejecting) return;
          const { id, name } = rejecting;
          reject.mutate(
            { requestId: id, reason },
            {
              onSuccess: () => {
                setRejecting(null);
                notice.announce({
                  text: `Request from ${name} rejected. They were told by email.`,
                });
              },
            },
          );
        }}
        onClose={() => {
          setRejecting(null);
        }}
        onCloseAutoFocus={notice.onCloseAutoFocus}
      />
    </section>
  );
}
