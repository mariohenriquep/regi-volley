import { http } from 'msw';
import { setupServer } from 'msw/node';
import { notAllowed } from './handlers';

/**
 * The network layer of every unit test: handlers are added per test with `server.use(...)`. The one default is the
 * coaching list answering 403, as it does for a member: the shell asks for it to decide whether to show the Coaching
 * section, so a test of a staff member overrides it.
 */
export const server = setupServer(http.get('/api/v1/coaching/sessions', () => notAllowed()));
