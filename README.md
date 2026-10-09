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
docker compose up -d       # local Postgres
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

## API overview

All paths are under `/api/v1`; the full description is [`docs/api/openapi.json`](docs/api/openapi.json) (OpenAPI 3, generated from the
controllers and kept in sync by a test) and the endpoint table with its rules is in [`docs/architecture.md`](docs/architecture.md)
section 14. Everything except the public routes needs `Authorization: Bearer <access token>`; the tenant is always the token's, never a
path, query or body value, and another association's id answers like one that does not exist (404).

| Area | Routes |
|---|---|
| Visitor (no token) | `GET /public/associations/{shortName}` (public page), `POST /public/associations` (register an association), `POST /public/associations/{shortName}/join-requests` (always `202 RECEIVED`) |
| Credentials | `POST /auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/activate`, `/auth/password-reset-requests`, `/auth/password-resets` (public); `POST /auth/logout-all`, `GET /me` |
| Member | `GET /sessions?weekOf=`, `POST /sessions/{id}/bookings`, `POST /sessions/{id}/bookings/{bookingId}/cancellation`, `GET /me/plan`, `GET /me/history` |
| Coach / admin of a session | `GET /sessions/{id}/roster` (who is in the session, with the booking ids and display names only), `POST /sessions/{id}/cancellation`, `PUT /sessions/{id}/capacity`, `PUT /sessions/{id}/attendance` |
| Administrator | `/levels`, `/venues`, `/training-groups`, `/plans`, `/join-requests` (list, approve, reject), `/members/{id}` (level, roles, deactivation, subscriptions, `activation-links`: re-send an activation link, always `202 RECEIVED`), `/subscriptions` (list by payment status, CSV export, overdue-marking, payments), `/payments/{id}/reversal` |

A quick tour against a local instance (`dev` profile; the activation link of a new association is only logged by reference until the
SMTP adapter exists, so the end-to-end journey is exercised by `ApiJourneyIntegrationTest`):

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
| `sast` | yes | yes | Semgrep (`p/java`, `p/owasp-top-ten`, `p/secrets`) |

Container image build, scan and publish (CD) come in a later issue. Dependabot covers `maven`
and `github-actions`.