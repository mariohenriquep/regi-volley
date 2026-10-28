# regi-volley

REST API for **RegiVolley** — a platform where amateur indoor volleyball associations manage
level-based training groups and members book a spot session by session, with a waitlist,
plans/credits and manual payment tracking.

- Product vision & requirements: [`docs/requirements.md`](docs/requirements.md)
- Architecture rules: [`docs/architecture.md`](docs/architecture.md)

## Stack

Java 21 · Spring Boot 4.1 · PostgreSQL + Flyway · JUnit 5, Mockito, AssertJ, Testcontainers ·
JaCoCo (≥ 85%) · Maven wrapper

## Running it

One-time per machine: copy `docs/toolchains.sample.xml` to `~/.m2/toolchains.xml` and point it
at a JDK 21.

```bash
docker compose up -d       # local Postgres and Mailpit (the dev mail catcher)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # http://localhost:8080; `dev` generates a throwaway JWT key
./mvnw verify              # tests + coverage gate (needs Docker for Testcontainers)
```

Authentication keys: outside profiles `dev` and `test` the app refuses to start without `JWT_SIGNING_KEY` and
`JWT_VERIFICATION_KEYS` (ES256, P-256; JWK, or PEM with a `kid: <id>` line before each block). Under `prod` it also
refuses the default `DB_PASSWORD`. See `docs/architecture.md` section 11 and `docs/security/threat-model-rest-api.md` D-2.
Two daily jobs run unless switched off (`SESSION_GENERATION_ENABLED`, `JOIN_REQUEST_PURGE_ENABLED`): the generation of the coming sessions and the
anonymisation of join requests left undecided for 30 days (RGPD data minimisation).

To keep the same key across dev restarts:

```bash
openssl ecparam -name prime256v1 -genkey -noout | openssl pkcs8 -topk8 -nocrypt -out dev-jwt.pem   # untracked (*.pem)
openssl ec -in dev-jwt.pem -pubout -out dev-jwt.pub.pem
export JWT_SIGNING_KEY="kid: dev-1
$(cat dev-jwt.pem)"
export JWT_VERIFICATION_KEYS="kid: dev-1
$(cat dev-jwt.pub.pem)"
```

## Frontend

A mobile-first PWA in [`frontend/`](frontend): React + Vite + TypeScript (strict), Tailwind CSS + shadcn/ui, React Router,
TanStack Query, and a typed API client generated from `docs/api/openapi.json`. Design and rules: `docs/architecture.md`
section 15. Needs Node 24 LTS or later (`frontend/.nvmrc` pins 24, which CI uses).

**Running frontend and backend together.** The app talks to the API on its own origin: in development Vite serves it on
<http://localhost:5173> and proxies `/api` to the API on `:8080`, keeping the browser's `Host`, so the refresh cookie and
the API's Origin check of the cookie routes work with no CORS (`CORS_ALLOWED_ORIGIN` stays unset).

```bash
docker compose up -d                                       # Postgres + Mailpit
SMTP_HOST=localhost SMTP_PORT=1025 SMTP_SECURITY=NONE \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # the API on :8080, mails to Mailpit (http://localhost:8025)
cd frontend && npm ci && npm run dev                       # the app on http://localhost:5173
```

From `frontend/`:

| Command | What it does |
|---|---|
| `npm run dev` | Vite dev server on :5173 with the `/api` proxy (`API_PROXY_TARGET` overrides `http://localhost:8080`) |
| `npm test` | unit and component tests (Vitest, Testing Library, MSW: no backend needed) |
| `npm run lint`, `npm run format:check`, `npm run typecheck` | ESLint, Prettier, `tsc -b` |
| `npm run build` | production build into `frontend/dist` (app shell, manifest, service worker) |
| `npm run api:generate` | regenerates `src/api/schema.gen.ts` from `docs/api/openapi.json`; commit it with the contract |
| `npm run api:check` | fails when the generated client is stale (CI runs it) |
| `npm run e2e` | the Playwright journeys against the real backend (below) |
| `npm run e2e:csp` | builds, serves `dist/` with the documented Content-Security-Policy and checks the dialogs, the coaching, admin and setup screens (their forms and questions) and the account menu raise no violation (API mocked; no backend needed) |

**Trying the app without curl.** With the three commands above running, open <http://localhost:5173>:

