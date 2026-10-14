import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import {
  BEGINNER_ID,
  INTERMEDIATE_ID,
  LEVELS_URL,
  MEMBER_ID,
  MEMBERS_URL,
  levelsAnswer,
  meAs,
  memberAnswer,
  memberDetail,
  signedIn,
  subscriptionSummary,
} from '@/test/fixtures';
import { apiError, recorder } from '@/test/handlers';
import { renderApp } from '@/test/render';
import { server } from '@/test/server';

const memberUrl = `${MEMBERS_URL}/${MEMBER_ID}`;
type Detail = ReturnType<typeof memberDetail>;

/** One member and the levels, as the API answers them; `state.detail` is what the next read returns. */
function serveMember(detail: Detail = memberDetail()) {
  const state = { detail };
  server.use(
    signedIn,
    meAs('MEMBER', 'ADMIN'),
    http.get(memberUrl, () => HttpResponse.json(state.detail)),
    http.get(LEVELS_URL, () => HttpResponse.json(levelsAnswer())),
  );
  return state;
}

async function openMember(detail: Detail = memberDetail()) {
  const state = serveMember(detail);
  const rendered = renderApp(`/admin/members/${MEMBER_ID}`);
  await screen.findByRole('heading', { level: 1, name: detail.name });
  return { ...rendered, state };
}

