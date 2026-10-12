import { delay, http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { unwrap } from '@/api/errors';
import { me, recorder, refreshRejected, tokenResponse, unauthenticated } from '@/test/handlers';
import { server } from '@/test/server';
import { createAuthSession } from './session';

const newSession = () => createAuthSession({ baseUrl: window.location.origin });

/** GET /me succeeds only with the given token. */
const meAcceptingOnly = (token: string, record?: (request: Request) => Promise<void>) =>
  http.get('/api/v1/me', async ({ request }) => {
    await record?.(request);
    return request.headers.get('Authorization') === `Bearer ${token}`
      ? HttpResponse.json(me)
      : unauthenticated();
  });

describe('AuthSession.start', () => {
  it('restores the session from the refresh cookie on start', async () => {
    // Arrange
    const { seen, record } = recorder();
    server.use(
      http.post('/api/v1/auth/refresh', async ({ request }) => {
        await record(request);
        return tokenResponse('access-1');
      }),
    );
    const session = newSession();

    // Act
    await session.start();

    // Assert
    expect(session.getStatus()).toBe('authenticated');
    expect(seen).toEqual([
      expect.objectContaining({
        method: 'POST',
        path: '/api/v1/auth/refresh',
        contentType: 'application/json',
        authorization: null,
      }),
    ]);
  });

  it('is anonymous when there is no valid refresh cookie', async () => {
    // Arrange
    server.use(refreshRejected);
    const session = newSession();

    // Act
    await session.start();

    // Assert
    expect(session.getStatus()).toBe('anonymous');
  });

  it('is anonymous when the API cannot be reached on start', async () => {
    // Arrange
    server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.error()));
    const session = newSession();

    // Act
    await session.start();

    // Assert
    expect(session.getStatus()).toBe('anonymous');
  });

  it('starts as unknown and notifies subscribers of the outcome', async () => {
    // Arrange
    server.use(http.post('/api/v1/auth/refresh', () => tokenResponse('access-1')));
    const session = newSession();
    const statuses: string[] = [];
    session.subscribe(() => statuses.push(session.getStatus()));
    const before = session.getStatus();

    // Act
    await session.start();

    // Assert
    expect(before).toBe('unknown');
    expect(statuses).toEqual(['authenticated']);
  });
});