| Screen | Route | What to do |
|---|---|---|
| Register an association | `/register` | fill the form (levels: first = entry level) and submit; the activation mail lands in Mailpit (<http://localhost:8025>) |
| Activate your account | `/activate#token=...` | open the link from the mail, choose a password, then sign in |
| Sign in / sign out | `/login` | the founder's email and password; sign out is in the *Account* menu of the header (with *Sign out on all devices*) |
| Sessions (home) | `/` | this week's sessions by day (Lisbon time), seats, waitlist and your own status; venue and your waitlist place; *Book* then *Confirm booking*; *Cancel* (it says whether cancelling now is free, from the API's deadline); previous/next week. Needs a group with sessions and a paid plan (groups, venues and plans have their screens under *Admin*; the payment screens and a route that generates sessions do not exist yet: see the E2E note below) |
| Coaching (coaches and administrators) | `/coaching` | the staff's week by day: status, "N of M confirmed", waitlist, attended / no-show / still to mark; *Mark attendance* or *Roster*, *Change seats*, *Cancel session* as the API allows (the entry is shown only when `GET /me` lists COACH or ADMIN); previous/next week, past weeks included |
| Roster and attendance | `/coaching/sessions/<id>` | seats and waitlist in order; *Attended* / *No-show* per person (or *Mark everyone as attended*) and one *Save attendance*; change the seats (shows who moved up) and cancel the session with a reason |
| Admin (administrators) | `/admin` | the home of the section, with a link to each admin screen (the entry is shown only when `GET /me` lists ADMIN; payments join it later) |
| Join requests | `/admin/join-requests` | the pending requests, oldest first (name, email, phone, when asked); *Approve* (creates the member at the entry level and emails an activation link) and *Reject* (optional reason) both ask first |
| Members | `/admin/members` | search by name or email (`?q=`, written to the address once typing pauses) and filter by status (`?status=`); level, role badges, status and a "Not activated" badge per member; a notice when the API cut the list |
| One member | `/admin/members/<id>` | contact, level and who changed it before, roles, whether the account is activated, subscriptions (read-only); change the level, give or take the coach and administrator roles, *Resend activation link*, *Deactivate member* (asks first); an erased (anonymised) member is read-only |
| Levels | `/admin/setup/levels` | the levels lowest first with the entry level marked; add (as the highest), rename, move up / down with buttons (the whole order is sent), *Make entry level*; members book their own level and lower ones |
| Venues | `/admin/setup/venues` | name, address and courts; add, edit; *Delete* asks first and is disabled, with the reason, while an active group uses the venue (the API's refusal is still shown if the list was stale) |
| Training groups | `/admin/setup/groups` | active groups (or `?archived=1` for all): levels, venue, coach, seats and the weekly schedule in Lisbon time; add / edit with the accepted levels (checkboxes), venue (fixed once chosen), weekly slots (add / remove rows: day, start time, length), seats (a multiple of 6 is a hint, not a rule) and a coach picked from the members holding COACH; *Archive* asks first. Sessions for a saved group appear after the next daily generation |
| Plans | `/admin/setup/plans` | name, type, terms, price and allowed levels; add / edit with the fields of the type (pack: credits and validity days; N per week: sessions per week; single session: validity days), the price typed in euros and sent as exact cents, allowed levels (none = all). Editing does not change what existing subscriptions paid |
| My plan | `/me/plan` | your subscriptions: plan, validity, credits or sessions left, payment status and the overdue warning |
| History | `/me/history` | your last three months, newest first (group and venue), and this month's no-shows with the near-the-limit warning |
| Forgot password | `/forgot-password` | request a link (the answer is the same for any address), open it from Mailpit, choose a new password |
| Public page of an association | `/a/<short-name>` | levels, groups with the weekly schedule, venues; "Want to try it?" opens the join form |
| Join request | `/a/<short-name>/join` | name, email, phone and the consent; the answer is the same whatever the email |
| Privacy policy (stub) | `/privacy` | the text the consent boxes link to; `src/lib/privacy.ts` holds its version |

Rate limits (threat model D-9) show as "Please wait N minutes"; restart the API to clear its in-memory counters.

**End-to-end journeys (local only for now).** With `docker compose up -d` and the API running as above (dev profile, Mailpit
SMTP), run `npx playwright install chromium` once, then `npm run e2e`. The journey registers an association through the UI,
reads the activation email from Mailpit's API, opens the link in the browser, sets the password, signs in, reloads (the session
comes back through the refresh cookie) and signs out; a visitor then opens the public page and sends a join request; and a member
resets a forgotten password through the UI. A fourth step runs the member journey on a phone-sized screen: it books a session in two
taps (a double booking from another device is refused with advice), cancels for free after a reload (the credit comes back),
books and cancels again, checks *My plan*, then checks *History* and signs out everywhere. Playwright starts `npm run dev` itself unless it is already running.

