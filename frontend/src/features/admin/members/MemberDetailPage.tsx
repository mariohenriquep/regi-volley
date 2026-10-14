import { ArrowLeftIcon } from 'lucide-react';
import { Link, useLocation, useParams } from 'react-router';
import { ApiError } from '@/api/errors';
import { QueryState } from '@/components/QueryState';
import { Badge } from '@/components/ui/badge';
import { longDate, lisbonDay } from '@/lib/lisbon';
import { usePageTitle } from '@/lib/usePageTitle';
import { isForbidden } from '../forbidden';
import { NotAdmin } from '../NotAdmin';
import { useAdminNotice } from '../useAdminNotice';
import { AccountSection } from './AccountSection';
import { RoleBadges, StatusBadge } from './badges';
import { DeactivateSection } from './DeactivateSection';
import { backToList } from './listState';
import { LevelSection } from './LevelSection';
import type { MemberDetail } from './model';
import { RolesSection } from './RolesSection';
import { SubscriptionsSection } from './SubscriptionsSection';
import { useDeactivation } from './useDeactivation';
import { useMember } from './useMembers';

/** The way back to the list, with the search and filter the member was opened from. */
function BackToMembers() {
  // Navigation state is `any` in the router's types; backToList reads it as `unknown`.
  const state: unknown = useLocation().state;
  return (
    <Link
      to={backToList(state)}
      className="flex w-fit items-center gap-1 text-sm text-primary underline underline-offset-4"
    >
      <ArrowLeftIcon className="size-4" aria-hidden="true" />
      Back to Members
    </Link>
  );
}

/**
 * One member for the administrator (US-04, US-07, US-08, US-20): contact, level and its history, roles, whether the account
 * is activated, the subscriptions (read-only here) and deactivation. An anonymised member (RGPD erasure) is read-only.
 */
export function MemberDetailPage() {
  const { memberId = '' } = useParams();
  const member = useMember(memberId);
  usePageTitle(member.data?.name ?? 'Member');

  if (isForbidden(member.error)) {
    return (
      <section className="grid gap-4">
        <h1 className="text-2xl font-semibold">Member</h1>
        <NotAdmin />
      </section>
    );
  }
  if (member.error instanceof ApiError && member.error.status === 404) {
    return (
      <section className="grid gap-4">
        <h1 className="text-2xl font-semibold">Member</h1>
        <BackToMembers />
        <p className="py-8 text-center font-medium">This member was not found.</p>
      </section>
    );
  }

  return (
    <section className="grid gap-5">
      {member.isSuccess && member.data ? null : <h1 className="text-2xl font-semibold">Member</h1>}
      <BackToMembers />
      <QueryState query={member} loadingLabel="Loading the member">
        {(detail) =>
          detail ? <MemberBody member={detail} /> : <p>This member could not be shown.</p>
        }
      </QueryState>
    </section>
  );
}

function MemberBody({ member }: { member: MemberDetail }) {
  const notice = useAdminNotice();
  const deactivation = useDeactivation(member, notice);
  return (
    <>
      <div className="grid gap-2">
        <h1 className="text-2xl font-semibold">{member.name}</h1>
        <div className="flex flex-wrap gap-1.5">
          <StatusBadge status={member.status} />
          {member.accountActivated === false && <Badge variant="warning">Not activated</Badge>}
          <RoleBadges roles={member.roles} />
        </div>
      </div>

      {notice.notice}

      {member.anonymised ? (
        <>
          <p className="rounded-lg border bg-card p-3 text-sm">
            This member’s personal data was erased on request. Nothing can be changed.
          </p>
          <LevelSection member={member} notice={notice} />
        </>
      ) : (
        <>
          <ContactSection member={member} />
          <LevelSection member={member} notice={notice} />
          <RolesSection member={member} notice={notice} />
          <AccountSection member={member} notice={notice} />
        </>
      )}
      <SubscriptionsSection
        subscriptions={member.subscriptions}
        truncated={member.subscriptionsTruncated}
      />
      {!member.anonymised && (
        <DeactivateSection member={member} notice={notice} deactivation={deactivation} />
      )}
    </>
  );
}

function ContactSection({ member }: { member: MemberDetail }) {
  return (
    <section aria-labelledby="contact-heading" className="grid gap-1">
      <h2 id="contact-heading" className="text-lg font-semibold">
        Contact
      </h2>
      {member.email && <p className="break-all">{member.email}</p>}
      {member.phone && <p>{member.phone}</p>}
      {member.joinedAt && (
        <p className="text-sm text-muted-foreground">
          Joined {longDate(lisbonDay(member.joinedAt))}
        </p>
      )}
    </section>
  );
}
