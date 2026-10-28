import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import {
  BEGINNER_ID,
  COACH_ID,
  GROUP_ID,
  GROUPS_URL,
  INTERMEDIATE_ID,
  LEVELS_URL,
  MEMBERS_URL,
  OTHER_VENUE_ID,
  VENUES_URL,
  groupEntry,
  levelsAnswer,
  meAs,
  memberEntry,
  memberList,
  signedIn,
  venueEntry,
} from '@/test/fixtures';
import { apiError, recorder } from '@/test/handlers';
import { renderApp } from '@/test/render';
import { server } from '@/test/server';

type Entry = ReturnType<typeof groupEntry>;

const OTHER_COACH_ID = '22222222-2222-2222-2222-222222222224';
const ARCHIVED_ID = 'f0000000-0000-0000-0000-000000000002';

const archived = () =>
  groupEntry({ id: ARCHIVED_ID, name: 'Old Friday', status: 'ARCHIVED', capacity: 8 });

const members = () =>
  memberList([
    memberEntry({ memberId: COACH_ID, name: 'Rui Coach', roles: ['MEMBER', 'COACH'] }),
    memberEntry({ memberId: OTHER_COACH_ID, name: 'Zé Coach', roles: ['MEMBER', 'COACH'] }),
    memberEntry({ memberId: 'c0000000-0000-0000-0000-000000000009', name: 'Ana Silva' }),
    memberEntry({
      memberId: 'c0000000-0000-0000-0000-000000000008',
      name: 'Boss Admin',
      roles: ['MEMBER', 'ADMIN'],
    }),
  ]);

interface Options {
  groups?: Entry[];
  venues?: ReturnType<typeof venueEntry>[];
  members?: ReturnType<typeof memberList>;
}

/** The groups as the API keeps them: active ones by default, every one with `includeArchived=true`. */
function serveGroups({
  groups = [groupEntry(), archived()],
  venues,
  members: roster,
}: Options = {}) {
  const state = { groups };
  const asked: URLSearchParams[] = [];
  const askedMembers: URLSearchParams[] = [];
  const { seen, record } = recorder();
  server.use(
    signedIn,
    meAs('MEMBER', 'ADMIN'),
    http.get(LEVELS_URL, () => HttpResponse.json(levelsAnswer())),
    http.get(VENUES_URL, () =>
      HttpResponse.json(
        venues ?? [
          venueEntry({ inUseByActiveGroup: true }),
          venueEntry({ id: OTHER_VENUE_ID, name: 'Escola Norte', courts: 1 }),
        ],
      ),
    ),
    http.get(MEMBERS_URL, ({ request }) => {
      askedMembers.push(new URL(request.url).searchParams);
      return HttpResponse.json(roster ?? members());
    }),
    http.get(GROUPS_URL, ({ request }) => {
      const params = new URL(request.url).searchParams;
      asked.push(params);
      const all = params.get('includeArchived') === 'true';
      return HttpResponse.json(state.groups.filter((group) => all || group.status === 'ACTIVE'));
    }),
    http.post(GROUPS_URL, async ({ request }) => {
      await record(request);
      return HttpResponse.json({ id: 'f0000000-0000-0000-0000-0000000000ff' }, { status: 201 });
    }),
    http.put(`${GROUPS_URL}/:groupId`, async ({ request }) => {
      await record(request);
      return HttpResponse.json({ id: GROUP_ID });
    }),
    http.post(`${GROUPS_URL}/:groupId/archival`, async ({ request, params }) => {
      await record(request);
      state.groups = state.groups.map((group) =>
        group.id === params.groupId ? { ...group, status: 'ARCHIVED' } : group,
      );
      return HttpResponse.json({ id: params.groupId, status: 'ARCHIVED' });
    }),
  );
  return { state, asked, askedMembers, seen };
}

async function openGroups(options?: Options, path = '/admin/setup/groups') {
  const served = serveGroups(options);
  const rendered = renderApp(path);
  await screen.findByRole('checkbox', { name: 'Show archived groups' });
  await waitFor(() => {
    expect(
      screen.queryByRole('status', { name: 'Loading training groups' }),
    ).not.toBeInTheDocument();
  });
  return { ...rendered, ...served };
}

const groupCard = (name: string) => screen.getByRole('article', { name });

