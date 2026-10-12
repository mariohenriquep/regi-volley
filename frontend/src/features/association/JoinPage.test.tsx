import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import {
  apiError,
  publicAssociation,
  rateLimited,
  recorder,
  refreshRejected,
} from '@/test/handlers';
import { renderApp } from '@/test/render';
import { server } from '@/test/server';

const association = http.get('/api/v1/public/associations/:shortName', () =>
  HttpResponse.json(publicAssociation),
);
const accepted = () => HttpResponse.json({ status: 'RECEIVED' }, { status: 202 });
const CONFIRMATION = 'If this email can join, the association will review your request.';

async function fill(user: ReturnType<typeof renderApp>['user'], consent = true) {
  await user.type(await screen.findByLabelText('Name'), 'Rita Silva');
  await user.type(screen.getByLabelText('Email'), 'rita@example.com');
  await user.type(screen.getByLabelText('Phone'), '912345678');
  if (consent) await user.click(screen.getByRole('checkbox', { name: /I agree/ }));
}

describe('join form', () => {
  it('sends the request and shows the confirmation', async () => {
    // Arrange
    const { seen, record } = recorder();
    server.use(
      refreshRejected,
      association,
      http.post('/api/v1/public/associations/:shortName/join-requests', async ({ request }) => {
        await record(request);
        return accepted();
      }),
    );
    const { user } = renderApp('/a/lisbon-spikers/join');
    await fill(user);

    // Act
    await user.click(screen.getByRole('button', { name: 'Send request' }));

    // Assert
    expect(await screen.findByText(CONFIRMATION)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Request received' })).toHaveFocus();
    expect(screen.getByRole('link', { name: 'Back to Lisbon Spikers' })).toHaveAttribute(
      'href',
      '/a/lisbon-spikers',
    );
    expect(seen).toHaveLength(1);
    expect(seen[0]?.path).toBe('/api/v1/public/associations/lisbon-spikers/join-requests');
    expect(seen[0]?.authorization).toBeNull();
    expect(JSON.parse(seen[0]?.body ?? '')).toEqual({
      name: 'Rita Silva',
      email: 'rita@example.com',
      phone: '912345678',
      consentAccepted: true,
      policyVersion: '2026-01',
    });
  });

  it('names the policy version in the consent and links the policy in a new tab', async () => {
    // Arrange
    server.use(refreshRejected, association);

    // Act
    renderApp('/a/lisbon-spikers/join');

    // Assert
    const checkbox = await screen.findByRole('checkbox', { name: /version 2026-01/ });
    expect(checkbox).not.toBeChecked();
    const policy = screen.getByRole('link', { name: 'privacy policy' });
    expect(policy).toHaveAttribute('href', '/privacy');
    expect(policy).toHaveAttribute('target', '_blank');
    expect(policy).toHaveAttribute('rel', expect.stringContaining('noopener'));
  });

  it('answers the same whatever the outcome behind the 202', async () => {
    // Arrange: the API answers one body for new, already a member and pending
    server.use(
      refreshRejected,
      association,
      http.post('/api/v1/public/associations/:shortName/join-requests', accepted),
    );
    const { user } = renderApp('/a/lisbon-spikers/join');
    await fill(user);

    // Act
    await user.click(screen.getByRole('button', { name: 'Send request' }));

    // Assert
    const page = await screen.findByRole('status');
    expect(page).toHaveTextContent(CONFIRMATION);
    expect(page).not.toHaveTextContent(/already|member|pending/i);
  });

  it('shows what is missing before sending anything, and puts focus on the summary', async () => {
    // Arrange
    const { seen, record } = recorder();
    server.use(
      refreshRejected,
      association,
      http.post('/api/v1/public/associations/:shortName/join-requests', async ({ request }) => {
        await record(request);
        return accepted();
      }),
    );
    const { user } = renderApp('/a/lisbon-spikers/join');
    await screen.findByLabelText('Name');

    // Act
    await user.click(screen.getByRole('button', { name: 'Send request' }));

    // Assert
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveFocus();
    expect(alert).toHaveTextContent('Name: Enter your name');
    expect(alert).toHaveTextContent('Email: Enter your email address');
    expect(alert).toHaveTextContent('Phone: Enter your phone number');
    expect(alert).toHaveTextContent('Consent: Accept the privacy policy to send your request');
    expect(screen.getByLabelText('Name')).toBeInvalid();
    expect(seen).toEqual([]);
  });

  it('limits what can be typed to what the API accepts', async () => {
    // Arrange
    server.use(refreshRejected, association);

    // Act
    renderApp('/a/lisbon-spikers/join');

    // Assert
    expect(await screen.findByLabelText('Name')).toHaveAttribute('maxlength', '100');
    expect(screen.getByLabelText('Email')).toHaveAttribute('maxlength', '254');
    expect(screen.getByLabelText('Phone')).toHaveAttribute('maxlength', '30');
  });

  it("shows the API's field errors on the fields", async () => {
    // Arrange
    server.use(
      refreshRejected,
      association,
      http.post('/api/v1/public/associations/:shortName/join-requests', () =>
        apiError(422, 'INVALID_FIELD', 'The phone number is not valid', { fields: ['phone'] }),
      ),
    );
    const { user } = renderApp('/a/lisbon-spikers/join');
    await fill(user);

    // Act
    await user.click(screen.getByRole('button', { name: 'Send request' }));

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Phone: The phone number is not valid',
    );
    const phone = screen.getByLabelText('Phone');
    expect(phone).toBeInvalid();
    expect(phone).toHaveAccessibleDescription('The phone number is not valid');
    expect(screen.queryByText(CONFIRMATION)).not.toBeInTheDocument();
  });

  it('marks the fields of a 400 and keeps what was typed', async () => {
    // Arrange
    server.use(
      refreshRejected,
      association,
      http.post('/api/v1/public/associations/:shortName/join-requests', () =>
        apiError(400, 'INVALID_REQUEST', 'Invalid request', { fields: ['email'] }),
      ),
    );
    const { user } = renderApp('/a/lisbon-spikers/join');
    await fill(user);

    // Act
    await user.click(screen.getByRole('button', { name: 'Send request' }));

    // Assert
    await screen.findByRole('alert');
    expect(screen.getByLabelText('Email')).toBeInvalid();
    expect(screen.getByLabelText('Email')).toHaveValue('rita@example.com');
  });

  it('shows a refused consent as a general message', async () => {
    // Arrange
    server.use(
      refreshRejected,
      association,
      http.post('/api/v1/public/associations/:shortName/join-requests', () =>
        apiError(422, 'BUSINESS_RULE_VIOLATION', 'The privacy policy must be accepted'),
      ),
    );
    const { user } = renderApp('/a/lisbon-spikers/join');
    await fill(user);

    // Act
    await user.click(screen.getByRole('button', { name: 'Send request' }));

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The privacy policy must be accepted',
    );
  });

  it('tells the visitor how long to wait when rate limited', async () => {
    // Arrange
    server.use(
      refreshRejected,
      association,
      http.post('/api/v1/public/associations/:shortName/join-requests', () => rateLimited(3600)),
    );
    const { user } = renderApp('/a/lisbon-spikers/join');
    await fill(user);

    // Act
    await user.click(screen.getByRole('button', { name: 'Send request' }));

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Too many attempts. Please wait 1 hour and try again.',
    );
    expect(screen.getByRole('button', { name: 'Send request' })).toBeEnabled();
  });

  it('shows generic text and the request id for a server error', async () => {
    // Arrange
    server.use(
      refreshRejected,
      association,
      http.post('/api/v1/public/associations/:shortName/join-requests', () =>
        apiError(500, 'INTERNAL_ERROR', 'java.lang.NullPointerException'),
      ),
    );
    const { user } = renderApp('/a/lisbon-spikers/join');
    await fill(user);

    // Act
    await user.click(screen.getByRole('button', { name: 'Send request' }));

    // Assert
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Something went wrong. Please try again.');
    expect(alert).toHaveTextContent('req-500');
    expect(alert).not.toHaveTextContent('NullPointer');
  });

  it('shows the not-found page when the association does not exist', async () => {
    // Arrange
    server.use(
      refreshRejected,
      http.get('/api/v1/public/associations/:shortName', () =>
        apiError(404, 'NOT_FOUND', 'Not found'),
      ),
    );

    // Act
    renderApp('/a/nobody/join');

    // Assert
    expect(
      await screen.findByRole('heading', { name: 'Association not found' }),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText('Name')).not.toBeInTheDocument();
  });

  it('is open to a signed-in user', async () => {
    // Arrange
    server.use(
      http.post('/api/v1/auth/refresh', () =>
        HttpResponse.json({ accessToken: 'tok', tokenType: 'Bearer', expiresIn: 600 }),
      ),
      association,
    );

    // Act
    const { router } = renderApp('/a/lisbon-spikers/join');

    // Assert
    expect(await screen.findByLabelText('Name')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/a/lisbon-spikers/join');
  });
});
