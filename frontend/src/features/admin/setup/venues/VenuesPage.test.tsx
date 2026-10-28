import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { OTHER_VENUE_ID, VENUE_ID, VENUES_URL, meAs, signedIn, venueEntry } from '@/test/fixtures';
import { apiError, recorder } from '@/test/handlers';
import { renderApp } from '@/test/render';
import { server } from '@/test/server';

type Entry = ReturnType<typeof venueEntry>;

const second = () =>
  venueEntry({
    id: OTHER_VENUE_ID,
    name: 'Escola Norte',
    address: 'Av. Norte 5, Lisbon',
    courts: 1,
  });

/** The venues as the API keeps them, with the three operations. */
function serveVenues(initial: Entry[] = [venueEntry(), second()]) {
  const state = { venues: initial };
  const { seen, record } = recorder();
  server.use(
    signedIn,
    meAs('MEMBER', 'ADMIN'),
    http.get(VENUES_URL, () => HttpResponse.json(state.venues)),
    http.post(VENUES_URL, async ({ request }) => {
      await record(request);
      const body = (await request.json()) as Omit<Entry, 'id' | 'inUseByActiveGroup'>;
      const created = venueEntry({ ...body, id: 'e0000000-0000-0000-0000-0000000000ff' });
      state.venues = [...state.venues, created];
      return HttpResponse.json(
        { id: created.id, name: body.name, address: body.address, courts: body.courts },
        { status: 201 },
      );
    }),
    http.put(`${VENUES_URL}/:venueId`, async ({ request, params }) => {
      await record(request);
      const body = (await request.json()) as Omit<Entry, 'id' | 'inUseByActiveGroup'>;
      state.venues = state.venues.map((venue) =>
        venue.id === params.venueId ? { ...venue, ...body } : venue,
      );
      return HttpResponse.json({ id: params.venueId, ...body });
    }),
    http.delete(`${VENUES_URL}/:venueId`, async ({ request, params }) => {
      await record(request);
      state.venues = state.venues.filter((venue) => venue.id !== params.venueId);
      return new HttpResponse(null, { status: 204 });
    }),
  );
  return { state, seen };
}

async function openVenues(initial?: Entry[]) {
  const served = serveVenues(initial);
  const rendered = renderApp('/admin/setup/venues');
  await screen.findByRole('list', { name: 'Venues' });
  return { ...rendered, ...served };
}

const venueCard = (name: string) => screen.getByRole('article', { name });

