import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import {
  levelsAnswer,
  MEMBER_ID,
  MEMBERS_URL,
  meAs,
  memberDetail,
  memberEntry,
  memberList,
  signedIn,
} from '@/test/fixtures';
import { apiError } from '@/test/handlers';
import { renderApp } from '@/test/render';
import { server } from '@/test/server';

type Entry = ReturnType<typeof memberEntry>;

/** The member list as the API answers it, remembering each query string asked for. */
function serveMembers(members: Entry[] = [memberEntry()], truncated = false) {
  const asked: URLSearchParams[] = [];
  server.use(
    signedIn,
    meAs('MEMBER', 'ADMIN'),
    http.get(MEMBERS_URL, ({ request }) => {
      asked.push(new URL(request.url).searchParams);
      return HttpResponse.json(memberList(members, truncated));
    }),
  );
  return asked;
}

const other = (overrides: Partial<Entry>) =>
  memberEntry({ memberId: 'c0000000-0000-0000-0000-000000000002', ...overrides });

describe('the member list', () => {
  it('shows name, level, roles, status and a link to the member', async () => {
    // Arrange
    serveMembers([memberEntry({ roles: ['MEMBER', 'COACH', 'ADMIN'], levelName: 'Intermediate' })]);

    // Act
    renderApp('/admin/members');

    // Assert
    expect(await screen.findByRole('heading', { level: 1, name: 'Members' })).toBeInTheDocument();
    const row = await screen.findByRole('article', { name: 'Ana Silva' });
    expect(row).toHaveTextContent('ana@example.com');
    expect(row).toHaveTextContent('Intermediate');
    expect(row).toHaveTextContent('Coach');
    expect(row).toHaveTextContent('Admin');
    expect(row).toHaveTextContent('Active');
    expect(within(row).getByRole('link', { name: 'Ana Silva' })).toHaveAttribute(
      'href',
      `/admin/members/${MEMBER_ID}`,
    );
    expect(row).not.toHaveTextContent('Not activated');
  });

  it('marks an account that was never activated, and an inactive member', async () => {
    // Arrange
    serveMembers([
      memberEntry({ accountActivated: false }),
      other({ name: 'Bruno Costa', status: 'INACTIVE' }),
    ]);

    // Act
    renderApp('/admin/members');

    // Assert
    expect(await screen.findByRole('article', { name: 'Ana Silva' })).toHaveTextContent(
      'Not activated',
    );
    const bruno = screen.getByRole('article', { name: 'Bruno Costa' });
    expect(bruno).toHaveTextContent('Inactive');
    expect(bruno).not.toHaveTextContent('Not activated');
  });

  it('claims nothing about activation when the API does not say', async () => {
    // Arrange
    serveMembers([memberEntry({ accountActivated: undefined })]);

    // Act
    renderApp('/admin/members');

    // Assert
    expect(await screen.findByRole('article', { name: 'Ana Silva' })).not.toHaveTextContent(
      'Not activated',
    );
  });

  it('asks for everybody first, without a filter', async () => {
    // Arrange
    const asked = serveMembers();

    // Act
    renderApp('/admin/members');
    await screen.findByRole('article', { name: 'Ana Silva' });

    // Assert
    expect(asked).toHaveLength(1);
    expect(asked[0]?.has('q')).toBe(false);
    expect(asked[0]?.has('status')).toBe(false);
  });

  it('says when the list was cut', async () => {
    // Arrange
    serveMembers([memberEntry()], true);

    // Act
    renderApp('/admin/members');

    // Assert
    expect(await screen.findByText(/Not every member is shown/)).toBeInTheDocument();
  });

  it('says so when there are no members at all', async () => {
    // Arrange
    serveMembers([]);

    // Act
    renderApp('/admin/members');

    // Assert
    expect(await screen.findByText('No members yet')).toBeInTheDocument();
  });

  it('shows the error with a retry, and the 403 of a non-administrator', async () => {
    // Arrange
    server.use(
      signedIn,
      meAs('MEMBER', 'ADMIN'),
      http.get(MEMBERS_URL, () => apiError(500, 'INTERNAL_ERROR', 'boom')),
    );

    // Act
    renderApp('/admin/members');

    // Assert
    expect(await screen.findByText('Something went wrong. Please try again.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });

  it('answers a member who is not an administrator with the 403 of the API', async () => {
    // Arrange
    server.use(
      signedIn,
      http.get(MEMBERS_URL, () => apiError(403, 'FORBIDDEN', 'Access denied')),
    );

    // Act
    renderApp('/admin/members');

    // Assert
    expect(await screen.findByText('Administration is for administrators.')).toBeInTheDocument();
  });
});

describe('opening a member', () => {
  it('comes back to the same search and filter', async () => {
    // Arrange
    server.use(
      http.get(`${MEMBERS_URL}/${MEMBER_ID}`, () => HttpResponse.json(memberDetail())),
      http.get('/api/v1/levels', () => HttpResponse.json(levelsAnswer())),
    );
    serveMembers();
    const { user, router } = renderApp('/admin/members?q=ana&status=ACTIVE');
    await user.click(await screen.findByRole('link', { name: 'Ana Silva' }));
    await screen.findByRole('heading', { level: 1, name: 'Ana Silva' });

    // Act
    await user.click(screen.getByRole('link', { name: 'Back to Members' }));

    // Assert
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/admin/members');
    });
    expect(router.state.location.search).toBe('?q=ana&status=ACTIVE');
    expect(await screen.findByLabelText('Search members')).toHaveValue('ana');
  });

  it('goes to the plain list when the member was opened from an address', async () => {
    // Arrange
    server.use(
      signedIn,
      meAs('MEMBER', 'ADMIN'),
      http.get(`${MEMBERS_URL}/${MEMBER_ID}`, () => HttpResponse.json(memberDetail())),
      http.get('/api/v1/levels', () => HttpResponse.json(levelsAnswer())),
    );
    renderApp(`/admin/members/${MEMBER_ID}`);

    // Act / Assert
    expect(await screen.findByRole('link', { name: 'Back to Members' })).toHaveAttribute(
      'href',
      '/admin/members',
    );
  });
});

describe('searching and filtering', () => {
  it('reads the search and the status from the address and asks the API for them', async () => {
    // Arrange
    const asked = serveMembers();

    // Act
    renderApp('/admin/members?q=ana&status=INACTIVE');
    await screen.findByRole('article', { name: 'Ana Silva' });

    // Assert
    expect(asked[0]?.get('q')).toBe('ana');
    expect(asked[0]?.get('status')).toBe('INACTIVE');
    expect(screen.getByLabelText('Search members')).toHaveValue('ana');
    expect(screen.getByLabelText('Status')).toHaveValue('INACTIVE');
  });

  it('writes what is typed to the address once typing pauses, and asks once for it', async () => {
    // Arrange
    const asked = serveMembers();
    const { user, router } = renderApp('/admin/members');
    await screen.findByRole('article', { name: 'Ana Silva' });

    // Act
    await user.type(screen.getByLabelText('Search members'), '  ana  ');

    // Assert
    await waitFor(() => {
      expect(router.state.location.search).toBe('?q=ana');
    });
    await waitFor(() => {
      expect(asked.at(-1)?.get('q')).toBe('ana');
    });
    // Not one request per key: the first answer plus the one for the finished text
    expect(asked.map((params) => params.get('q'))).toEqual([null, 'ana']);
  });

  it('does not add a history entry for each search', async () => {
    // Arrange
    serveMembers();
    const { user, router } = renderApp('/admin/members');
    await screen.findByRole('article', { name: 'Ana Silva' });

    // Act
    await user.type(screen.getByLabelText('Search members'), 'ana');
    await waitFor(() => {
      expect(router.state.location.search).toBe('?q=ana');
    });

    // Assert
    expect(router.state.historyAction).toBe('REPLACE');
  });

  it('filters by status through the address', async () => {
    // Arrange
    const asked = serveMembers();
    const { user, router } = renderApp('/admin/members');
    await screen.findByRole('article', { name: 'Ana Silva' });

    // Act
    await user.selectOptions(screen.getByLabelText('Status'), 'ACTIVE');

    // Assert
    await waitFor(() => {
      expect(asked.at(-1)?.get('status')).toBe('ACTIVE');
    });
    expect(router.state.location.search).toBe('?status=ACTIVE');
  });

  it('drops the filter from the address when "All" is chosen again', async () => {
    // Arrange
    const asked = serveMembers();
    const { user, router } = renderApp('/admin/members?status=INACTIVE');
    await screen.findByRole('article', { name: 'Ana Silva' });

    // Act
    await user.selectOptions(screen.getByLabelText('Status'), 'All');

    // Assert
    await waitFor(() => {
      expect(asked.at(-1)?.has('status')).toBe(false);
    });
    expect(router.state.location.search).toBe('');
  });

  it('ignores a status it does not know', async () => {
    // Arrange
    const asked = serveMembers();

    // Act
    renderApp('/admin/members?status=EVERYBODY');
    await screen.findByRole('article', { name: 'Ana Silva' });

    // Assert
    expect(asked[0]?.has('status')).toBe(false);
    expect(screen.getByLabelText('Status')).toHaveValue('');
  });

  it('keeps the search box at the API limit of 100 characters', async () => {
    // Arrange
    serveMembers();

    // Act
    renderApp('/admin/members');

    // Assert
    expect(await screen.findByLabelText('Search members')).toHaveAttribute('maxlength', '100');
  });

  it('says nothing matches, and offers to clear the search', async () => {
    // Arrange
    serveMembers([]);

    // Act
    const { user, router } = renderApp('/admin/members?q=zzz');

    // Assert
    expect(await screen.findByText('No members match')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Clear search and filter' }));
    await waitFor(() => {
      expect(router.state.location.search).toBe('');
    });
    expect(screen.getByLabelText('Search members')).toHaveValue('');
  });

  it('follows the address when the user goes back', async () => {
    // Arrange
    serveMembers();
    const { router } = renderApp('/admin/members?q=ana');
    await screen.findByRole('article', { name: 'Ana Silva' });

    // Act
    await router.navigate('/admin/members?q=bruno');

    // Assert
    await waitFor(() => {
      expect(screen.getByLabelText('Search members')).toHaveValue('bruno');
    });
  });
});
