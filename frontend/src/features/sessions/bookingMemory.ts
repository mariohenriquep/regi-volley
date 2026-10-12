import { type QueryClient, useQuery } from '@tanstack/react-query';

/**
 * Booking ids the app learned from its own booking calls, by session id.
 *
 * The week list says whether the member has a booking, but not its id, and cancelling needs the id. So an id is known
 * only for a booking made since the app was opened. It lives in the query cache (in memory, never web storage), which
 * is cleared when the user changes, and it is lost on reload. Backend follow-up: `myBookingId` on the session list
 * (see docs/architecture.md, section 15); then this module goes away.
 */
const KEY = ['booking-ids'] as const;
type Ids = Readonly<Record<string, string>>;

export function useRememberedBookingIds(): Ids {
  const { data } = useQuery<Ids>({
    queryKey: KEY,
    queryFn: () => ({}),
    initialData: {},
    enabled: false,
    staleTime: Infinity,
    gcTime: Infinity,
  });
  return data;
}

export function rememberBooking(queryClient: QueryClient, sessionId: string, bookingId: string) {
  queryClient.setQueryData<Ids>(KEY, (ids) => ({ ...ids, [sessionId]: bookingId }));
}

export function forgetBooking(queryClient: QueryClient, sessionId: string) {
  queryClient.setQueryData<Ids>(KEY, (ids) => {
    return Object.fromEntries(Object.entries(ids ?? {}).filter(([id]) => id !== sessionId));
  });
}
