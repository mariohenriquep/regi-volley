import { useAuth } from '@/auth/context';
import { mondayOf } from '@/lib/lisbon';
import { useLisbonToday } from '@/lib/useLisbonToday';
import { useCoachingWeek } from './useCoaching';

/**
 * Whether to show the Coaching section. `GET /me` carries no roles, so the answer comes from the coaching endpoint
 * itself: it lists the week for a coach or an administrator and answers 403 to anyone else. The request is this week's
 * list, the same query the Coaching page opens with, so the page finds it already cached. While it is pending, and when it
 * fails for any other reason, the section stays hidden. Not a security boundary: the API decides on every call.
 */
export function useIsStaff(): boolean {
  const { status } = useAuth();
  const today = useLisbonToday();
  const week = useCoachingWeek(mondayOf(today), { enabled: status === 'authenticated' });
  return week.isSuccess;
}
