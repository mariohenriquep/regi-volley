import { useId } from 'react';
import { Badge } from '@/components/ui/badge';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { QueryState } from '@/components/QueryState';
import { longDate } from '@/lib/lisbon';
import { usePageTitle } from '@/lib/usePageTitle';
import type { Plan, Subscription } from './model';
import { usePlan } from './usePlan';

// Maps, not objects: the type and the status are the API's text, and a lookup of "constructor" must find nothing.
const TYPE_LABELS = new Map([
  ['MONTHLY_UNLIMITED', 'Monthly, unlimited'],
  ['MONTHLY_N_PER_WEEK', 'Monthly, a fixed number of sessions a week'],
  ['PACK', 'Pack of credits'],
  ['SINGLE_SESSION', 'Single session'],
]);

const PAYMENT = new Map<string, { label: string; variant: 'success' | 'warning' | 'destructive' }>([
  ['PAID', { label: 'Paid', variant: 'success' }],
  ['PENDING', { label: 'Payment pending', variant: 'warning' }],
  ['OVERDUE', { label: 'Overdue', variant: 'destructive' }],
]);

const plural = (count: number, one: string, many: string) =>
  `${String(count)} ${count === 1 ? one : many}`;

function balance(subscription: Subscription): string | null {
  const { type, remaining } = subscription;
  if (type === 'MONTHLY_UNLIMITED') return 'Unlimited sessions';
  if (remaining === undefined) return null;
  return type === 'MONTHLY_N_PER_WEEK'
    ? `${plural(remaining, 'session', 'sessions')} left this week`
    : `${plural(remaining, 'credit', 'credits')} left`;
}

/** My plan (US-23): what the member holds, what is left, and whether the payment lets them book (RN-18). */
export function MyPlanPage() {
  usePageTitle('My plan');
  const plan = usePlan();
  return (
    <section className="grid gap-4">
      <h1 className="text-2xl font-semibold">My plan</h1>
      <QueryState query={plan} loadingLabel="Loading your plan">
        {(data) => <Subscriptions plan={data} />}
      </QueryState>
    </section>
  );
}

function Subscriptions({ plan }: { plan: Plan }) {
  if (plan.subscriptions.length === 0) {
    return (
      <div className="grid gap-1 py-8 text-center">
        <p className="font-medium">You have no current plan</p>
        <p className="text-sm text-muted-foreground">
          Ask your association to assign you one, then you can book.
        </p>
      </div>
    );
  }
  return (
    <div className="grid gap-4">
      {plan.subscriptions.map((subscription) => (
        <SubscriptionCard key={subscription.id} subscription={subscription} asOf={plan.asOf} />
      ))}
    </div>
  );
}

function SubscriptionCard({ subscription, asOf }: { subscription: Subscription; asOf: string }) {
  const titleId = useId();
  const payment = PAYMENT.get(subscription.paymentStatus);
  const left = balance(subscription);
  const upcoming = asOf !== '' && subscription.startDate > asOf;
  return (
    <article
      aria-labelledby={titleId}
      className="grid gap-3 rounded-xl bg-card p-4 text-card-foreground ring-1 ring-foreground/10"
    >
      <div className="flex items-start justify-between gap-2">
        <div className="grid gap-0.5">
          <h2 id={titleId} className="text-base font-semibold">
            {subscription.planName}
          </h2>
          <p className="text-sm text-muted-foreground">
            {TYPE_LABELS.get(subscription.type) ?? subscription.type}
          </p>
        </div>
        <Badge variant={payment?.variant ?? 'secondary'}>
          {payment?.label ?? subscription.paymentStatus}
        </Badge>
      </div>
      <p className="text-sm">
        {longDate(subscription.startDate)} – {longDate(subscription.endDate)}
      </p>
      {upcoming && (
        <p className="text-sm text-muted-foreground">
          Starts on {longDate(subscription.startDate)}
        </p>
      )}
      {left && <p className="text-lg font-semibold">{left}</p>}
      {subscription.paymentStatus === 'OVERDUE' && (
        <Alert variant="destructive">
          <AlertDescription>
            You can&apos;t book while your payment is overdue — contact your association.
          </AlertDescription>
        </Alert>
      )}
    </article>
  );
}
