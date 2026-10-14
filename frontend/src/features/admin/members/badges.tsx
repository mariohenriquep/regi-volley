import type { Role } from '@/auth/useMe';
import { Badge } from '@/components/ui/badge';

const STATUS = new Map<string, { label: string; variant: 'success' | 'secondary' }>([
  ['ACTIVE', { label: 'Active', variant: 'success' }],
  ['INACTIVE', { label: 'Inactive', variant: 'secondary' }],
]);

/** The member's status in words; a status this version does not know is shown as the API wrote it. */
export function StatusBadge({ status }: { status: string }) {
  const look = STATUS.get(status);
  return <Badge variant={look?.variant ?? 'outline'}>{look?.label ?? status}</Badge>;
}

const ROLES = new Map<Role, { label: string; variant: 'outline' | 'default' }>([
  ['MEMBER', { label: 'Member', variant: 'outline' }],
  ['COACH', { label: 'Coach', variant: 'default' }],
  ['ADMIN', { label: 'Admin', variant: 'default' }],
]);

/** One badge per role, in the order the API gave them. */
export function RoleBadges({ roles }: { roles: readonly Role[] }) {
  return (
    <>
      {roles.map((role) => {
        const look = ROLES.get(role);
        return (
          <Badge key={role} variant={look?.variant ?? 'outline'}>
            {look?.label ?? role}
          </Badge>
        );
      })}
    </>
  );
}
