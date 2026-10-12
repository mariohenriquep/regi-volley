import { randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { expect, type APIRequestContext } from '@playwright/test';

/**
 * Test data for the member journey. The admin screens do not exist yet (F5), so everything the API exposes is created
 * through the REST API as the founder (venue, group, plan, role, subscription, payment). Sessions are the exception:
 * `GenerateSessions` runs from the scheduler and has no HTTP route (docs/architecture.md section 14), and a dev-only
 * endpoint was rejected, so the sessions (and a past, attended one for the history) are inserted with SQL into the dev
 * Postgres of `docker compose` (container `regi-volley-postgres`; credentials from docker-compose.yml). Only ids and
 * timestamps made here go into the statements. Remove the SQL when a generation route or the admin screens exist.
 */
const POSTGRES_CONTAINER = process.env.E2E_POSTGRES_CONTAINER ?? 'regi-volley-postgres';

const CONTAINER_NAME = /^regi-volley-[a-z0-9-]+$/;
const LOCAL_DOCKER_HOST = /^(unix:\/\/|tcp:\/\/(localhost|127\.0\.0\.1|\[::1\])(:|$))/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** An id that goes into a SQL statement: the API's ids are UUIDs, and nothing else is ever interpolated. */
function uuid(value: string): string {
  if (!UUID.test(value)) throw new Error(`Refusing to put a non-UUID into SQL: ${value}`);
  return value;
}

/** Writes only to the throwaway Postgres of `docker compose` on this machine, never to a remote or another project's database. */
function assertLocalDevDatabase(): void {
  if (!CONTAINER_NAME.test(POSTGRES_CONTAINER)) {
    throw new Error(
      `E2E_POSTGRES_CONTAINER must match ${String(CONTAINER_NAME)}, got ${POSTGRES_CONTAINER}`,
    );
  }
  const host = process.env.DOCKER_HOST;
  if (host && !LOCAL_DOCKER_HOST.test(host)) {
    throw new Error(`Refusing to seed through a remote Docker host (DOCKER_HOST=${host})`);
  }
}

function sql(statements: string): void {
  assertLocalDevDatabase();
  execFileSync(
    'docker',
    [
      'exec',
      '-i',
      POSTGRES_CONTAINER,
      'psql',
      '-U',
      'regi_volley',
      '-d',
      'regi_volley',
      '-v',
      'ON_ERROR_STOP=1',
      '-q',
    ],
    { input: statements, stdio: ['pipe', 'ignore', 'inherit'] },
  );
}

async function created<T>(response: Awaited<ReturnType<APIRequestContext['post']>>): Promise<T> {
  expect(response.status(), await response.text()).toBeLessThan(300);
  return (await response.json()) as T;
}

const HOUR = 3_600_000;
const DAY = 24 * HOUR;

export interface Seeded {
  groupName: string;
  planName: string;
}

/**
 * The founder is a member with a paid 10-credit pack, in a group coached by a second member (a session's coach does not
 * book a seat in it) with two upcoming sessions.
 */
export async function seedMemberWithSessions(
  request: APIRequestContext,
  credentials: { email: string; password: string },
  suffix: string,
  shortName: string,
): Promise<Seeded> {
  const login = await created<{ accessToken: string }>(
    await request.post('/api/v1/auth/login', { data: credentials }),
  );
  const headers = { Authorization: `Bearer ${login.accessToken}` };
  const post = async <T>(path: string, data?: unknown) =>
    created<T>(await request.post(`/api/v1/${path}`, { headers, data }));

  const { memberId, associationId } = await created<{ memberId: string; associationId: string }>(
    await request.get('/api/v1/me', { headers }),
  );

  // The coach: someone asks to join through the public page, the founder approves, then grants the coach role.
  const coachEmail = `coach-${suffix}@example.com`;
  const join = await request.post(`/api/v1/public/associations/${shortName}/join-requests`, {
    data: {
      name: 'E2E Coach',
      email: coachEmail,
      phone: '961234567',
      consentAccepted: true,
      policyVersion: '2026-01',
    },
  });
  expect(join.status(), await join.text()).toBe(202);
  const pending = (await (await request.get('/api/v1/join-requests', { headers })).json()) as {
    id: string;
    email: string;
  }[];
  const requestId = pending.find(({ email }) => email === coachEmail)?.id;
  expect(requestId, 'the coach join request is pending').toBeTruthy();
  const { memberId: coachId } = await post<{ memberId: string }>(
    `join-requests/${requestId ?? ''}/approval`,
  );
  const coachRole = await request.put(`/api/v1/members/${coachId}/roles/COACH`, { headers });
  expect(coachRole.status(), await coachRole.text()).toBe(200);

  const levels = await post<{ entryLevelId: string }>('levels', { name: `Advanced ${suffix}` });
  const venue = await post<{ id: string }>('venues', {
    name: 'E2E Pavilion',
    address: 'Rua da Bola 1, Lisbon',
    courts: 2,
  });
  const groupName = `E2E Group ${suffix}`;
  const group = await post<{ id: string }>('training-groups', {
    name: groupName,
    venueId: venue.id,
    coachId,
    capacity: 12,
    acceptedLevelIds: [levels.entryLevelId],
    schedule: [{ dayOfWeek: 'MONDAY', startTime: '20:00', durationMinutes: 90 }],
  });
  const planName = `E2E Pack ${suffix}`;
  const plan = await post<{ id: string }>('plans', {
    name: planName,
    type: 'PACK',
    priceCents: 5000,
    credits: 10,
    validityDays: 90,
    allowedLevelIds: [],
  });
  const subscription = await post<{ id: string }>(`members/${memberId}/subscriptions`, {
    planId: plan.id,
  });
  await post(`subscriptions/${subscription.id}/payments`, {
    amountCents: 5000,
    method: 'CASH',
    paidOn: new Date().toISOString().slice(0, 10),
  });

  // Two upcoming sessions on the round hour more than a day ahead (free cancellation, booking already open) and, for the
  // history, a past session attended and a past one missed.
  uuid(associationId);
  uuid(group.id);
  uuid(coachId);
  const nextHour = Math.ceil(Date.now() / HOUR) * HOUR;
  const row = (startsAt: number, status: 'SCHEDULED' | 'COMPLETED') => {
    const id = uuid(randomUUID());
    const insert = `INSERT INTO sessions (id, association_id, training_group_id, coach_id, starts_at, ends_at, capacity, status, version)
      VALUES ('${id}', '${associationId}', '${group.id}', '${coachId}', '${new Date(startsAt).toISOString()}',
              '${new Date(startsAt + 1.5 * HOUR).toISOString()}', 12, '${status}', 0);`;
    return { id, insert };
  };
  const upcoming = [row(nextHour + 30 * HOUR, 'SCHEDULED'), row(nextHour + 54 * HOUR, 'SCHEDULED')];
  const attended = row(nextHour - 3 * DAY, 'COMPLETED');
  const missed = row(nextHour - 2 * DAY, 'COMPLETED');
  const booking = (sessionId: string, status: 'ATTENDED' | 'NO_SHOW', at: number) =>
    `INSERT INTO bookings (id, association_id, session_id, member_id, status, requested_at, confirmed_at)
      VALUES ('${uuid(randomUUID())}', '${associationId}', '${uuid(sessionId)}', '${uuid(memberId)}', '${status}',
              '${new Date(at - DAY).toISOString()}', '${new Date(at - DAY).toISOString()}');`;
  sql(
    [
      ...upcoming.map(({ insert }) => insert),
      attended.insert,
      missed.insert,
      booking(attended.id, 'ATTENDED', nextHour - 3 * DAY),
      booking(missed.id, 'NO_SHOW', nextHour - 2 * DAY),
    ].join('\n'),
  );
  return { groupName, planName };
}
