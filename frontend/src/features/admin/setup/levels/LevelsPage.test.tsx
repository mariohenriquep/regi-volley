import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { BEGINNER_ID, INTERMEDIATE_ID, LEVELS_URL, meAs, signedIn } from '@/test/fixtures';
import { apiError, recorder } from '@/test/handlers';
import { renderApp } from '@/test/render';
import { server } from '@/test/server';

const ADVANCED_ID = 'd0000000-0000-0000-0000-000000000003';

interface Level {
  id: string;
  name: string;
}

/** The association's levels as the API keeps them: every operation answers with the levels after it. */
function serveLevels(
  initial: Level[] = [
    { id: BEGINNER_ID, name: 'Beginner' },
    { id: INTERMEDIATE_ID, name: 'Intermediate' },
    { id: ADVANCED_ID, name: 'Advanced' },
  ],
) {
  const state = { levels: initial, entry: BEGINNER_ID };
  const { seen, record } = recorder();
  const answer = () => ({
    entryLevelId: state.entry,
    levels: state.levels.map((level, rank) => ({ ...level, rank })),
  });
  server.use(
    signedIn,
    meAs('MEMBER', 'ADMIN'),
    http.get(LEVELS_URL, () => HttpResponse.json(answer())),
    http.post(LEVELS_URL, async ({ request }) => {
      await record(request);
      const body = (await request.json()) as { name: string };
      state.levels = [
        ...state.levels,
        { id: 'd0000000-0000-0000-0000-0000000000ff', name: body.name },
      ];
      return HttpResponse.json(answer(), { status: 201 });
    }),
    http.put(`${LEVELS_URL}/order`, async ({ request }) => {
      await record(request);
      const body = (await request.json()) as { levelIds: string[] };
      state.levels = body.levelIds.map(
        (id) => state.levels.find((level) => level.id === id) as Level,
      );
      return HttpResponse.json(answer());
    }),
    http.put(`${LEVELS_URL}/entry-level`, async ({ request }) => {
      await record(request);
      state.entry = ((await request.json()) as { levelId: string }).levelId;
      return HttpResponse.json(answer());
    }),
    http.put(`${LEVELS_URL}/:levelId`, async ({ request, params }) => {
      await record(request);
      const body = (await request.json()) as { name: string };
      state.levels = state.levels.map((level) =>
        level.id === params.levelId ? { ...level, name: body.name } : level,
      );
      return HttpResponse.json(answer());
    }),
  );
  return { state, seen };
}

async function openLevels(initial?: Level[]) {
  const served = serveLevels(initial);
  const rendered = renderApp('/admin/setup/levels');
  await screen.findByRole('list', { name: 'Levels' });
  return { ...rendered, ...served };
}

const names = () =>
  within(screen.getByRole('list', { name: 'Levels' }))
    .getAllByRole('listitem')
    .map((item) => item.querySelector('[data-level-name]')?.textContent);

