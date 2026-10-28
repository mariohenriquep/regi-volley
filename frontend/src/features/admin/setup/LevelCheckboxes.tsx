import { ErrorAlert } from '@/components/ErrorAlert';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import type { LevelOption } from '../members/model';

/**
 * A set of levels to tick, lowest first, as the API lists them (`GET /levels`): the accepted levels of a group, the allowed
 * levels of a plan. A fieldset, so a screen reader announces the question before each checkbox; `id` is the fieldset's, which
 * the error summary focuses.
 */
export function LevelCheckboxes({
  id,
  legend,
  hint,
  error,
  levels,
  selected,
  onChange,
}: {
  id: string;
  legend: string;
  hint: string;
  error?: string;
  levels: {
    isPending: boolean;
    error: unknown;
    data: LevelOption[] | undefined;
    refetch: () => unknown;
  };
  selected: ReadonlySet<string>;
  onChange: (next: Set<string>) => void;
}) {
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [hintId, error ? errorId : null].filter(Boolean).join(' ');
  return (
    <fieldset
      id={id}
      tabIndex={-1}
      aria-describedby={describedBy}
      aria-invalid={error ? true : undefined}
      className="grid gap-2 outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
    >
      <legend className="mb-2 text-sm font-medium">{legend}</legend>
      <p id={hintId} className="text-sm text-muted-foreground">
        {hint}
      </p>
      {levels.isPending && <p role="status">Loading levels…</p>}
      {levels.error != null && (
        <div className="grid gap-2">
          <ErrorAlert error={levels.error} />
          <Button
            type="button"
            variant="outline"
            className="h-11 w-fit"
            onClick={() => {
              void levels.refetch();
            }}
          >
            Try again
          </Button>
        </div>
      )}
      {levels.data?.map((level) => {
        const boxId = `${id}-${level.id}`;
        return (
          <div key={level.id} className="flex items-center gap-3 py-1">
            <Checkbox
              id={boxId}
              checked={selected.has(level.id)}
              onCheckedChange={(checked) => {
                const next = new Set(selected);
                if (checked === true) next.add(level.id);
                else next.delete(level.id);
                onChange(next);
              }}
              className="size-5"
            />
            <Label htmlFor={boxId} className="min-h-6 flex-1 text-base font-normal md:text-sm">
              {level.name}
            </Label>
          </div>
        );
      })}
      {error && (
        <p id={errorId} className="text-sm text-destructive">
          {error}
        </p>
      )}
    </fieldset>
  );
}
