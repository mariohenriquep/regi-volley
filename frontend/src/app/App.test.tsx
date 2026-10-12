import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { me, recorder, refreshRejected, tokenResponse, unauthenticated } from '@/test/handlers';
import { renderApp } from '@/test/render';
import { server } from '@/test/server';

const meWith = (token: string) =>
  http.get('/api/v1/me', ({ request }) =>
    request.headers.get('Authorization') === `Bearer ${token}`
      ? HttpResponse.json(me)
      : unauthenticated(),
  );

describe('start-up', () => {
  it('restores the session from the refresh cookie and shows the home page', async () => {
    // Arrange
    server.use(
      http.post('/api/v1/auth/refresh', () => tokenResponse('restored')),
      meWith('restored'),
    );

    // Act
    renderApp('/');

    // Assert
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument();
    expect(await screen.findByText(me.memberId)).toBeInTheDocument();
  });

  it('shows a loading state while the session is being restored', () => {
    // Arrange
    server.use(http.post('/api/v1/auth/refresh', () => new Promise<never>(() => undefined)));

    // Act
    renderApp('/');

    // Assert
    expect(screen.getByRole('status')).toHaveTextContent('Loading');
  });
});

describe('route guards', () => {
  it('sends an anonymous visitor of a private page to the sign-in page', async () => {
    // Arrange
    server.use(refreshRejected);

    // Act
    const { router } = renderApp('/');

    // Assert
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
  });

  it('sends a signed-in user away from the sign-in page', async () => {
    // Arrange
    server.use(
      http.post('/api/v1/auth/refresh', () => tokenResponse('restored')),
      meWith('restored'),
    );

    // Act
    const { router } = renderApp('/login');

    // Assert
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/');
  });

  it('returns to the sign-in page when the session expires', async () => {
    // Arrange
    server.use(
      http.post('/api/v1/auth/refresh', () => tokenResponse('restored'), { once: true }),
      refreshRejected,
      http.get('/api/v1/me', () => unauthenticated()),
    );

    // Act
    renderApp('/');

    // Assert
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('never returns to a path that resolves to another origin', async () => {
    // Arrange
    server.use(
      http.post('/api/v1/auth/refresh', () => tokenResponse('restored')),
      meWith('restored'),
    );

    // Act
    const { router } = renderApp({ pathname: '/login', state: { from: '/\\evil.com' } });

    // Assert
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/');
  });

  it('returns to the page asked for after signing in', async () => {
    // Arrange
    server.use(
      http.post('/api/v1/auth/refresh', () => tokenResponse('restored')),
      meWith('restored'),
    );

    // Act
    const { router } = renderApp({ pathname: '/login', state: { from: '/elsewhere?x=1' } });

    // Assert
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
    expect(router.state.location.pathname + router.state.location.search).toBe('/elsewhere?x=1');
  });
});

describe('sign in', () => {
  it('signs in with email and password and shows the home page', async () => {
    // Arrange
    const { seen, record } = recorder();
    server.use(
      refreshRejected,
      http.post('/api/v1/auth/login', async ({ request }) => {
        await record(request);
        return tokenResponse('signed-in');
      }),
      meWith('signed-in'),
    );
    const { user, router } = renderApp('/login');

    // Act
    await user.type(await screen.findByLabelText('Email'), 'ana@example.com');
    await user.type(screen.getByLabelText('Password'), 'a long passphrase');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    // Assert
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/');
    expect(JSON.parse(seen[0]?.body ?? '')).toEqual({
      email: 'ana@example.com',
      password: 'a long passphrase',
    });
  });

  it("shows the API's message when the credentials are wrong", async () => {
    // Arrange
    server.use(
      refreshRejected,
      http.post('/api/v1/auth/login', () =>
        HttpResponse.json(
          { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password', requestId: 'r-1' },
          { status: 401 },
        ),
      ),
    );
    const { user } = renderApp('/login');

    // Act
    await user.type(await screen.findByLabelText('Email'), 'ana@example.com');
    await user.type(screen.getByLabelText('Password'), 'wrong password');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password');
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('shows generic text and the request id for a server error', async () => {
    // Arrange
    server.use(
      refreshRejected,
      http.post('/api/v1/auth/login', () =>
        HttpResponse.json(
          { code: 'INTERNAL_ERROR', message: 'An unexpected error occurred', requestId: 'req-500' },
          { status: 500 },
        ),
      ),
    );
    const { user } = renderApp('/login');

    // Act
    await user.type(await screen.findByLabelText('Email'), 'ana@example.com');
    await user.type(screen.getByLabelText('Password'), 'a long passphrase');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    // Assert
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Something went wrong. Please try again.');
    expect(alert).toHaveTextContent('req-500');
    expect(alert).not.toHaveTextContent('An unexpected error occurred');
  });
});

describe('navigation', () => {
  it('offers an anonymous visitor sign in and registration, and nothing to sign out of', async () => {
    // Arrange
    server.use(refreshRejected);

    // Act
    renderApp('/privacy');

    // Assert
    const nav = await screen.findByRole('navigation', { name: 'Main' });
    expect(within(nav).getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login');
    expect(within(nav).getByRole('link', { name: 'Register an association' })).toHaveAttribute(
      'href',
      '/register',
    );
    expect(screen.queryByRole('button', { name: 'Sign out' })).not.toBeInTheDocument();
  });

  it('offers a signed-in user to sign out instead', async () => {
    // Arrange
    server.use(
      http.post('/api/v1/auth/refresh', () => tokenResponse('restored')),
      meWith('restored'),
    );

    // Act
    renderApp('/privacy');

    // Assert
    expect(await screen.findByRole('button', { name: 'Sign out' })).toBeInTheDocument();
    const nav = screen.getByRole('navigation', { name: 'Main' });
    expect(within(nav).queryByRole('link', { name: 'Sign in' })).not.toBeInTheDocument();
  });

  it('links the privacy policy from every page', async () => {
    // Arrange
    server.use(refreshRejected);

    // Act
    renderApp('/no-such-page');

    // Assert
    expect(await screen.findByRole('link', { name: 'Privacy policy' })).toHaveAttribute(
      'href',
      '/privacy',
    );
  });
});

describe('sign in page links', () => {
  it('leads to password recovery and to registering an association', async () => {
    // Arrange
    server.use(refreshRejected);
    renderApp('/login');

    // Act
    const main = within(await screen.findByRole('main'));

    // Assert
    expect(main.getByRole('link', { name: 'Forgot password?' })).toHaveAttribute(
      'href',
      '/forgot-password',
    );
    expect(main.getByRole('link', { name: 'Register an association' })).toHaveAttribute(
      'href',
      '/register',
    );
  });

  it('shows how long to wait when sign-in is rate limited, without saying anything about the account', async () => {
    // Arrange
    server.use(
      refreshRejected,
      http.post('/api/v1/auth/login', () =>
        HttpResponse.json(
          { code: 'TOO_MANY_REQUESTS', message: 'Too many requests', requestId: 'r-429' },
          { status: 429, headers: { 'Retry-After': '600' } },
        ),
      ),
    );
    const { user } = renderApp('/login');

    // Act
    await user.type(await screen.findByLabelText('Email'), 'ana@example.com');
    await user.type(screen.getByLabelText('Password'), 'a long passphrase');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Too many attempts. Please wait 10 minutes and try again.',
    );
  });
});

describe('sign out', () => {
  it('signs out of this device and shows the sign-in page', async () => {
    // Arrange
    const { seen, record } = recorder();
    server.use(
      http.post('/api/v1/auth/refresh', () => tokenResponse('restored'), { once: true }),
      refreshRejected,
      meWith('restored'),
      http.post('/api/v1/auth/logout', async ({ request }) => {
        await record(request);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = renderApp('/');
    await screen.findByRole('heading', { name: 'Welcome back' });

    // Act
    await user.click(screen.getByRole('button', { name: 'Sign out' }));

    // Assert
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(seen.map((request) => request.path)).toEqual(['/api/v1/auth/logout']);
  });

  it('signs out of every device', async () => {
    // Arrange
    const { seen, record } = recorder();
    server.use(
      http.post('/api/v1/auth/refresh', () => tokenResponse('restored'), { once: true }),
      refreshRejected,
      meWith('restored'),
      http.post('/api/v1/auth/logout-all', async ({ request }) => {
        await record(request);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = renderApp('/');
    await screen.findByRole('heading', { name: 'Welcome back' });

    // Act
    await user.click(screen.getByRole('button', { name: 'Sign out on all devices' }));

    // Assert
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(seen).toEqual([
      expect.objectContaining({
        path: '/api/v1/auth/logout-all',
        authorization: 'Bearer restored',
      }),
    ]);
  });
});

describe('sign-out failures', () => {
  it('says so when the server did not confirm the sign-out, and lets the user try again', async () => {
    // Arrange
    const { seen, record } = recorder();
    let answer = 500;
    server.use(
      http.post('/api/v1/auth/refresh', () => tokenResponse('restored'), { once: true }),
      refreshRejected,
      meWith('restored'),
      http.post('/api/v1/auth/logout', async ({ request }) => {
        await record(request);
        return answer === 204
          ? new HttpResponse(null, { status: 204 })
          : HttpResponse.json({ code: 'INTERNAL_ERROR', requestId: 'r-7' }, { status: 500 });
      }),
    );
    const { user } = renderApp('/');
    await screen.findByRole('heading', { name: 'Welcome back' });

    // Act
    await user.click(screen.getByRole('button', { name: 'Sign out' }));
    const notice = await screen.findByRole('alert');
    answer = 204;
    await user.click(screen.getByRole('button', { name: 'Try again' }));

    // Assert
    expect(notice).toHaveTextContent('Could not sign out on the server — try again');
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });
    expect(seen).toHaveLength(2);
  });

  it('stays on the page and shows the error when signing out everywhere fails', async () => {
    // Arrange
    server.use(
      http.post('/api/v1/auth/refresh', () => tokenResponse('restored')),
      meWith('restored'),
      http.post('/api/v1/auth/logout-all', () =>
        HttpResponse.json({ code: 'INTERNAL_ERROR', requestId: 'r-8' }, { status: 500 }),
      ),
    );
    const { user } = renderApp('/');
    await screen.findByRole('heading', { name: 'Welcome back' });

    // Act
    await user.click(screen.getByRole('button', { name: 'Sign out on all devices' }));

    // Assert
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Something went wrong. Please try again.');
    expect(alert).toHaveTextContent('r-8');
    expect(screen.getByRole('heading', { name: 'Welcome back' })).toBeInTheDocument();
  });
});

describe('cached data', () => {
  it('drops what was cached when a user signs in and when they sign out', async () => {
    // Arrange
    server.use(
      refreshRejected,
      http.post('/api/v1/auth/login', () => tokenResponse('signed-in')),
      meWith('signed-in'),
      http.post('/api/v1/auth/logout', () => new HttpResponse(null, { status: 204 })),
    );
    const { user, queryClient } = renderApp('/login');
    const email = await screen.findByLabelText('Email');
    queryClient.setQueryData(['left-over'], 'previous user');

    // Act
    await user.type(email, 'ana@example.com');
    await user.type(screen.getByLabelText('Password'), 'a long passphrase');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    await screen.findByText(me.memberId);
    const afterLogin = queryClient.getQueryData(['left-over']);
    await user.click(screen.getByRole('button', { name: 'Sign out' }));
    await screen.findByRole('heading', { name: 'Sign in' });

    // Assert
    expect(afterLogin).toBeUndefined();
    expect(queryClient.getQueryData(['me'])).toBeUndefined();
  });
});

describe('account links', () => {
  it.each([
    ['/activate', 'Activate your account'],
    ['/reset-password', 'Choose a new password'],
  ])('%s reads the token and scrubs it from the address bar', async (path, heading) => {
    // Arrange
    server.use(refreshRejected);
    window.history.replaceState(null, '', `${path}#token=secret-token-123`);

    // Act
    renderApp(path);

    // Assert
    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument();
    expect(window.location.hash).toBe('');
    expect(window.location.pathname).toBe(path);
    expect(document.body).not.toHaveTextContent('secret-token-123');
    expect(screen.getByLabelText('New password')).toBeInTheDocument();
  });

  it('says so when the link has no token', async () => {
    // Arrange
    server.use(refreshRejected);
    window.history.replaceState(null, '', '/activate');

    // Act
    renderApp('/activate');

    // Assert
    expect(await screen.findByText(/link is incomplete/i)).toBeInTheDocument();
  });
});

describe('unknown pages', () => {
  it('shows a not-found page', async () => {
    // Arrange
    server.use(refreshRejected);

    // Act
    renderApp('/no-such-page');

    // Assert
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByRole('link', { name: 'Go to the home page' })).toHaveAttribute(
        'href',
        '/',
      );
    });
  });
});
