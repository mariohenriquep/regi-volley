import { useState } from 'react';
import { TextField } from '@/components/form/TextField';
import type { LevelOption } from '../../members/model';
import { FormSheet } from '../FormSheet';
import { useFormProblems } from '../useFormProblems';
import { MAX_LEVEL_NAME } from './limits';
import { useRenameLevel } from './useLevelMutations';

const LABELS = { 'level-name': 'Level name' };
const FIELD_MAP = { name: 'level-name' };

/** Renames one level (US-03). A name another level already has is the API's refusal and is shown in the sheet. */
export function RenameLevelSheet({
  level,
  onRenamed,
  onClose,
  onCloseAutoFocus,
}: {
  level: LevelOption;
  onRenamed: (from: string, to: string) => void;
  onClose: () => void;
  onCloseAutoFocus: (event: Event) => void;
}) {
  const rename = useRenameLevel();
  const [name, setName] = useState(level.name);
  const { problems, fail, clear } = useFormProblems(rename.error, FIELD_MAP);

  const submit = () => {
    const trimmed = name.trim();
    if (!trimmed) {
      fail({ 'level-name': 'Enter a name for the level' });
      return;
    }
    clear();
    rename.mutate(
      { id: level.id, name: trimmed },
      {
        onSuccess: () => {
          onRenamed(level.name, trimmed);
        },
      },
    );
  };

  return (
    <FormSheet
      title={`Rename ${level.name}`}
      description="Members and groups keep this level; only its name changes."
      submitLabel="Save name"
      pendingLabel="Saving…"
      pending={rename.isPending}
      problems={problems}
      labels={LABELS}
      onSubmit={submit}
      onClose={onClose}
      onCloseAutoFocus={onCloseAutoFocus}
    >
      <TextField
        id="level-name"
        label="Level name"
        maxLength={MAX_LEVEL_NAME}
        required
        value={name}
        error={problems?.fields['level-name']}
        onChange={(event) => {
          setName(event.target.value);
        }}
      />
    </FormSheet>
  );
}
