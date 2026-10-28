import { useState } from 'react';
import type { components } from '@/api/schema.gen';
import { SelectField } from '@/components/form/SelectField';
import { TextField } from '@/components/form/TextField';
import { centsToInput, parseEuros } from '@/lib/money';
import { useLevels } from '../../members/useMembers';
import { FormSheet } from '../FormSheet';
import { LevelCheckboxes } from '../LevelCheckboxes';
import { wholeNumber, wholeNumberHint } from '../numbers';
import { useFormProblems } from '../useFormProblems';
import {
  CREDITS,
  isPlanType,
  MAX_PLAN_NAME,
  PLAN_TYPES,
  type Plan,
  type PlanType,
  SESSIONS_PER_WEEK,
  VALIDITY_DAYS,
} from './model';
import { useSavePlan } from './usePlanMutations';

const LABELS = {
  'plan-name': 'Name',
  'plan-type': 'Type',
  'plan-sessions': 'Sessions per week',
  'plan-credits': 'Credits',
  'plan-validity': 'Valid for (days)',
  'plan-price': 'Price (€)',
  'plan-levels': 'Allowed levels',
};
// A 400 names the request property, a 422 the rule's label: both meet in one normalised key.
const FIELD_MAP = {
  name: 'plan-name',
  type: 'plan-type',
  sessionsperweek: 'plan-sessions',
  credits: 'plan-credits',
  validitydays: 'plan-validity',
  pricecents: 'plan-price',
  price: 'plan-price',
  allowedlevelids: 'plan-levels',
};

const hasValidity = (type: PlanType) => type === 'PACK' || type === 'SINGLE_SESSION';

type PlanBody = components['schemas']['PlanRequest'];

/**
 * Creates a plan or edits one (US-19, RN-13, RN-14). The fields depend on the type, and only those are sent. The price is typed
 * in euros and read as exact cents (`parseEuros`). Editing a plan does not change what existing subscriptions paid or got: the
 * API snapshots both, and the sheet says so. The type rules (a pack needs validity, ...) are the API's; the sheet only keeps
 * numbers within its limits.
 */