async function openCreate(options?: Options) {
  const opened = await openGroups(options);
  await opened.user.click(screen.getByRole('button', { name: 'Add group' }));
  const dialog = await screen.findByRole('dialog', { name: 'Add a training group' });
  // The venues, the levels and the coaches are read when the sheet opens.
  await within(dialog).findByRole('checkbox', { name: 'Intermediate' });
  await waitFor(() => {
    expect(within(dialog).getByRole('option', { name: 'Rui Coach' })).toBeInTheDocument();
  });
  return { ...opened, dialog };
}

const slot = (dialog: HTMLElement, position: number) =>
  within(dialog).getByRole('group', { name: `Slot ${String(position)}` });

describe('the training groups list', () => {
  it('shows name, levels, venue, coach, seats, status and the weekly schedule in Lisbon time', async () => {
    // Arrange / Act
    const { asked } = await openGroups();

    // Assert
    expect(screen.getByRole('heading', { level: 1, name: 'Training groups' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to Admin' })).toHaveAttribute('href', '/admin');
    const card = groupCard('Tuesday evening');
    expect(card).toHaveTextContent('Active');
    expect(card).toHaveTextContent('Beginner, Intermediate');
    expect(card).toHaveTextContent('Pavilhão Central');
    expect(card).toHaveTextContent('Rui Coach');
    expect(card).toHaveTextContent('12 seats');
    const schedule = within(card).getByRole('list', { name: 'Weekly schedule' });
    expect(
      within(schedule)
        .getAllByRole('listitem')
        .map((item) => item.textContent),
    ).toEqual(['Tuesday 20:30, 90 min', 'Thursday 21:00, 60 min']);
    expect(card).toHaveTextContent('Lisbon time');
    expect(asked[0]?.get('includeArchived')).not.toBe('true');
    expect(screen.queryByRole('article', { name: 'Old Friday' })).not.toBeInTheDocument();
  });

  it('shows archived groups on request, without edit or archive, and keeps the choice in the address', async () => {
    // Arrange
    const { user, asked, router } = await openGroups();

    // Act
    await user.click(screen.getByRole('checkbox', { name: 'Show archived groups' }));

    // Assert
    const old = await screen.findByRole('article', { name: 'Old Friday' });
    expect(old).toHaveTextContent('Archived');
    expect(within(old).queryByRole('button')).not.toBeInTheDocument();
    expect(asked.at(-1)?.get('includeArchived')).toBe('true');
    expect(router.state.location.search).toBe('?archived=1');
    expect(
      within(groupCard('Tuesday evening')).getByRole('button', { name: /^Edit/ }),
    ).toBeEnabled();
  });

  it('starts with archived groups shown when the address says so', async () => {
    // Arrange / Act
    await openGroups(undefined, '/admin/setup/groups?archived=1');

    // Assert
    expect(await screen.findByRole('article', { name: 'Old Friday' })).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'Show archived groups' })).toBeChecked();
  });

  it('says so when there are no groups', async () => {
    // Arrange / Act
    await openGroups({ groups: [] });

    // Assert
    expect(screen.getByText('No training groups yet')).toBeInTheDocument();
  });

  it('tells a member who is not an administrator that it is for administrators', async () => {
    // Arrange
    server.use(
      signedIn,
      http.get(GROUPS_URL, () => apiError(403, 'FORBIDDEN', 'Access denied')),
    );

    // Act
    renderApp('/admin/setup/groups');

    // Assert
    expect(await screen.findByText('Administration is for administrators.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add group' })).not.toBeInTheDocument();
  });
});

