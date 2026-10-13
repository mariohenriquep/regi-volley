import { useSearchParams } from 'react-router';
import { ApiError } from '@/api/errors';
import { QueryState } from '@/components/QueryState';
import { groupByDay } from '@/features/sessions/model';
import { addDays, dayHeading, mondayOf } from '@/lib/lisbon';
import { useLisbonToday } from '@/lib/useLisbonToday';
import { usePageTitle } from '@/lib/usePageTitle';
import { CoachingCard } from './CoachingCard';
import { MAX_WEEKS_AHEAD, MAX_WEEKS_BACK, resolveCoachingWeek } from './model';
import { useCoachingWeek } from './useCoaching';
import { useStaffActions } from './useStaffActions';
import { WeekNavigator } from './WeekNavigator';

/** The staff's week: the sessions a coach leads (an administrator sees all), and what can be done with each (US-11, US-12, US-17). */
export function CoachingPage() {
  usePageTitle('Coaching');
  const today = useLisbonToday();
  const [params, setParams] = useSearchParams();
  const thisWeek = mondayOf(today);
  const week = resolveCoachingWeek(params.get('week'), thisWeek);
  const sessions = useCoachingWeek(week);
  const actions = useStaffActions();

  const goToWeek = (target: string) => {
    actions.clearNotice();
    setParams(target === thisWeek ? {} : { week: target });
  };

  const notStaff = sessions.error instanceof ApiError && sessions.error.status === 403;

  return (
    <section className="grid gap-4">
      <h1 className="text-2xl font-semibold">Coaching</h1>

      {notStaff ? (
        <p className="py-8 text-center font-medium">Coaching is for coaches and administrators.</p>
      ) : (
        <>
          <WeekNavigator
            week={week}
            thisWeek={thisWeek}
            first={addDays(thisWeek, -7 * MAX_WEEKS_BACK)}
            last={addDays(thisWeek, 7 * MAX_WEEKS_AHEAD)}
            onGo={goToWeek}
          />

          {actions.notice}

          <QueryState query={sessions} loadingLabel="Loading sessions">
            {(lines) =>
              lines.length === 0 ? (
                <div className="grid gap-1 py-8 text-center">
                  <p className="font-medium">No sessions this week</p>
                  <p className="text-sm text-muted-foreground">
                    Try another week, or check back later.
                  </p>
                </div>
              ) : (
                <div className="grid gap-6">
                  {groupByDay(lines).map(({ day, sessions: inDay }) => (
                    <section key={day} aria-labelledby={`day-${day}`} className="grid gap-3">
                      <h2 id={`day-${day}`} className="text-lg font-semibold">
                        {dayHeading(day)}
                      </h2>
                      {inDay.map((session) => (
                        <CoachingCard
                          key={session.id}
                          session={session}
                          onCancel={actions.openCancel}
                          onChangeSeats={actions.openChangeSeats}
                        />
                      ))}
                    </section>
                  ))}
                </div>
              )
            }
          </QueryState>
        </>
      )}

      {actions.dialogs}
    </section>
  );
}
