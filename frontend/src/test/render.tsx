import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StrictMode } from 'react';
import { createMemoryRouter, type InitialEntry, RouterProvider } from 'react-router';
import { AppProviders } from '@/app/AppProviders';
import { createQueryClient } from '@/app/queryClient';
import { routes } from '@/app/routes';
import { createAuthSession } from '@/auth/session';

/** Renders the whole app (providers, guards, pages) at `path`, with the network mocked by MSW. */
export function renderApp(path: InitialEntry = '/', options: { strict?: boolean } = {}) {
  const session = createAuthSession({ baseUrl: window.location.origin });
  const queryClient = createQueryClient();
  // Same retry rules as the app, without the back-off: a failing query must not outlast the test's patience.
  queryClient.setDefaultOptions({
    ...queryClient.getDefaultOptions(),
    queries: { ...queryClient.getDefaultOptions().queries, retryDelay: 0 },
  });
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const user = userEvent.setup();
  const app = (
    <AppProviders session={session} queryClient={queryClient}>
      <RouterProvider router={router} />
    </AppProviders>
  );
  render(options.strict ? <StrictMode>{app}</StrictMode> : app);
  return { router, session, queryClient, user };
}
