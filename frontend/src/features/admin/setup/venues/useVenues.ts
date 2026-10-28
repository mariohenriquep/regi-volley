import { useQuery } from '@tanstack/react-query';
import { unwrap } from '@/api/errors';
import { useAuth } from '@/auth/context';
import { venuesKey } from '../keys';
import { type Venue, toVenues } from './model';

/** The association's venues, with whether an active group uses each. */
export function useVenues() {
  const { api } = useAuth();
  return useQuery({
    queryKey: venuesKey,
    queryFn: async (): Promise<Venue[]> => toVenues(await unwrap(api.GET('/api/v1/venues'))),
  });
}
