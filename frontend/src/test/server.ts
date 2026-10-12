import { setupServer } from 'msw/node';

/** The network layer of every unit test: handlers are added per test with `server.use(...)`. */
export const server = setupServer();
