import { ErrorAlert } from '@/components/ErrorAlert';
import { Button } from '@/components/ui/button';
import type { AdminNotice } from '../useAdminNotice';
import type { MemberDetail } from './model';
import { useResendActivationLink } from './useMemberMutations';

/**
 * Whether the member has set a password (P7). While they have not, an administrator can send the link again. The API
 * answers the same 202 whether or not a link went out, so the notice promises nothing more than that.
 */
export function AccountSection({ member, notice }: { member: MemberDetail; notice: AdminNotice }) {
  const resend = useResendActivationLink(member.id);
  if (member.accountActivated === undefined) return null;
  const canResend = !member.accountActivated && member.status === 'ACTIVE';

  return (
    <section aria-labelledby="account-heading" className="grid gap-3">
      <h2 id="account-heading" className="text-lg font-semibold">
        Account
      </h2>
      <p>{member.accountActivated ? 'Activated' : 'Not activated yet'}</p>
      {canResend && (
        <>
          <p className="text-sm text-muted-foreground">
            Sends a new activation link to the member’s account address. Older links stop working.
          </p>
          {resend.error && <ErrorAlert error={resend.error} />}
          <Button
            variant="outline"
            className="h-11"
            disabled={resend.isPending}
            onClick={(event) => {
              notice.remember(event.currentTarget);
              resend.reset();
              resend.mutate(undefined, {
                onSuccess: () => {
                  notice.announce({
                    text: `If ${member.name} has not activated yet, a new activation link is on its way.`,
                  });
                },
              });
            }}
          >
            {resend.isPending ? 'Sending…' : 'Resend activation link'}
          </Button>
        </>
      )}
    </section>
  );
}