describe('AuthSession.login', () => {
  it('signs in and sends the access token as a bearer header afterwards', async () => {
    // Arrange
    const { seen, record } = recorder();
    server.use(
      http.post('/api/v1/auth/login', async ({ request }) => {
        await record(request);
        return tokenResponse('access-login');
      }),
      meAcceptingOnly('access-login'),
    );
    const session = newSession();

    // Act
    await session.login('ana@example.com', 'a long passphrase');
    const result = await unwrap(session.api.GET('/api/v1/me'));

    // Assert
    expect(session.getStatus()).toBe('authenticated');
    expect(result).toEqual(me);
    expect(JSON.parse(seen[0]?.body ?? '')).toEqual({
      email: 'ana@example.com',
      password: 'a long passphrase',
    });
  });

  it('throws the API error and stays anonymous on wrong credentials', async () => {
    // Arrange
    server.use(
      http.post('/api/v1/auth/login', () =>
        HttpResponse.json(
          { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password', requestId: 'r-1' },
          { status: 401 },
        ),
      ),
    );
    const session = newSession();

    // Act
    const attempt = session.login('ana@example.com', 'wrong password');

    // Assert
    await expect(attempt).rejects.toMatchObject({
      status: 401,
      code: 'INVALID_CREDENTIALS',
      message: 'Invalid email or password',
    });
    expect(session.getStatus()).not.toBe('authenticated');
  });

  it('never retries a failed login through the refresh flow', async () => {
    // Arrange
    const { seen, record } = recorder();
    server.use(
      http.post('/api/v1/auth/login', () => unauthenticated()),
      http.post('/api/v1/auth/refresh', async ({ request }) => {
        await record(request);
        return tokenResponse('never');
      }),
    );
    const session = newSession();

    // Act
    const attempt = session.login('ana@example.com', 'wrong password');

    // Assert
    await expect(attempt).rejects.toMatchObject({ status: 401 });
    expect(seen).toHaveLength(0);
  });
});

describe('AuthSession.api on 401', () => {
  it('refreshes once and retries the request with the new token', async () => {
    // Arrange
    server.use(
      http.post('/api/v1/auth/login', () => tokenResponse('expired')),
      http.post('/api/v1/auth/refresh', () => tokenResponse('fresh')),
      meAcceptingOnly('fresh'),
    );
    const session = newSession();
    await session.login('ana@example.com', 'a long passphrase');

    // Act
    const result = await unwrap(session.api.GET('/api/v1/me'));

    // Assert
    expect(result).toEqual(me);
    expect(session.getStatus()).toBe('authenticated');
  });

  it('retries a request at most once and signs out when the retry is refused too', async () => {
    // Arrange
    const { seen, record } = recorder();
    let refreshes = 0;
    server.use(
      http.post('/api/v1/auth/login', () => tokenResponse('expired')),
      http.post('/api/v1/auth/refresh', () => {
        refreshes += 1;
        return tokenResponse(`fresh-${String(refreshes)}`);
      }),
      meAcceptingOnly('never-valid', record),
    );
    const session = newSession();
    await session.login('ana@example.com', 'a long passphrase');

    // Act
    const attempt = unwrap(session.api.GET('/api/v1/me'));

    // Assert
    await expect(attempt).rejects.toMatchObject({ status: 401, code: 'UNAUTHENTICATED' });
    expect(refreshes).toBe(1);
    expect(seen.map((request) => request.authorization)).toEqual([
      'Bearer expired',
      'Bearer fresh-1',
    ]);
    expect(session.getStatus()).toBe('anonymous');
  });

  it('shares one refresh between concurrent requests that get a 401', async () => {
    // Arrange
    let refreshes = 0;
    server.use(
      http.post('/api/v1/auth/login', () => tokenResponse('expired')),
      http.post('/api/v1/auth/refresh', async () => {
        refreshes += 1;
        await delay(20);
        return tokenResponse('fresh');
      }),
      meAcceptingOnly('fresh'),
    );
    const session = newSession();
    await session.login('ana@example.com', 'a long passphrase');

    // Act
    const results = await Promise.all([
      unwrap(session.api.GET('/api/v1/me')),
      unwrap(session.api.GET('/api/v1/me')),
      unwrap(session.api.GET('/api/v1/me')),
    ]);

    // Assert
    expect(results).toEqual([me, me, me]);
    expect(refreshes).toBe(1);
  });

  it('does not refresh again for a 401 that arrives after another request already refreshed', async () => {
    // Arrange
    let refreshes = 0;
    let releaseSlow: () => void = () => undefined;
    const slowGate = new Promise<void>((resolve) => {
      releaseSlow = resolve;
    });
    server.use(
      http.post('/api/v1/auth/login', () => tokenResponse('expired')),
      http.post('/api/v1/auth/refresh', () => {
        refreshes += 1;
        return tokenResponse('fresh');
      }),
      http.get('/api/v1/me', async ({ request }) => {
        const token = request.headers.get('Authorization');
        if (token === 'Bearer fresh') return HttpResponse.json(me);
        if (new URL(request.url).searchParams.has('slow')) await slowGate;
        return unauthenticated();
      }),
    );
    const session = newSession();
    await session.login('ana@example.com', 'a long passphrase');
    // a request typed without query parameters: the test only needs a second, slower call to the same route
    const slowInit = { querySerializer: () => 'slow=1' };

    // Act
    const slow = unwrap(session.api.GET('/api/v1/me', slowInit));
    const fast = await unwrap(session.api.GET('/api/v1/me'));
    releaseSlow();
    const late = await slow;

    // Assert
    expect(fast).toEqual(me);
    expect(late).toEqual(me);
    expect(refreshes).toBe(1);
  });

  it('becomes anonymous and returns the 401 when the refresh is refused', async () => {
    // Arrange
    server.use(
      http.post('/api/v1/auth/login', () => tokenResponse('expired')),
      refreshRejected,
      meAcceptingOnly('fresh'),
    );
    const session = newSession();
    await session.login('ana@example.com', 'a long passphrase');

    // Act
    const attempt = unwrap(session.api.GET('/api/v1/me'));

    // Assert
    await expect(attempt).rejects.toMatchObject({ status: 401 });
    expect(session.getStatus()).toBe('anonymous');
  });

  it('stays signed in when the refresh fails for a transient reason', async () => {
    // Arrange
    server.use(
      http.post('/api/v1/auth/login', () => tokenResponse('expired')),
      http.post('/api/v1/auth/refresh', () =>
        HttpResponse.json(
          { code: 'SERVICE_BUSY', message: 'The service is busy, try again shortly' },
          { status: 503 },
        ),
      ),
      meAcceptingOnly('fresh'),
    );
    const session = newSession();
    await session.login('ana@example.com', 'a long passphrase');

    // Act
    const attempt = unwrap(session.api.GET('/api/v1/me'));

    // Assert
    await expect(attempt).rejects.toMatchObject({ status: 401 });
    expect(session.getStatus()).toBe('authenticated');
  });

  it('passes other errors through without refreshing', async () => {
    // Arrange
    const { seen, record } = recorder();
    server.use(
      http.post('/api/v1/auth/login', () => tokenResponse('access')),
      http.post('/api/v1/auth/refresh', async ({ request }) => {
        await record(request);
        return tokenResponse('fresh');
      }),
      http.get('/api/v1/me', () =>
        HttpResponse.json({ code: 'FORBIDDEN', message: 'Access is denied' }, { status: 403 }),
      ),
    );
    const session = newSession();
    await session.login('ana@example.com', 'a long passphrase');

    // Act
    const attempt = unwrap(session.api.GET('/api/v1/me'));

    // Assert
    await expect(attempt).rejects.toMatchObject({ status: 403, code: 'FORBIDDEN' });
    expect(seen).toHaveLength(0);
  });
});

describe('AuthSession.logout', () => {
  it('revokes the cookie session with a JSON request and forgets the access token', async () => {
    // Arrange
    const { seen, record } = recorder();
    const meCalls = recorder();
    server.use(
      http.post('/api/v1/auth/login', () => tokenResponse('access')),
      http.post('/api/v1/auth/logout', async ({ request }) => {
        await record(request);
        return new HttpResponse(null, { status: 204 });
      }),
      refreshRejected,
      meAcceptingOnly('access', meCalls.record),
    );
    const session = newSession();
    await session.login('ana@example.com', 'a long passphrase');

    // Act
    await session.logout();
    const afterwards = unwrap(session.api.GET('/api/v1/me'));

    // Assert
    expect(session.getStatus()).toBe('anonymous');
    expect(seen).toEqual([
      expect.objectContaining({
        method: 'POST',
        path: '/api/v1/auth/logout',
        contentType: 'application/json',
        authorization: null,
      }),
    ]);
    await expect(afterwards).rejects.toMatchObject({ status: 401 });
    expect(meCalls.seen[0]?.authorization).toBeNull();
  });

  it('signs out locally even when the server cannot be reached', async () => {
    // Arrange
    server.use(
      http.post('/api/v1/auth/login', () => tokenResponse('access')),
      http.post('/api/v1/auth/logout', () => HttpResponse.error()),
    );
    const session = newSession();
    await session.login('ana@example.com', 'a long passphrase');

    // Act
    const attempt = session.logout();

    // Assert
    await expect(attempt).rejects.toMatchObject({ code: 'NETWORK_ERROR' });
    expect(session.getStatus()).toBe('anonymous');
  });

  it('reports a logout the server refused, after signing out locally', async () => {
    // Arrange
    server.use(
      http.post('/api/v1/auth/login', () => tokenResponse('access')),
      http.post('/api/v1/auth/logout', () =>
        HttpResponse.json({ code: 'INTERNAL_ERROR', requestId: 'r-5' }, { status: 500 }),
      ),
    );
    const session = newSession();
    await session.login('ana@example.com', 'a long passphrase');

    // Act
    const attempt = session.logout();

    // Assert
    await expect(attempt).rejects.toMatchObject({ status: 500, requestId: 'r-5' });
    expect(session.getStatus()).toBe('anonymous');
  });

  it('stays signed in when signing out everywhere fails', async () => {
    // Arrange
    const meCalls = recorder();
    server.use(
      http.post('/api/v1/auth/login', () => tokenResponse('access')),
      http.post('/api/v1/auth/logout-all', () =>
        HttpResponse.json({ code: 'INTERNAL_ERROR', requestId: 'r-6' }, { status: 500 }),
      ),
      meAcceptingOnly('access', meCalls.record),
    );
    const session = newSession();
    await session.login('ana@example.com', 'a long passphrase');

    // Act
    const attempt = session.logoutAll();

    // Assert
    await expect(attempt).rejects.toMatchObject({ status: 500, requestId: 'r-6' });
    expect(session.getStatus()).toBe('authenticated');
    await unwrap(session.api.GET('/api/v1/me'));
    expect(meCalls.seen[0]?.authorization).toBe('Bearer access');
  });

  it('is not undone by a refresh that was in flight when the user signed out', async () => {
    // Arrange
    let releaseRefresh: () => void = () => undefined;
    const refreshGate = new Promise<void>((resolve) => {
      releaseRefresh = resolve;
    });
    const meCalls = recorder();
    server.use(
      http.post('/api/v1/auth/refresh', async () => {
        await refreshGate;
        return tokenResponse('late-token');
      }),
      http.post('/api/v1/auth/logout', () => new HttpResponse(null, { status: 204 })),
      http.get('/api/v1/me', async ({ request }) => {
        await meCalls.record(request);
        return unauthenticated();
      }),
    );
    const session = newSession();
    const starting = session.start();

    // Act
    await session.logout();
    releaseRefresh();
    await starting;

    // Assert
    expect(session.getStatus()).toBe('anonymous');
    server.use(refreshRejected);
    await expect(unwrap(session.api.GET('/api/v1/me'))).rejects.toMatchObject({ status: 401 });
    expect(meCalls.seen[0]?.authorization).toBeNull();
  });

  it('signs out everywhere with the bearer token', async () => {
    // Arrange
    const { seen, record } = recorder();
    server.use(
      http.post('/api/v1/auth/login', () => tokenResponse('access')),
      http.post('/api/v1/auth/logout-all', async ({ request }) => {
        await record(request);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const session = newSession();
    await session.login('ana@example.com', 'a long passphrase');

    // Act
    await session.logoutAll();

    // Assert
    expect(session.getStatus()).toBe('anonymous');
    expect(seen).toEqual([
      expect.objectContaining({ path: '/api/v1/auth/logout-all', authorization: 'Bearer access' }),
    ]);
  });
});

describe('AuthSession.api boundaries', () => {
  it('never sends the bearer token to another origin', async () => {
    // Arrange
    const { seen, record } = recorder();
    server.use(
      http.post('/api/v1/auth/login', () => tokenResponse('access')),
      http.get('https://evil.example/api/v1/me', async ({ request }) => {
        await record(request);
        return HttpResponse.json(me);
      }),
    );
    const session = newSession();
    await session.login('ana@example.com', 'a long passphrase');

    // Act
    const attempt = session.api.GET('/api/v1/me', { baseUrl: 'https://evil.example' });

    // Assert
    await expect(attempt).rejects.toThrow(/outside the API/);
    expect(seen).toHaveLength(0);
  });

  it('never sends the bearer token outside /api on our own origin', async () => {
    // Arrange
    const send = vi.fn<(request: Request) => Promise<Response>>(() =>
      Promise.resolve(Response.json(me)),
    );
    const session = createAuthSession({ baseUrl: `${window.location.origin}/static`, fetch: send });

    // Act
    const attempt = session.api.GET('/api/v1/me');

    // Assert
    await expect(attempt).rejects.toThrow(/outside the API/);
    expect(send).not.toHaveBeenCalled();
  });

  it('refuses to follow redirects on API requests', async () => {
    // Arrange
    const send = vi.fn<(request: Request) => Promise<Response>>(() =>
      Promise.resolve(Response.json(me)),
    );
    const session = createAuthSession({ baseUrl: window.location.origin, fetch: send });

    // Act
    await session.api.GET('/api/v1/me');
    await session.refresh();

    // Assert
    expect(send.mock.calls.map(([request]) => request.redirect)).toEqual(['error', 'error']);
  });
});

describe('AuthSession refresh across tabs', () => {
  /** A Web Locks stand-in shared by the "tabs": one holder at a time, in request order. */
  function fakeLocks() {
    let tail: Promise<unknown> = Promise.resolve();
    const names: string[] = [];
    const locks = {
      request: <T>(name: string, callback: () => Promise<T>): Promise<T> => {
        names.push(name);
        const run = tail.then(callback);
        tail = run.catch(() => undefined);
        return run;
      },
    };
    return { locks, names };
  }

  it('refreshes inside the shared rv-refresh lock', async () => {
    // Arrange
    const { locks, names } = fakeLocks();
    server.use(http.post('/api/v1/auth/refresh', () => tokenResponse('fresh')));
    const session = createAuthSession({ baseUrl: window.location.origin, locks });

    // Act
    await session.start();

    // Assert
    expect(names).toEqual(['rv-refresh']);
    expect(session.getStatus()).toBe('authenticated');
  });

  it('lets one tab refresh at a time, so the second presents the rotated cookie', async () => {
    // Arrange
    const { locks } = fakeLocks();
    let running = 0;
    let maxRunning = 0;
    let issued = 0;
    server.use(
      http.post('/api/v1/auth/refresh', async () => {
        running += 1;
        maxRunning = Math.max(maxRunning, running);
        await delay(10);
        running -= 1;
        issued += 1;
        return tokenResponse(`token-${String(issued)}`);
      }),
    );
    const firstTab = createAuthSession({ baseUrl: window.location.origin, locks });
    const secondTab = createAuthSession({ baseUrl: window.location.origin, locks });

    // Act
    await Promise.all([firstTab.start(), secondTab.start()]);

    // Assert
    expect(maxRunning).toBe(1);
    expect(firstTab.getStatus()).toBe('authenticated');
    expect(secondTab.getStatus()).toBe('authenticated');
  });

  it("uses the browser's navigator.locks by default", async () => {
    // Arrange
    const { locks, names } = fakeLocks();
    Object.defineProperty(navigator, 'locks', { value: locks, configurable: true });
    server.use(http.post('/api/v1/auth/refresh', () => tokenResponse('fresh')));
    const session = newSession();

    // Act
    try {
      await session.start();
    } finally {
      Reflect.deleteProperty(navigator, 'locks');
    }

    // Assert
    expect(names).toEqual(['rv-refresh']);
    expect(session.getStatus()).toBe('authenticated');
  });

  it('still refreshes where the Web Locks API is missing', async () => {
    // Arrange
    server.use(http.post('/api/v1/auth/refresh', () => tokenResponse('fresh')));
    const session = createAuthSession({ baseUrl: window.location.origin, locks: null });

    // Act
    await session.start();

    // Assert
    expect(session.getStatus()).toBe('authenticated');
  });
});
