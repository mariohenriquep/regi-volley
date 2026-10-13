import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react';
import { useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import { ErrorAlert } from '@/components/ErrorAlert';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { addDays, dayHeading, mondayOf, weekRange } from '@/lib/lisbon';
import { useLisbonToday } from '@/lib/useLisbonToday';
import { usePageTitle } from '@/lib/usePageTitle';
import { BookDialog } from './BookDialog';
import { CancelDialog, type CancelTarget } from './CancelDialog';
import { bookedNotice, cancelledNotice } from './messages';
import { groupByDay, resolveWeek, type Session } from './model';
import { Notice } from './Notice';
import { SessionCard } from './SessionCard';
import { useWeek } from './useWeek';

/** The API lists sessions the schedule has generated (four weeks by default); a week further out is simply empty. */
const MAX_WEEKS_AHEAD = 8;

interface NoticeContent {
  text: string;
  tone: 'info' | 'warning';
}

/** Home: this week's sessions, book in two taps, cancel (US-13, US-14, US-15). */
export function SessionsPage() {
  usePageTitle('Sessions');
  const today = useLisbonToday();
  const [params, setParams] = useSearchParams();
  const thisWeek = mondayOf(today);
  const lastWeek = addDays(thisWeek, 7 * MAX_WEEKS_AHEAD);
  const week = resolveWeek(params.get('week'), thisWeek, lastWeek);
  const sessions = useWeek(week);

  const [booking, setBooking] = useState<Session | null>(null);
  const [cancelling, setCancelling] = useState<CancelTarget | null>(null);
  const [notice, setNotice] = useState<NoticeContent | null>(null);
  const noticeRef = useRef<HTMLDivElement>(null);
  const focusNoticeOnClose = useRef(false);

  const opener = useRef<HTMLElement | null>(null);

  /**
   * Where focus goes when a dialog closes. The dialogs have no Radix trigger, so the control that opened them is
   * remembered here. After an outcome that control is gone (Book becomes Cancel), so focus goes to the notice.
   */
  const handleCloseAutoFocus = (event: Event) => {
    event.preventDefault();
    if (focusNoticeOnClose.current) {
      focusNoticeOnClose.current = false;
      noticeRef.current?.focus();
    } else {
      opener.current?.focus();
    }
  };

  const goToWeek = (target: string) => {
    setNotice(null);
    setParams(target === thisWeek ? {} : { week: target });
  };

  return (
    <section className="grid gap-4">
      <h1 className="text-2xl font-semibold">Sessions</h1>

      <nav aria-label="Week" className="flex items-center justify-between gap-2">
        <Button
          variant="outline"
          size="icon-lg"
          className="size-11"
          aria-label="Previous week"
          disabled={week <= thisWeek}
          onClick={() => {
            goToWeek(addDays(week, -7));
          }}
        >
          <ChevronLeftIcon aria-hidden="true" />
        </Button>
        <p className="text-center text-sm font-medium" aria-live="polite">
          {week === thisWeek ? 'This week · ' : ''}
          {weekRange(week)}
        </p>
        <Button
          variant="outline"
          size="icon-lg"
          className="size-11"
          aria-label="Next week"
          disabled={week >= lastWeek}
          onClick={() => {
            goToWeek(addDays(week, 7));
          }}
        >
          <ChevronRightIcon aria-hidden="true" />
        </Button>
      </nav>

      {notice && (
        <Notice
          ref={noticeRef}
          tone={notice.tone}
          onDismiss={() => {
            setNotice(null);
          }}
        >
          {notice.text}
        </Notice>
      )}

      {sessions.isPending && <SessionsSkeleton />}
      {sessions.isError && (
        <div className="grid gap-3">
          <ErrorAlert error={sessions.error} />
          <Button
            variant="outline"
            className="h-11"
            onClick={() => {
              void sessions.refetch();
            }}
          >
            Try again
          </Button>
        </div>
      )}
      {sessions.data?.length === 0 && (
        <div className="grid gap-1 py-8 text-center">
          <p className="font-medium">No sessions this week</p>
          <p className="text-sm text-muted-foreground">Try another week, or check back later.</p>
        </div>
      )}
      {sessions.data && sessions.data.length > 0 && (
        <div className="grid gap-6">
          {groupByDay(sessions.data).map(({ day, sessions: inDay }) => (
            <section key={day} aria-labelledby={`day-${day}`} className="grid gap-3">
              <h2 id={`day-${day}`} className="text-lg font-semibold">
                {dayHeading(day)}
              </h2>
              {inDay.map((session) => (
                <SessionCard
                  key={session.id}
                  session={session}
                  onBook={(session, button) => {
                    opener.current = button;
                    setBooking(session);
                  }}
                  onCancel={(target, bookingId, button) => {
                    opener.current = button;
                    setCancelling({ session: target, bookingId });
                  }}
                />
              ))}
            </section>
          ))}
        </div>
      )}

      <BookDialog
        session={booking}
        onClose={() => {
          setBooking(null);
        }}
        onBooked={(placed) => {
          focusNoticeOnClose.current = true;
          setNotice({ text: bookedNotice(placed), tone: 'info' });
          setBooking(null);
        }}
        onCloseAutoFocus={handleCloseAutoFocus}
      />
      <CancelDialog
        target={cancelling}
        onClose={() => {
          setCancelling(null);
        }}
        onCancelled={(outcome) => {
          focusNoticeOnClose.current = true;
          setNotice({ text: cancelledNotice(outcome), tone: outcome.late ? 'warning' : 'info' });
          setCancelling(null);
        }}
        onCloseAutoFocus={handleCloseAutoFocus}
      />
    </section>
  );
}

function SessionsSkeleton() {
  return (
    <div role="status" aria-label="Loading sessions" aria-busy="true" className="grid gap-3">
      <Skeleton className="h-6 w-40" />
      <Skeleton className="h-36 w-full rounded-xl" />
      <Skeleton className="h-36 w-full rounded-xl" />
    </div>
  );
}
