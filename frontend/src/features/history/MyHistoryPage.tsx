import { Badge } from '@/components/ui/badge';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { QueryState } from '@/components/QueryState';
import { dayHeading, formatTime, lisbonDay } from '@/lib/lisbon';
import { usePageTitle } from '@/lib/usePageTitle';
import type { History } from './entries';
import { useHistory } from './useHistory';

type Variant = 'success' | 'warning' | 'destructive' | 'secondary';

/** A Map, not an object: the status is the API's text, and `STATUS['constructor']` must not find anything. */
const STATUS = new Map<string, { label: string; variant: Variant }>([
  ['ATTENDED', { label: 'Attended', variant: 'success' }],
  ['NO_SHOW', { label: 'No-show', variant: 'destructive' }],
  ['CONFIRMED', { label: 'Confirmed', variant: 'secondary' }],
  ['WAITLISTED', { label: 'Waitlisted', variant: 'warning' }],
]);

/** My history (US-18): the last three months, newest first as the API sends them, and the no-show warning (RN-11). */
export function MyHistoryPage() {
  usePageTitle('History');
  const history = useHistory();
  return (
    <section className="grid gap-4">
      <h1 className="text-2xl font-semibold">History</h1>
      <QueryState query={history} loadingLabel="Loading your history">
        {(data) => <Entries history={data} />}
      </QueryState>
    </section>
  );
}

function Entries({ history }: { history: History }) {
  const { noShowsThisMonth, noShowLimit, nearLimit, entries } = history;
  const reached = noShowLimit > 0 && noShowsThisMonth >= noShowLimit;
  return (
    <>
      <p className="text-sm text-muted-foreground">
        {noShowsThisMonth} of {noShowLimit} no-shows this month
      </p>
      {nearLimit && (
        <Alert variant="destructive">
          <AlertDescription>
            {reached
              ? `You've reached this month's no-show limit (${String(noShowsThisMonth)} of ${String(noShowLimit)}).`
              : `You're close to this month's no-show limit (${String(noShowsThisMonth)} of ${String(noShowLimit)}).`}{' '}
            Please cancel in time when you cannot come.
          </AlertDescription>
        </Alert>
      )}
      {entries.length === 0 ? (
        <div className="grid gap-1 py-8 text-center">
          <p className="font-medium">No sessions in the last 3 months</p>
          <p className="text-sm text-muted-foreground">Sessions you book will show up here.</p>
        </div>
      ) : (
        <ul className="grid gap-3">
          {entries.map((entry) => {
            const status = STATUS.get(entry.status);
            return (
              <li
                key={entry.bookingId}
                className="flex items-start justify-between gap-2 rounded-xl bg-card p-4 ring-1 ring-foreground/10"
              >
                <div className="grid gap-0.5">
                  <p className="font-medium">{dayHeading(lisbonDay(entry.startsAt))}</p>
                  <p className="text-sm text-muted-foreground">
                    {formatTime(entry.startsAt)} – {formatTime(entry.endsAt)}
                  </p>
                </div>
                <Badge variant={status?.variant ?? 'secondary'}>
                  {status?.label ?? entry.status}
                </Badge>
              </li>
            );
          })}
        </ul>
      )}
    </>
  );
}
