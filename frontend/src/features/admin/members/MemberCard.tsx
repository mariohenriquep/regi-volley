import { useId } from 'react';
import { Link } from 'react-router';
import { Badge } from '@/components/ui/badge';
import type { MemberRow } from './model';
import type { ListState } from './listState';
import { RoleBadges, StatusBadge } from './badges';

/** One member of the list: the whole card opens the member (the name is the link). `search` is the list's query string, for the way back. */
export function MemberCard({ member, search }: { member: MemberRow; search: string }) {
  const titleId = useId();
  return (
    <article
      aria-labelledby={titleId}
      className="relative grid gap-2 rounded-xl bg-card p-4 text-card-foreground ring-1 ring-foreground/10 focus-within:ring-3 focus-within:ring-ring/50 hover:bg-accent"
    >
      <div className="grid gap-0.5">
        <h2 id={titleId} className="text-base font-semibold">
          <Link
            to={`/admin/members/${member.id}`}
            state={{ search } satisfies ListState}
            className="outline-none after:absolute after:inset-0 after:rounded-xl"
          >
            {member.name}
          </Link>
        </h2>
        {member.email && <p className="text-sm break-all text-muted-foreground">{member.email}</p>}
        {member.levelName && <p className="text-sm">{member.levelName}</p>}
      </div>
      <div className="flex flex-wrap gap-1.5">
        <StatusBadge status={member.status} />
        {member.accountActivated === false && <Badge variant="warning">Not activated</Badge>}
        <RoleBadges roles={member.roles} />
      </div>
    </article>
  );
}
