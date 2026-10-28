import { PlusIcon, Trash2Icon } from 'lucide-react';
import { SelectField } from '@/components/form/SelectField';
import { TextField } from '@/components/form/TextField';
import { Button } from '@/components/ui/button';
import { DAYS, MAX_SLOTS, SLOT_MINUTES } from './model';
import { type SlotRow, slotControl } from './slotRow';

/**
 * The weekly slots of a group: day, start time (Lisbon) and length, one fieldset per slot. Rows are added and removed with
 * buttons; the API's limits (1 to 14 slots) are mirrored, and overlaps are the API's to refuse.
 */
export function SlotRows({
  id,
  rows,
  errors,
  scheduleError,
  onChange,
  onAdd,
  onRemove,
}: {
  /** Id of the whole fieldset, which the error summary focuses for a problem with the schedule as a whole. */
  id: string;
  rows: readonly SlotRow[];
  /** Errors by control id (see {@link slotControl}). */
  errors: Readonly<Record<string, string | undefined>>;
  scheduleError?: string;
  onChange: (key: number, change: Partial<Omit<SlotRow, 'key'>>) => void;
  onAdd: () => void;
  onRemove: (key: number) => void;
}) {
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  return (
    <fieldset
      id={id}
      tabIndex={-1}
      aria-describedby={[hintId, scheduleError ? errorId : null].filter(Boolean).join(' ')}
      aria-invalid={scheduleError ? true : undefined}
      className="grid gap-3 outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
    >
      <legend className="mb-2 text-sm font-medium">Weekly schedule</legend>
      <p id={hintId} className="text-sm text-muted-foreground">
        Times are Lisbon time. The slots of one group cannot overlap.
      </p>
      {rows.map((row, index) => (
        <fieldset key={row.key} className="grid gap-3 rounded-lg border p-3">
          <legend className="px-1 text-sm font-medium">{`Slot ${String(index + 1)}`}</legend>
          <SelectField
            id={slotControl(row, 'day')}
            label="Day"
            value={row.day}
            error={errors[slotControl(row, 'day')]}
            onChange={(event) => {
              onChange(row.key, { day: event.target.value });
            }}
          >
            {DAYS.map(({ value, label }) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </SelectField>
          <div className="grid grid-cols-2 gap-3">
            <TextField
              id={slotControl(row, 'time')}
              label="Start time (Lisbon)"
              type="time"
              required
              value={row.time}
              error={errors[slotControl(row, 'time')]}
              onChange={(event) => {
                onChange(row.key, { time: event.target.value });
              }}
            />
            <TextField
              id={slotControl(row, 'minutes')}
              label="Length (minutes)"
              type="number"
              inputMode="numeric"
              min={SLOT_MINUTES.min}
              max={SLOT_MINUTES.max}
              required
              value={row.minutes}
              error={errors[slotControl(row, 'minutes')]}
              onChange={(event) => {
                onChange(row.key, { minutes: event.target.value });
              }}
            />
          </div>
          <Button
            type="button"
            variant="outline"
            className="h-11 w-fit"
            disabled={rows.length === 1}
            onClick={() => {
              onRemove(row.key);
            }}
          >
            <Trash2Icon aria-hidden="true" />
            <span aria-hidden="true">Remove</span>
            <span className="sr-only">{`Remove slot ${String(index + 1)}`}</span>
          </Button>
        </fieldset>
      ))}
      {scheduleError && (
        <p id={errorId} className="text-sm text-destructive">
          {scheduleError}
        </p>
      )}
      <Button
        type="button"
        variant="outline"
        className="h-11 w-fit"
        disabled={rows.length >= MAX_SLOTS}
        onClick={onAdd}
      >
        <PlusIcon aria-hidden="true" />
        Add slot
      </Button>
    </fieldset>
  );
}
