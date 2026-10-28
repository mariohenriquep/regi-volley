import { ArrowDownIcon, ArrowUpIcon } from 'lucide-react';
import { type SubmitEvent, useEffect, useState } from 'react';
import { ErrorAlert } from '@/components/ErrorAlert';
import { QueryState } from '@/components/QueryState';
import { ErrorSummary } from '@/components/form/ErrorSummary';
import { TextField } from '@/components/form/TextField';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { isForbidden } from '../../forbidden';
import type { LevelOption } from '../../members/model';
import { useLevels } from '../../members/useMembers';
import { useAdminNotice } from '../../useAdminNotice';
import { SetupScreen } from '../SetupScreen';
import { useFormProblems } from '../useFormProblems';
import { MAX_LEVEL_NAME } from './limits';
import { RenameLevelSheet } from './RenameLevelSheet';
import { useAddLevel, useChangeEntryLevel, useReorderLevels } from './useLevelMutations';

type Direction = 'up' | 'down';

const buttonId = (levelId: string, direction: Direction) => `level-${levelId}-${direction}`;

/**
 * The association's levels (US-03, RN-20, RN-21), lowest first: add, rename, reorder with buttons (no drag-only control)
 * and choose the entry level. The API checks every change (a name taken, an order that misses a level) and answers with the
 * levels after it; the screen reads the list again and shows a refusal as the API wrote it.
 */
export function LevelsPage() {
  const levels = useLevels();
  const notice = useAdminNotice();
  const [renaming, setRenaming] = useState<LevelOption | null>(null);

  return (
    <SetupScreen title="Levels" forbidden={isForbidden(levels.error)}>
      <p className="text-sm text-muted-foreground">
        List the levels from the lowest to the highest. Members can book sessions of groups that
        accept their own level or a lower one. New members start at the entry level.
      </p>
      {notice.notice}
      <QueryState query={levels} loadingLabel="Loading levels">
        {(options) => <LevelList options={options} notice={notice} onRename={setRenaming} />}
      </QueryState>
      <AddLevelForm notice={notice} />

      <Dialog
        open={renaming !== null}
        onOpenChange={(open) => {
          if (!open) setRenaming(null);
        }}
      >
        {renaming && (
          <RenameLevelSheet
            key={renaming.id}
            level={renaming}
            onRenamed={(from, to) => {
              setRenaming(null);
              notice.announce({ text: `${from} is now called ${to}.` });
            }}
            onClose={() => {
              setRenaming(null);
            }}
            onCloseAutoFocus={notice.onCloseAutoFocus}
          />
        )}
      </Dialog>
    </SetupScreen>
  );
}

