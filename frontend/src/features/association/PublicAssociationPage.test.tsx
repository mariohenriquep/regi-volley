import { screen, within } from '@testing-library/react';
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

const page = (body: object = publicAssociation) =>
  http.get('/api/v1/public/associations/:shortName', () => HttpResponse.json(body));

describe('association public page', () => {
  it('shows the association, its levels, groups with their weekly schedule, and venues', async () => {
    // Arrange
    server.use(refreshRejected, page());

    // Act
    renderApp('/a/lisbon-spikers');

    // Assert
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Lisbon Spikers' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Lisbon')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'info@lisbonspikers.example' })).toHaveAttribute(
      'href',
      'mailto:info@lisbonspikers.example',
    );

    const levels = screen.getByRole('region', { name: 'Levels' });
    expect(
      within(levels)
        .getAllByRole('listitem')
        .map((item) => item.textContent),
    ).toEqual(['Beginner', 'Intermediate', 'Advanced']);
    expect(within(levels).getByText(/lowest first/i)).toBeInTheDocument();

    const groups = screen.getByRole('region', { name: 'Training groups' });
    expect(within(groups).getByRole('heading', { name: 'Tuesday evening' })).toBeInTheDocument();
    expect(within(groups).getByText('Tuesday 20:30, 1 h 30 min')).toBeInTheDocument();
    expect(within(groups).getByText('Thursday 21:00, 1 h')).toBeInTheDocument();
    expect(within(groups).getByText(/Pavilhão Central/)).toBeInTheDocument();
    expect(within(groups).getByText(/Beginner, Intermediate/)).toBeInTheDocument();
    expect(screen.getByText(/Times are Lisbon time/)).toBeInTheDocument();

    const venues = screen.getByRole('region', { name: 'Venues' });
    expect(within(venues).getByText('Rua da Bola 1, Lisbon')).toBeInTheDocument();
    expect(within(venues).getByText('2 courts')).toBeInTheDocument();
  });

  it('offers "Want to try it?" which leads to the join form', async () => {
    // Arrange
    server.use(refreshRejected, page());
    const { user, router } = renderApp('/a/lisbon-spikers');

    // Act
    await user.click(await screen.findByRole('link', { name: 'Want to try it?' }));

    // Assert: the join page's code loads on demand, so the router arrives a moment later
    expect(await screen.findByRole('heading', { name: 'Request to join' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/a/lisbon-spikers/join');
  });

  it('is open to a signed-in user too', async () => {
    // Arrange
    server.use(
      http.post('/api/v1/auth/refresh', () =>
        HttpResponse.json({ accessToken: 'tok', tokenType: 'Bearer', expiresIn: 600 }),
      ),
      page(),
    );

    // Act
    renderApp('/a/lisbon-spikers');

    // Assert
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Lisbon Spikers' }),
    ).toBeInTheDocument();
  });

  it('says so when a group has no schedule or the association has nothing to list yet', async () => {
    // Arrange
    server.use(refreshRejected, page({ ...publicAssociation, groups: [], venues: [] }));

    // Act
    renderApp('/a/lisbon-spikers');

    // Assert
    expect(await screen.findByText('No training groups yet.')).toBeInTheDocument();
    expect(screen.getByText('No venues yet.')).toBeInTheDocument();
  });

  it('shows a friendly page when the association is not found', async () => {
    // Arrange
    server.use(
      refreshRejected,
      http.get('/api/v1/public/associations/:shortName', () =>
        apiError(404, 'NOT_FOUND', 'Not found'),
      ),
    );

    // Act
    renderApp('/a/nobody');

    // Assert
    expect(
      await screen.findByRole('heading', { name: 'Association not found' }),
    ).toBeInTheDocument();
    expect(screen.getByText(/Check the address/)).toBeInTheDocument();
    expect(
      within(screen.getByRole('main')).getByRole('link', { name: 'Register an association' }),
    ).toHaveAttribute('href', '/register');
  });

  it('asks the API with the short name from the address and no credentials', async () => {
    // Arrange
    const { seen, record } = recorder();
    server.use(
      refreshRejected,
      http.get('/api/v1/public/associations/:shortName', async ({ request }) => {
        await record(request);
        return HttpResponse.json(publicAssociation);
      }),
    );

    // Act
    renderApp('/a/Lisbon-Spikers');
    await screen.findByRole('heading', { level: 1, name: 'Lisbon Spikers' });

    // Assert
    expect(seen).toEqual([
      expect.objectContaining({
        path: '/api/v1/public/associations/Lisbon-Spikers',
        authorization: null,
      }),
    ]);
  });

  it('asks the visitor to wait when rate limited', async () => {
    // Arrange
    server.use(
      refreshRejected,
      http.get('/api/v1/public/associations/:shortName', () => rateLimited(30)),
    );

    // Act
    renderApp('/a/lisbon-spikers');

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent('Please wait 30 seconds');
  });

  it('shows generic text for a server error', async () => {
    // Arrange
    server.use(
      refreshRejected,
      http.get('/api/v1/public/associations/:shortName', () =>
        apiError(500, 'INTERNAL_ERROR', 'boom'),
      ),
    );

    // Act
    renderApp('/a/lisbon-spikers');

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Something went wrong. Please try again.',
    );
  });

  it('renders text the association typed as text, never as markup', async () => {
    // Arrange
    server.use(
      refreshRejected,
      page({
        ...publicAssociation,
        name: '<img src=x onerror=alert(1)>Club',
        contactEmail: 'a@b.c?cc=evil@x.y',
      }),
    );

    // Act
    renderApp('/a/lisbon-spikers');

    // Assert
    expect(await screen.findByRole('heading', { level: 1 })).toHaveTextContent(
      '<img src=x onerror=alert(1)>Club',
    );
    expect(document.querySelector('img')).toBeNull();
    expect(screen.queryByRole('link', { name: /a@b\.c/ })).not.toBeInTheDocument();
    expect(screen.getByText('a@b.c?cc=evil@x.y')).toBeInTheDocument();
  });
});