describe('creating a training group', () => {
  async function fillBasics(
    user: Awaited<ReturnType<typeof openCreate>>['user'],
    dialog: HTMLElement,
  ) {
    await user.type(within(dialog).getByLabelText('Name'), 'Saturday open play');
    await user.click(within(dialog).getByRole('checkbox', { name: 'Intermediate' }));
    await user.selectOptions(within(dialog).getByLabelText('Venue'), 'Escola Norte');
    await user.selectOptions(within(dialog).getByLabelText('Coach'), 'Zé Coach');
    await user.type(within(dialog).getByLabelText('Seats'), '14');
  }

  async function fillSlot(
    user: Awaited<ReturnType<typeof openCreate>>['user'],
    row: HTMLElement,
    day: string,
    time: string,
    minutes: string,
  ) {
    await user.selectOptions(within(row).getByLabelText('Day'), day);
    await user.type(within(row).getByLabelText('Start time (Lisbon)'), time);
    const length = within(row).getByLabelText('Length (minutes)');
    await user.clear(length);
    await user.type(length, minutes);
  }

  it('sends the name, levels, venue, coach, seats and two weekly slots, and says when sessions appear', async () => {
    // Arrange
    const { user, seen, dialog } = await openCreate();
    await fillBasics(user, dialog);
    await fillSlot(user, slot(dialog, 1), 'Saturday', '10:00', '120');
    await user.click(within(dialog).getByRole('button', { name: 'Add slot' }));
    await fillSlot(user, slot(dialog, 2), 'Sunday', '18:30', '90');

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Add group' }));

    // Assert
    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
    expect(seen[0]).toMatchObject({ method: 'POST', path: GROUPS_URL });
    expect(JSON.parse(seen[0]?.body ?? '')).toEqual({
      name: 'Saturday open play',
      acceptedLevelIds: [INTERMEDIATE_ID],
      venueId: OTHER_VENUE_ID,
      coachId: OTHER_COACH_ID,
      capacity: 14,
      schedule: [
        { dayOfWeek: 'SATURDAY', startTime: '10:00', durationMinutes: 120 },
        { dayOfWeek: 'SUNDAY', startTime: '18:30', durationMinutes: 90 },
      ],
    });
    expect(screen.getByRole('status')).toHaveTextContent(
      'Saturday open play was added. Sessions for this group appear after the next daily generation.',
    );
  });

  it('explains the times, the seats and what a coach is', async () => {
    // Arrange / Act
    const { dialog } = await openCreate();

    // Assert
    expect(dialog).toHaveTextContent(
      'Sessions for this group appear after the next daily generation.',
    );
    expect(within(slot(dialog, 1)).getByLabelText('Start time (Lisbon)')).toBeInTheDocument();
    expect(within(dialog).getByLabelText('Seats')).toHaveAccessibleDescription(
      'Usually a multiple of 6 (12 seats is two teams on one court), but any number from 1 to 1000 is allowed. The coach does not use a seat.',
    );
    expect(within(dialog).getByLabelText('Coach')).toHaveAccessibleDescription(
      'Active members who hold the coach role.',
    );
  });

  it('lists only the active members who hold the coach role', async () => {
    // Arrange / Act
    const { dialog, askedMembers } = await openCreate();

    // Assert
    const coaches = within(within(dialog).getByLabelText('Coach')).getAllByRole('option');
    expect(coaches.map((option) => option.textContent)).toEqual([
      'Choose a coach',
      'Rui Coach',
      'Zé Coach',
    ]);
    expect(askedMembers[0]?.get('status')).toBe('ACTIVE');
  });

  it('lists the levels lowest first and the venues', async () => {
    // Arrange / Act
    const { dialog } = await openCreate();

    // Assert
    const levels = within(dialog).getByRole('group', { name: 'Accepted levels' });
    expect(
      within(levels)
        .getAllByText(/^(Beginner|Intermediate)$/)
        .map((label) => label.textContent),
    ).toEqual(['Beginner', 'Intermediate']);
    expect(within(levels).getByRole('checkbox', { name: 'Beginner' })).not.toBeChecked();
    expect(levels).toHaveAccessibleDescription(
      'Members can book a group that accepts their own level or a lower one.',
    );
    const venues = within(within(dialog).getByLabelText('Venue')).getAllByRole('option');
    expect(venues.map((option) => option.textContent)).toEqual([
      'Choose a venue',
      'Pavilhão Central',
      'Escola Norte',
    ]);
  });

  it('adds and removes slot rows; the last one stays', async () => {
    // Arrange
    const { user, dialog } = await openCreate();
    const rows = () => within(dialog).getAllByRole('group', { name: /^Slot \d+$/ });
    expect(rows()).toHaveLength(1);
    expect(within(dialog).getByRole('button', { name: 'Remove slot 1' })).toBeDisabled();

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Add slot' }));
    await user.click(within(dialog).getByRole('button', { name: 'Add slot' }));
    await user.selectOptions(within(slot(dialog, 2)).getByLabelText('Day'), 'Friday');

    // Assert
    expect(rows()).toHaveLength(3);

    // Act: removing the first row keeps the others' values and renumbers
    await user.click(within(dialog).getByRole('button', { name: 'Remove slot 1' }));

    // Assert
    expect(rows()).toHaveLength(2);
    expect(within(slot(dialog, 1)).getByLabelText('Day')).toHaveValue('FRIDAY');
    expect(within(dialog).getByRole('button', { name: 'Remove slot 1' })).toBeEnabled();
  });

  it('stops adding slots at the API’s limit of 14', async () => {
    // Arrange
    const { user, dialog } = await openCreate();

    // Act
    for (let added = 1; added < 14; added += 1) {
      await user.click(within(dialog).getByRole('button', { name: 'Add slot' }));
    }

    // Assert
    expect(within(dialog).getAllByRole('group', { name: /^Slot \d+$/ })).toHaveLength(14);
    expect(within(dialog).getByRole('button', { name: 'Add slot' })).toBeDisabled();
  });

  it('refuses what is missing before asking the API', async () => {
    // Arrange
    const { user, seen, dialog } = await openCreate();

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Add group' }));

    // Assert
    expect(within(dialog).getByLabelText('Name')).toHaveAccessibleDescription(
      'Enter the group name',
    );
    expect(
      within(dialog).getByRole('group', { name: 'Accepted levels' }),
    ).toHaveAccessibleDescription(expect.stringContaining('Tick at least one level'));
    expect(within(dialog).getByLabelText('Venue')).toHaveAccessibleDescription('Choose a venue');
    expect(within(dialog).getByLabelText('Coach')).toHaveAccessibleDescription(
      expect.stringContaining('Choose a coach'),
    );
    expect(within(dialog).getByLabelText('Seats')).toBeInvalid();
    expect(
      within(slot(dialog, 1)).getByLabelText('Start time (Lisbon)'),
    ).toHaveAccessibleDescription('Enter the start time');
    expect(within(dialog).getByRole('alert')).toHaveFocus();
    expect(seen).toEqual([]);
  });

  it.each(['0', '241', '1.5', ''])('refuses a slot length of %j', async (minutes) => {
    // Arrange
    const { user, seen, dialog } = await openCreate();
    await fillBasics(user, dialog);
    const row = slot(dialog, 1);
    await user.type(within(row).getByLabelText('Start time (Lisbon)'), '10:00');
    const length = within(row).getByLabelText('Length (minutes)');
    await user.clear(length);
    if (minutes) await user.type(length, minutes);

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Add group' }));

    // Assert
    expect(length).toBeInvalid();
    expect(length).toHaveAccessibleDescription('Enter a whole number from 1 to 240.');
    expect(seen).toEqual([]);
  });

  it('shows the API’s refusal of overlapping slots and keeps the sheet and its values', async () => {
    // Arrange
    const { user, dialog } = await openCreate();
    server.use(
      http.post(GROUPS_URL, () =>
        apiError(
          422,
          'BUSINESS_RULE_VIOLATION',
          'Schedule slots overlap: TUESDAY 20:30 for 90 minutes and TUESDAY 21:00 for 60 minutes',
        ),
      ),
    );
    await fillBasics(user, dialog);
    await fillSlot(user, slot(dialog, 1), 'Tuesday', '20:30', '90');
    await user.click(within(dialog).getByRole('button', { name: 'Add slot' }));
    await fillSlot(user, slot(dialog, 2), 'Tuesday', '21:00', '60');

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Add group' }));

    // Assert
    const alert = await within(dialog).findByRole('alert');
    expect(alert).toHaveTextContent('Schedule slots overlap');
    expect(alert).toHaveTextContent('Reference: req-422');
    expect(within(dialog).getByLabelText('Name')).toHaveValue('Saturday open play');
    expect(within(slot(dialog, 2)).getByLabelText('Start time (Lisbon)')).toHaveValue('21:00');
  });

  it('puts an API message on the field it names', async () => {
    // Arrange
    const { user, dialog } = await openCreate();
    server.use(
      http.post(GROUPS_URL, () =>
        apiError(422, 'INVALID_FIELD', 'The seats must be at least one', { fields: ['capacity'] }),
      ),
    );
    await fillBasics(user, dialog);
    await fillSlot(user, slot(dialog, 1), 'Saturday', '10:00', '60');

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Add group' }));

    // Assert
    await waitFor(() => {
      expect(within(dialog).getByLabelText('Seats')).toHaveAccessibleDescription(
        expect.stringContaining('The seats must be at least one'),
      );
    });
    expect(within(dialog).getByLabelText('Seats')).toBeInvalid();
  });

  it('shows the API refusal of a coach who cannot coach', async () => {
    // Arrange
    const { user, dialog } = await openCreate();
    server.use(
      http.post(GROUPS_URL, () =>
        apiError(
          422,
          'BUSINESS_RULE_VIOLATION',
          'The coach must be an active member of the association holding the coach role',
        ),
      ),
    );
    await fillBasics(user, dialog);
    await fillSlot(user, slot(dialog, 1), 'Saturday', '10:00', '60');

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Add group' }));

    // Assert
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'The coach must be an active member of the association holding the coach role',
    );
  });

  it('says what is missing when there is no venue or no coach yet', async () => {
    // Arrange
    const { user } = await openGroups({
      venues: [],
      members: memberList([memberEntry({ name: 'Ana Silva' })]),
    });

    // Act
    await user.click(screen.getByRole('button', { name: 'Add group' }));
    const dialog = await screen.findByRole('dialog', { name: 'Add a training group' });

    // Assert
    expect(await within(dialog).findByText(/There is no venue yet/)).toBeInTheDocument();
    expect(within(dialog).getByRole('link', { name: 'Add a venue' })).toHaveAttribute(
      'href',
      '/admin/setup/venues',
    );
    expect(
      await within(dialog).findByText(/No active member holds the coach role/),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByRole('link', { name: 'Give the role on the Members page' }),
    ).toHaveAttribute('href', '/admin/members');
  });

  it('says when the coach list may be incomplete', async () => {
    // Arrange / Act
    const { dialog } = await openCreate({
      members: { ...members(), truncated: true },
    });

    // Assert
    expect(dialog).toHaveTextContent(
      'Only the first members were checked, so a coach may be missing from this list.',
    );
  });
});

