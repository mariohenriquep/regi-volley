import { useSyncExternalStore } from 'react';
import { Button } from '@/components/ui/button';
import { useUpdates } from './context';

/** "A new version is available — reload": shown only when the service worker has one waiting. */
export function UpdatePrompt() {
  const updates = useUpdates();
  const available = useSyncExternalStore(updates.subscribe, updates.getSnapshot);
  if (!available) return null;
  return (
    <div
      role="status"
      className="flex flex-wrap items-center justify-between gap-2 border-b bg-secondary px-4 py-2 text-sm text-secondary-foreground"
    >
      <p>A new version is available — reload when you are ready. Unsent input is lost on reload.</p>
      <div className="flex gap-2">
        <Button
          size="sm"
          onClick={() => {
            void updates.apply();
          }}
        >
          Reload
        </Button>
        <Button size="sm" variant="outline" onClick={updates.dismiss}>
          Later
        </Button>
      </div>
    </div>
  );
}