describe('the member', () => {
  it('shows contact, level, roles, status and the way back', async () => {
    // Arrange / Act
    await openMember(memberDetail({ roles: ['MEMBER', 'COACH'] }));

    // Assert
    expect(screen.getByRole('link', { name: 'Back to Members' })).toHaveAttribute(
      'href',
      '/admin/members',
    );
    const contact = screen.getByRole('region', { name: 'Contact' });
    expect(contact).toHaveTextContent('ana@example.com');
    expect(contact).toHaveTextContent('+351 910 000 002');
    expect(contact).toHaveTextContent('Joined 1 June 2026');
    expect(screen.getByText('Active')).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Level' })).toHaveTextContent('Beginner');
    const roles = screen.getByRole('region', { name: 'Roles' });
    expect(roles).toHaveTextContent('Member');
    expect(within(roles).getByRole('button', { name: 'Remove coach role' })).toBeInTheDocument();
    expect(within(roles).getByRole('button', { name: 'Make administrator' })).toBeInTheDocument();
  });

  it('lists the level history, newest first, with who changed it', async () => {
    // Arrange / Act
    await openMember(
      memberDetail({
        levelChanges: [
          {
            changedAt: '2026-06-10T10:00:00Z',
            changedByName: 'Rui Coach',
            fromLevelName: 'Beginner',
            toLevelName: 'Intermediate',
          },
          {
            changedAt: '2026-07-14T21:30:00Z',
            changedByName: 'Founder Admin',
            fromLevelName: 'Intermediate',
            toLevelName: 'Advanced',
          },
        ],
      }),
    );

    // Assert
    const history = screen.getByRole('list', { name: 'Level history' });
    const items = within(history).getAllByRole('listitem');
    expect(items[0]).toHaveTextContent('Intermediate → Advanced');
    expect(items[0]).toHaveTextContent('Founder Admin');
    expect(items[0]).toHaveTextContent('Tue 14 Jul, 22:30');
    expect(items[1]).toHaveTextContent('Beginner → Intermediate');
  });

  it('shows the subscriptions read-only, with what is paid and left', async () => {
    // Arrange / Act
    await openMember(
      memberDetail({
        subscriptions: [
          subscriptionSummary(),
          subscriptionSummary({
            subscriptionId: '66666666-6666-6666-6666-666666666667',
            planName: 'Monthly',
            type: 'MONTHLY_UNLIMITED',
            priceCents: 3500,
            paidCents: 1000,
            outstandingCents: 2500,
            paymentStatus: 'OVERDUE',
            creditsLeft: undefined,
            creditsUsed: undefined,
          }),
        ],
        subscriptionsTruncated: true,
      }),
    );

    // Assert
    const section = screen.getByRole('region', { name: 'Subscriptions' });
    const pack = within(section).getByRole('article', { name: 'Pack of 10' });
    expect(pack).toHaveTextContent('1 July 2026 – 29 September 2026');
    expect(pack).toHaveTextContent('Paid');
    expect(pack).toHaveTextContent('€50.00 of €50.00 paid');
    expect(pack).toHaveTextContent('7 credits left');
    const monthly = within(section).getByRole('article', { name: 'Monthly' });
    expect(monthly).toHaveTextContent('Overdue');
    expect(monthly).toHaveTextContent('€10.00 of €35.00 paid');
    expect(monthly).not.toHaveTextContent('credits left');
    expect(section).toHaveTextContent('Only the most recent subscriptions are shown.');
    expect(within(section).queryByRole('button')).not.toBeInTheDocument();
  });

  it('says so when there are no subscriptions', async () => {
    // Arrange / Act
    await openMember();

    // Assert
    expect(screen.getByRole('region', { name: 'Subscriptions' })).toHaveTextContent(
      'No subscriptions yet.',
    );
  });

  it('shows a member who is not in the association as not found', async () => {
    // Arrange
    server.use(
      signedIn,
      meAs('MEMBER', 'ADMIN'),
      http.get(memberUrl, () => apiError(404, 'NOT_FOUND', 'Not found')),
      http.get(LEVELS_URL, () => HttpResponse.json(levelsAnswer())),
    );

    // Act
    renderApp(`/admin/members/${MEMBER_ID}`);

    // Assert
    expect(await screen.findByText('This member was not found.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to Members' })).toBeInTheDocument();
  });

  it('shows the error with a retry when the member cannot be read', async () => {
    // Arrange
    server.use(
      signedIn,
      meAs('MEMBER', 'ADMIN'),
      http.get(memberUrl, () => apiError(500, 'INTERNAL_ERROR', 'boom')),
      http.get(LEVELS_URL, () => HttpResponse.json(levelsAnswer())),
    );

    // Act
    renderApp(`/admin/members/${MEMBER_ID}`);

    // Assert
    expect(await screen.findByText('Something went wrong. Please try again.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });

  it('answers a member who is not an administrator with the 403 of the API', async () => {
    // Arrange
    server.use(
      signedIn,
      http.get(memberUrl, () => apiError(403, 'FORBIDDEN', 'Access denied')),
      http.get(LEVELS_URL, () => apiError(403, 'FORBIDDEN', 'Access denied')),
    );

    // Act
    renderApp(`/admin/members/${MEMBER_ID}`);

    // Assert
    expect(await screen.findByText('Administration is for administrators.')).toBeInTheDocument();
  });
});

describe('an anonymised member', () => {
  it('is read-only: no contact, no actions', async () => {
    // Arrange / Act
    await openMember(
      memberDetail({
        name: 'Erased member',
        anonymised: true,
        email: undefined,
        phone: undefined,
        status: 'INACTIVE',
        accountActivated: false,
      }),
    );

    // Assert
    expect(screen.getByText(/personal data was erased/)).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Contact' })).not.toBeInTheDocument();
    expect(within(screen.getByRole('main')).queryByRole('button')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('New level')).not.toBeInTheDocument();
  });
});

describe('changing the level', () => {
  it('offers the levels of the association, marks the entry level and waits for a different choice', async () => {
    // Arrange / Act
    const { user } = await openMember();
    const select = await screen.findByLabelText('New level');

    // Assert
    expect(select).toHaveValue(BEGINNER_ID);
    expect(
      within(select)
        .getAllByRole('option')
        .map((option) => option.textContent),
    ).toEqual(['Beginner (entry level)', 'Intermediate']);
    const save = screen.getByRole('button', { name: 'Change level' });
    expect(save).toBeDisabled();
    await user.selectOptions(select, INTERMEDIATE_ID);
    expect(save).toBeEnabled();
    expect(screen.getByRole('region', { name: 'Level' })).toHaveTextContent(
      'applies to bookings made from now on',
    );
  });

  it('sends only the level, reports the outcome and reads the member again', async () => {
    // Arrange
    const { seen, record } = recorder();
    const { user, state } = await openMember();
    server.use(
      http.put(`${memberUrl}/level`, async ({ request }) => {
        await record(request);
        state.detail = memberDetail({ levelId: INTERMEDIATE_ID, levelName: 'Intermediate' });
        return HttpResponse.json(memberAnswer({ levelId: INTERMEDIATE_ID }));
      }),
    );

    // Act
    await user.selectOptions(await screen.findByLabelText('New level'), INTERMEDIATE_ID);
    await user.click(screen.getByRole('button', { name: 'Change level' }));

    // Assert
    expect(await screen.findByRole('status')).toHaveTextContent('Ana Silva is now Intermediate.');
    expect(seen).toHaveLength(1);
    expect(JSON.parse(seen[0]?.body ?? '')).toEqual({ levelId: INTERMEDIATE_ID });
    await waitFor(() => {
      expect(screen.getByRole('region', { name: 'Level' })).toHaveTextContent('Intermediate');
    });
    expect(screen.getByLabelText('New level')).toHaveValue(INTERMEDIATE_ID);
    expect(screen.getByRole('button', { name: 'Change level' })).toBeDisabled();
  });

  it('shows the API refusal next to the level', async () => {
    // Arrange
    const { user } = await openMember();
    server.use(
      http.put(`${memberUrl}/level`, () =>
        apiError(422, 'BUSINESS_RULE_VIOLATION', 'The level does not belong to the association'),
      ),
    );

    // Act
    await user.selectOptions(await screen.findByLabelText('New level'), INTERMEDIATE_ID);
    await user.click(screen.getByRole('button', { name: 'Change level' }));

    // Assert
    const alert = await within(screen.getByRole('region', { name: 'Level' })).findByRole('alert');
    expect(alert).toHaveTextContent('The level does not belong to the association');
    expect(alert).toHaveTextContent('Reference: req-422');
  });

  it('shows the error when the levels cannot be read, without hiding the rest', async () => {
    // Arrange
    server.use(
      signedIn,
      meAs('MEMBER', 'ADMIN'),
      http.get(memberUrl, () => HttpResponse.json(memberDetail())),
      http.get(LEVELS_URL, () => apiError(500, 'INTERNAL_ERROR', 'boom')),
    );

    // Act
    renderApp(`/admin/members/${MEMBER_ID}`);

    // Assert
    const level = await screen.findByRole('region', { name: 'Level' });
    expect(
      await within(level).findByText('Something went wrong. Please try again.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Roles' })).toBeInTheDocument();
  });
});

describe('roles', () => {
  it('grants COACH with one request and reports it', async () => {
    // Arrange
    const { seen, record } = recorder();
    const { user, state } = await openMember();
    server.use(
      http.put(`${memberUrl}/roles/COACH`, async ({ request }) => {
        await record(request);
        state.detail = memberDetail({ roles: ['MEMBER', 'COACH'] });
        return HttpResponse.json(memberAnswer({ roles: ['MEMBER', 'COACH'] }));
      }),
    );

    // Act
    await user.click(screen.getByRole('button', { name: 'Make coach' }));

    // Assert
    expect(await screen.findByRole('status')).toHaveTextContent('Ana Silva can now coach.');
    expect(seen).toHaveLength(1);
    expect(seen[0]?.method).toBe('PUT');
    expect(seen[0]?.body).toBe('');
    expect(await screen.findByRole('button', { name: 'Remove coach role' })).toBeInTheDocument();
  });

  it('revokes COACH', async () => {
    // Arrange
    const { seen, record } = recorder();
    const { user, state } = await openMember(memberDetail({ roles: ['MEMBER', 'COACH'] }));
    server.use(
      http.delete(`${memberUrl}/roles/COACH`, async ({ request }) => {
        await record(request);
        state.detail = memberDetail();
        return HttpResponse.json(memberAnswer());
      }),
    );

    // Act
    await user.click(screen.getByRole('button', { name: 'Remove coach role' }));

    // Assert
    expect(await screen.findByRole('status')).toHaveTextContent(
      'The coach role was removed from Ana Silva.',
    );
    expect(seen[0]?.method).toBe('DELETE');
    expect(await screen.findByRole('button', { name: 'Make coach' })).toBeInTheDocument();
  });

  it('shows a refusal of the role change next to the roles, verbatim', async () => {
    // Arrange
    const { user } = await openMember(memberDetail({ roles: ['COACH'] }));
    server.use(
      http.delete(`${memberUrl}/roles/COACH`, () =>
        apiError(422, 'BUSINESS_RULE_VIOLATION', 'The last role of a member cannot be revoked'),
      ),
    );

    // Act
    await user.click(screen.getByRole('button', { name: 'Remove coach role' }));

    // Assert
    const alert = await within(screen.getByRole('region', { name: 'Roles' })).findByRole('alert');
    expect(alert).toHaveTextContent('The last role of a member cannot be revoked');
    expect(alert).toHaveTextContent('Reference: req-422');
  });

  it('asks before making an administrator, and says what it allows', async () => {
    // Arrange
    const { seen, record } = recorder();
    const { user, state } = await openMember();
    server.use(
      http.put(`${memberUrl}/roles/ADMIN`, async ({ request }) => {
        await record(request);
        state.detail = memberDetail({ roles: ['MEMBER', 'ADMIN'] });
        return HttpResponse.json(memberAnswer({ roles: ['MEMBER', 'ADMIN'] }));
      }),
    );

    // Act
    await user.click(screen.getByRole('button', { name: 'Make administrator' }));
    const dialog = await screen.findByRole('alertdialog', {
      name: 'Make Ana Silva an administrator?',
    });

    // Assert
    expect(dialog).toHaveTextContent('Administrators manage members');
    expect(seen).toHaveLength(0);
    await user.click(within(dialog).getByRole('button', { name: 'Make administrator' }));
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Ana Silva is now an administrator.',
    );
    expect(seen).toHaveLength(1);
  });

  it('revokes ADMIN after a confirmation', async () => {
    // Arrange
    const { seen, record } = recorder();
    const { user, state } = await openMember(memberDetail({ roles: ['MEMBER', 'ADMIN'] }));
    server.use(
      http.delete(`${memberUrl}/roles/ADMIN`, async ({ request }) => {
        await record(request);
        state.detail = memberDetail();
        return HttpResponse.json(memberAnswer());
      }),
    );

    // Act
    await user.click(screen.getByRole('button', { name: 'Remove administrator role' }));
    const dialog = await screen.findByRole('alertdialog', {
      name: 'Remove the administrator role from Ana Silva?',
    });
    expect(seen).toHaveLength(0);
    await user.click(within(dialog).getByRole('button', { name: 'Remove role' }));

    // Assert
    expect(await screen.findByRole('status')).toHaveTextContent(
      'The administrator role was removed from Ana Silva.',
    );
    expect(seen).toHaveLength(1);
  });

  it('shows the last-administrator refusal clearly, in the dialog, and keeps the role', async () => {
    // Arrange
    const { user } = await openMember(memberDetail({ roles: ['MEMBER', 'ADMIN'] }));
    server.use(
      http.delete(`${memberUrl}/roles/ADMIN`, () =>
        apiError(409, 'CONFLICT', 'The association must keep at least one active administrator'),
      ),
    );

    // Act
    await user.click(screen.getByRole('button', { name: 'Remove administrator role' }));
    const dialog = await screen.findByRole('alertdialog');
    await user.click(within(dialog).getByRole('button', { name: 'Remove role' }));

    // Assert
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'The association must keep at least one active administrator',
    );
    expect(within(dialog).getByText('Reference: req-409')).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Not now' }));
    expect(
      await screen.findByRole('button', { name: 'Remove administrator role' }),
    ).toBeInTheDocument();
  });

  it('does not show the refusal of an earlier attempt when the dialog opens again', async () => {
    // Arrange
    const { user } = await openMember(memberDetail({ roles: ['MEMBER', 'ADMIN'] }));
    server.use(
      http.delete(`${memberUrl}/roles/ADMIN`, () =>
        apiError(409, 'CONFLICT', 'The association must keep at least one active administrator'),
      ),
    );
    await user.click(screen.getByRole('button', { name: 'Remove administrator role' }));
    let dialog = await screen.findByRole('alertdialog');
    await user.click(within(dialog).getByRole('button', { name: 'Remove role' }));
    await within(dialog).findByRole('alert');
    await user.click(within(dialog).getByRole('button', { name: 'Not now' }));
    await waitFor(() => {
      expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    });

    // Act
    await user.click(screen.getByRole('button', { name: 'Remove administrator role' }));
    dialog = await screen.findByRole('alertdialog');

    // Assert
    expect(within(dialog).queryByRole('alert')).not.toBeInTheDocument();
  });
});

