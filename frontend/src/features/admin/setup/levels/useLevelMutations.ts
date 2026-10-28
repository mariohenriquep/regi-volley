import { unwrap } from '@/api/errors';
import { membersKey, levelsKey } from '../../members/useMembers';
import { groupsKey, plansKey } from '../keys';
import { useSetupMutation } from '../useSetupMutation';

/** Level names appear in the lists of members, groups and plans, so a change to the levels reads them all again. */
const REFRESH = [levelsKey, membersKey, groupsKey, plansKey] as const;

export function useAddLevel() {
  return useSetupMutation(async (api, name: string): Promise<void> => {
    await unwrap(api.POST('/api/v1/levels', { body: { name } }));
  }, REFRESH);
}

export function useRenameLevel() {
  return useSetupMutation(
    async (api, { id, name }: { id: string; name: string }): Promise<void> => {
      await unwrap(
        api.PUT('/api/v1/levels/{levelId}', { params: { path: { levelId: id } }, body: { name } }),
      );
    },
    REFRESH,
  );
}

/** The API takes the whole order: every level exactly once, lowest first. */
export function useReorderLevels() {
  return useSetupMutation(async (api, levelIds: string[]): Promise<void> => {
    await unwrap(api.PUT('/api/v1/levels/order', { body: { levelIds } }));
  }, REFRESH);
}

export function useChangeEntryLevel() {
  return useSetupMutation(async (api, levelId: string): Promise<void> => {
    await unwrap(api.PUT('/api/v1/levels/entry-level', { body: { levelId } }));
  }, REFRESH);
}
