import { useState } from 'react';
import { Link } from 'react-router';
import type { components } from '@/api/schema.gen';
import { SelectField } from '@/components/form/SelectField';
import { TextField } from '@/components/form/TextField';
import { useLevels } from '../../members/useMembers';
import { FormSheet } from '../FormSheet';
import { LevelCheckboxes } from '../LevelCheckboxes';
import { wholeNumber, wholeNumberHint } from '../numbers';
import { useFormProblems } from '../useFormProblems';
import type { Group, Named } from './model';
import { DAYS, isDay, MAX_GROUP_NAME, MAX_SLOTS, SEATS, SLOT_MINUTES } from './model';
import { SlotRows } from './SlotRows';
import { type SlotRow, slotControl } from './slotRow';
import { useCoaches } from './useGroups';
import { useSaveGroup } from './useGroupMutations';
import { useVenues } from '../venues/useVenues';

type Schemas = components['schemas'];

const LABELS: Record<string, string> = {
  'group-name': 'Name',
  'group-levels': 'Accepted levels',
  'group-venue': 'Venue',
  'group-coach': 'Coach',
  'group-seats': 'Seats',
  'group-schedule': 'Weekly schedule',
};
// A 400 names the request property, a 422 the rule's label: both meet in one normalised key. Problems with a slot (a 400 names
// `schedule[2].startTime`, the index dropped by the normaliser) have no row to point at, so they go to the whole schedule.
const FIELD_MAP = {
  name: 'group-name',
  acceptedlevelids: 'group-levels',
  venueid: 'group-venue',
  coachid: 'group-coach',
  capacity: 'group-seats',
  schedule: 'group-schedule',
  scheduledayofweek: 'group-schedule',
  schedulestarttime: 'group-schedule',
  scheduledurationminutes: 'group-schedule',
};

const TIME = /^([01]\d|2[0-3]):[0-5]\d$/;

let nextKey = 1;
const newRow = (day = DAYS[0].value as string, time = '', minutes = '90'): SlotRow => ({
  key: nextKey++,
  day,
  time,
  minutes,
});

/**
 * Creates a training group or edits one (US-09, RN-01, RN-02, RN-21): name, the levels it accepts, venue, weekly slots in
 * Lisbon time, seats and coach. The venue is chosen once: the API's edit has none, so it is shown fixed. Overlapping slots, a
 * coach who cannot coach and the rest of the rules are the API's to refuse; its message is shown in the sheet. Sessions are
 * made by the daily generation, not by saving, and the sheet says so.
 */
