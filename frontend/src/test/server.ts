import { setupServer } from 'msw/node';
import { meAs } from './fixtures';

/**
 * The network layer of every unit test: handlers are added per test with `server.use(...)`. The one default is
 * `GET /me` answering a plain member: the shell reads the roles from it to decide whether to show the Coaching section,
 * so a test of a coach or an administrator overrides it with `meAs('COACH')`.
 */
export const server = setupServer(meAs('MEMBER'));
