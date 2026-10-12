import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { routes } from '@/app/routes';
import { RouteError } from './RouteError';

function Boom(): never {
  throw new Error('SQL exploded: secret-detail');
}

describe('RouteError', () => {
  it('replaces a page that failed to render with a friendly message and a way out, and shows no detail', () => {
    // Arrange: React Router logs the error it caught
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const router = createMemoryRouter(
      [{ path: '/', Component: Boom, errorElement: <RouteError /> }],
      { initialEntries: ['/'] },
    );

    // Act
    render(<RouterProvider router={router} />);

    // Assert
    expect(screen.getByRole('heading', { name: 'Something went wrong' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Reload the app' })).toHaveAttribute('href', '/');
    expect(screen.queryByText(/secret-detail/)).not.toBeInTheDocument();
  });

  it('guards the whole route table: the pages, and the shell itself', () => {
    // Arrange
    const [shell] = routes;
    const [pages] = shell?.children ?? [];

    // Act / Assert: a page that throws keeps the header and the bar; a throwing shell still ends on the same message
    expect(pages?.errorElement).toBeDefined();
    expect(shell?.errorElement).toBeDefined();
  });
});
