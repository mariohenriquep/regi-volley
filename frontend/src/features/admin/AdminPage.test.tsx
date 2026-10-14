import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { meAs, signedIn } from '@/test/fixtures';
import { apiError, me, recorder, refreshRejected } from '@/test/handlers';
import { renderApp } from '@/test/render';
import { server } from '@/test/server';

const sections = () =>
  within(screen.getByRole('navigation', { name: 'Sections' }))
    .getAllByRole('link')
    .map((link) => link.textContent);

describe('the Admin section in the navigation', () => {
  it('is offered to an administrator, from the roles in /me, after the other sections', async () => {
    // Arrange
    server.use(
      signedIn,
      meAs('MEMBER', 'ADMIN'),
      http.get('/api/v1/sessions', () => HttpResponse.json([])),
    );

    // Act
    renderApp('/');
    await screen.findByText('No sessions this week');

    // Assert
    const bar = await screen.findByRole('navigation', { name: 'Sections' });
    expect(within(bar).getByRole('link', { name: 'Admin' })).toHaveAttribute('href', '/admin');
    expect(sections()).toEqual(['Sessions', 'My plan', 'History', 'Coaching', 'Admin']);
  });

  it('is not offered to a coach, who keeps Coaching', async () => {
    // Arrange
    server.use(
      signedIn,
      meAs('MEMBER', 'COACH'),
      http.get('/api/v1/sessions', () => HttpResponse.json([])),
    );

    // Act
    renderApp('/');
    await screen.findByText('No sessions this week');

    // Assert
    await screen.findByRole('navigation', { name: 'Sections' });
    expect(sections()).toEqual(['Sessions', 'My plan', 'History', 'Coaching']);
  });

  it('is not offered to a plain member', async () => {
    // Arrange: the default /me is a plain member
    server.use(
      signedIn,
      http.get('/api/v1/sessions', () => HttpResponse.json([])),
    );

    // Act
    renderApp('/');
    await screen.findByText('No sessions this week');

    // Assert
    await screen.findByRole('navigation', { name: 'Sections' });
    expect(sections()).toEqual(['Sessions', 'My plan', 'History']);
  });

  it('is dropped when the role is revoked, as soon as the page is in front again', async () => {
    // Arrange
    let roles = ['MEMBER', 'ADMIN'];
    server.use(
      signedIn,
      http.get('/api/v1/sessions', () => HttpResponse.json([])),
      http.get('/api/v1/me', () => HttpResponse.json({ ...me, roles })),
    );
    renderApp('/');
    await screen.findByRole('link', { name: 'Admin' });

    // Act
    roles = ['MEMBER'];
    window.dispatchEvent(new Event('visibilitychange'));

    // Assert
    await waitFor(() => {
      expect(screen.queryByRole('link', { name: 'Admin' })).not.toBeInTheDocument();
    });
  });
});

describe('the Admin home', () => {
  it('links the screens of the section, without asking the API for anything but /me', async () => {
    // Arrange
    const { seen, record } = recorder();
    server.use(
      signedIn,
      meAs('MEMBER', 'ADMIN'),
      http.all('/api/v1/*', async ({ request }) => {
        await record(request);
        return apiError(500, 'INTERNAL_ERROR', 'unexpected');
      }),
    );

    // Act
    renderApp('/admin');

    // Assert
    expect(await screen.findByRole('heading', { level: 1, name: 'Admin' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /^Join requests/ })).toHaveAttribute(
      'href',
      '/admin/join-requests',
    );
    expect(screen.getByRole('link', { name: /^Members/ })).toHaveAttribute(
      'href',
      '/admin/members',
    );
    expect(seen.filter(({ path }) => path !== '/api/v1/me')).toEqual([]);
  });

  it('tells a member who types the address that it is for administrators', async () => {
    // Arrange: a plain member
    server.use(signedIn);

    // Act
    renderApp('/admin');

    // Assert
    expect(await screen.findByText('Administration is for administrators.')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /^Members/ })).not.toBeInTheDocument();
  });

  it('is behind the sign-in', async () => {
    // Arrange: no session
    server.use(refreshRejected);

    // Act
    const { router } = renderApp('/admin');

    // Assert
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/login');
    });
  });
});
