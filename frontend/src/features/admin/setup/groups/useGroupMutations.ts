import { unwrap } from '@/api/errors';
import type { components } from '@/api/schema.gen';
import { groupsKey, venuesKey } from '../keys';
import { useSetupMutation } from '../useSetupMutation';

type Schemas = components['schemas'];

/** A venue shows whether an active group uses it, so a change to a group reads the venues again. */
const REFRESH = [groupsKey, venuesKey] as const;

/**
 * Creates the group (`id` null, with its venue), or replaces what the one with `id` says. The venue of a group cannot be
 * changed, so an edit sends none.
 */
export function useSaveGroup() {
  return useSetupMutation(
    async (
      api,
      {
        id,
        body,
      }: {
        id: string | null;
        body: Schemas['CreateTrainingGroupRequest'] | Schemas['EditTrainingGroupRequest'];
      },
    ): Promise<void> => {
      await unwrap(
        id === null
          ? api.POST('/api/v1/training-groups', {
              // A new group always carries its venue: the sheet only submits when one is chosen.
              body: body as Schemas['CreateTrainingGroupRequest'],
            })
          : api.PUT('/api/v1/training-groups/{groupId}', {
              params: { path: { groupId: id } },
              body,
            }),
      );
    },
    REFRESH,
  );
}

export function useArchiveGroup() {
  return useSetupMutation(async (api, groupId: string): Promise<void> => {
    await unwrap(
      api.POST('/api/v1/training-groups/{groupId}/archival', { params: { path: { groupId } } }),
    );
  }, REFRESH);
}
