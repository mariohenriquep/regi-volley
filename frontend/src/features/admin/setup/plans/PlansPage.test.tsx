import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import {
  BEGINNER_ID,
  INTERMEDIATE_ID,
  LEVELS_URL,
  PLAN_ID,
  PLANS_URL,
  levelsAnswer,
  meAs,
  planEntry,
  signedIn,
} from '@/test/fixtures';
import { apiError, recorder } from '@/test/handlers';
import { renderApp } from '@/test/render';
import { server } from '@/test/server';

type Entry = ReturnType<typeof planEntry>;

const monthly = () =>
  planEntry({
    id: 'a1000000-0000-0000-0000-000000000002',
    name: 'Monthly',
    type: 'MONTHLY_UNLIMITED',
    priceCents: 3500,
    credits: undefined,
    validityDays: undefined,
  });
const weekly = () =>
  planEntry({
    id: 'a1000000-0000-0000-0000-000000000003',
    name: 'Twice a week',
    type: 'MONTHLY_N_PER_WEEK',
    priceCents: 2500,
    sessionsPerWeek: 2,
    credits: undefined,
    validityDays: undefined,
    allowedLevels: [{ id: BEGINNER_ID, name: 'Beginner' }],
  });
const single = () =>
  planEntry({
    id: 'a1000000-0000-0000-0000-000000000004',
    name: 'Drop-in',
    type: 'SINGLE_SESSION',
    priceCents: 600,
    credits: 1,
    validityDays: 7,
  });

/** The plans as the API keeps them. */
function servePlans(initial: Entry[] = [planEntry(), monthly(), weekly(), single()]) {
  const state = { plans: initial };
  const { seen, record } = recorder();
  server.use(
    signedIn,
    meAs('MEMBER', 'ADMIN'),
    http.get(LEVELS_URL, () => HttpResponse.json(levelsAnswer())),
    http.get(PLANS_URL, () => HttpResponse.json(state.plans)),
    http.post(PLANS_URL, async ({ request }) => {
      await record(request);
      const { allowedLevelIds, ...body } = (await request.json()) as Record<string, unknown> & {
        allowedLevelIds: string[];
      };
      state.plans = [
        ...state.plans,
        planEntry({
          ...(body as Partial<Entry>),
          id: 'a1000000-0000-0000-0000-0000000000ff',
          allowedLevels: allowedLevelIds.map((id) => ({
            id,
            name: id === BEGINNER_ID ? 'Beginner' : 'Intermediate',
          })),
        }),
      ];
      return HttpResponse.json({ id: 'a1000000-0000-0000-0000-0000000000ff' }, { status: 201 });
    }),
    http.put(`${PLANS_URL}/:planId`, async ({ request, params }) => {
      await record(request);
      const body = (await request.json()) as Partial<Entry>;
      state.plans = state.plans.map((plan) =>
        plan.id === params.planId
          ? planEntry({ ...plan, ...body, allowedLevels: plan.allowedLevels })
          : plan,
      );
      return HttpResponse.json({ id: params.planId });
    }),
  );
  return { state, seen };
}

async function openPlans(initial?: Entry[]) {
  const served = servePlans(initial);
  const rendered = renderApp('/admin/setup/plans');
  await screen.findByRole('button', { name: 'Add plan' });
  await waitFor(() => {
    expect(screen.queryByRole('status', { name: 'Loading plans' })).not.toBeInTheDocument();
  });
  return { ...rendered, ...served };
}

const planCard = (name: string) => screen.getByRole('article', { name });