function LevelList({
  options,
  notice,
  onRename,
}: {
  options: LevelOption[];
  notice: ReturnType<typeof useAdminNotice>;
  onRename: (level: LevelOption) => void;
}) {
  const reorder = useReorderLevels();
  const entry = useChangeEntryLevel();
  const busy = reorder.isPending || entry.isPending;
  const [moved, setMoved] = useState<{ id: string; direction: Direction; text: string } | null>(
    null,
  );

  // The list is rebuilt from the API's answer after a move; the keyboard user is put back on the control they used, which
  // belongs to the moved level (or to the other direction when it has reached an end).
  useEffect(() => {
    if (!moved || reorder.isPending) return;
    const preferred = document.getElementById(buttonId(moved.id, moved.direction));
    const other = document.getElementById(
      buttonId(moved.id, moved.direction === 'up' ? 'down' : 'up'),
    );
    const target = preferred && !(preferred as HTMLButtonElement).disabled ? preferred : other;
    target?.focus();
  }, [moved, options, reorder.isPending]);

  const move = (index: number, direction: Direction) => {
    const level = options[index];
    if (!level) return;
    const to = direction === 'up' ? index - 1 : index + 1;
    const ids = options.map((option) => option.id);
    ids.splice(index, 1);
    ids.splice(to, 0, level.id);
    notice.clear();
    reorder.mutate(ids, {
      onSuccess: () => {
        setMoved({
          id: level.id,
          direction,
          text: `${level.name} moved ${direction}: now ${String(to + 1)} of ${String(ids.length)}.`,
        });
      },
    });
  };

  return (
    <div className="grid gap-3">
      <ol aria-label="Levels" className="grid gap-3">
        {options.map((level, index) => (
          <li
            key={level.id}
            className="grid gap-3 rounded-xl bg-card p-4 text-card-foreground ring-1 ring-foreground/10"
          >
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-sm text-muted-foreground" aria-hidden="true">
                {index + 1}.
              </span>
              <span data-level-name className="text-base font-semibold">
                {level.name}
              </span>
              {level.isEntry && <Badge variant="success">Entry level</Badge>}
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Button
                id={buttonId(level.id, 'up')}
                variant="outline"
                size="icon-lg"
                className="size-11"
                aria-label={`Move ${level.name} up`}
                disabled={index === 0 || busy}
                onClick={() => {
                  move(index, 'up');
                }}
              >
                <ArrowUpIcon aria-hidden="true" />
              </Button>
              <Button
                id={buttonId(level.id, 'down')}
                variant="outline"
                size="icon-lg"
                className="size-11"
                aria-label={`Move ${level.name} down`}
                disabled={index === options.length - 1 || busy}
                onClick={() => {
                  move(index, 'down');
                }}
              >
                <ArrowDownIcon aria-hidden="true" />
              </Button>
              <Button
                variant="outline"
                className="h-11 px-4"
                onClick={(event) => {
                  notice.remember(event.currentTarget);
                  onRename(level);
                }}
              >
                Rename <span className="sr-only">{level.name}</span>
              </Button>
              {!level.isEntry && (
                <Button
                  variant="outline"
                  className="h-11 px-4"
                  disabled={busy}
                  onClick={() => {
                    notice.clear();
                    entry.mutate(level.id, {
                      onSuccess: () => {
                        notice.announce({
                          text: `${level.name} is now the entry level. New members start there.`,
                        });
                      },
                    });
                  }}
                >
                  Make entry level <span className="sr-only">for {level.name}</span>
                </Button>
              )}
            </div>
          </li>
        ))}
      </ol>
      <p aria-live="polite" className="sr-only">
        {moved?.text}
      </p>
      {reorder.error && <ErrorAlert error={reorder.error} />}
      {entry.error && <ErrorAlert error={entry.error} />}
    </div>
  );
}

const ADD_LABELS = { 'new-level': 'New level name' };
const ADD_FIELDS = { name: 'new-level' };

function AddLevelForm({ notice }: { notice: ReturnType<typeof useAdminNotice> }) {
  const add = useAddLevel();
  const [name, setName] = useState('');
  const { problems, fail, clear } = useFormProblems(add.error, ADD_FIELDS);

  const submit = (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) {
      fail({ 'new-level': 'Enter a name for the level' });
      return;
    }
    clear();
    notice.clear();
    add.mutate(trimmed, {
      onSuccess: () => {
        setName('');
        notice.announce({ text: `${trimmed} was added as the highest level.` });
      },
    });
  };

  return (
    <form
      noValidate
      method="post"
      aria-labelledby="add-level-heading"
      className="grid gap-3"
      onSubmit={submit}
    >
      <h2 id="add-level-heading" className="text-lg font-semibold">
        Add a level
      </h2>
      <ErrorSummary problems={problems} labels={ADD_LABELS} />
      <TextField
        id="new-level"
        label="New level name"
        hint="A new level is added as the highest one; use the arrows to move it."
        maxLength={MAX_LEVEL_NAME}
        value={name}
        error={problems?.fields['new-level']}
        onChange={(event) => {
          setName(event.target.value);
        }}
      />
      <Button type="submit" className="h-11" disabled={add.isPending}>
        {add.isPending ? 'Adding…' : 'Add level'}
      </Button>
    </form>
  );
}
