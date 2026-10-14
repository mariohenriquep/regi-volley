import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import {
  JOIN_REQUESTS_URL,
  joinRequest,
  meAs,
  REQUEST_ID,
  signedIn,
  MEMBER_ID,
} from '@/test/fixtures';
import { apiError, recorder } from '@/test/handlers';
import { renderApp } from '@/test/render';
import { server } from '@/test/server';

const approvalUrl = `${JOIN_REQUESTS_URL}/${REQUEST_ID}/approval`;
const rejectionUrl = `${JOIN_REQUESTS_URL}/${REQUEST_ID}/rejection`;

/** The pending list: what `pending` holds is what the API answers, and a decision removes the request from it. */
function servePending(pending = [joinRequest()]) {
  const state = { pending };
  server.use(
    signedIn,
    meAs('MEMBER', 'ADMIN'),
    http.get(JOIN_REQUESTS_URL, () => HttpResponse.json(state.pending)),
  );
  return state;
}

describe('the pending requests', () => {
  it('lists name, email, phone and the request date in Lisbon time, in the order the API gives', async () => {
    // Arrange
    servePending([
      joinRequest({ requestedAt: '2026-07-13T09:15:00Z' }),
      joinRequest({
        id: 'b0000000-0000-0000-0000-000000000002',
        name: 'Tiago Novo',
        email: 'tiago@example.com',
        phone: '+351 910 000 003',
        requestedAt: '2026-07-14T21:30:00Z',
      }),
    ]);

    // Act
    renderApp('/admin/join-requests');

    // Assert
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Join requests' }),
    ).toBeInTheDocument();
    const rita = await screen.findByRole('article', { name: 'Rita Visitor' });
    expect(rita).toHaveTextContent('rita@example.com');
    expect(rita).toHaveTextContent('+351 910 000 001');
    expect(rita).toHaveTextContent('Mon 13 Jul, 10:15');
    const names = screen.getAllByRole('article').map((card) => within(card).getByRole('heading'));
    expect(names.map((heading) => heading.textContent)).toEqual(['Rita Visitor', 'Tiago Novo']);
    expect(screen.getByRole('article', { name: 'Tiago Novo' })).toHaveTextContent(
      'Tue 14 Jul, 22:30',
    );
  });

  it('links back to the Admin home', async () => {
    // Arrange
    servePending();

    // Act
    renderApp('/admin/join-requests');

    // Assert
    expect(await screen.findByRole('link', { name: 'Back to Admin' })).toHaveAttribute(
      'href',
      '/admin',
    );
  });

  it('says so when nobody is waiting', async () => {
    // Arrange
    servePending([]);

    // Act
    renderApp('/admin/join-requests');

    // Assert
    expect(await screen.findByText('No requests are waiting')).toBeInTheDocument();
    expect(screen.queryByRole('article')).not.toBeInTheDocument();
  });

  it('warns that the list is cut when the API sends its maximum of 100', async () => {
    // Arrange
    servePending(
      Array.from({ length: 100 }, (_, index) =>
        joinRequest({
          id: `b0000000-0000-0000-0000-${String(index + 100).padStart(12, '0')}`,
          name: `Person ${String(index + 1)}`,
        }),
      ),
    );

    // Act
    renderApp('/admin/join-requests');

    // Assert
    expect(await screen.findByText(/Showing the 100 oldest requests/)).toBeInTheDocument();
  });

  it('shows the error with a retry when the list cannot be read', async () => {
    // Arrange
    server.use(
      signedIn,
      meAs('MEMBER', 'ADMIN'),
      http.get(JOIN_REQUESTS_URL, () => apiError(500, 'INTERNAL_ERROR', 'boom')),
    );

    // Act
    renderApp('/admin/join-requests');

    // Assert
    expect(await screen.findByText('Something went wrong. Please try again.')).toBeInTheDocument();
    expect(screen.getByText('Reference: req-500')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });

  it('answers a member who is not an administrator with the 403 of the API', async () => {
    // Arrange
    server.use(
      signedIn,
      http.get(JOIN_REQUESTS_URL, () => apiError(403, 'FORBIDDEN', 'Access denied')),
    );

    // Act
    renderApp('/admin/join-requests');

    // Assert
    expect(await screen.findByText('Administration is for administrators.')).toBeInTheDocument();
  });
});