describe('deactivating', () => {
  it('explains the consequences first and sends nothing until confirmed', async () => {
    // Arrange
    const { seen, record } = recorder();
    const { user } = await openMember();
    server.use(
      http.post(`${memberUrl}/deactivation`, async ({ request }) => {
        await record(request);
        return HttpResponse.json({
          member: memberAnswer({ status: 'INACTIVE' }),
          bookingsCancelled: 0,
        });
      }),
    );

    // Act
    await user.click(screen.getByRole('button', { name: 'Deactivate member' }));

    // Assert
    const dialog = await screen.findByRole('alertdialog', { name: 'Deactivate Ana Silva?' });
    expect(dialog).toHaveTextContent('future bookings will be cancelled');
    expect(dialog).toHaveTextContent('history is kept');
    expect(seen).toHaveLength(0);
    await user.click(within(dialog).getByRole('button', { name: 'Keep active' }));
    await waitFor(() => {
      expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    });
    expect(seen).toHaveLength(0);
  });

  it('deactivates on confirmation, reports the cancelled bookings and reads the member again', async () => {
    // Arrange
    const { seen, record } = recorder();
    const { user, state } = await openMember();
    server.use(
      http.post(`${memberUrl}/deactivation`, async ({ request }) => {
        await record(request);
        state.detail = memberDetail({ status: 'INACTIVE' });
        return HttpResponse.json({
          member: memberAnswer({ status: 'INACTIVE' }),
          bookingsCancelled: 2,
          failedSessionIds: [],
        });
      }),
    );
    await user.click(screen.getByRole('button', { name: 'Deactivate member' }));
    const dialog = await screen.findByRole('alertdialog');

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Deactivate' }));

    // Assert
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Ana Silva was deactivated. 2 future bookings were cancelled.',
    );
    expect(seen).toHaveLength(1);
    expect(seen[0]?.method).toBe('POST');
    await waitFor(() => {
      expect(screen.getByText('Inactive')).toBeInTheDocument();
    });
    expect(screen.queryByRole('button', { name: 'Deactivate member' })).not.toBeInTheDocument();
  });

  it('warns when some bookings could not be cancelled and offers to try again', async () => {
    // Arrange
    const { seen, record } = recorder();
    const { user, state } = await openMember();
    let answers = [
      { bookingsCancelled: 1, failedSessionIds: ['33333333-3333-3333-3333-333333333333'] },
      { bookingsCancelled: 1, failedSessionIds: [] },
    ];
    server.use(
      http.post(`${memberUrl}/deactivation`, async ({ request }) => {
        await record(request);
        state.detail = memberDetail({ status: 'INACTIVE' });
        const [answer = { bookingsCancelled: 0, failedSessionIds: [] }, ...rest] = answers;
        answers = rest;
        return HttpResponse.json({ member: memberAnswer({ status: 'INACTIVE' }), ...answer });
      }),
    );
    await user.click(screen.getByRole('button', { name: 'Deactivate member' }));
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Deactivate' }),
    );

    // Act
    const warning = await screen.findByText(/could not be cancelled/);
    expect(warning).toHaveTextContent('1 session');
    await user.click(screen.getByRole('button', { name: 'Cancel the remaining bookings' }));

    // Assert
    await waitFor(() => {
      expect(
        screen.queryByRole('button', { name: 'Cancel the remaining bookings' }),
      ).not.toBeInTheDocument();
    });
    expect(seen).toHaveLength(2);
    expect(await screen.findByRole('status')).toHaveTextContent('1 future booking was cancelled');
  });

  it('shows the last-administrator refusal in the dialog', async () => {
    // Arrange
    const { user } = await openMember(memberDetail({ roles: ['MEMBER', 'ADMIN'] }));
    server.use(
      http.post(`${memberUrl}/deactivation`, () =>
        apiError(409, 'CONFLICT', 'The association must keep at least one active administrator'),
      ),
    );

    // Act
    await user.click(screen.getByRole('button', { name: 'Deactivate member' }));
    const dialog = await screen.findByRole('alertdialog');
    await user.click(within(dialog).getByRole('button', { name: 'Deactivate' }));

    // Assert
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'The association must keep at least one active administrator',
    );
    await user.click(within(dialog).getByRole('button', { name: 'Keep active' }));
    expect(await screen.findByRole('button', { name: 'Deactivate member' })).toBeInTheDocument();
  });

  it('is not offered for a member who is already inactive', async () => {
    // Arrange / Act
    await openMember(memberDetail({ status: 'INACTIVE' }));

    // Assert
    expect(screen.queryByRole('button', { name: 'Deactivate member' })).not.toBeInTheDocument();
    expect(screen.getByText('Inactive')).toBeInTheDocument();
  });
});

