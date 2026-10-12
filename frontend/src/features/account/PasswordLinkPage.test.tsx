import { act, screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { apiError, rateLimited, recorder, refreshRejected } from '@/test/handlers';
import { renderApp } from '@/test/render';
import { server } from '@/test/server';

type User = ReturnType<typeof renderApp>['user'];

const TOKEN = 'test-link-token-fixture';
const PASSWORD = 'a long passphrase 42';

const CASES = [
  {
    name: 'activation',
    path: '/activate',
    endpoint: '/api/v1/auth/activate',
    title: 'Activate your account',
    submit: 'Activate account',
    done: 'Account activated',
  },
  {
    name: 'password reset',
    path: '/reset-password',
    endpoint: '/api/v1/auth/password-resets',
    title: 'Choose a new password',
    submit: 'Change password',
    done: 'Password changed',
  },
] as const;

const open = (path: string, withToken = true) => {
  server.use(refreshRejected);
  window.history.replaceState(null, '', withToken ? `${path}#token=${TOKEN}` : path);
  return renderApp(path);
};

async function typePasswords(user: User, password = PASSWORD, confirm = password) {
  await user.type(await screen.findByLabelText('New password'), password);
  await user.type(screen.getByLabelText('Confirm new password'), confirm);
}

describe.each(CASES)('$name link page', ({ path, endpoint, title, submit, done }) => {
  it('posts the token and the new password, and shows the success screen', async () => {
    // Arrange
    const { seen, record } = recorder();
    server.use(
      http.post(endpoint, async ({ request }) => {
        await record(request);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = open(path);
    await typePasswords(user);

    // Act
    await user.click(screen.getByRole('button', { name: submit }));

    // Assert
    expect(await screen.findByRole('heading', { name: done })).toHaveFocus();
    expect(within(screen.getByRole('main')).getByRole('link', { name: 'Sign in' })).toHaveAttribute(
      'href',
      '/login',
    );
    expect(seen).toHaveLength(1);
    expect(seen[0]?.path).toBe(endpoint);
    expect(seen[0]?.authorization).toBeNull();
    expect(JSON.parse(seen[0]?.body ?? '')).toEqual({ token: TOKEN, password: PASSWORD });
    expect(screen.queryByLabelText('New password')).not.toBeInTheDocument();
    expect(document.body).not.toHaveTextContent(TOKEN);
  });

  it('keeps the token out of the address, the history and every request URL', async () => {
    // Arrange
    const urls: string[] = [];
    server.use(
      http.post(endpoint, ({ request }) => {
        urls.push(request.url);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user, router, queryClient } = open(path);
    await typePasswords(user);

    // Act
    await user.click(screen.getByRole('button', { name: submit }));
    await screen.findByRole('heading', { name: done });

    // Assert
    expect(window.location.href).not.toContain(TOKEN);
    expect(router.state.location.hash).toBe('');
    expect(JSON.stringify(router.state.location)).not.toContain(TOKEN);
    expect(urls.join()).not.toContain(TOKEN);
    expect(
      JSON.stringify(
        queryClient
          .getQueryCache()
          .getAll()
          .map((query) => query.queryKey),
      ),
    ).not.toContain(TOKEN);
    expect(
      JSON.stringify(
        queryClient
          .getMutationCache()
          .getAll()
          .map((mutation) => mutation.options.mutationKey ?? null),
      ),
    ).not.toContain(TOKEN);
  });

  it('scrubs the fragment before the form renders', async () => {
    // Arrange / Act
    open(path);

    // Assert
    expect(await screen.findByRole('heading', { name: title })).toBeInTheDocument();
    expect(window.location.hash).toBe('');
    expect(window.location.pathname).toBe(path);
  });

  it('labels both fields and describes the password rule', async () => {
    // Arrange / Act
    open(path);

    // Assert
    const password = await screen.findByLabelText('New password');
    expect(password).toHaveAttribute('type', 'password');
    expect(password).toHaveAttribute('autocomplete', 'new-password');
    expect(password).toHaveAccessibleDescription(/10 to 128 characters/);
    expect(screen.getByLabelText('Confirm new password')).toHaveAttribute(
      'autocomplete',
      'new-password',
    );
  });

  it('checks that the two passwords match before sending anything', async () => {
    // Arrange
    const { seen, record } = recorder();
    server.use(
      http.post(endpoint, async ({ request }) => {
        await record(request);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = open(path);
    await typePasswords(user, PASSWORD, 'something else entirely');

    // Act
    await user.click(screen.getByRole('button', { name: submit }));

    // Assert
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveFocus();
    expect(alert).toHaveTextContent('The passwords do not match');
    expect(screen.getByLabelText('Confirm new password')).toBeInvalid();
    expect(seen).toEqual([]);
  });

  it('asks for a password', async () => {
    // Arrange
    const { user } = open(path);
    await screen.findByLabelText('New password');

    // Act
    await user.click(screen.getByRole('button', { name: submit }));

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent('Enter a new password');
  });

  it("shows the API's password message on the field and lets the user try again with the same link", async () => {
    // Arrange
    const bodies: string[] = [];
    let answer: 'refuse' | 'accept' = 'refuse';
    server.use(
      http.post(endpoint, async ({ request }) => {
        bodies.push(await request.text());
        return answer === 'refuse'
          ? apiError(422, 'INVALID_FIELD', 'This password is too common, choose another one', {
              fields: ['password'],
            })
          : new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = open(path);
    await typePasswords(user, 'password123456');

    // Act
    await user.click(screen.getByRole('button', { name: submit }));
    const alert = await screen.findByRole('alert');
    answer = 'accept';
    await user.clear(screen.getByLabelText('New password'));
    await user.clear(screen.getByLabelText('Confirm new password'));
    await typePasswords(user);
    await user.click(screen.getByRole('button', { name: submit }));

    // Assert
    expect(alert).toHaveTextContent(
      'New password: This password is too common, choose another one',
    );
    await screen.findByRole('heading', { name: done });
    expect(bodies.map((body) => (JSON.parse(body) as { token: string }).token)).toEqual([
      TOKEN,
      TOKEN,
    ]);
  });

  it('shows an invalid or expired link and forgets the token', async () => {
    // Arrange
    server.use(
      http.post(endpoint, () =>
        apiError(400, 'INVALID_LINK', 'This link is invalid or has expired'),
      ),
    );
    const { user } = open(path);
    await typePasswords(user);

    // Act
    await user.click(screen.getByRole('button', { name: submit }));

    // Assert
    expect(await screen.findByText('This link is invalid or has expired.')).toBeInTheDocument();
    expect(screen.queryByLabelText('New password')).not.toBeInTheDocument();
  });

  it('says how to get help when the link has no token', async () => {
    // Arrange / Act
    open(path, false);

    // Assert
    expect(await screen.findByText(/This link is incomplete/)).toBeInTheDocument();
    expect(screen.queryByLabelText('New password')).not.toBeInTheDocument();
  });

  it('tells the user how long to wait when rate limited, and keeps the form', async () => {
    // Arrange
    server.use(http.post(endpoint, () => rateLimited(120)));
    const { user } = open(path);
    await typePasswords(user);

    // Act
    await user.click(screen.getByRole('button', { name: submit }));

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Too many attempts. Please wait 2 minutes and try again.',
    );
    expect(screen.getByLabelText('New password')).toHaveValue(PASSWORD);
  });

  it('shows generic text for a server error', async () => {
    // Arrange
    server.use(http.post(endpoint, () => apiError(500, 'INTERNAL_ERROR', 'stack trace')));
    const { user } = open(path);
    await typePasswords(user);

    // Act
    await user.click(screen.getByRole('button', { name: submit }));

    // Assert
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Something went wrong. Please try again.');
    expect(alert).not.toHaveTextContent('stack trace');
  });
});

describe('a newer link opened while the page is showing', () => {
  it('starts over with the new token and scrubs it too', async () => {
    // Arrange
    const { seen, record } = recorder();
    server.use(
      http.post('/api/v1/auth/activate', async ({ request }) => {
        await record(request);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = open('/activate');
    await typePasswords(user);
    await user.click(screen.getByRole('button', { name: 'Activate account' }));
    await screen.findByRole('heading', { name: 'Account activated' });

    // Act
    window.history.replaceState(null, '', '/activate#token=second-token');
    window.dispatchEvent(new HashChangeEvent('hashchange'));
    await typePasswords(user);
    await user.click(screen.getByRole('button', { name: 'Activate account' }));

    // Assert
    await waitFor(() => {
      expect(seen).toHaveLength(2);
    });
    expect(JSON.parse(seen[1]?.body ?? '')).toEqual({ token: 'second-token', password: PASSWORD });
    expect(window.location.hash).toBe('');
  });
});

describe('where the token lives', () => {
  const linkOf = (router: ReturnType<typeof renderApp>['router']) => {
    const data = Object.values(router.state.loaderData).find(
      (value): value is { link: { take: () => string | null } } =>
        typeof value === 'object' && value !== null && 'link' in value,
    );
    if (!data) throw new Error('no account-link loader data');
    return data.link;
  };

  it('is taken once by the form, so the router keeps nothing', async () => {
    // Arrange / Act
    const { router } = open('/activate');
    await screen.findByLabelText('New password');

    // Assert
    expect(linkOf(router).take()).toBeNull();
  });

  it('is cleared from the router location too, which a browser starts with the fragment in it', async () => {
    // Arrange
    server.use(refreshRejected);
    window.history.replaceState(null, '', `/activate#token=${TOKEN}`);

    // Act
    const { router } = renderApp({ pathname: '/activate', hash: `#token=${TOKEN}` });
    await screen.findByLabelText('New password');

    // Assert
    await waitFor(() => {
      expect(router.state.location.hash).toBe('');
    });
    expect(JSON.stringify(router.state.location)).not.toContain(TOKEN);
  });

  it('is cleared from the router location after a second link too', async () => {
    // Arrange
    const { router } = open('/activate');
    await screen.findByLabelText('New password');

    // Act
    window.history.replaceState(null, '', '/activate#token=second-token');
    await router.navigate('/activate#token=second-token');
    window.dispatchEvent(new HashChangeEvent('hashchange'));

    // Assert
    await waitFor(() => {
      expect(router.state.location.hash).toBe('');
    });
    expect(window.location.hash).toBe('');
  });

  it('survives React strict mode, which renders and initialises twice', async () => {
    // Arrange
    const { seen, record } = recorder();
    server.use(
      http.post('/api/v1/auth/activate', async ({ request }) => {
        await record(request);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    server.use(refreshRejected);
    window.history.replaceState(null, '', `/activate#token=${TOKEN}`);
    const { user } = renderApp('/activate', { strict: true });
    await typePasswords(user);

    // Act
    await user.click(screen.getByRole('button', { name: 'Activate account' }));

    // Assert
    await screen.findByRole('heading', { name: 'Account activated' });
    expect(JSON.parse(seen[0]?.body ?? '')).toEqual({ token: TOKEN, password: PASSWORD });
  });
});

describe('a link opened while the password is being sent', () => {
  it('does not replace the attempt in flight, and still scrubs the address', async () => {
    // Arrange
    let release: () => void = () => undefined;
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    const { seen, record } = recorder();
    server.use(
      http.post('/api/v1/auth/activate', async ({ request }) => {
        await record(request);
        await gate;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = open('/activate');
    await typePasswords(user);
    await user.click(screen.getByRole('button', { name: 'Activate account' }));
    await waitFor(() => {
      expect(seen).toHaveLength(1);
    });

    // Act
    window.history.replaceState(null, '', '/activate#token=second-token');
    window.dispatchEvent(new HashChangeEvent('hashchange'));
    release();

    // Assert
    expect(await screen.findByRole('heading', { name: 'Account activated' })).toBeInTheDocument();
    expect(window.location.hash).toBe('');
    expect(seen).toHaveLength(1);
  });
});

describe('a new link whose address the router already cleared', () => {
  it('is still read, from the event itself', async () => {
    // Arrange
    const { seen, record } = recorder();
    server.use(
      http.post('/api/v1/auth/activate', async ({ request }) => {
        await record(request);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { user } = open('/activate');
    await screen.findByLabelText('New password');

    // Act: the address bar is bare again by the time the event is handled
    act(() => {
      window.dispatchEvent(
        new HashChangeEvent('hashchange', {
          newURL: `${window.location.origin}/activate#token=raced`,
        }),
      );
    });
    await typePasswords(user);
    await user.click(screen.getByRole('button', { name: 'Activate account' }));

    // Assert
    await waitFor(() => {
      expect(seen).toHaveLength(1);
    });
    expect(JSON.parse(seen[0]?.body ?? '')).toEqual({ token: 'raced', password: PASSWORD });
  });
});

describe('the same link opened again in the same tab', () => {
  it('shows the form again, with the token read and scrubbed again', async () => {
    // Arrange
    server.use(
      http.post('/api/v1/auth/activate', () => apiError(400, 'INVALID_LINK', 'invalid link')),
    );
    const { user } = open('/activate');
    await typePasswords(user);
    await user.click(screen.getByRole('button', { name: 'Activate account' }));
    await screen.findByText('This link is invalid or has expired.');

    // Act
    window.history.replaceState(null, '', `/activate#token=${TOKEN}`);
    window.dispatchEvent(new HashChangeEvent('hashchange'));

    // Assert
    expect(await screen.findByLabelText('New password')).toBeInTheDocument();
    expect(window.location.hash).toBe('');
  });
});

describe('help for a dead link', () => {
  it('tells an invited member to ask the association to resend the activation link', async () => {
    // Arrange / Act
    open('/activate', false);

    // Assert
    expect(await screen.findByText(/ask your association to resend it/i)).toBeInTheDocument();
  });

  it('offers a new password link when the reset link is dead', async () => {
    // Arrange / Act
    open('/reset-password', false);

    // Assert
    expect(await screen.findByRole('link', { name: 'Request a new link' })).toHaveAttribute(
      'href',
      '/forgot-password',
    );
  });
});

describe('forgot password', () => {
  const CONFIRMATION =
    'If an account exists for this email, we have sent a link to reset the password.';

  it('always shows the same confirmation', async () => {
    // Arrange
    const { seen, record } = recorder();
    server.use(
      refreshRejected,
      http.post('/api/v1/auth/password-reset-requests', async ({ request }) => {
        await record(request);
        return HttpResponse.json({ status: 'RECEIVED' }, { status: 202 });
      }),
    );
    const { user } = renderApp('/forgot-password');
    await user.type(await screen.findByLabelText('Email'), 'nobody@example.com');

    // Act
    await user.click(screen.getByRole('button', { name: 'Send reset link' }));

    // Assert
    expect(await screen.findByRole('heading', { name: 'Check your email' })).toHaveFocus();
    expect(screen.getByText(CONFIRMATION)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to sign in' })).toHaveAttribute('href', '/login');
    expect(JSON.parse(seen[0]?.body ?? '')).toEqual({ email: 'nobody@example.com' });
    expect(seen[0]?.authorization).toBeNull();
  });

  it('asks for a valid email before sending anything', async () => {
    // Arrange
    const { seen, record } = recorder();
    server.use(
      refreshRejected,
      http.post('/api/v1/auth/password-reset-requests', async ({ request }) => {
        await record(request);
        return HttpResponse.json({ status: 'RECEIVED' }, { status: 202 });
      }),
    );
    const { user } = renderApp('/forgot-password');
    await user.type(await screen.findByLabelText('Email'), 'not-an-email');

    // Act
    await user.click(screen.getByRole('button', { name: 'Send reset link' }));

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Email: Enter a valid email address',
    );
    expect(seen).toEqual([]);
  });

  it("shows the API's message for an email it refuses, which says nothing about accounts", async () => {
    // Arrange
    server.use(
      refreshRejected,
      http.post('/api/v1/auth/password-reset-requests', () =>
        apiError(422, 'INVALID_FIELD', 'The email address is not valid', { fields: ['email'] }),
      ),
    );
    const { user } = renderApp('/forgot-password');
    await user.type(await screen.findByLabelText('Email'), 'a@b');

    // Act
    await user.click(screen.getByRole('button', { name: 'Send reset link' }));

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Email: The email address is not valid',
    );
  });

  it('tells the user how long to wait when rate limited', async () => {
    // Arrange
    server.use(
      refreshRejected,
      http.post('/api/v1/auth/password-reset-requests', () => rateLimited(1800)),
    );
    const { user } = renderApp('/forgot-password');
    await user.type(await screen.findByLabelText('Email'), 'ana@example.com');

    // Act
    await user.click(screen.getByRole('button', { name: 'Send reset link' }));

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Too many attempts. Please wait 30 minutes and try again.',
    );
    expect(screen.queryByText(CONFIRMATION)).not.toBeInTheDocument();
  });

  it('is for visitors only', async () => {
    // Arrange
    server.use(
      http.post('/api/v1/auth/refresh', () =>
        HttpResponse.json({ accessToken: 'tok', tokenType: 'Bearer', expiresIn: 600 }),
      ),
      http.get('/api/v1/sessions', () => HttpResponse.json([])),
    );

    // Act
    const { router } = renderApp('/forgot-password');

    // Assert
    expect(await screen.findByRole('heading', { level: 1, name: 'Sessions' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/');
  });
});