describe('the plans screen', () => {
  it('lists name, type, terms, price and allowed levels of each plan', async () => {
    // Arrange / Act
    await openPlans();

    // Assert
    expect(screen.getByRole('heading', { level: 1, name: 'Plans' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to Admin' })).toHaveAttribute('href', '/admin');
    const pack = planCard('Pack of 10');
    expect(pack).toHaveTextContent('Pack of sessions');
    expect(pack).toHaveTextContent('10 credits, valid for 90 days');
    expect(pack).toHaveTextContent('€50.00');
    expect(pack).toHaveTextContent('All levels');
    expect(planCard('Monthly')).toHaveTextContent('Unlimited sessions for one month');
    expect(planCard('Monthly')).toHaveTextContent('€35.00');
    expect(planCard('Twice a week')).toHaveTextContent('2 sessions per week for one month');
    expect(planCard('Twice a week')).toHaveTextContent('Beginner');
    expect(planCard('Twice a week')).not.toHaveTextContent('All levels');
    expect(planCard('Drop-in')).toHaveTextContent('1 session, valid for 7 days');
    expect(planCard('Drop-in')).toHaveTextContent('€6.00');
  });

  it('says so when there are no plans', async () => {
    // Arrange
    server.use(
      signedIn,
      meAs('MEMBER', 'ADMIN'),
      http.get(PLANS_URL, () => HttpResponse.json([])),
    );

    // Act
    renderApp('/admin/setup/plans');

    // Assert
    expect(await screen.findByText('No plans yet')).toBeInTheDocument();
  });

  async function openCreate(initial?: Entry[]) {
    const opened = await openPlans(initial);
    await opened.user.click(screen.getByRole('button', { name: 'Add plan' }));
    const dialog = await screen.findByRole('dialog', { name: 'Add a plan' });
    return { ...opened, dialog };
  }

  it('creates a pack with its credits and validity, for all levels', async () => {
    // Arrange
    const { user, seen, dialog } = await openCreate([]);

    // Act
    await user.type(within(dialog).getByLabelText('Name'), 'Pack of 20');
    await user.selectOptions(within(dialog).getByLabelText('Type'), 'Pack of sessions');
    await user.type(within(dialog).getByLabelText('Credits'), '20');
    await user.type(within(dialog).getByLabelText('Valid for (days)'), '120');
    await user.type(within(dialog).getByLabelText('Price (€)'), '89.90');
    await user.click(within(dialog).getByRole('button', { name: 'Add plan' }));

    // Assert
    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
    expect(seen[0]).toMatchObject({ method: 'POST', path: PLANS_URL });
    expect(JSON.parse(seen[0]?.body ?? '')).toEqual({
      name: 'Pack of 20',
      type: 'PACK',
      priceCents: 8990,
      credits: 20,
      validityDays: 120,
      allowedLevelIds: [],
    });
    expect(await screen.findByRole('article', { name: 'Pack of 20' })).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Pack of 20 was added');
  });

  it('shows only the fields of the chosen type and sends only those', async () => {
    // Arrange
    const { user, seen, dialog } = await openCreate([]);
    const has = (label: string) => within(dialog).queryByLabelText(label) !== null;

    // Assert: a pack asks for credits and validity (the form starts with one)
    expect(has('Credits')).toBe(true);
    expect(has('Valid for (days)')).toBe(true);
    expect(has('Sessions per week')).toBe(false);

    // Act / Assert: unlimited asks for neither
    await user.selectOptions(within(dialog).getByLabelText('Type'), 'Monthly, unlimited sessions');
    expect(has('Credits')).toBe(false);
    expect(has('Valid for (days)')).toBe(false);
    expect(has('Sessions per week')).toBe(false);

    // Act / Assert: N per week asks for the weekly sessions
    await user.selectOptions(within(dialog).getByLabelText('Type'), 'Monthly, N sessions per week');
    expect(has('Sessions per week')).toBe(true);
    expect(has('Credits')).toBe(false);

    // Act / Assert: a single session asks for its validity only
    await user.selectOptions(within(dialog).getByLabelText('Type'), 'Single session');
    expect(has('Valid for (days)')).toBe(true);
    expect(has('Credits')).toBe(false);
    expect(has('Sessions per week')).toBe(false);

    // Act: values typed for another type are not sent
    await user.selectOptions(within(dialog).getByLabelText('Type'), 'Pack of sessions');
    await user.type(within(dialog).getByLabelText('Credits'), '5');
    await user.selectOptions(within(dialog).getByLabelText('Type'), 'Single session');
    await user.type(within(dialog).getByLabelText('Name'), 'Drop-in');
    await user.type(within(dialog).getByLabelText('Valid for (days)'), '7');
    await user.type(within(dialog).getByLabelText('Price (€)'), '6');
    await user.click(within(dialog).getByRole('button', { name: 'Add plan' }));

    // Assert: a single session always holds exactly one credit
    await waitFor(() => {
      expect(seen).toHaveLength(1);
    });
    expect(JSON.parse(seen[0]?.body ?? '')).toEqual({
      name: 'Drop-in',
      type: 'SINGLE_SESSION',
      priceCents: 600,
      credits: 1,
      validityDays: 7,
      allowedLevelIds: [],
    });
  });

  it('creates a monthly plan for some levels only', async () => {
    // Arrange
    const { user, seen, dialog } = await openCreate([]);

    // Act
    await user.type(within(dialog).getByLabelText('Name'), 'Open play');
    await user.selectOptions(within(dialog).getByLabelText('Type'), 'Monthly, N sessions per week');
    await user.type(within(dialog).getByLabelText('Sessions per week'), '3');
    await user.type(within(dialog).getByLabelText('Price (€)'), '30');
    await user.click(await within(dialog).findByRole('checkbox', { name: 'Intermediate' }));
    await user.click(within(dialog).getByRole('button', { name: 'Add plan' }));

    // Assert
    await waitFor(() => {
      expect(seen).toHaveLength(1);
    });
    expect(JSON.parse(seen[0]?.body ?? '')).toEqual({
      name: 'Open play',
      type: 'MONTHLY_N_PER_WEEK',
      priceCents: 3000,
      sessionsPerWeek: 3,
      allowedLevelIds: [INTERMEDIATE_ID],
    });
  });

  it('explains that no level ticked means every level', async () => {
    // Arrange / Act
    const { dialog } = await openCreate([]);

    // Assert
    const levels = await within(dialog).findByRole('group', { name: 'Allowed levels' });
    expect(levels).toHaveAccessibleDescription(
      'Leave all unticked to allow every level. Otherwise only members of the ticked levels can use this plan.',
    );
  });

  it.each([
    ['0.10', 10],
    ['0.20', 20],
    ['0.30', 30],
    ['19.99', 1999],
    ['19,99', 1999],
    ['1.15', 115],
    ['0', 0],
    ['50', 5000],
  ])('sends the price %s as exactly %i cents', async (typed, cents) => {
    // Arrange
    const { user, seen, dialog } = await openCreate([]);

    // Act
    await user.type(within(dialog).getByLabelText('Name'), 'Edge');
    await user.selectOptions(within(dialog).getByLabelText('Type'), 'Monthly, unlimited sessions');
    await user.type(within(dialog).getByLabelText('Price (€)'), typed);
    await user.click(within(dialog).getByRole('button', { name: 'Add plan' }));

    // Assert
    await waitFor(() => {
      expect(seen).toHaveLength(1);
    });
    expect((JSON.parse(seen[0]?.body ?? '') as { priceCents: number }).priceCents).toBe(cents);
  });

  it.each(['abc', '1.234', '-5', '€5', '1000000.01', ''])(
    'refuses the price %j without asking the API',
    async (typed) => {
      // Arrange
      const { user, seen, dialog } = await openCreate([]);
      await user.type(within(dialog).getByLabelText('Name'), 'Edge');
      await user.selectOptions(
        within(dialog).getByLabelText('Type'),
        'Monthly, unlimited sessions',
      );
      if (typed) await user.type(within(dialog).getByLabelText('Price (€)'), typed);

      // Act
      await user.click(within(dialog).getByRole('button', { name: 'Add plan' }));

      // Assert
      expect(within(dialog).getByLabelText('Price (€)')).toBeInvalid();
      expect(within(dialog).getByRole('alert')).toHaveFocus();
      expect(seen).toEqual([]);
    },
  );

  it('refuses a missing name and missing type-specific numbers before asking the API', async () => {
    // Arrange
    const { user, seen, dialog } = await openCreate([]);
    await user.type(within(dialog).getByLabelText('Price (€)'), '10');

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Add plan' }));

    // Assert
    expect(within(dialog).getByLabelText('Name')).toHaveAccessibleDescription(
      'Enter the plan name',
    );
    expect(within(dialog).getByLabelText('Credits')).toHaveAccessibleDescription(
      expect.stringContaining('Enter a whole number from 1 to 1000.'),
    );
    expect(within(dialog).getByLabelText('Valid for (days)')).toHaveAccessibleDescription(
      expect.stringContaining('Enter a whole number from 1 to 3650.'),
    );
    expect(seen).toEqual([]);
  });

  it('puts the API message on the field it names', async () => {
    // Arrange
    const { user, dialog } = await openCreate([]);
    server.use(
      http.post(PLANS_URL, () =>
        apiError(422, 'INVALID_FIELD', 'A PACK plan needs validityDays of at least 1', {
          fields: ['validityDays'],
        }),
      ),
    );
    await user.type(within(dialog).getByLabelText('Name'), 'Pack');
    await user.type(within(dialog).getByLabelText('Credits'), '10');
    await user.type(within(dialog).getByLabelText('Valid for (days)'), '30');
    await user.type(within(dialog).getByLabelText('Price (€)'), '10');

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Add plan' }));

    // Assert
    await waitFor(() => {
      expect(within(dialog).getByLabelText('Valid for (days)')).toHaveAccessibleDescription(
        expect.stringContaining('A PACK plan needs validityDays of at least 1'),
      );
    });
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('edits a plan: prefilled, the price changed, and says existing subscriptions keep theirs', async () => {
    // Arrange
    const { user, seen } = await openPlans();

    // Act
    await user.click(within(planCard('Pack of 10')).getByRole('button', { name: /^Edit/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit Pack of 10' });

    // Assert: prefilled
    expect(within(dialog).getByLabelText('Name')).toHaveValue('Pack of 10');
    expect(within(dialog).getByLabelText('Type')).toHaveDisplayValue('Pack of sessions');
    expect(within(dialog).getByLabelText('Credits')).toHaveValue(10);
    expect(within(dialog).getByLabelText('Valid for (days)')).toHaveValue(90);
    expect(within(dialog).getByLabelText('Price (€)')).toHaveValue('50.00');
    expect(dialog).toHaveTextContent(
      'Members who already have this plan keep the price and terms they bought.',
    );

    // Act
    const price = within(dialog).getByLabelText('Price (€)');
    await user.clear(price);
    await user.type(price, '45.5');
    await user.click(within(dialog).getByRole('button', { name: 'Save plan' }));

    // Assert
    await waitFor(() => {
      expect(planCard('Pack of 10')).toHaveTextContent('€45.50');
    });
    expect(seen[0]).toMatchObject({ method: 'PUT', path: `${PLANS_URL}/${PLAN_ID}` });
    expect(JSON.parse(seen[0]?.body ?? '')).toEqual({
      name: 'Pack of 10',
      type: 'PACK',
      priceCents: 4550,
      credits: 10,
      validityDays: 90,
      allowedLevelIds: [],
    });
    expect(screen.getByRole('status')).toHaveTextContent('Pack of 10 was saved');
  });

  it('starts an edit with the plan’s levels ticked', async () => {
    // Arrange
    const { user } = await openPlans();

    // Act
    await user.click(within(planCard('Twice a week')).getByRole('button', { name: /^Edit/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit Twice a week' });

    // Assert
    expect(await within(dialog).findByRole('checkbox', { name: 'Beginner' })).toBeChecked();
    expect(within(dialog).getByRole('checkbox', { name: 'Intermediate' })).not.toBeChecked();
    expect(within(dialog).getByLabelText('Sessions per week')).toHaveValue(2);
  });

  it('shows the API refusal of an edit and keeps the sheet open', async () => {
    // Arrange
    const { user } = await openPlans();
    server.use(
      http.put(`${PLANS_URL}/${PLAN_ID}`, () =>
        apiError(409, 'CONFLICT', 'The data was changed by someone else. Reload and try again.'),
      ),
    );
    await user.click(within(planCard('Pack of 10')).getByRole('button', { name: /^Edit/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit Pack of 10' });

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Save plan' }));

    // Assert
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('changed by someone else');
  });

  it('tells a member who is not an administrator that it is for administrators', async () => {
    // Arrange
    server.use(
      signedIn,
      http.get(PLANS_URL, () => apiError(403, 'FORBIDDEN', 'Access denied')),
    );

    // Act
    renderApp('/admin/setup/plans');

    // Assert
    expect(await screen.findByText('Administration is for administrators.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add plan' })).not.toBeInTheDocument();
  });
});
