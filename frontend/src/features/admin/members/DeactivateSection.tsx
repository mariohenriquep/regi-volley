import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { ConfirmDialog } from '../ConfirmDialog';
import type { AdminNotice } from '../useAdminNotice';
import type { MemberDetail } from './model';
import type { useDeactivation } from './useDeactivation';

/**
 * Deactivating (US-08): the member can no longer sign in or book, their future bookings are cancelled and their history
 * stays. Asks first and says so. The API refuses the last active administrator (409); that message is read in the dialog.
 */
export function DeactivateSection({
  member,
  notice,
  deactivation: { deactivate, run },
}: {
  member: MemberDetail;
  notice: AdminNotice;
  /** Held by the page body: it must outlive this section, which goes away once the member is inactive. */
  deactivation: ReturnType<typeof useDeactivation>;
}) {
  const [asking, setAsking] = useState(false);
  if (member.status !== 'ACTIVE') return null;

  return (
    <section aria-labelledby="deactivate-heading" className="grid gap-3">
      <h2 id="deactivate-heading" className="text-lg font-semibold">
        Deactivate
      </h2>
      <p className="text-sm text-muted-foreground">
        A deactivated member can no longer sign in or book. Their history is kept.
      </p>
      <Button
        variant="destructive"
        className="h-11"
        onClick={(event) => {
          notice.remember(event.currentTarget);
          deactivate.reset();
          setAsking(true);
        }}
      >
        Deactivate member
      </Button>

      <ConfirmDialog
        open={asking}
        title={`Deactivate ${member.name}?`}
        description={
          <>
            <span className="block">
              Their future bookings will be cancelled and any credits given back; people on a
              waitlist move up.
            </span>
            <span className="block">
              They can no longer sign in or book. Their history is kept. The app cannot reactivate a
              member yet.
            </span>
          </>
        }
        confirmLabel="Deactivate"
        cancelLabel="Keep active"
        pendingLabel="Deactivating…"
        destructive
        pending={deactivate.isPending}
        error={deactivate.error}
        onConfirm={() => {
          run(() => {
            setAsking(false);
          });
        }}
        onClose={() => {
          setAsking(false);
        }}
        onCloseAutoFocus={notice.onCloseAutoFocus}
      />
    </section>
  );
}
