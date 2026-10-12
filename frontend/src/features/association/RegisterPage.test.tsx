import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { apiError, rateLimited, recorder, refreshRejected, tokenResponse } from '@/test/handlers';
import { renderApp } from '@/test/render';
import { server } from '@/test/server';

type User = ReturnType<typeof renderApp>['user'];

const created = () => HttpResponse.json({ shortName: 'lisbon-spikers' }, { status: 201 });

async function fillAll(user: User) {
  await user.type(await screen.findByLabelText('Association name'), 'Lisbon Spikers');
  await user.type(screen.getByLabelText('Short name'), 'Lisbon-Spikers');
  await user.type(screen.getByLabelText('Locality'), 'Lisbon');
  await user.type(screen.getByLabelText('Contact email'), 'info@lisbonspikers.example');
  await user.type(screen.getByLabelText('Level 1'), 'Beginner');
  await user.type(screen.getByLabelText('Your name'), 'Rita Silva');
  await user.type(screen.getByLabelText('Your email'), 'rita@example.com');
  await user.type(screen.getByLabelText('Your phone'), '912345678');
  await user.click(screen.getByRole('checkbox', { name: /I agree/ }));
}

const submit = (user: User) => user.click(screen.getByRole('button', { name: 'Register' }));

describe('register association', () => {
  it('sends the registration and asks the founder to check their email', async () => {
    // Arrange
    const { seen, record } = recorder();
    server.use(
      refreshRejected,
      http.post('/api/v1/public/associations', async ({ request }) => {
        await record(request);
        return created();
      }),
    );
    const { user } = renderApp('/register');
    await fillAll(user);
    await user.click(screen.getByRole('button', { name: 'Add level' }));
    await user.type(screen.getByLabelText('Level 2'), 'Advanced');
    await user.type(screen.getByLabelText('NIF (optional)'), '123456789');

    // Act
    await submit(user);

    // Assert
    expect(
      await screen.findByRole('heading', { name: 'Check your email to activate your account' }),
    ).toHaveFocus();
    expect(within(screen.getByRole('main')).getByRole('link', { name: 'Sign in' })).toHaveAttribute(
      'href',
      '/login',
    );
    expect(screen.getByRole('link', { name: 'your public page' })).toHaveAttribute(
      'href',
      '/a/lisbon-spikers',
    );
    expect(seen).toHaveLength(1);
    expect(seen[0]?.authorization).toBeNull();
    expect(JSON.parse(seen[0]?.body ?? '')).toEqual({
      name: 'Lisbon Spikers',
      shortName: 'Lisbon-Spikers',
      nif: '123456789',
      locality: 'Lisbon',
      contactEmail: 'info@lisbonspikers.example',
      levelNames: ['Beginner', 'Advanced'],
      founderName: 'Rita Silva',
      founderEmail: 'rita@example.com',
      founderPhone: '912345678',
      consentAccepted: true,
      policyVersion: '2026-01',
    });
  });

  it('shows the public address as it is typed, normalised only for display', async () => {
    // Arrange
    server.use(refreshRejected);
    const { user } = renderApp('/register');

    // Act
    await user.type(await screen.findByLabelText('Short name'), '  Lisbon-Spikers ');

    // Assert
    expect(screen.getByText(`${window.location.origin}/a/lisbon-spikers`)).toBeInTheDocument();
    expect(screen.getByLabelText('Short name')).toHaveValue('  Lisbon-Spikers ');
  });

  it('says the contact email is shown publicly and explains the entry level', async () => {
    // Arrange
    server.use(refreshRejected);

    // Act
    renderApp('/register');

    // Assert
    expect(await screen.findByLabelText('Contact email')).toHaveAccessibleDescription(
      /shown publicly/i,
    );
    expect(screen.getByRole('group', { name: 'Levels' })).toHaveAccessibleDescription(
      /first level is the entry level/i,
    );
  });

  it('adds, reorders and removes levels, keeping what was typed in each', async () => {
    // Arrange
    const { seen, record } = recorder();
    server.use(
      refreshRejected,
      http.post('/api/v1/public/associations', async ({ request }) => {
        await record(request);
        return created();
      }),
    );
    const { user } = renderApp('/register');
    await fillAll(user);
    await user.click(screen.getByRole('button', { name: 'Add level' }));
    await user.type(screen.getByLabelText('Level 2'), 'Intermediate');
    await user.click(screen.getByRole('button', { name: 'Add level' }));
    await user.type(screen.getByLabelText('Level 3'), 'Advanced');

    // Act
    await user.click(screen.getByRole('button', { name: 'Move level 3 up' }));
    await user.click(screen.getByRole('button', { name: 'Move level 2 up' }));
    await user.click(screen.getByRole('button', { name: 'Remove level 3' }));
    await submit(user);

    // Assert: Beginner, Intermediate, Advanced -> Beginner, Advanced, Intermediate -> Advanced, Beginner, Intermediate
    //         -> the last (Intermediate) removed
    await screen.findByRole('heading', { name: 'Check your email to activate your account' });
    expect((JSON.parse(seen[0]?.body ?? '') as { levelNames: string[] }).levelNames).toEqual([
      'Advanced',
      'Beginner',
    ]);
  });

  it('keeps at least one level and at most twenty', async () => {
    // Arrange
    server.use(refreshRejected);
    const { user } = renderApp('/register');
    await screen.findByLabelText('Level 1');

    // Act
    for (let count = 1; count < 20; count += 1) {
      await user.click(screen.getByRole('button', { name: 'Add level' }));
    }

    // Assert
    expect(screen.getByRole('button', { name: 'Add level' })).toBeDisabled();
    expect(screen.getAllByLabelText(/^Level \d+$/)).toHaveLength(20);
    expect(screen.getByLabelText('Level 20')).toHaveAttribute('maxlength', '50');
  });

  it('cannot remove the only level', async () => {
    // Arrange
    server.use(refreshRejected);

    // Act
    renderApp('/register');

    // Assert
    expect(await screen.findByRole('button', { name: 'Remove level 1' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Move level 1 up' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Move level 1 down' })).toBeDisabled();
  });

  it('shows what is missing before sending anything', async () => {
    // Arrange
    const { seen, record } = recorder();
    server.use(
      refreshRejected,
      http.post('/api/v1/public/associations', async ({ request }) => {
        await record(request);
        return created();
      }),
    );
    const { user } = renderApp('/register');
    await screen.findByLabelText('Association name');

    // Act
    await submit(user);

    // Assert
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveFocus();
    for (const label of [
      'Association name',
      'Short name',
      'Locality',
      'Contact email',
      'Levels',
      'Your name',
      'Your email',
      'Your phone',
      'Consent',
    ]) {
      expect(within(alert).getByText(new RegExp(`^${label}:`))).toBeInTheDocument();
    }
    expect(seen).toEqual([]);
  });

  it('shows a taken short name on the short name field', async () => {
    // Arrange
    server.use(
      refreshRejected,
      http.post('/api/v1/public/associations', () =>
        apiError(409, 'CONFLICT', "The short name 'lisbon-spikers' is already taken"),
      ),
    );
    const { user } = renderApp('/register');
    await fillAll(user);

    // Act
    await submit(user);

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent(
      "Short name: The short name 'lisbon-spikers' is already taken",
    );
    const shortName = screen.getByLabelText('Short name');
    expect(shortName).toBeInvalid();
    expect(shortName).toHaveAccessibleDescription(/already taken/);
    expect(screen.getByLabelText('Association name')).toHaveValue('Lisbon Spikers');
  });

  it("shows the API's 422 on the field it names, in the API's words", async () => {
    // Arrange
    server.use(
      refreshRejected,
      http.post('/api/v1/public/associations', () =>
        apiError(
          422,
          'INVALID_FIELD',
          'The short name must have 3 to 40 characters: lowercase letters, digits and single hyphens, not starting or ending with a hyphen',
          { fields: ['short name'] },
        ),
      ),
    );
    const { user } = renderApp('/register');
    await fillAll(user);

    // Act
    await submit(user);

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Short name: The short name must have 3 to 40',
    );
    expect(screen.getByLabelText('Short name')).toBeInvalid();
  });

  it('marks the level group for a 400 on a level name', async () => {
    // Arrange
    server.use(
      refreshRejected,
      http.post('/api/v1/public/associations', () =>
        apiError(400, 'INVALID_REQUEST', 'Invalid request', { fields: ['levelNames[0]'] }),
      ),
    );
    const { user } = renderApp('/register');
    await fillAll(user);

    // Act
    await submit(user);

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent('Levels: Check this field.');
  });

  it('tells the visitor how long to wait when rate limited', async () => {
    // Arrange
    server.use(
      refreshRejected,
      http.post('/api/v1/public/associations', () => rateLimited(2700)),
    );
    const { user } = renderApp('/register');
    await fillAll(user);

    // Act
    await submit(user);

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Too many attempts. Please wait 45 minutes and try again.',
    );
  });

  it('shows generic text for a server error', async () => {
    // Arrange
    server.use(
      refreshRejected,
      http.post('/api/v1/public/associations', () =>
        apiError(500, 'INTERNAL_ERROR', 'SQL exploded'),
      ),
    );
    const { user } = renderApp('/register');
    await fillAll(user);

    // Act
    await submit(user);

    // Assert
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Something went wrong. Please try again.');
    expect(alert).not.toHaveTextContent('SQL');
  });

  it('sends a signed-in user away (PublicOnly)', async () => {
    // Arrange
    server.use(
      http.post('/api/v1/auth/refresh', () => tokenResponse('tok')),
      http.get('/api/v1/sessions', () => HttpResponse.json([])),
    );

    // Act
    const { router } = renderApp('/register');

    // Assert
    expect(await screen.findByRole('heading', { level: 1, name: 'Sessions' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/');
  });
});
