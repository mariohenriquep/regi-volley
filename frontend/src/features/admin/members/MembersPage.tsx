import { ArrowLeftIcon } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router';
import { QueryState } from '@/components/QueryState';
import { SelectField } from '@/components/form/SelectField';
import { TextField } from '@/components/form/TextField';
import { Button } from '@/components/ui/button';
import { plural } from '@/features/coaching/model';
import { usePageTitle } from '@/lib/usePageTitle';
import { isForbidden } from '../forbidden';
import { NotAdmin } from '../NotAdmin';
import { MemberCard } from './MemberCard';
import { MAX_SEARCH, resolveStatus, STATUS_FILTERS } from './model';
import { useMembers } from './useMembers';

/** How long typing must pause before the search is written to the address (and so asked of the API). */
const SEARCH_PAUSE_MS = 300;

const STATUS_LABELS = { ACTIVE: 'Active', INACTIVE: 'Inactive' } as const;

/**
 * The association's members (US-07, US-08), by name. The search and the status filter live in the address (`?q=&status=`), so
 * a reload, the back button and a shared link all show the same list; the search is written there once typing pauses.
 */
export function MembersPage() {
  usePageTitle('Members');
  const [params, setParams] = useSearchParams();
  const { search } = useLocation();
  const urlSearch = params.get('q') ?? '';
  const status = resolveStatus(params.get('status'));

  const [text, setText] = useState(urlSearch);
  const [seenSearch, setSeenSearch] = useState(urlSearch);
  // The address changed from outside (back button, a link): the box follows it. Adjusting state while rendering is React's way.
  if (urlSearch !== seenSearch) {
    setSeenSearch(urlSearch);
    if (urlSearch !== text.trim()) setText(urlSearch);
  }

  const wanted = text.trim();
  useEffect(() => {
    if (wanted === urlSearch) return;
    const timer = setTimeout(() => {
      setParams(
        (current) => {
          const next = new URLSearchParams(current);
          if (wanted === '') next.delete('q');
          else next.set('q', wanted);
          return next;
        },
        { replace: true },
      );
    }, SEARCH_PAUSE_MS);
    return () => {
      clearTimeout(timer);
    };
  }, [wanted, urlSearch, setParams]);

  const members = useMembers({ status, q: urlSearch });
  const filtered = urlSearch !== '' || status !== undefined;

  if (isForbidden(members.error)) {
    return (
      <section className="grid gap-4">
        <h1 className="text-2xl font-semibold">Members</h1>
        <NotAdmin />
      </section>
    );
  }

  const chooseStatus = (value: string) => {
    const chosen = resolveStatus(value);
    setParams(
      (current) => {
        const next = new URLSearchParams(current);
        if (chosen) next.set('status', chosen);
        else next.delete('status');
        return next;
      },
      { replace: true },
    );
  };

  return (
    <section className="grid gap-4">
      <h1 className="text-2xl font-semibold">Members</h1>
      <Link
        to="/admin"
        className="flex w-fit items-center gap-1 text-sm text-primary underline underline-offset-4"
      >
        <ArrowLeftIcon className="size-4" aria-hidden="true" />
        Back to Admin
      </Link>

      <form
        role="search"
        aria-label="Find members"
        className="grid gap-3"
        onSubmit={(event) => {
          event.preventDefault();
        }}
      >
        <TextField
          id="member-search"
          label="Search members"
          hint="By name or email."
          type="search"
          autoComplete="off"
          maxLength={MAX_SEARCH}
          value={text}
          onChange={(event) => {
            setText(event.target.value);
          }}
        />
        <SelectField
          id="member-status"
          label="Status"
          value={status ?? ''}
          onChange={(event) => {
            chooseStatus(event.target.value);
          }}
        >
          <option value="">All</option>
          {STATUS_FILTERS.map((value) => (
            <option key={value} value={value}>
              {STATUS_LABELS[value]}
            </option>
          ))}
        </SelectField>
      </form>

      <QueryState query={members} loadingLabel="Loading members">
        {({ members: rows, truncated }) =>
          rows.length === 0 ? (
            <div className="grid justify-items-center gap-2 py-8 text-center">
              <p className="font-medium">{filtered ? 'No members match' : 'No members yet'}</p>
              {filtered ? (
                <Button
                  variant="outline"
                  className="h-11"
                  onClick={() => {
                    setText('');
                    setParams({}, { replace: true });
                  }}
                >
                  Clear search and filter
                </Button>
              ) : (
                <p className="text-sm text-muted-foreground">
                  Members appear here once you approve their join requests.
                </p>
              )}
            </div>
          ) : (
            <div className="grid gap-3">
              <p role="status" className="text-sm text-muted-foreground">
                {plural(rows.length, 'member', 'members')}
              </p>
              {truncated && (
                <p className="text-sm text-muted-foreground">
                  Not every member is shown. Search or filter to narrow the list.
                </p>
              )}
              <ul aria-label="Members" className="grid gap-3">
                {rows.map((member) => (
                  <li key={member.id}>
                    <MemberCard member={member} search={search} />
                  </li>
                ))}
              </ul>
            </div>
          )
        }
      </QueryState>
    </section>
  );
}
