import { unwrap } from '@/api/errors';
import { groupsKey, venuesKey } from '../keys';
import { useSetupMutation } from '../useSetupMutation';

export interface VenueInput {
  name: string;
  address: string;
  courts: number;
}

/** Group lists show the venue's name, so a change to a venue reads them again. */
const REFRESH = [venuesKey, groupsKey] as const;

/** Creates the venue, or replaces what the one with `id` says. */
export function useSaveVenue() {
  return useSetupMutation(
    async (api, { id, ...body }: VenueInput & { id: string | null }): Promise<void> => {
      await unwrap(
        id === null
          ? api.POST('/api/v1/venues', { body })
          : api.PUT('/api/v1/venues/{venueId}', { params: { path: { venueId: id } }, body }),
      );
    },
    REFRESH,
  );
}

export function useDeleteVenue() {
  return useSetupMutation(async (api, venueId: string): Promise<void> => {
    await unwrap(api.DELETE('/api/v1/venues/{venueId}', { params: { path: { venueId } } }));
  }, REFRESH);
}
