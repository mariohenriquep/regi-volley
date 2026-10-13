import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { addDays, weekRange } from '@/lib/lisbon';

/** Previous and next week around the label of the week on screen; `week` and `thisWeek` are Mondays (`YYYY-MM-DD`). */
export function WeekNavigator({
  week,
  thisWeek,
  first,
  last,
  onGo,
}: {
  week: string;
  thisWeek: string;
  first: string;
  last: string;
  onGo: (week: string) => void;
}) {
  return (
    <nav aria-label="Week" className="flex items-center justify-between gap-2">
      <Button
        variant="outline"
        size="icon-lg"
        className="size-11"
        aria-label="Previous week"
        disabled={week <= first}
        onClick={() => {
          onGo(addDays(week, -7));
        }}
      >
        <ChevronLeftIcon aria-hidden="true" />
      </Button>
      <p className="text-center text-sm font-medium" aria-live="polite">
        {week === thisWeek ? 'This week · ' : ''}
        {weekRange(week)}
      </p>
      <Button
        variant="outline"
        size="icon-lg"
        className="size-11"
        aria-label="Next week"
        disabled={week >= last}
        onClick={() => {
          onGo(addDays(week, 7));
        }}
      >
        <ChevronRightIcon aria-hidden="true" />
      </Button>
    </nav>
  );
}