describe('the activation link', () => {
  it('is offered only to an active member whose account is not activated', async () => {
    // Arrange / Act
    await openMember(memberDetail({ accountActivated: false }));

    // Assert
    expect(screen.getByRole('region', { name: 'Account' })).toHaveTextContent('Not activated yet');
    expect(screen.getByRole('button', { name: 'Resend activation link' })).toBeInTheDocument();
  });

  it('is not offered when the account is activated', async () => {
    // Arrange / Act
    await openMember();

    // Assert
    expect(screen.getByRole('region', { name: 'Account' })).toHaveTextContent('Activated');
    expect(
      screen.queryByRole('button', { name: 'Resend activation link' }),
    ).not.toBeInTheDocument();
  });

  it('is not offered to an inactive member', async () => {
    // Arrange / Act
    await openMember(memberDetail({ accountActivated: false, status: 'INACTIVE' }));

    // Assert
    expect(
      screen.queryByRole('button', { name: 'Resend activation link' }),
    ).not.toBeInTheDocument();
  });

  it('sends one request without a body and answers the same for every 202, promising nothing', async () => {
    // Arrange
    const { seen, record } = recorder();
    const { user } = await openMember(memberDetail({ accountActivated: false }));
    server.use(
      http.post(`${memberUrl}/activation-links`, async ({ request }) => {
        await record(request);
        return HttpResponse.json({ status: 'RECEIVED' }, { status: 202 });
      }),
    );

    // Act
    await user.click(screen.getByRole('button', { name: 'Resend activation link' }));

    // Assert
    const notice = await screen.findByRole('status');
    expect(notice).toHaveTextContent(
      'If Ana Silva has not activated yet, a new activation link is on its way.',
    );
    expect(seen).toHaveLength(1);
    expect(seen[0]?.method).toBe('POST');
    expect(seen[0]?.body).toBe('');
  });

  it('shows the API wait time when the resends are limited', async () => {
    // Arrange
    const { user } = await openMember(memberDetail({ accountActivated: false }));
    server.use(
      http.post(`${memberUrl}/activation-links`, () =>
        apiError(429, 'TOO_MANY_REQUESTS', 'Too many requests', {
          headers: { 'Retry-After': '1800' },
        }),
      ),
    );

    // Act
    await user.click(screen.getByRole('button', { name: 'Resend activation link' }));

    // Assert
    const alert = await within(screen.getByRole('region', { name: 'Account' })).findByRole('alert');
    expect(alert).toHaveTextContent('Please wait 30 minutes and try again.');
  });
});