describe('approving', () => {
  it('asks first, says what happens, and sends nothing until confirmed', async () => {
    // Arrange
    const { seen, record } = recorder();
    servePending();
    server.use(
      http.post(approvalUrl, async ({ request }) => {
        await record(request);
        return HttpResponse.json({ memberId: MEMBER_ID, request: joinRequest() });
      }),
    );
    const { user } = renderApp('/admin/join-requests');
    const card = await screen.findByRole('article', { name: 'Rita Visitor' });

    // Act
    await user.click(within(card).getByRole('button', { name: /^Approve/ }));

    // Assert
    const dialog = await screen.findByRole('alertdialog', { name: 'Approve Rita Visitor?' });
    expect(dialog).toHaveTextContent('creates the member at the entry level');
    expect(dialog).toHaveTextContent('emails them an activation link');
    expect(seen).toHaveLength(0);
    await user.click(within(dialog).getByRole('button', { name: 'Not now' }));
    await waitFor(() => {
      expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    });
    expect(seen).toHaveLength(0);
  });

  it('approves on confirmation, reports the outcome, refreshes the list and links the new member', async () => {
    // Arrange
    const { seen, record } = recorder();
    const state = servePending();
    server.use(
      http.post(approvalUrl, async ({ request }) => {
        await record(request);
        state.pending = [];
        return HttpResponse.json({
          memberId: MEMBER_ID,
          request: joinRequest({ status: 'APPROVED' }),
        });
      }),
    );
    const { user } = renderApp('/admin/join-requests');
    const card = await screen.findByRole('article', { name: 'Rita Visitor' });
    await user.click(within(card).getByRole('button', { name: /^Approve/ }));
    const dialog = await screen.findByRole('alertdialog', { name: 'Approve Rita Visitor?' });

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Approve request' }));

    // Assert
    const notice = await screen.findByRole('status');
    expect(notice).toHaveTextContent('Rita Visitor is now a member');
    expect(notice).toHaveTextContent('activation link');
    expect(within(notice).getByRole('link', { name: 'Open member' })).toHaveAttribute(
      'href',
      `/admin/members/${MEMBER_ID}`,
    );
    expect(seen).toHaveLength(1);
    expect(seen[0]).toMatchObject({ method: 'POST', path: approvalUrl });
    await waitFor(() => {
      expect(screen.queryByRole('article', { name: 'Rita Visitor' })).not.toBeInTheDocument();
    });
    expect(screen.getByText('No requests are waiting')).toBeInTheDocument();
    expect(notice).toHaveFocus();
  });

  it('shows the API refusal in the dialog, with its reference, and keeps the dialog open', async () => {
    // Arrange: someone else decided it first
    servePending();
    server.use(
      http.post(approvalUrl, () =>
        apiError(
          422,
          'BUSINESS_RULE_VIOLATION',
          'Cannot move the join request from approved to approved',
        ),
      ),
    );
    const { user } = renderApp('/admin/join-requests');
    const card = await screen.findByRole('article', { name: 'Rita Visitor' });
    await user.click(within(card).getByRole('button', { name: /^Approve/ }));
    const dialog = await screen.findByRole('alertdialog', { name: 'Approve Rita Visitor?' });

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Approve request' }));

    // Assert
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'Cannot move the join request from approved to approved',
    );
    expect(within(dialog).getByText('Reference: req-422')).toBeInTheDocument();
  });
});

describe('rejecting', () => {
  it('sends the optional reason, trimmed, and reports the outcome', async () => {
    // Arrange
    const { seen, record } = recorder();
    const state = servePending();
    server.use(
      http.post(rejectionUrl, async ({ request }) => {
        await record(request);
        state.pending = [];
        return HttpResponse.json(joinRequest({ status: 'REJECTED' }));
      }),
    );
    const { user } = renderApp('/admin/join-requests');
    const card = await screen.findByRole('article', { name: 'Rita Visitor' });
    await user.click(within(card).getByRole('button', { name: /^Reject/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Reject Rita Visitor?' });
    expect(dialog).toHaveTextContent('not included in the email');

    // Act
    await user.type(within(dialog).getByLabelText('Reason (optional)'), '  Group is full  ');
    await user.click(within(dialog).getByRole('button', { name: 'Reject request' }));

    // Assert
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Request from Rita Visitor rejected.',
    );
    expect(seen).toHaveLength(1);
    expect(JSON.parse(seen[0]?.body ?? '')).toEqual({ reason: 'Group is full' });
    await waitFor(() => {
      expect(screen.queryByRole('article', { name: 'Rita Visitor' })).not.toBeInTheDocument();
    });
  });

  it('sends an empty body when no reason is given', async () => {
    // Arrange
    const { seen, record } = recorder();
    servePending();
    server.use(
      http.post(rejectionUrl, async ({ request }) => {
        await record(request);
        return HttpResponse.json(joinRequest({ status: 'REJECTED' }));
      }),
    );
    const { user } = renderApp('/admin/join-requests');
    const card = await screen.findByRole('article', { name: 'Rita Visitor' });
    await user.click(within(card).getByRole('button', { name: /^Reject/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Reject Rita Visitor?' });

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Reject request' }));

    // Assert
    await screen.findByRole('status');
    expect(JSON.parse(seen[0]?.body ?? '')).toEqual({});
  });

  it('shows the API message on the reason field when it is refused', async () => {
    // Arrange
    servePending();
    server.use(
      http.post(rejectionUrl, () =>
        apiError(422, 'INVALID_FIELD', 'The reason must be at most 500 characters', {
          fields: ['reason'],
        }),
      ),
    );
    const { user } = renderApp('/admin/join-requests');
    const card = await screen.findByRole('article', { name: 'Rita Visitor' });
    await user.click(within(card).getByRole('button', { name: /^Reject/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Reject Rita Visitor?' });

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Reject request' }));

    // Assert
    const reason = within(dialog).getByLabelText('Reason (optional)');
    await waitFor(() => {
      expect(reason).toHaveAccessibleDescription('The reason must be at most 500 characters');
    });
    expect(reason).toBeInvalid();
  });

  it('shows any other refusal in the dialog and sends the reason no further than 500 characters', async () => {
    // Arrange
    servePending();
    server.use(
      http.post(rejectionUrl, () => apiError(409, 'CONFLICT', 'Please reload and try again.')),
    );
    const { user } = renderApp('/admin/join-requests');
    const card = await screen.findByRole('article', { name: 'Rita Visitor' });
    await user.click(within(card).getByRole('button', { name: /^Reject/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Reject Rita Visitor?' });

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Reject request' }));

    // Assert
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'Please reload and try again.',
    );
    expect(within(dialog).getByLabelText('Reason (optional)')).toHaveAttribute('maxlength', '500');
  });
});
