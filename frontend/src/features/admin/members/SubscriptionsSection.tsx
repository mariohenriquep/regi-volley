import { useId } from 'react';
import { Badge } from '@/components/ui/badge';
import { formatCents } from '@/lib/money';
import { longDate } from '@/lib/lisbon';
import { plural } from '@/features/coaching/model';
import type { MemberSubscription } from './model';

// A Map, not an object: the status is the API's text, and a lookup of "constructor" must find nothing.
const PAYMENT = new Map<string, { label: string; variant: 'success' | 'warning' | 'destructive' }>([
  ['PAID', { label: 'Paid', variant: 'success' }],
  ['PENDING', { label: 'Payment pending', variant: 'warning' }],
  ['OVERDUE', { label: 'Overdue', variant: 'destructive' }],
]);

/** What the member holds, read-only: payments are recorded on their own screen. */
export function SubscriptionsSection({
  subscriptions,
  truncated,
}: {
  subscriptions: MemberSubscription[];
  truncated: boolean;
}) {
  return (
    <section aria-labelledby="subscriptions-heading" className="grid gap-3">
      <h2 id="subscriptions-heading" className="text-lg font-semibold">
        Subscriptions
      </h2>
      {subscriptions.length === 0 ? (
        <p className="text-sm text-muted-foreground">No subscriptions yet.</p>
      ) : (
        subscriptions.map((subscription) => (
          <SubscriptionCard key={subscription.id} subscription={subscription} />
        ))
      )}
      {truncated && (
        <p className="text-sm text-muted-foreground">
          Only the most recent subscriptions are shown.
        </p>
      )}
    </section>
  );
}

function SubscriptionCard({ subscription }: { subscription: MemberSubscription }) {
  const titleId = useId();
  const { startDate, endDate, paidCents, priceCents, creditsLeft } = subscription;
  const payment = PAYMENT.get(subscription.paymentStatus);
  return (
    <article
      aria-labelledby={titleId}
      className="grid gap-1.5 rounded-xl bg-card p-4 text-card-foreground ring-1 ring-foreground/10"
    >
      <div className="flex items-start justify-between gap-2">
        <h3 id={titleId} className="text-base font-semibold">
          {subscription.planName}
        </h3>
        <Badge variant={payment?.variant ?? 'outline'}>
          {payment?.label ?? subscription.paymentStatus}
        </Badge>
      </div>
      {startDate && endDate && (
        <p className="text-sm">
          {longDate(startDate)} – {longDate(endDate)}
        </p>
      )}
      {paidCents !== undefined && priceCents !== undefined && (
        <p className="text-sm">
          {formatCents(paidCents)} of {formatCents(priceCents)} paid
        </p>
      )}
      {creditsLeft !== undefined && (
        <p className="text-sm">{plural(creditsLeft, 'credit', 'credits')} left</p>
      )}
    </article>
  );
}
