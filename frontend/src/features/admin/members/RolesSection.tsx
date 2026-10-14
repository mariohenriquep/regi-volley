import { useState } from 'react';
import { ErrorAlert } from '@/components/ErrorAlert';
import { Button } from '@/components/ui/button';
import { ConfirmDialog } from '../ConfirmDialog';
import type { AdminNotice } from '../useAdminNotice';
import type { MemberDetail } from './model';
import { type ManagedRole, useChangeRole } from './useMemberMutations';

const ROLES: {
  role: ManagedRole;
  name: string;
  /** Said in the question that precedes the change; `null` for a role that needs no question. */
  confirm: { grant: string; revoke: string } | null;
}[] = [
  { role: 'COACH', name: 'Coach', confirm: null },
  {
    role: 'ADMIN',
    name: 'Administrator',
    confirm: {
      grant:
        'Administrators manage members, roles, plans and payments, and see everyone’s contact details.',
      revoke:
        'They will no longer be able to manage members, plans or payments. The association must keep at least one active administrator.',
    },
  },
];

/**
 * Coach and administrator (US-04, US-20). MEMBER is every member's own role and is not offered. The API refuses taking the
 * last administrator away (409) and a member's last role (422); its message is shown as it wrote it. Giving or taking the
 * administrator role asks first, in a dialog where a refusal is read.
 */
export function RolesSection({ member, notice }: { member: MemberDetail; notice: AdminNotice }) {
  const change = useChangeRole(member.id);
  const [asking, setAsking] = useState<{ role: ManagedRole; grant: boolean } | null>(null);
  const target = ROLES.find(({ role }) => role === asking?.role);

  const apply = (role: ManagedRole, grant: boolean, onDone?: () => void) => {
    const name = ROLES.find((entry) => entry.role === role)?.name ?? role;
    change.mutate(
      { role, grant },
      {
        onSuccess: () => {
          onDone?.();
          notice.announce({ text: roleNotice(member.name, role, name, grant) });
        },
      },
    );
  };

  return (
    <section aria-labelledby="roles-heading" className="grid gap-3">
      <h2 id="roles-heading" className="text-lg font-semibold">
        Roles
      </h2>
      <ul className="grid gap-2">
        <li className="flex items-center justify-between gap-3">
          <span className="font-medium">Member</span>
          <span className="text-sm text-muted-foreground">Every member</span>
        </li>
        {ROLES.map(({ role, name, confirm }) => {
          const held = member.roles.includes(role);
          return (
            <li key={role} className="flex items-center justify-between gap-3">
              <span className="font-medium">{name}</span>
              <Button
                variant={held ? 'outline' : 'default'}
                className="h-11 px-4"
                disabled={change.isPending}
                onClick={(event) => {
                  notice.remember(event.currentTarget);
                  change.reset();
                  if (confirm) setAsking({ role, grant: !held });
                  else apply(role, !held);
                }}
              >
                {held ? `Remove ${name.toLowerCase()} role` : `Make ${name.toLowerCase()}`}
              </Button>
            </li>
          );
        })}
      </ul>
      {asking === null && change.error && <ErrorAlert error={change.error} />}

      <ConfirmDialog
        open={asking !== null && target?.confirm != null}
        title={
          asking?.grant
            ? `Make ${member.name} an administrator?`
            : `Remove the administrator role from ${member.name}?`
        }
        description={asking?.grant ? target?.confirm?.grant : target?.confirm?.revoke}
        confirmLabel={asking?.grant ? 'Make administrator' : 'Remove role'}
        pendingLabel="Saving…"
        destructive={asking?.grant === false}
        pending={change.isPending}
        error={change.error}
        onConfirm={() => {
          if (!asking) return;
          apply(asking.role, asking.grant, () => {
            setAsking(null);
          });
        }}
        onClose={() => {
          setAsking(null);
          change.reset();
        }}
        onCloseAutoFocus={notice.onCloseAutoFocus}
      />
    </section>
  );
}

function roleNotice(member: string, role: ManagedRole, name: string, grant: boolean): string {
  if (!grant) return `The ${name.toLowerCase()} role was removed from ${member}.`;
  return role === 'COACH' ? `${member} can now coach.` : `${member} is now an administrator.`;
}