describe('editing a training group', () => {
  async function openEdit(options?: Options) {
    const opened = await openGroups(options);
    await opened.user.click(
      within(groupCard('Tuesday evening')).getByRole('button', { name: /^Edit/ }),
    );
    const dialog = await screen.findByRole('dialog', { name: 'Edit Tuesday evening' });
    await waitFor(() => {
      expect(within(dialog).getByRole('checkbox', { name: 'Beginner' })).toBeChecked();
    });
    return { ...opened, dialog };
  }

  it('starts from what the group says now, with the venue fixed', async () => {
    // Arrange / Act
    const { dialog } = await openEdit();

    // Assert
    expect(within(dialog).getByLabelText('Name')).toHaveValue('Tuesday evening');
    expect(within(dialog).getByRole('checkbox', { name: 'Intermediate' })).toBeChecked();
    const venue = within(dialog).getByLabelText('Venue');
    expect(venue).toBeDisabled();
    expect(venue).toHaveDisplayValue('Pavilhão Central');
    expect(venue).toHaveAccessibleDescription(
      'The venue of a group cannot be changed. Archive the group and create a new one to move it.',
    );
    await waitFor(() => {
      expect(within(dialog).getByLabelText('Coach')).toHaveDisplayValue('Rui Coach');
    });
    expect(within(dialog).getByLabelText('Seats')).toHaveValue(12);
    expect(within(slot(dialog, 1)).getByLabelText('Day')).toHaveValue('TUESDAY');
    expect(within(slot(dialog, 1)).getByLabelText('Start time (Lisbon)')).toHaveValue('20:30');
    expect(within(slot(dialog, 1)).getByLabelText('Length (minutes)')).toHaveValue(90);
    expect(within(slot(dialog, 2)).getByLabelText('Day')).toHaveValue('THURSDAY');
  });

  it('saves the change without a venue, and says when sessions appear', async () => {
    // Arrange
    const { user, seen, dialog } = await openEdit();
    await waitFor(() => {
      expect(within(dialog).getByLabelText('Coach')).toHaveDisplayValue('Rui Coach');
    });

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Remove slot 2' }));
    const seats = within(dialog).getByLabelText('Seats');
    await user.clear(seats);
    await user.type(seats, '18');
    await user.selectOptions(within(dialog).getByLabelText('Coach'), 'Zé Coach');
    await user.click(within(dialog).getByRole('button', { name: 'Save group' }));

    // Assert
    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
    expect(seen[0]).toMatchObject({ method: 'PUT', path: `${GROUPS_URL}/${GROUP_ID}` });
    expect(JSON.parse(seen[0]?.body ?? '')).toEqual({
      name: 'Tuesday evening',
      acceptedLevelIds: [BEGINNER_ID, INTERMEDIATE_ID],
      coachId: OTHER_COACH_ID,
      capacity: 18,
      schedule: [{ dayOfWeek: 'TUESDAY', startTime: '20:30', durationMinutes: 90 }],
    });
    expect(screen.getByRole('status')).toHaveTextContent(
      'Tuesday evening was saved. Sessions for this group appear after the next daily generation.',
    );
  });

  it('keeps a coach who no longer holds the role selectable, so the API can say why it refuses', async () => {
    // Arrange / Act
    const { dialog } = await openEdit({
      members: memberList([
        memberEntry({ memberId: OTHER_COACH_ID, name: 'Zé Coach', roles: ['MEMBER', 'COACH'] }),
      ]),
    });

    // Assert
    await waitFor(() => {
      expect(within(dialog).getByLabelText('Coach')).toHaveDisplayValue('Rui Coach');
    });
  });

  it('shows the API refusal of a group that was archived meanwhile', async () => {
    // Arrange
    const { user, dialog } = await openEdit();
    server.use(
      http.put(`${GROUPS_URL}/${GROUP_ID}`, () =>
        apiError(
          422,
          'BUSINESS_RULE_VIOLATION',
          'The training group is archived and cannot be changed',
        ),
      ),
    );

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Save group' }));

    // Assert
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('is archived');
  });
});