export function GroupSheet({
  group,
  onSaved,
  onClose,
  onCloseAutoFocus,
}: {
  /** `null` to add a group. */
  group: Group | null;
  onSaved: (name: string) => void;
  onClose: () => void;
  onCloseAutoFocus: (event: Event) => void;
}) {
  const save = useSaveGroup();
  const levels = useLevels();
  const venues = useVenues();
  const coaches = useCoaches();

  const [name, setName] = useState(group?.name ?? '');
  const [accepted, setAccepted] = useState<Set<string>>(
    new Set(group?.acceptedLevels.map(({ id }) => id)),
  );
  const [venueId, setVenueId] = useState(group?.venue?.id ?? '');
  const [coachId, setCoachId] = useState(group?.coach?.id ?? '');
  const [seats, setSeats] = useState(group ? String(group.capacity) : '');
  const [rows, setRows] = useState<SlotRow[]>(() =>
    group && group.schedule.length > 0
      ? group.schedule.map((slot) =>
          newRow(
            isDay(slot.dayOfWeek) ? slot.dayOfWeek : (DAYS[0].value as string),
            slot.startTime,
            String(slot.durationMinutes),
          ),
        )
      : [newRow()],
  );
  const { problems, fail, clear } = useFormProblems(save.error, FIELD_MAP);

  const labels = {
    ...LABELS,
    ...Object.fromEntries(
      rows.flatMap((row, index) => [
        [slotControl(row, 'time'), `Slot ${String(index + 1)} start time`],
        [slotControl(row, 'minutes'), `Slot ${String(index + 1)} length`],
      ]),
    ),
  };

  const venueOptions: Named[] =
    venues.data?.map(({ id, name: venueName }) => ({ id, name: venueName })) ?? [];
  if (group?.venue && !venueOptions.some(({ id }) => id === group.venue?.id)) {
    venueOptions.unshift(group.venue);
  }
  const coachOptions: Named[] = [...(coaches.data?.coaches ?? [])];
  // A coach who lost the role stays in the list, so the group still shows who it has and the API can say why it refuses.
  if (group?.coach && !coachOptions.some(({ id }) => id === group.coach?.id)) {
    coachOptions.unshift(group.coach);
  }

  const submit = () => {
    const missing: Record<string, string> = {};
    if (!name.trim()) missing['group-name'] = 'Enter the group name';
    if (accepted.size === 0) missing['group-levels'] = 'Tick at least one level';
    if (!venueId) missing['group-venue'] = 'Choose a venue';
    if (!coachId) missing['group-coach'] = 'Choose a coach';
    const capacity = wholeNumber(seats, SEATS.min, SEATS.max);
    if (capacity === null) {
      missing['group-seats'] = seats.trim()
        ? wholeNumberHint(SEATS.min, SEATS.max)
        : 'Enter the number of seats';
    }
    const schedule: Schemas['WeeklySlotRequest'][] = [];
    for (const row of rows) {
      const minutes = wholeNumber(row.minutes, SLOT_MINUTES.min, SLOT_MINUTES.max);
      if (!TIME.test(row.time)) missing[slotControl(row, 'time')] = 'Enter the start time';
      if (minutes === null) {
        missing[slotControl(row, 'minutes')] = wholeNumberHint(SLOT_MINUTES.min, SLOT_MINUTES.max);
      }
      if (isDay(row.day) && TIME.test(row.time) && minutes !== null) {
        schedule.push({ dayOfWeek: row.day, startTime: row.time, durationMinutes: minutes });
      }
    }
    if (Object.keys(missing).length > 0 || capacity === null) {
      fail(missing);
      return;
    }

    clear();
    const common = {
      name: name.trim(),
      acceptedLevelIds: [...accepted],
      coachId,
      capacity,
      schedule,
    };
    save.mutate(
      { id: group?.id ?? null, body: group ? common : { ...common, venueId } },
      {
        onSuccess: () => {
          onSaved(common.name);
        },
      },
    );
  };

  const noVenue = venues.isSuccess && venueOptions.length === 0;
  const noCoach = coaches.isSuccess && coachOptions.length === 0;
  const coachHint =
    'Active members who hold the coach role.' +
    (coaches.data?.incomplete
      ? ' Only the first members were checked, so a coach may be missing from this list.'
      : '');

  return (
    <FormSheet
      title={group ? `Edit ${group.name}` : 'Add a training group'}
      description="Sessions for this group appear after the next daily generation."
      submitLabel={group ? 'Save group' : 'Add group'}
      pendingLabel="Saving…"
      pending={save.isPending}
      problems={problems}
      labels={labels}
      onSubmit={submit}
      onClose={onClose}
      onCloseAutoFocus={onCloseAutoFocus}
    >
      <TextField
        id="group-name"
        label="Name"
        maxLength={MAX_GROUP_NAME}
        required
        value={name}
        error={problems?.fields['group-name']}
        onChange={(event) => {
          setName(event.target.value);
        }}
      />

      <LevelCheckboxes
        id="group-levels"
        legend="Accepted levels"
        hint="Members can book a group that accepts their own level or a lower one."
        error={problems?.fields['group-levels']}
        levels={levels}
        selected={accepted}
        onChange={setAccepted}
      />

      <div className="grid gap-2">
        <SelectField
          id="group-venue"
          label="Venue"
          hint={
            group
              ? 'The venue of a group cannot be changed. Archive the group and create a new one to move it.'
              : undefined
          }
          value={venueId}
          disabled={group !== null}
          error={problems?.fields['group-venue']}
          onChange={(event) => {
            setVenueId(event.target.value);
          }}
        >
          <option value="">Choose a venue</option>
          {venueOptions.map(({ id, name: venueName }) => (
            <option key={id} value={id}>
              {venueName}
            </option>
          ))}
        </SelectField>
        {noVenue && (
          <p className="text-sm text-muted-foreground">
            There is no venue yet.{' '}
            <Link to="/admin/setup/venues" className="text-primary underline underline-offset-4">
              Add a venue
            </Link>{' '}
            first.
          </p>
        )}
      </div>

      <SlotRows
        id="group-schedule"
        rows={rows}
        errors={problems?.fields ?? {}}
        scheduleError={problems?.fields['group-schedule']}
        onChange={(key, change) => {
          setRows((current) =>
            current.map((row) => (row.key === key ? { ...row, ...change } : row)),
          );
        }}
        onAdd={() => {
          setRows((current) => (current.length >= MAX_SLOTS ? current : [...current, newRow()]));
        }}
        onRemove={(key) => {
          setRows((current) =>
            current.length <= 1 ? current : current.filter((row) => row.key !== key),
          );
        }}
      />

      <TextField
        id="group-seats"
        label="Seats"
        hint="Usually a multiple of 6 (12 seats is two teams on one court), but any number from 1 to 1000 is allowed. The coach does not use a seat."
        type="number"
        inputMode="numeric"
        min={SEATS.min}
        max={SEATS.max}
        required
        value={seats}
        error={problems?.fields['group-seats']}
        onChange={(event) => {
          setSeats(event.target.value);
        }}
      />

      <div className="grid gap-2">
        <SelectField
          id="group-coach"
          label="Coach"
          hint={coachHint}
          value={coachId}
          error={problems?.fields['group-coach']}
          onChange={(event) => {
            setCoachId(event.target.value);
          }}
        >
          <option value="">Choose a coach</option>
          {coachOptions.map(({ id, name: coachName }) => (
            <option key={id} value={id}>
              {coachName}
            </option>
          ))}
        </SelectField>
        {noCoach && (
          <p className="text-sm text-muted-foreground">
            No active member holds the coach role yet.{' '}
            <Link to="/admin/members" className="text-primary underline underline-offset-4">
              Give the role on the Members page
            </Link>
            .
          </p>
        )}
      </div>
    </FormSheet>
  );
}
