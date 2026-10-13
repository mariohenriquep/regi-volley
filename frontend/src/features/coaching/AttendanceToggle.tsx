import type { Mark } from './useCoachingMutations';

const CHOICES: { mark: Mark; label: string; checked: string }[] = [
  {
    mark: 'ATTENDED',
    label: 'Attended',
    checked: 'peer-checked:bg-emerald-700 peer-checked:text-white',
  },
  { mark: 'NO_SHOW', label: 'No-show', checked: 'peer-checked:bg-red-700 peer-checked:text-white' },
];

/**
 * Two big answers for one seat holder: real radio buttons in a fieldset named after the person, so a screen reader hears
 * "Ana Silva, Attended, radio button" and the arrow keys move between the answers. Nothing chosen means "not marked yet".
 */
export function AttendanceToggle({
  bookingId,
  name,
  value,
  onChange,
}: {
  bookingId: string;
  name: string;
  value: Mark | undefined;
  onChange: (mark: Mark) => void;
}) {
  return (
    <fieldset className="grid min-w-0 gap-2">
      <legend className="mb-2 text-base font-medium">{name}</legend>
      <div className="grid grid-cols-2 gap-2">
        {CHOICES.map(({ mark, label, checked }) => (
          <label key={mark} className="relative block">
            <input
              type="radio"
              name={`mark-${bookingId}`}
              className="peer sr-only"
              checked={value === mark}
              onChange={() => {
                onChange(mark);
              }}
            />
            <span
              className={`flex h-12 cursor-pointer items-center justify-center rounded-lg border border-border bg-background text-sm font-medium select-none peer-focus-visible:ring-3 peer-focus-visible:ring-ring/50 ${checked}`}
            >
              {label}
            </span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}
