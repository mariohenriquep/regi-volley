import { useState } from 'react';
import { ErrorAlert } from '@/components/ErrorAlert';
import { QueryState } from '@/components/QueryState';
import { SelectField } from '@/components/form/SelectField';
import { Button } from '@/components/ui/button';
import { formatDateTime } from '@/lib/lisbon';
import type { AdminNotice } from '../useAdminNotice';
import type { LevelChange, LevelOption, MemberDetail } from './model';
import { useChangeLevel } from './useMemberMutations';
import { useLevels } from './useMembers';

/**
 * The member's level (US-07, RN-20): the current one, a change to another level of the association, and who changed it
 * before. The new level applies to bookings made from then on, which is the API's rule; the client only says so.
 */
export function LevelSection({ member, notice }: { member: MemberDetail; notice: AdminNotice }) {
  const levels = useLevels();
  return (
    <section aria-labelledby="level-heading" className="grid gap-3">
      <h2 id="level-heading" className="text-lg font-semibold">
        Level
      </h2>
      <p>
        <span className="text-muted-foreground">Now: </span>
        <span className="font-medium">{member.levelName ?? 'Unknown'}</span>
      </p>
      {!member.anonymised && (
        <QueryState query={levels} loadingLabel="Loading levels">
          {(options) => <ChangeLevelForm member={member} options={options} notice={notice} />}
        </QueryState>
      )}
      <LevelHistory changes={member.levelChanges} />
    </section>
  );
}

function ChangeLevelForm({
  member,
  options,
  notice,
}: {
  member: MemberDetail;
  options: LevelOption[];
  notice: AdminNotice;
}) {
  const change = useChangeLevel(member.id);
  const [choice, setChoice] = useState<string | null>(null);
  const selected = choice ?? member.levelId ?? '';
  const unchanged = selected === (member.levelId ?? '');

  return (
    <form
      noValidate
      method="post"
      className="grid gap-3"
      onSubmit={(event) => {
        event.preventDefault();
        if (unchanged) return;
        change.mutate(selected, {
          onSuccess: () => {
            const name = options.find((option) => option.id === selected)?.name ?? 'the new level';
            notice.announce({ text: `${member.name} is now ${name}.` });
          },
        });
      }}
    >
      <SelectField
        id="new-level"
        label="New level"
        hint="The new level applies to bookings made from now on."
        value={selected}
        onChange={(event) => {
          setChoice(event.target.value);
          change.reset();
        }}
      >
        {options.map(({ id, name, isEntry }) => (
          <option key={id} value={id}>
            {isEntry ? `${name} (entry level)` : name}
          </option>
        ))}
      </SelectField>
      {change.error && <ErrorAlert error={change.error} />}
      <Button type="submit" className="h-11" disabled={unchanged || change.isPending}>
        {change.isPending ? 'Saving…' : 'Change level'}
      </Button>
    </form>
  );
}

function LevelHistory({ changes }: { changes: LevelChange[] }) {
  if (changes.length === 0) return null;
  return (
    <div className="grid gap-2">
      <h3 className="text-sm font-medium">Level history</h3>
      <ul aria-label="Level history" className="grid gap-2 text-sm">
        {changes.map((change) => (
          <li key={`${change.changedAt}-${change.to ?? ''}`} className="grid gap-0.5">
            <span className="font-medium">
              {change.from ? `${change.from} → ${change.to ?? ''}` : `Set to ${change.to ?? ''}`}
            </span>
            <span className="text-muted-foreground">
              {change.by ? `${change.by} · ` : ''}
              {formatDateTime(change.changedAt)}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