*Test data for the member journey* (`e2e/seed.ts`). Everything with an HTTP route is created through the REST API as the founder
(a second member as coach through a join request, the coach role, venue, group, plan, subscription, payment). **Sessions are
inserted with SQL**: the generation has no HTTP route (it is the scheduler's, `docs/architecture.md` section 14) and a dev-only
endpoint was rejected. The SQL goes through `docker exec regi-volley-postgres psql` (credentials from `docker-compose.yml`;
`E2E_POSTGRES_CONTAINER` overrides the container name) and only inserts rows with ids and timestamps the test made. Replace it
when a generation route exists.

To run the journey beside the usual dev servers, give it its own ports: start the API with `SERVER_PORT=8081
WEB_ORIGIN=http://localhost:5174` (plus the SMTP variables) and run `WEB_PORT=5174 API_PROXY_TARGET=http://localhost:8081 npm run e2e`.
A fifth step runs the coach journey on a phone: a coach (activated from the emailed link) opens *Coaching*, marks attendance of a
session that started an hour ago (one attended, one no-show; the counts change), raises the seats of a full session (the waitlisted
member moves up; a number below the confirmed is refused on the field), cancels a session ahead with a reason, and a player signs in
and finds no *Coaching*. Its data is in `seedCoaching` (`e2e/seed.ts`): the coach and the player join through the public page, and
the started session and the single-seat session are SQL, like the others.
A sixth step runs the administrator journey on a phone: a second administrator (made by `seedAdmin`: she joins, the founder approves her
and grants ADMIN, so the founder does not sign in again) opens *Admin*, approves the pending request of the visitor of the first step,
finds the new member by searching, changes their level (the history shows who), grants and revokes COACH, resends the activation link
(Mailpit gets a second, different link) and deactivates them; the list then filters by *Inactive*.
A seventh step runs the setup journey on a phone: the second administrator adds a level (it is the highest), moves it up and renames it,
adds two venues, adds a group with two slots and a coach (two overlapping slots are refused by the API and shown; moving one saves the
group), adds a pack plan and lowers its price, finds the venue of the group not deletable (and the API's refusal when the list is made
stale), deletes the spare venue, and archives the group, which then shows with the archived toggle.
Registration is limited to 3 per hour per address, a join request to 5 per hour per IP (a run files 5) and sign-in to 5 per 15
minutes per address, and one run uses each once, so a second run within the hour gets 429: restart the API (the limiter is in memory).

**Production** is same-origin too (threat model D-7b): one reverse proxy serves `frontend/dist` (with `index.html` as the
fallback for unknown paths) and forwards `/api` to the API, and `WEB_ORIGIN` is set to that origin. Deployment comes with
the container work.

## Email

The API sends the account links (activation, password reset) and the notices of the booking rules (moved up from the waitlist,
session cancelled, no-show limit warning, join request approved / rejected) by SMTP, asynchronously after the commit and with a few
retries. Configuration is from the environment:

| Variable | Meaning | Default |
|---|---|---|
| `SMTP_HOST` | mail server; **unset = no SMTP**: the logging adapters stay active (only profiles `dev` and `test` may run like that) | unset |
| `SMTP_PORT` | server port | `587` |
| `SMTP_SECURITY` | `STARTTLS` (required, not opportunistic), `TLS` (implicit, port 465) or `NONE` (only towards `localhost`, a loopback address or `mailpit`; refused for any other host and outside `dev` / `test`) | `STARTTLS` |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | credentials (secrets: from the platform store, never the repo) | unset |
| `MAIL_FROM` | the From header, e.g. `RegiVolley <no-reply@example.org>` | unset |
| `WEB_ORIGIN` | the frontend's origin, e.g. `https://app.example.org`; the links in the emails are built on it (https required outside dev / test) | unset |

A port that does not match the mode (TLS on 587, STARTTLS on 465) is logged as a warning. Outside `dev` and `test` the application **refuses to start** unless all of them are set and the connection is encrypted (the message
names the missing variables, never their values). The links point at the frontend: `WEB_ORIGIN/activate#token=...` and
`WEB_ORIGIN/reset-password#token=...` (the token is in the fragment, so it reaches no server log); the frontend must serve both routes
remove the token from the address bar (`history.replaceState`) right after reading it, and POST the token with the new password to `/api/v1/auth/activate` or `/api/v1/auth/password-resets`.

**Seeing the emails in development.** `docker compose up -d` starts [Mailpit](https://mailpit.axllent.org/), which accepts any mail and
delivers none. Point the app at it and open the inbox at <http://localhost:8025>:

```bash
export SMTP_HOST=localhost SMTP_PORT=1025 SMTP_SECURITY=NONE MAIL_FROM='RegiVolley <no-reply@regivolley.local>'
export WEB_ORIGIN=http://localhost:5173      # optional under dev: this is the default
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Registering an association through the API then puts the founder's activation email in the Mailpit inbox. Without `SMTP_HOST` the
dev application only logs that a mail is due (by link reference, never the token). Tests use an in-process SMTP server (GreenMail),
never a real provider.

## Dev data

Under the `dev` profile (never with `prod`) the first start creates a ready-to-use association, so you can sign in and book
without registering, reading Mailpit and activating first. It is skipped when the association `volei-lisboa` already exists.

| Account | Email | Roles |
|---|---|---|
| Administrator (founder) | `admin@volei-lisboa.test` | ADMIN, MEMBER |
| Coach | `coach@volei-lisboa.test` | COACH, MEMBER |

Both sign in with the password `dev-password-volley` (a throwaway dev database, not a secret).

What is created, through the same use cases the API uses: the association **Volei Lisboa** (`volei-lisboa`) with the levels
Beginner (entry), Intermediate and Advanced; one venue; one group, *Evening training*, Monday and Wednesday 20:00 (Lisbon), 90
minutes, 12 seats, all levels, **coached by the coach**; a 10-credit pack (90 days, 30.00 EUR) assigned to the administrator and paid
in full; and the group's sessions for the coming weeks. The administrator is deliberately not the group's coach, because a session's
coach cannot book it (RN-02), so the administrator can book straight away:

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
     -d '{"email":"admin@volei-lisboa.test","password":"dev-password-volley"}' | jq -r .accessToken)
curl -s localhost:8080/api/v1/sessions -H "Authorization: Bearer $TOKEN"        # pick a sessionId
curl -s -X POST localhost:8080/api/v1/sessions/$SESSION_ID/bookings -H "Authorization: Bearer $TOKEN"   # CONFIRMED
```

* **Reset:** `docker compose down -v` removes the database volume; the next start seeds again. If `volei-lisboa` already exists
  (a manual test, or a seed that stopped half way) the seed does nothing, so reset the database or pick another short name.
* **Disable:** `DEV_SEED_ENABLED=false` (or `regi-volley.dev-seed.enabled=false`). Tests never seed unless a test turns it on.
* The seed logs ids only. The two accounts are activated directly (the dev password is set and the membership confirmed, and the
  activation link that registration issued is closed), because a fresh database has no mailbox; the production activation flow is
  untouched. With `SMTP_HOST` set, Mailpit still receives the (already closed) activation mails and the welcome notice.

## API overview

All paths are under `/api/v1`; the full description is [`docs/api/openapi.json`](docs/api/openapi.json) (OpenAPI 3, generated from the
controllers and kept in sync by a test) and the endpoint table with its rules is in [`docs/architecture.md`](docs/architecture.md)
section 14. Everything except the public routes needs `Authorization: Bearer <access token>`; the tenant is always the token's, never a
path, query or body value, and another association's id answers like one that does not exist (404).

| Area | Routes |
|---|---|
| Visitor (no token) | `GET /public/associations/{shortName}` (public page), `POST /public/associations` (register an association), `POST /public/associations/{shortName}/join-requests` (always `202 RECEIVED`) |
| Credentials | `POST /auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/activate`, `/auth/password-reset-requests`, `/auth/password-resets` (public); `POST /auth/logout-all`, `GET /me` (your `memberId`, `associationId` and the `roles` you hold now: `MEMBER`, `COACH`, `ADMIN`; display only, every call is still authorised by the API) |
| Member | `GET /sessions?weekOf=` (each line: venue, seats, waitlist, the free-cancellation deadline and, for you only, `myBookingStatus`, `myBookingId` and your `waitlistPosition`), `POST /sessions/{id}/bookings`, `POST /sessions/{id}/bookings/{bookingId}/cancellation`, `GET /me/plan`, `GET /me/history` (each entry with its group and venue name) |
| Coach / admin | `GET /coaching/sessions?weekOf=` (the staff's week: a coach sees the sessions they coach, an administrator all of them, cancelled ones too; each line with the group and venue name, status, `capacity`, `confirmedCount`, `waitlistCount`, `attendedCount`, `noShowCount`, `unmarkedCount` and the `canMarkAttendance` / `canCancel` / `canChangeCapacity` flags) |
| Coach / admin of a session | `GET /sessions/{id}/roster` (who is in the session, with the booking ids and display names only, plus `coaching`: the session's own line of the staff's week with its counts and the `canMarkAttendance` / `canCancel` / `canChangeCapacity` flags), `POST /sessions/{id}/cancellation`, `PUT /sessions/{id}/capacity`, `PUT /sessions/{id}/attendance` |
| Administrator | `/levels`, `/venues`, `/training-groups`, `/plans`, `/join-requests` (list, approve, reject), `/members/{id}` (level, roles, deactivation, subscriptions, `activation-links`: re-send an activation link, always `202 RECEIVED`), `/subscriptions` (list by payment status, CSV export, overdue-marking, payments), `/payments/{id}/reversal` |
| Administrator, reads | `GET /members?status=&q=` (name, email, phone, level, roles, status, `accountActivated`, `joinedAt`; at most 300 and `truncated` says when there were more; anonymised members are not listed), `GET /members/{id}` (adds the level history with names and the most recent subscriptions with plan name, payment status, credits used / left, paid and outstanding cents), `GET /levels`, `GET /venues` (with `inUseByActiveGroup`), `GET /training-groups?includeArchived=` (levels, venue and coach named, weekly slots in Lisbon time), `GET /plans` (terms, price, allowed levels named), `GET /subscriptions/{id}/payments` (payments and reversals, oldest first, with paid / outstanding totals) |

A refused booking or cancellation answers `422 BUSINESS_RULE_VIOLATION` (`409 CONFLICT` for a duplicate) with the unchanged `code` and a
stable `reason` beside it (`NO_BALANCE`, `PAYMENT_OVERDUE`, `BOOKING_NOT_OPEN`, ...: architecture.md section 12); clients branch on the
reason and show the `message`.

A quick tour against a local instance (`dev` profile; the activation link of a new association arrives by email, in the Mailpit inbox
when configured as above; the end-to-end journey is also exercised by `ApiJourneyIntegrationTest`):

```bash
curl -s localhost:8080/api/v1/public/associations/my-club                       # the public page
curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
     -d '{"email":"ana@example.com","password":"..."}'                          # {"accessToken": "...", "tokenType": "Bearer", "expiresIn": 600}
curl -s localhost:8080/api/v1/sessions -H "Authorization: Bearer $TOKEN"        # this week's bookable sessions
```

After changing a controller or a request/response record, regenerate the description with
`./mvnw test -Dtest=OpenApiContractTest -Dopenapi.update=true` and commit `docs/api/openapi.json`; the test fails otherwise.
springdoc is a test-scope dependency only: the running application serves no documentation endpoint.

## CI pipeline

`.github/workflows/ci.yml`, with a read-only `GITHUB_TOKEN` by default:

| Job | PR | push to `main` | What it does |
|---|---|---|---|
| `build-and-test` | yes | yes | `./mvnw verify` (tests + JaCoCo >= 85%), CycloneDX SBOM artifact |
| `secret-scan` | yes | yes | Gitleaks |
| `sast` | yes | yes | Semgrep (`p/java`, `p/owasp-top-ten`, `p/secrets`, `p/typescript`, `p/react`) |
| `frontend` | yes | yes | `npm ci`, `npm audit` of the runtime dependencies (high), API client up to date, ESLint + Prettier, typecheck, unit tests, build |

Container image build, scan and publish (CD) come in a later issue. Dependabot covers `maven`,
`github-actions` and `npm` (`/frontend`, minor and patch bumps grouped), each with a 7-day cooldown.