import { Badge } from '@/components/ui/badge';

const LOOK = new Map<string, { label: string; variant: 'secondary' | 'success' | 'destructive' }>([
  ['SCHEDULED', { label: 'Scheduled', variant: 'secondary' }],
  ['COMPLETED', { label: 'Completed', variant: 'success' }],
  ['CANCELLED', { label: 'Cancelled', variant: 'destructive' }],
]);

/** The session's status in words; a status this version does not know is shown as the API wrote it. */
export function StatusBadge({ status }: { status: string }) {
  const look = LOOK.get(status);
  return <Badge variant={look?.variant ?? 'outline'}>{look?.label ?? status}</Badge>;
}