describe('archiving a training group', () => {
  it('asks first, says what it does and does not do, then archives', async () => {
    // Arrange
    const { user, seen } = await openGroups();

    // Act
    await user.click(
      within(groupCard('Tuesday evening')).getByRole('button', { name: /^Archive/ }),
    );
    const dialog = await screen.findByRole('alertdialog', { name: 'Archive Tuesday evening?' });

    // Assert
    expect(seen).toEqual([]);
    expect(dialog).toHaveTextContent('stops creating new sessions');
    expect(dialog).toHaveTextContent('Sessions that already exist are not changed or cancelled');

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Archive group' }));

    // Assert
    await waitFor(() => {
      expect(screen.queryByRole('article', { name: 'Tuesday evening' })).not.toBeInTheDocument();
    });
    expect(seen[0]).toMatchObject({ method: 'POST', path: `${GROUPS_URL}/${GROUP_ID}/archival` });
    expect(screen.getByRole('status')).toHaveTextContent(
      'Tuesday evening was archived. Its existing sessions are unchanged.',
    );
  });

  it('keeps the group when the question is not confirmed', async () => {
    // Arrange
    const { user, seen } = await openGroups();

    // Act
    await user.click(
      within(groupCard('Tuesday evening')).getByRole('button', { name: /^Archive/ }),
    );
    const dialog = await screen.findByRole('alertdialog');
    await user.click(within(dialog).getByRole('button', { name: 'Not now' }));

    // Assert
    expect(seen).toEqual([]);
    expect(groupCard('Tuesday evening')).toBeInTheDocument();
  });

  it('shows the API refusal in the question', async () => {
    // Arrange
    const { user } = await openGroups();
    server.use(
      http.post(`${GROUPS_URL}/${GROUP_ID}/archival`, () =>
        apiError(409, 'CONFLICT', 'The data was changed by someone else. Reload and try again.'),
      ),
    );

    // Act
    await user.click(
      within(groupCard('Tuesday evening')).getByRole('button', { name: /^Archive/ }),
    );
    const dialog = await screen.findByRole('alertdialog');
    await user.click(within(dialog).getByRole('button', { name: 'Archive group' }));

    // Assert
    expect(await within(dialog).findByText(/changed by someone else/)).toBeInTheDocument();
    expect(within(dialog).getByText('Reference: req-409')).toBeInTheDocument();
  });
});