export function PlanSheet({
  plan,
  onSaved,
  onClose,
  onCloseAutoFocus,
}: {
  /** `null` to add a plan. */
  plan: Plan | null;
  onSaved: (name: string) => void;
  onClose: () => void;
  onCloseAutoFocus: (event: Event) => void;
}) {
  const save = useSavePlan();
  const levels = useLevels();
  const [name, setName] = useState(plan?.name ?? '');
  const [type, setType] = useState<PlanType>(isPlanType(plan?.type) ? plan.type : 'PACK');
  const [sessions, setSessions] = useState(String(plan?.sessionsPerWeek ?? ''));
  const [credits, setCredits] = useState(plan?.type === 'PACK' ? String(plan.credits ?? '') : '');
  const [validity, setValidity] = useState(String(plan?.validityDays ?? ''));
  const [price, setPrice] = useState(plan ? centsToInput(plan.priceCents) : '');
  const [allowed, setAllowed] = useState<Set<string>>(
    new Set(plan?.allowedLevels.map(({ id }) => id)),
  );
  const { problems, fail, clear } = useFormProblems(save.error, FIELD_MAP);

  const submit = () => {
    const missing: Record<string, string> = {};
    if (!name.trim()) missing['plan-name'] = 'Enter the plan name';
    const cents = parseEuros(price);
    if (cents === null) {
      missing['plan-price'] = price.trim()
        ? 'Enter an amount in euros with at most two decimals, such as 19.99.'
        : 'Enter the price in euros';
    }
    const weekly =
      type === 'MONTHLY_N_PER_WEEK'
        ? wholeNumber(sessions, SESSIONS_PER_WEEK.min, SESSIONS_PER_WEEK.max)
        : null;
    if (type === 'MONTHLY_N_PER_WEEK' && weekly === null) {
      missing['plan-sessions'] = wholeNumberHint(SESSIONS_PER_WEEK.min, SESSIONS_PER_WEEK.max);
    }
    const packCredits = type === 'PACK' ? wholeNumber(credits, CREDITS.min, CREDITS.max) : null;
    if (type === 'PACK' && packCredits === null) {
      missing['plan-credits'] = wholeNumberHint(CREDITS.min, CREDITS.max);
    }
    const days = hasValidity(type)
      ? wholeNumber(validity, VALIDITY_DAYS.min, VALIDITY_DAYS.max)
      : null;
    if (hasValidity(type) && days === null) {
      missing['plan-validity'] = wholeNumberHint(VALIDITY_DAYS.min, VALIDITY_DAYS.max);
    }
    if (Object.keys(missing).length > 0 || cents === null) {
      fail(missing);
      return;
    }

    // Only what the type uses; a single session always holds exactly one credit (the API insists).
    const body: PlanBody = {
      name: name.trim(),
      type,
      priceCents: cents,
      allowedLevelIds: [...allowed],
      ...(weekly !== null && { sessionsPerWeek: weekly }),
      ...(type === 'PACK' && packCredits !== null && { credits: packCredits }),
      ...(type === 'SINGLE_SESSION' && { credits: 1 }),
      ...(days !== null && { validityDays: days }),
    };
    clear();
    save.mutate(
      { id: plan?.id ?? null, body },
      {
        onSuccess: () => {
          onSaved(body.name);
        },
      },
    );
  };

  return (
    <FormSheet
      title={plan ? `Edit ${plan.name}` : 'Add a plan'}
      description={
        plan
          ? 'Members who already have this plan keep the price and terms they bought. Changes apply to new subscriptions.'
          : 'A plan is what members buy: a monthly pass, a pack of sessions or a single session.'
      }
      submitLabel={plan ? 'Save plan' : 'Add plan'}
      pendingLabel="Saving…"
      pending={save.isPending}
      problems={problems}
      labels={LABELS}
      onSubmit={submit}
      onClose={onClose}
      onCloseAutoFocus={onCloseAutoFocus}
    >
      <TextField
        id="plan-name"
        label="Name"
        maxLength={MAX_PLAN_NAME}
        required
        value={name}
        error={problems?.fields['plan-name']}
        onChange={(event) => {
          setName(event.target.value);
        }}
      />
      <SelectField
        id="plan-type"
        label="Type"
        value={type}
        error={problems?.fields['plan-type']}
        onChange={(event) => {
          if (isPlanType(event.target.value)) setType(event.target.value);
        }}
      >
        {PLAN_TYPES.map((option) => (
          <option key={option.type} value={option.type}>
            {option.label}
          </option>
        ))}
      </SelectField>

      {type === 'MONTHLY_N_PER_WEEK' && (
        <TextField
          id="plan-sessions"
          label="Sessions per week"
          type="number"
          inputMode="numeric"
          min={SESSIONS_PER_WEEK.min}
          max={SESSIONS_PER_WEEK.max}
          required
          value={sessions}
          error={problems?.fields['plan-sessions']}
          onChange={(event) => {
            setSessions(event.target.value);
          }}
        />
      )}
      {type === 'PACK' && (
        <TextField
          id="plan-credits"
          label="Credits"
          hint="A booking uses one credit."
          type="number"
          inputMode="numeric"
          min={CREDITS.min}
          max={CREDITS.max}
          required
          value={credits}
          error={problems?.fields['plan-credits']}
          onChange={(event) => {
            setCredits(event.target.value);
          }}
        />
      )}
      {hasValidity(type) && (
        <TextField
          id="plan-validity"
          label="Valid for (days)"
          hint="Counted from the day the subscription starts."
          type="number"
          inputMode="numeric"
          min={VALIDITY_DAYS.min}
          max={VALIDITY_DAYS.max}
          required
          value={validity}
          error={problems?.fields['plan-validity']}
          onChange={(event) => {
            setValidity(event.target.value);
          }}
        />
      )}
      {!hasValidity(type) && (
        <p className="text-sm text-muted-foreground">A monthly plan lasts one calendar month.</p>
      )}

      <TextField
        id="plan-price"
        label="Price (€)"
        hint="In euros, up to two decimals, for example 19.99."
        inputMode="decimal"
        autoComplete="off"
        maxLength={10}
        required
        value={price}
        error={problems?.fields['plan-price']}
        onChange={(event) => {
          setPrice(event.target.value);
        }}
      />

      <LevelCheckboxes
        id="plan-levels"
        legend="Allowed levels"
        hint="Leave all unticked to allow every level. Otherwise only members of the ticked levels can use this plan."
        error={problems?.fields['plan-levels']}
        levels={levels}
        selected={allowed}
        onChange={setAllowed}
      />
    </FormSheet>
  );
}