describe('the levels screen', () => {
  it('lists the levels lowest first, marks the entry level and explains who can book what', async () => {
    // Arrange / Act
    await openLevels();

    // Assert
    expect(screen.getByRole('heading', { level: 1, name: 'Levels' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to Admin' })).toHaveAttribute('href', '/admin');
    expect(names()).toEqual(['Beginner', 'Intermediate', 'Advanced']);
    const items = within(screen.getByRole('list', { name: 'Levels' })).getAllByRole('listitem');
    expect(items[0]).toHaveTextContent('Entry level');
    expect(items[1]).not.toHaveTextContent('Entry level');
    expect(
      screen.getByText(/book sessions of groups that accept their own level or a lower one/i),
    ).toBeInTheDocument();
  });

  it('adds a level as the highest one and says so', async () => {
    // Arrange
    const { user, seen } = await openLevels();

    // Act
    await user.type(screen.getByLabelText('New level name'), 'Expert');
    await user.click(screen.getByRole('button', { name: 'Add level' }));

    // Assert
    expect(await screen.findByRole('status')).toHaveTextContent('Expert was added');
    await waitFor(() => {
      expect(names()).toEqual(['Beginner', 'Intermediate', 'Advanced', 'Expert']);
    });
    expect(seen).toHaveLength(1);
    expect(JSON.parse(seen[0]?.body ?? '')).toEqual({ name: 'Expert' });
    expect(screen.getByLabelText('New level name')).toHaveValue('');
  });

  it('refuses a blank name without asking the API', async () => {
    // Arrange
    const { user, seen } = await openLevels();

    // Act
    await user.type(screen.getByLabelText('New level name'), '   ');
    await user.click(screen.getByRole('button', { name: 'Add level' }));

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent('Enter a name for the level');
    expect(seen).toEqual([]);
  });

  it('shows the API refusal of a duplicate name, which names no field', async () => {
    // Arrange
    const { user } = await openLevels();
    server.use(
      http.post(LEVELS_URL, () =>
        apiError(
          422,
          'BUSINESS_RULE_VIOLATION',
          "The association already has a level named 'Beginner'",
        ),
      ),
    );

    // Act
    await user.type(screen.getByLabelText('New level name'), 'beginner');
    await user.click(screen.getByRole('button', { name: 'Add level' }));

    // Assert
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent("The association already has a level named 'Beginner'");
    expect(alert).toHaveTextContent('Reference: req-422');
    expect(screen.getByLabelText('New level name')).toHaveValue('beginner');
  });

  it('renames a level in a dialog', async () => {
    // Arrange
    const { user, seen } = await openLevels();

    // Act
    await user.click(screen.getByRole('button', { name: 'Rename Intermediate' }));
    const dialog = await screen.findByRole('dialog', { name: 'Rename Intermediate' });
    const field = within(dialog).getByLabelText('Level name');
    expect(field).toHaveValue('Intermediate');
    await user.clear(field);
    await user.type(field, 'Club');
    await user.click(within(dialog).getByRole('button', { name: 'Save name' }));

    // Assert
    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
    expect(names()).toEqual(['Beginner', 'Club', 'Advanced']);
    expect(seen[0]).toMatchObject({ method: 'PUT', path: `${LEVELS_URL}/${INTERMEDIATE_ID}` });
    expect(JSON.parse(seen[0]?.body ?? '')).toEqual({ name: 'Club' });
    expect(screen.getByRole('status')).toHaveTextContent('Intermediate is now called Club');
  });

  it('shows the API message on the name field when the rename is refused', async () => {
    // Arrange
    const { user } = await openLevels();
    server.use(
      http.put(`${LEVELS_URL}/${BEGINNER_ID}`, () =>
        apiError(422, 'INVALID_FIELD', 'A level needs a name', { fields: ['name'] }),
      ),
    );

    // Act
    await user.click(screen.getByRole('button', { name: 'Rename Beginner' }));
    const dialog = await screen.findByRole('dialog', { name: 'Rename Beginner' });
    await user.type(within(dialog).getByLabelText('Level name'), '!');
    await user.click(within(dialog).getByRole('button', { name: 'Save name' }));

    // Assert
    expect(await within(dialog).findByLabelText('Level name')).toHaveAccessibleDescription(
      'A level needs a name',
    );
    expect(within(dialog).getByLabelText('Level name')).toBeInvalid();
  });

  it('moves a level up and down with buttons, sending the whole order', async () => {
    // Arrange
    const { user, seen } = await openLevels();

    // Act
    await user.click(screen.getByRole('button', { name: 'Move Advanced up' }));

    // Assert
    await waitFor(() => {
      expect(names()).toEqual(['Beginner', 'Advanced', 'Intermediate']);
    });
    expect(seen[0]).toMatchObject({ method: 'PUT', path: `${LEVELS_URL}/order` });
    expect(JSON.parse(seen[0]?.body ?? '')).toEqual({
      levelIds: [BEGINNER_ID, ADVANCED_ID, INTERMEDIATE_ID],
    });
    // The keyboard user stays on the control they were using, which is now on the moved level.
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Move Advanced up' })).toHaveFocus();
    });
    expect(screen.getByText('Advanced moved up: now 2 of 3.')).toBeInTheDocument();

    // Act
    await user.click(screen.getByRole('button', { name: 'Move Beginner down' }));

    // Assert
    await waitFor(() => {
      expect(names()).toEqual(['Advanced', 'Beginner', 'Intermediate']);
    });
  });

  it('cannot move the first level up or the last one down', async () => {
    // Arrange / Act
    await openLevels();

    // Assert
    expect(screen.getByRole('button', { name: 'Move Beginner up' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Move Advanced down' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Move Beginner down' })).toBeEnabled();
  });

  it('changes the entry level and says who starts there', async () => {
    // Arrange
    const { user, seen } = await openLevels();
    expect(screen.queryByRole('button', { name: /Make Beginner/ })).not.toBeInTheDocument();

    // Act
    await user.click(screen.getByRole('button', { name: 'Make entry level for Intermediate' }));

    // Assert
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Intermediate is now the entry level. New members start there.',
    );
    expect(seen[0]).toMatchObject({ method: 'PUT', path: `${LEVELS_URL}/entry-level` });
    expect(JSON.parse(seen[0]?.body ?? '')).toEqual({ levelId: INTERMEDIATE_ID });
    await waitFor(() => {
      const items = within(screen.getByRole('list', { name: 'Levels' })).getAllByRole('listitem');
      expect(items[1]).toHaveTextContent('Entry level');
      expect(items[0]).not.toHaveTextContent('Entry level');
    });
  });

  it('shows the API refusal of a reorder and reads the list again', async () => {
    // Arrange
    const { user } = await openLevels();
    server.use(
      http.put(`${LEVELS_URL}/order`, () =>
        apiError(409, 'CONFLICT', 'The data was changed by someone else. Reload and try again.'),
      ),
    );

    // Act
    await user.click(screen.getByRole('button', { name: 'Move Intermediate up' }));

    // Assert
    expect(await screen.findByRole('alert')).toHaveTextContent('changed by someone else');
    expect(names()).toEqual(['Beginner', 'Intermediate', 'Advanced']);
  });

  it('tells a member who is not an administrator that it is for administrators', async () => {
    // Arrange
    server.use(
      signedIn,
      http.get(LEVELS_URL, () => apiError(403, 'FORBIDDEN', 'Access denied')),
    );

    // Act
    renderApp('/admin/setup/levels');

    // Assert
    expect(await screen.findByText('Administration is for administrators.')).toBeInTheDocument();
  });

  it('shows the error with Try again when the levels cannot be read', async () => {
    // Arrange
    let fail = true;
    server.use(
      signedIn,
      http.get(LEVELS_URL, () =>
        fail
          ? apiError(500, 'INTERNAL_ERROR', 'boom')
          : HttpResponse.json({
              entryLevelId: BEGINNER_ID,
              levels: [{ id: BEGINNER_ID, name: 'Beginner', rank: 0 }],
            }),
      ),
    );
    const { user } = renderApp('/admin/setup/levels');
    await screen.findByText('Something went wrong. Please try again.');

    // Act
    fail = false;
    await user.click(screen.getByRole('button', { name: 'Try again' }));

    // Assert
    expect(await screen.findByRole('list', { name: 'Levels' })).toBeInTheDocument();
  });
});