describe('the venues screen', () => {
  it('lists name, address and courts, with the way back', async () => {
    // Arrange / Act
    await openVenues();

    // Assert
    expect(screen.getByRole('heading', { level: 1, name: 'Venues' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to Admin' })).toHaveAttribute('href', '/admin');
    const central = venueCard('Pavilhão Central');
    expect(central).toHaveTextContent('Rua da Bola 1, Lisbon');
    expect(central).toHaveTextContent('2 courts');
    expect(venueCard('Escola Norte')).toHaveTextContent('1 court');
    expect(venueCard('Escola Norte')).not.toHaveTextContent('1 courts');
  });

  it('says so when there are no venues', async () => {
    // Arrange
    server.use(
      signedIn,
      meAs('MEMBER', 'ADMIN'),
      http.get(VENUES_URL, () => HttpResponse.json([])),
    );

    // Act
    renderApp('/admin/setup/venues');

    // Assert
    expect(await screen.findByText('No venues yet')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add venue' })).toBeInTheDocument();
  });

  it('adds a venue', async () => {
    // Arrange
    const { user, seen } = await openVenues();

    // Act
    await user.click(screen.getByRole('button', { name: 'Add venue' }));
    const dialog = await screen.findByRole('dialog', { name: 'Add a venue' });
    await user.type(within(dialog).getByLabelText('Name'), 'Pavilhão Sul');
    await user.type(within(dialog).getByLabelText('Address'), 'Rua Sul 9, Lisbon');
    await user.type(within(dialog).getByLabelText('Courts'), '3');
    await user.click(within(dialog).getByRole('button', { name: 'Add venue' }));

    // Assert
    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
    expect(JSON.parse(seen[0]?.body ?? '')).toEqual({
      name: 'Pavilhão Sul',
      address: 'Rua Sul 9, Lisbon',
      courts: 3,
    });
    expect(await screen.findByRole('article', { name: 'Pavilhão Sul' })).toHaveTextContent(
      '3 courts',
    );
    expect(screen.getByRole('status')).toHaveTextContent('Pavilhão Sul was added');
  });

  it.each([
    ['', 'Enter the number of courts'],
    ['0', 'Enter a whole number from 1 to 1000.'],
    ['1.5', 'Enter a whole number from 1 to 1000.'],
    ['1001', 'Enter a whole number from 1 to 1000.'],
  ])('refuses %j courts before asking the API', async (courts, message) => {
    // Arrange
    const { user, seen } = await openVenues();
    await user.click(screen.getByRole('button', { name: 'Add venue' }));
    const dialog = await screen.findByRole('dialog', { name: 'Add a venue' });
    await user.type(within(dialog).getByLabelText('Name'), 'Pavilhão Sul');
    await user.type(within(dialog).getByLabelText('Address'), 'Rua Sul 9, Lisbon');
    if (courts) await user.type(within(dialog).getByLabelText('Courts'), courts);

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Add venue' }));

    // Assert
    expect(within(dialog).getByLabelText('Courts')).toHaveAccessibleDescription(message);
    expect(within(dialog).getByRole('alert')).toHaveFocus();
    expect(seen).toEqual([]);
  });

  it('refuses a blank name and address before asking the API', async () => {
    // Arrange
    const { user, seen } = await openVenues();
    await user.click(screen.getByRole('button', { name: 'Add venue' }));
    const dialog = await screen.findByRole('dialog', { name: 'Add a venue' });

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Add venue' }));

    // Assert
    expect(within(dialog).getByLabelText('Name')).toHaveAccessibleDescription(
      'Enter the venue name',
    );
    expect(within(dialog).getByLabelText('Address')).toHaveAccessibleDescription(
      'Enter the address',
    );
    expect(seen).toEqual([]);
  });

  it('puts the API message on the field it names', async () => {
    // Arrange
    const { user } = await openVenues();
    server.use(
      http.post(VENUES_URL, () =>
        apiError(422, 'INVALID_FIELD', 'The venue name is too long', { fields: ['venue name'] }),
      ),
    );
    await user.click(screen.getByRole('button', { name: 'Add venue' }));
    const dialog = await screen.findByRole('dialog', { name: 'Add a venue' });
    await user.type(within(dialog).getByLabelText('Name'), 'x');
    await user.type(within(dialog).getByLabelText('Address'), 'y');
    await user.type(within(dialog).getByLabelText('Courts'), '2');

    // Act
    await user.click(within(dialog).getByRole('button', { name: 'Add venue' }));

    // Assert
    await waitFor(() => {
      expect(within(dialog).getByLabelText('Name')).toHaveAccessibleDescription(
        'The venue name is too long',
      );
    });
    expect(within(dialog).getByRole('alert')).toHaveTextContent('Reference: req-422');
  });

  it('edits a venue, starting from what it says now', async () => {
    // Arrange
    const { user, seen } = await openVenues();

    // Act
    await user.click(within(venueCard('Escola Norte')).getByRole('button', { name: /^Edit/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit Escola Norte' });
    expect(within(dialog).getByLabelText('Courts')).toHaveValue(1);
    const courts = within(dialog).getByLabelText('Courts');
    await user.clear(courts);
    await user.type(courts, '4');
    await user.click(within(dialog).getByRole('button', { name: 'Save venue' }));

    // Assert
    await waitFor(() => {
      expect(venueCard('Escola Norte')).toHaveTextContent('4 courts');
    });
    expect(seen[0]).toMatchObject({ method: 'PUT', path: `${VENUES_URL}/${OTHER_VENUE_ID}` });
    expect(JSON.parse(seen[0]?.body ?? '')).toEqual({
      name: 'Escola Norte',
      address: 'Av. Norte 5, Lisbon',
      courts: 4,
    });
    expect(screen.getByRole('status')).toHaveTextContent('Escola Norte was saved');
  });

  it('deletes a venue that no group uses, after a question', async () => {
    // Arrange
    const { user, seen } = await openVenues();

    // Act
    await user.click(within(venueCard('Escola Norte')).getByRole('button', { name: /^Delete/ }));
    const dialog = await screen.findByRole('alertdialog', { name: 'Delete Escola Norte?' });
    expect(seen).toEqual([]);
    await user.click(within(dialog).getByRole('button', { name: 'Delete venue' }));

    // Assert
    await waitFor(() => {
      expect(screen.queryByRole('article', { name: 'Escola Norte' })).not.toBeInTheDocument();
    });
    expect(seen[0]).toMatchObject({ method: 'DELETE', path: `${VENUES_URL}/${OTHER_VENUE_ID}` });
    expect(screen.getByRole('status')).toHaveTextContent('Escola Norte was deleted');
  });

  it('keeps the venue when the question is not confirmed', async () => {
    // Arrange
    const { user, seen } = await openVenues();

    // Act
    await user.click(within(venueCard('Escola Norte')).getByRole('button', { name: /^Delete/ }));
    const dialog = await screen.findByRole('alertdialog');
    await user.click(within(dialog).getByRole('button', { name: 'Not now' }));

    // Assert
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(seen).toEqual([]);
    expect(venueCard('Escola Norte')).toBeInTheDocument();
  });

  it('disables Delete, with the reason, for a venue an active group uses', async () => {
    // Arrange / Act
    const { seen } = await openVenues([venueEntry({ inUseByActiveGroup: true })]);

    // Assert
    const card = venueCard('Pavilhão Central');
    const remove = within(card).getByRole('button', { name: /^Delete/ });
    expect(remove).toBeDisabled();
    expect(remove).toHaveAccessibleDescription(
      'An active training group uses this venue. Archive the group to be able to delete it.',
    );
    expect(seen).toEqual([]);
  });

  it('shows the API refusal in the question when the venue turned out to be in use', async () => {
    // Arrange
    const { user } = await openVenues();
    server.use(
      http.delete(`${VENUES_URL}/${OTHER_VENUE_ID}`, () =>
        apiError(
          422,
          'BUSINESS_RULE_VIOLATION',
          'The venue is used by an active training group and cannot be deleted',
        ),
      ),
    );

    // Act
    await user.click(within(venueCard('Escola Norte')).getByRole('button', { name: /^Delete/ }));
    const dialog = await screen.findByRole('alertdialog');
    await user.click(within(dialog).getByRole('button', { name: 'Delete venue' }));

    // Assert
    expect(await within(dialog).findByText(/used by an active training group/)).toBeInTheDocument();
    expect(within(dialog).getByText('Reference: req-422')).toBeInTheDocument();
    // The page behind the question is hidden from the accessibility tree while it is open.
    expect(screen.getByRole('article', { name: 'Escola Norte', hidden: true })).toBeInTheDocument();
  });

  it('tells a member who is not an administrator that it is for administrators', async () => {
    // Arrange
    server.use(
      signedIn,
      http.get(VENUES_URL, () => apiError(403, 'FORBIDDEN', 'Access denied')),
    );

    // Act
    renderApp('/admin/setup/venues');

    // Assert
    expect(await screen.findByText('Administration is for administrators.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add venue' })).not.toBeInTheDocument();
  });

  it('uses the venue id from the card, never anything else, in the delete path', async () => {
    // Arrange
    const { user, seen } = await openVenues([venueEntry()]);

    // Act
    await user.click(screen.getByRole('button', { name: /^Delete/ }));
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Delete venue' }),
    );

    // Assert
    await waitFor(() => {
      expect(seen[0]?.path).toBe(`${VENUES_URL}/${VENUE_ID}`);
    });
  });
});
