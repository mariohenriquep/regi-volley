import { PlusIcon } from 'lucide-react';
import { useId, useState } from 'react';
import { QueryState } from '@/components/QueryState';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { isForbidden } from '../../forbidden';
import { useAdminNotice } from '../../useAdminNotice';
import { SetupScreen } from '../SetupScreen';
import { type Plan, priceText, termsText, typeLabel } from './model';
import { PlanSheet } from './PlanSheet';
import { usePlans } from './usePlans';

type Editing = { plan: Plan | null };

/**
 * The association's plans (US-19, RN-13, RN-14): what each one is, gives, costs and allows. Add and edit; there is no delete
 * (the API has none: a plan that members hold must stay readable). Editing never changes what existing subscriptions paid.
 */
export function PlansPage() {
  const plans = usePlans();
  const notice = useAdminNotice();
  const [editing, setEditing] = useState<Editing | null>(null);

  return (
    <SetupScreen title="Plans" forbidden={isForbidden(plans.error)}>
      {notice.notice}
      <Button
        className="h-11 w-fit"
        onClick={(event) => {
          notice.remember(event.currentTarget);
          setEditing({ plan: null });
        }}
      >
        <PlusIcon aria-hidden="true" />
        Add plan
      </Button>

      <QueryState query={plans} loadingLabel="Loading plans">
        {(rows) =>
          rows.length === 0 ? (
            <div className="grid justify-items-center gap-2 py-8 text-center">
              <p className="font-medium">No plans yet</p>
              <p className="text-sm text-muted-foreground">
                Members need a plan before they can book. Add one, then assign it to a member.
              </p>
            </div>
          ) : (
            <ul aria-label="Plans" className="grid gap-3">
              {rows.map((plan) => (
                <li key={plan.id}>
                  <PlanCard
                    plan={plan}
                    onEdit={(button) => {
                      notice.remember(button);
                      setEditing({ plan });
                    }}
                  />
                </li>
              ))}
            </ul>
          )
        }
      </QueryState>

      <Dialog
        open={editing !== null}
        onOpenChange={(open) => {
          if (!open) setEditing(null);
        }}
      >
        {editing && (
          <PlanSheet
            key={editing.plan?.id ?? 'new'}
            plan={editing.plan}
            onSaved={(name) => {
              const created = editing.plan === null;
              setEditing(null);
              notice.announce({ text: `${name} was ${created ? 'added' : 'saved'}.` });
            }}
            onClose={() => {
              setEditing(null);
            }}
            onCloseAutoFocus={notice.onCloseAutoFocus}
          />
        )}
      </Dialog>
    </SetupScreen>
  );
}

function PlanCard({ plan, onEdit }: { plan: Plan; onEdit: (button: HTMLElement) => void }) {
  const id = useId();
  const terms = termsText(plan);
  return (
    <article
      aria-labelledby={id}
      className="grid gap-3 rounded-xl bg-card p-4 text-card-foreground ring-1 ring-foreground/10"
    >
      <div className="grid gap-1">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 id={id} className="text-base font-semibold">
            {plan.name}
          </h2>
          <span className="text-base font-semibold">{priceText(plan)}</span>
        </div>
        <div>
          <Badge variant="outline">{typeLabel(plan.type)}</Badge>
        </div>
        {terms && <p className="text-sm">{terms}</p>}
        <p className="text-sm text-muted-foreground">
          {plan.allowedLevels.length === 0
            ? 'All levels'
            : plan.allowedLevels.map((level) => level.name).join(', ')}
        </p>
      </div>
      <div>
        <Button
          variant="outline"
          className="h-11 px-4"
          onClick={(event) => {
            onEdit(event.currentTarget);
          }}
        >
          Edit <span className="sr-only">{plan.name}</span>
        </Button>
      </div>
    </article>
  );
}
