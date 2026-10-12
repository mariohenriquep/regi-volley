import { XIcon } from 'lucide-react';
import type { ReactNode, Ref } from 'react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

/**
 * The result of an action, announced to screen readers (`role="status"`) and focusable, so the page can move focus
 * here when the control that was used has gone (the Book button turns into Cancel).
 */
export function Notice({
  ref,
  tone = 'info',
  children,
  onDismiss,
}: {
  ref?: Ref<HTMLDivElement>;
  tone?: 'info' | 'warning';
  children: ReactNode;
  onDismiss: () => void;
}) {
  return (
    <div
      ref={ref}
      role="status"
      tabIndex={-1}
      className={cn(
        'flex items-start justify-between gap-2 rounded-lg border p-3 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50',
        tone === 'warning' ? 'border-amber-300 bg-amber-50 text-amber-950' : 'bg-card',
      )}
    >
      <p>{children}</p>
      <Button variant="ghost" size="icon-sm" aria-label="Dismiss" onClick={onDismiss}>
        <XIcon aria-hidden="true" />
      </Button>
    </div>
  );
}
