# Threat model: REST API + JWT authentication (issue #26)

Status: written before the code (Plan stage), 8/10/2026. Scope: everything #26 adds in
`infrastructure.web` and `infrastructure.security`. The system is **not deployed**; "future" means
it needs infrastructure that does not exist yet (SMTP, staging, a host). Related:
`docs/architecture.md` §8, §11, §12; `docs/requirements.md` NFRs "Multi-tenant", "RGPD",
"Autenticação", "Operação".

Method: light STRIDE per surface, then concrete decisions. Each decision has an ID (`D-n`) that the
checklist in §9 refers to.

## 1. Assets, actors, trust boundaries

**Assets (by value):** (1) personal data of members of many associations in one database (name,
email, phone, attendance, payment history); (2) tenant isolation itself; (3) booking and payment
integrity (last seat, credits, append-only payments RN-19); (4) credentials and the JWT signing
key; (5) availability of booking on match nights.

**Actors**

| Actor | Authenticated | Power | Typical abuse |
|---|---|---|---|
| Visitor | no | public page, register association, request to join | enumeration, spam, filing requests under someone else's email, flooding |
| Member | yes | own bookings, own plan and history | reading/changing another member's or tenant's data (IDOR) |
| Coach | yes | member powers + own groups/sessions | acting outside own groups |
| Administrator | yes | everything inside **one** association | crossing into another tenant; escalating by orphaning the last admin |
| Outsider with a stolen token or password | n/a | whatever the victim can do | replay, credential stuffing |
| Insider with DB/log/env access (operator) | n/a | n/a | out of scope beyond "no secrets/PII in logs, images or repo" |

One person in several associations is **out of MVP scope** (open question in requirements). The
design must not preclude it: identity (`app_user`) and membership (`membership`) are separate
tables; the token carries one selected membership.

**Trust boundaries and data flows**

```
Browser/PWA --TLS--> [reverse proxy / platform TLS] --> Spring app (filter chain -> controllers
   -> use cases -> repository adapters) --> PostgreSQL (EU)
                                     \--> email provider (future, Phase 2)
```

- B1: Internet to the app. Everything arriving is hostile until the filter chain says otherwise.
- B2: Token to tenant. The only trusted source of `(associationId, memberId)` is the verified
  token **re-checked against the database** (D-6). Never path, query, body or header.
- B3: App to database. Every business read already takes `AssociationId` (§8).
- B4: App to email provider (future). Email is the proof of mailbox ownership; the provider sees
  addresses and link tokens, so the activation/reset link token must not be logged (D-11).

Flows: visitor registers association -> founder activates account by emailed link -> logs in ->
bearer access token (memory) + refresh cookie. Visitor requests to join -> admin approves ->
member activates by emailed link -> logs in.

## 2. Current state that shapes the design (read from the code)

- `Actor(AssociationId, MemberId)` is the only identity the application layer sees; every use
  case loads the actor's `Member` and checks roles (`Permissions`) and tenant scope. Good.
- **Gap G1:** read use cases (`MemberHistoryService`, `MyPlanService`, `ListBookableSessionsService`)
  do **not** check that the actor is still active. A deactivated or anonymised member would still
  read their data if the web layer only validated the token. The security layer must therefore
  enforce "active, not anonymised" itself on every request (D-6). Do not "fix" this by editing
  each use case only; do both, the resolver is the authoritative gate.
- **Gap G2:** `RegisterAssociationCommand` and `ApproveJoinRequest` create a `Member` but nothing
  creates credentials. Approval only calls `Notifier.memberApproved(ids)`.
- **Gap G3:** `LoggingNotifier` is the only notifier. There is **no email adapter**, so no activation
  or reset mail can be delivered until Phase 2's SMTP adapter exists. Stated plainly: login can be
  built and tested in #26, but real users cannot onboard until an email adapter exists. That is a
  **go-live blocker**, not a #26 blocker.
  **Closed by issue #40** (see section 14).
- `JoinRequestNotPossibleException` already uses one message for "already member" and "pending
  request exists", but an HTTP client still tells 4xx from 2xx (the #25 review finding). D-14 fixes it.
- `Invalid*Exception` (invariants) are plain `RuntimeException`s; `*NotFoundException` too;
  `BusinessRuleException` is the user-facing base (`InvalidFieldException`, `NotAllowedException`,
  `JoinRequestNotPossibleException`, ...). The advice must order handlers most-specific first.
- `application.yml` defaults `DB_PASSWORD` to a local placeholder (Gitleaks-allowlisted). That is
  fine for dev; a `prod` profile must have no defaults for secrets and must fail fast (D-3).
- `GenerateSessionsCommand` takes the tenant directly (scheduler). **Do not expose it over HTTP in
  #26.** If an admin trigger is wanted later, it needs `Actor` + `Permissions.requireAdmin`.

## 3. STRIDE per surface

Legend: S spoofing, T tampering, R repudiation, I information disclosure, D denial of service, E
elevation. "Test" refers to §10.

### 3.1 Login and token issuance

| # | STRIDE | Threat | Mitigation | Test |
|---|---|---|---|---|
| L1 | S | Credential stuffing / brute force | Per-IP and per-email-hash rate limit with Retry-After (D-9); Argon2id (D-8); password min length and common-password check (D-8) | rate-limit test |
| L2 | I | Account enumeration by response body, status or timing | One 401 body for unknown email, wrong password, unconfirmed membership and inactive member; dummy hash verify on unknown email; throttle applies to unknown emails identically (D-10) | uniform-response test |
| L3 | S | Pre-hijacking: attacker registers/requests with the victim's email and sets a password first | No password is ever accepted without proof of the mailbox: credentials are created only by consuming an emailed single-use link (D-11) | activation test |
| L4 | S | Forced membership: attacker registers an association with the victim's email to attach it to the victim's account | Membership is unusable until the emailed link is consumed (D-11) | membership-confirmation test |
| L5 | T | Forged/modified token, `alg:none`, algorithm confusion, wrong `kid`, wrong issuer/audience | Pinned ES256 decoder (anything else, including `none`, HS* and RS*, rejected), public key selected by `kid` from a closed key set, `iss`/`aud`/`exp` validated (D-1..D-3) | tampered-token tests |
| L6 | I | Tokens or passwords in logs/URLs | Bearer header only (never query); `Authorization`, cookies and bodies never logged; reset token in URL **fragment** (D-11, §7) | log-capture test |
| L7 | D | Argon2 CPU/memory exhaustion by many parallel logins | Rate limit before hashing; cap concurrent hashes (semaphore, 4); size container memory for it (D-8) | load sanity, manual |
| L8 | R | No trace of who logged in or failed | Structured audit events with ids + truncated IP, 30-day retention (§7) | log-capture test |

### 3.2 Token use (every authenticated request)

| # | STRIDE | Threat | Mitigation | Test |
|---|---|---|---|---|
| U1 | E | Roles baked into a long-lived token survive demotion/deactivation | No roles in the token; `Member` re-read per request, use cases check roles (D-5, D-6) | deactivate-then-call test |
| U2 | E | Tenant taken from request (path/body/header) | Tenant only from `AuthenticatedActor`; DTOs have no tenant fields; unknown JSON properties rejected (D-12) | IDOR matrix, DTO arch test |
| U3 | S | Stolen access token replayed | 10-min lifetime + security-stamp claim checked against DB, so password change/logout-all kills it immediately (D-4, D-6) | stamp test |
| U4 | S | Stolen refresh token | Opaque, hashed at rest, rotated, reuse detection revokes the family, HttpOnly cookie (D-7) | refresh-reuse test |
| U5 | T | CSRF on cookie-carrying endpoints | Only `/auth/refresh` and `/auth/logout` read the cookie; `SameSite=Strict`, Origin check, JSON-only; all else is header-bearer so no ambient credential (D-7) | CSRF tests |
| U6 | I | Expired-token error text discloses detail | Custom entry point: constant body, no `error_description` (§7) | body assertion |
| U7 | D | Valid user hammers booking/history | Authenticated per-user limit (D-9) | rate-limit test |

### 3.3 Public endpoints (association page, register association, join request)

| # | STRIDE | Threat | Mitigation | Test |
|---|---|---|---|---|
| P1 | I | Join request tells a stranger whether an email is a member or has a pending request | Identical `202` and body in all three outcomes (created / member exists / pending exists); no id returned (D-14) | uniformity test |
| P2 | T | Request filed under someone else's email (no email verification), including a **false RGPD consent** given on their behalf | MVP: admin approval is the human gate; per-IP and per-email limits; request invisible to anyone but that tenant's admins. **Residual risk R2.** Follow-up before go-live: emailed confirmation link makes the request visible to admins only once confirmed (D-14) | follow-up |
| P3 | D | Flooding registrations/join requests/page lookups | Rate limits per IP and per short name (D-9); body size limit (D-13) | rate-limit test |
| P4 | I | Public page leaks more than intended (ids, contact data of individuals) | Response DTO is an explicit whitelist: name, locality, levels, schedules, venues, association contact email as entered; no ids of members, no counts of members | DTO test |
| P5 | I | Short-name enumeration | Accepted: short names are public by design (US-24). Not secret, still rate-limited | none |
| P6 | E | Register association with a role/shortName tampering | Founder roles fixed in the use case (`ADMIN`, `MEMBER`); DTO has no role field | DTO test |
| P7 | S | Founder created but credentials provisioning fails after the use case committed (non-atomic: `UnitOfWork` uses `REQUIRES_NEW`) | Idempotent provisioner, failure logged by ids, `resend activation` admin endpoint for members; **founder case is residual risk R3** (operator re-runs provisioning; pilot-scale) | provisioning test |

### 3.4 Member, coach and admin endpoints

| # | STRIDE | Threat | Mitigation | Test |
|---|---|---|---|---|
| M1 | I/E | IDOR: id of another tenant's session/booking/payment/member in the URL | Tenant from principal, repositories already tenant-scoped; other-tenant id is `*NotFound` -> **404, never 403** (D-12) | IDOR matrix |
| M2 | E | Same-tenant IDOR: member cancels another member's booking, coach acts on another coach's session | Already decided in use cases (`CancelBooking`, `Permissions.isStaffOf`); web layer adds nothing and must not bypass | per-endpoint role tests |
| M3 | T | Mass assignment (roles, status, version, ids in body) | Request DTOs are hand-written records with only the allowed fields; `FAIL_ON_UNKNOWN_PROPERTIES=true` | unknown-property test |
| M4 | T | Last seat / credit double-spend through parallel HTTP calls | Already protected (§10); an HTTP-level race test confirms the controller adds no non-transactional step | HTTP race test |
| M5 | R | Payment or role change cannot be attributed | `Payment.recordedBy`, level-change `changedBy` exist in the domain; add audit log line (ids) for record/reverse payment, grant/revoke role, deactivate | log-capture test |
| M6 | E | Orphaning the association (last admin) | Already guarded (§13); map to 409 | error-mapping test |
| M7 | I | List endpoints return other tenants' rows or too much PII | Lists already tenant-scoped; add max page size 100; DTOs expose contact data only to admin endpoints | DTO + IDOR tests |

### 3.5 Error responses and logging

| # | STRIDE | Threat | Mitigation | Test |
|---|---|---|---|---|
| E1 | I | Stack traces, SQL, class names in bodies | `server.error.include-*=never`; one `@RestControllerAdvice` with a catch-all `500 {"code":"INTERNAL_ERROR"}` | 500-body test |
| E2 | I | PII in log lines through Spring's own handlers: `MethodArgumentNotValidException` messages quote **rejected values** (e.g. an email), `HttpMessageNotReadableException` may echo JSON | Handlers log exception class and field names only; raise `DefaultHandlerExceptionResolver` log level to ERROR; assert in a log-capture test (D-13) | log-capture test |
| E3 | I | 404 vs 403 difference reveals other tenants' ids | Cross-tenant = 404 (above) | IDOR matrix |
| E4 | T | Log forging via client-supplied request id / header values | Server generates `X-Request-Id`; client value ignored | header test |

## 4. JWT design decisions

| ID | Decision | Rationale |
|---|---|---|
| D-1 | **ES256 (ECDSA P-256 + SHA-256)**, asymmetric. The decoder accepts exactly `ES256`: tokens with `none`, `HS*`, `RS*` or any other `alg` are rejected before any key lookup. | Decided by the owner (8/10/2026). The signing key is private to the app and verification needs only public keys, so a leaked verifier or log cannot mint tokens, and a second verifier (worker, gateway) can be added later without sharing a secret. Pinning one algorithm family also removes RS/HS confusion: no symmetric algorithm is registered, so a public key can never be used as an HMAC secret. Built with `NimbusJwtDecoder` over a custom `JWTProcessor` whose `JWSVerificationKeySelector` is limited to `JWSAlgorithm.ES256` and the configured EC public keys (Spring's `withPublicKey` shortcut is RSA-only); signing with `NimbusJwtEncoder` over an `ECKey`. |
| D-2 | **Key management.** Private signing key(s) come from the environment or the platform secret store as PEM (PKCS#8) or JWK, never from a file in the repo or the image: `JWT_SIGNING_KEY` (the active private key, with its `kid`) and `JWT_VERIFICATION_KEYS` (public keys, each with a `kid`, always including the active one's public half). The `kid` goes in the JOSE header; the verifier looks it up in the closed set and an unknown `kid` -> 401. **Rotation by overlap:** (1) add the new public key to `JWT_VERIFICATION_KEYS` and deploy; (2) switch `JWT_SIGNING_KEY` to the new private key and deploy; (3) after access TTL + skew (15 min) remove the old public key. No logout of anyone. **Fail-fast under profile `prod`:** the app refuses to start if the signing key is missing, is not an EC key on curve P-256, does not match any verification key, has no `kid`, or any verification key is not P-256 or is duplicated. **No JWKS endpoint:** one service both signs and verifies, nothing external needs the keys, and an endpoint is attack surface (and a place to leak a wrong key). Add `/.well-known/jwks.json` (public keys only) in the issue that introduces a second verifier. **Local dev:** only under profile `dev` (and in tests), if no key is configured the app generates an ephemeral P-256 keypair at startup, logs a one-line warning with the `kid` only (never key material), and tokens die on restart. Under any other profile that fallback does not exist. To keep a stable dev key: `openssl ecparam -name prime256v1 -genkey -noout \| openssl pkcs8 -topk8 -nocrypt -out dev-jwt.pem`, then export it via the environment; the file stays untracked (`*.pem` in `.gitignore`). Nothing key-like is committed, so Gitleaks needs no allowlist entry. |
| D-3 | Same fail-fast for `DB_PASSWORD` under profile `prod`: the `regi_volley` default must not boot in production. | The placeholder default is only safe on a developer's machine. |
| D-4 | **Access token: 10 minutes**, clock skew 30 s. Claims: `iss`=`regi-volley-api`, `aud`=`regi-volley-web`, `sub`=user id (UUID), `aid`=association id, `mid`=member id, `sv`=security stamp, `iat`, `exp`. **No** roles, email, name, phone (JWT payloads are readable by the client and end up in proxies and crash dumps). `iss` and `aud` are validated; any missing claim -> 401. | `aid`/`mid` are the minimum to build `Actor`. A single selected membership in the token keeps multi-association possible later without a format change. |
| D-5 | **Roles are resolved server-side per request, never from the token.** | `Member.roles` is tenant business data (§11); honours demotion and deactivation at once; the use cases already read it. |
| D-6 | **`PrincipalResolver`** (in `infrastructure.security`) runs after signature validation on every request: loads `app_user` by `sub` (status ok, `security_stamp == sv`), the `membership` row `(user, aid, mid)` is CONFIRMED, and `MemberRepository.findById(aid, mid)` is present, `ACTIVE`, not anonymised. Any failure -> 401 (same body as an invalid token). Result: `AuthenticatedActor`, from which the **only** `new Actor(...)` in the codebase is built. | Closes G1; makes deactivation, erasure and password change effective immediately; costs two primary-key reads per request, acceptable at the NFR scale (300 members per association). Deliberately **no cache**: the point is immediacy. |
| D-7 | **Refresh token:** opaque, 256-bit `SecureRandom`, stored as SHA-256 hash (the value is high-entropy, a slow hash adds nothing). Table `refresh_token(id, family_id, user_id, membership_id, token_hash unique, issued_at, expires_at, rotated_at, revoked_at)`. Every `/auth/refresh` rotates: old token marked rotated, new one issued in the same family. **Reuse of a rotated token revokes the whole family** (re-login required) and writes a security audit event. A 10-second grace window for a duplicate presentation of a just-rotated token (two PWA tabs) answers 401 without revoking the family. Lifetime 30 days idle, 90 days absolute per family. The refresh call re-runs `PrincipalResolver`, so a deactivated member cannot refresh. Revoked on: logout (family), logout-all, password change/reset (all families, plus stamp bump), membership no longer active. | Standard rotation + reuse detection; short access tokens plus DB resolution make the refresh token the only long-lived secret. |
| D-7a | **Transport: access token in `Authorization: Bearer` (kept in JS memory only, never localStorage); refresh token in an `HttpOnly; Secure; SameSite=Strict` cookie named `__Secure-rt`, `Path=/api/v1/auth`.** Tokens in query strings or form bodies are rejected. | XSS cannot read the refresh token and the access token dies in 10 minutes; every endpoint except two uses a header, so there is no ambient credential to forge. **CSRF consequence:** Spring CSRF protection is disabled for the stateless `/api/**` routes (Semgrep's `csrf-disabled` rule will fire: suppress with a `nosemgrep` comment that points here). On the two cookie endpoints (`/auth/refresh`, `/auth/logout`) require `Content-Type: application/json`, a matching `Origin` (reject when it is present and not the PWA origin), and rely on `SameSite=Strict`. Worst case of a forged refresh is a token rotation the attacker cannot read. |
| D-7b | **Deployment constraint:** PWA and API are served from the **same origin** (reverse proxy path `/api`) or at least the same site. Then CORS is off in production and `SameSite=Strict` works. A different registrable domain would break the cookie and force `SameSite=None`: do not do that. **If the open frontend question lands on server-side rendering (Thymeleaf + HTMX), revisit: a server session cookie with CSRF tokens fits better than JWT.** | Written down so the frontend decision is made knowing this. |

## 5. Credentials

| ID | Decision |
|---|---|
| D-8 | **Hashing:** `DelegatingPasswordEncoder` with **Argon2id** as the default id (m=19 MiB, t=2, p=1, 16-byte salt, 32-byte hash: the OWASP minimum profile), `bcrypt` (cost 12) accepted as a legacy id, `upgradeEncoding` on successful login. Needs BouncyCastle (see §11). Cap concurrent hash operations at 4 (semaphore). **Policy:** 10 to 128 characters, Unicode-normalised (NFKC), no composition rules, no forced rotation, rejected if on an offline list of ~10k common passwords or equal to the email/local part/association name. A breached-password API (HIBP) is **deferred**: it needs an outbound call and a privacy review. |
| D-9 | **Rate limiting** (in-memory token buckets, bounded cache; the app is one container, so no shared store is needed yet, restart resets counters, accepted). Login: 5 / 15 min per email-hash and 30 / 10 min per IP. Password-reset request: 3 / hour per email-hash, 10 / hour per IP. Register association: 3 / hour per IP. Join request: 5 / hour per IP and 3 / day per (short name, email-hash). Refresh: 60 / min per IP. Public page: 120 / min per IP. Authenticated: 300 / min per user. Answer `429` + `Retry-After`. **No hard account lock** (it lets an attacker lock out a victim); per-email throttling plus a working reset link is the recovery. The client IP is taken from `X-Forwarded-For` only from the configured trusted proxy (`server.forward-headers-strategy` set deliberately), otherwise the limiter is bypassable by spoofing. The per-IP limit is generous because mobile carriers NAT many users behind one address. |
| D-10 | **Uniform responses.** Login: always `401 {"code":"INVALID_CREDENTIALS"}` for unknown email, wrong password, unconfirmed membership, inactive or anonymised member; unknown email still verifies a precomputed dummy hash; the true reason goes to the audit log by id. Password-reset request: always `202`, mail sent asynchronously so timing does not differ. Register association: no email lookup exists (founder email is only unique per association), so nothing to enumerate; `ShortNameAlreadyTaken` is a 409 and is fine because short names are public. Join request: D-14. |
| D-11 | **Email verification: yes, mandatory, and it is the same mechanism as activation and reset.** One table `account_token(id, user_id or membership_id, purpose ACTIVATE/RESET, token_hash, expires_at, used_at)`: 256-bit opaque value, hashed, single use, newest of a purpose invalidates older ones. ACTIVATE valid 7 days (admins approve at night, members act days later), RESET 30 minutes. Link form `https://<app>/set-password#token=...` (fragment: not sent to the server, not in access logs or Referer); the PWA POSTs the token and the new password. A membership is created `PENDING` and becomes `CONFIRMED` only when its link is consumed, so (L3) nobody gets a password without owning the mailbox and (L4) nobody can attach a membership to someone else's account. Consuming a token bumps the security stamp and revokes refresh families. |
| D-11a | **Password reset: in MVP.** It is the same table and endpoints as activation (`POST /auth/password-reset-requests {email}` -> `202`, `POST /auth/password-resets {token, password}`), so it costs almost nothing, and without it a forgotten password has no recovery path. Magic-link login stays an option for later: it is another `purpose` on the same table. It is not in #26. |
| D-11b | Email delivery goes through an `infrastructure.security` port (`AccountLinkSender`). #26 ships it with a `dev`/`test`-profile adapter only (the link is exposed to tests, never logged in a `prod`-like profile). The SMTP adapter is Phase 2 and a go-live blocker (G3). Activation/reset tokens are opaque, not JWTs, so they can never be accepted as access tokens. |

## 6. User and member mapping

```
app_user(id, email unique lower-case, password_hash nullable until activated,
         security_stamp, status, created_at)
membership(id, user_id, association_id, member_id, status PENDING|CONFIRMED,
           unique(association_id, member_id), unique(user_id, association_id),
           FK (association_id, member_id) -> members)   -- tenant-consistent, like payments
```

- `app_user` is an identity table, not tenant business data: it has no `association_id` and is
  the documented exception to "every business table" in §8. All tenant data hangs off
  `membership`, whose composite FK keeps `member_id` inside its tenant.
- Created by the provisioner after `RegisterAssociation` (founder) and after `ApproveJoinRequest`
  (via the `Notifier.memberApproved` hook, post-commit, failures logged by id). If an `app_user`
  with that email already exists and is verified, only a `PENDING` membership is added.
- MVP login: exactly one `CONFIRMED` membership is supported. With more than one the login
  **fails closed** (log by ids) until a selection step exists (follow-up, not #26).
- Admin recovery for a member whose provisioning failed: `POST /api/v1/members/{id}/activation-links`
  (admin only, tenant-scoped, idempotent).
- Erasure (future RGPD use case) must: go through `AdminGuard` (§13), anonymise `Member` and
  `JoinRequest`, **delete the `app_user` row and its memberships/tokens when it was the user's
  only membership**, and revoke refresh families. That needs an application-owned port (for
  example `CredentialsEraser`, implemented in `infrastructure.security`) because the application
  layer cannot import security. Record it in the erasure issue. Deactivation needs no extra
  step: D-6 rejects the next request.

## 7. Authorization, error mapping and HTTP hygiene

**Decisions in this section**

| ID | Decision |
|---|---|
| D-12 | The tenant comes only from `AuthenticatedActor`. No route has an association id, no DTO has tenant/role/version fields, unknown JSON properties are rejected, other-tenant ids answer 404 exactly like nonexistent ones. Enforced by tests (§10.5). |
| D-13 | One exception advice, one error shape, generic 500, headers/size limits/actuator/logging rules below. Spring's own handlers must not log rejected values. |
| D-14 | `POST .../join-requests` answers `202 {"status":"RECEIVED"}` for **all three** outcomes (created, already a member, pending request exists) and returns no request id (the applicant never needs it: they hear back by email). The web layer maps `JoinRequestNotPossibleException` to that same response; a unique-index race is swallowed the same way. The timing difference (no insert on the duplicate path) is accepted and bounded by the rate limits. Unknown short name stays 404 (public information). Notifying the mailbox owner of a duplicate attempt and confirming the email before the admin sees the request are P1 (needs the email adapter). |

**Authorization rules**

1. Default deny: `anyRequest().authenticated()`; public routes listed explicitly:
   `POST /api/v1/public/associations`, `GET /api/v1/public/associations/{shortName}`,
   `POST /api/v1/public/associations/{shortName}/join-requests`, and the `/api/v1/auth/*` set
   (login, refresh, logout, activate, password-reset-requests, password-resets).
2. Controllers get the caller as `@AuthenticationPrincipal AuthenticatedActor` and convert it to
   `Actor` in one place. **No route contains an association id; no DTO has `associationId`,
   `tenantId`, `memberId` of the caller, `roles` or `version`.** `memberId` in a path is only
   ever the *target* of an admin operation.
3. The use cases remain the authority on roles (`Permissions`). The web layer adds no parallel
   role matrix (two rule sets drift); it only guarantees an authenticated, active, tenant-bound
   actor. Every endpoint must map to a use case that either checks a role or only touches the
   actor's own data; a PR adding an endpoint that reads other people's data without a check is
   rejected in review.
4. Cross-tenant ids behave exactly like nonexistent ids (404).

**Exception to HTTP** (single `@RestControllerAdvice` in `infrastructure.web.exception`, most
specific first; body `{"code","message","requestId"}`; code chosen by an explicit mapping, not by
reflecting on class names)

| Exception | Status | Notes |
|---|---|---|
| Bean validation, malformed JSON, unknown property, wrong content type | 400 / 415 | message generic ("Invalid request"); field names only, **never rejected values** |
| `NotAllowedException` | 403 | before the generic business handler |
| any `*NotFoundException`, `ShortNameNotFoundException` | 404 | also for other-tenant ids |
| `JoinRequestNotPossibleException` | 202 | same as success (D-14) |
| `ShortNameAlreadyTakenException`, `MemberEmailAlreadyUsedException`, `LastAdministratorException`, `AggregateModifiedConcurrentlyException` (after the 5 retries), `*ModifiedConcurrentlyException`, `DuplicateBookingException` | 409 | |
| other `BusinessRuleException` (window closed, session full, overlap, ...) | 422 | English message shown as-is (§12); messages carry ids and Lisbon-formatted times, no names/emails |
| authentication missing/invalid | 401 | constant body, `WWW-Authenticate: Bearer` without `error_description` |
| rate limited | 429 | `Retry-After` |
| anything else, including `Invalid*Exception` invariants, data-integrity errors | 500 | `{"code":"INTERNAL_ERROR"}`; log class + ids + requestId, never the message when it can hold data |

The 401 entry point and 403 access-denied handler of Spring Security must write the same error
shape (filter-chain errors do not reach `@RestControllerAdvice`).

**HTTP hygiene**

- Headers on every `/api/**` response: `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`,
  `Referrer-Policy: no-referrer`, `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`,
  `X-Frame-Options: DENY`; HSTS (`max-age` one year, `includeSubDomains`) set once TLS terminates
  in front of the app (platform/proxy; Spring emits it only on HTTPS requests).
- CORS: off in production (same origin, D-7b). A `dev` profile allows the configured local PWA
  origin only: explicit methods and headers (`Authorization`, `Content-Type`), credentials only
  for `/api/v1/auth/**`, never `*`.
- Size limits: JSON body <= 64 KiB (filter rejecting larger `Content-Length` and unknown length),
  header size 8 KiB, multipart disabled, `@Size` on every string field and list (for example
  `levelNames` <= 20 items), page size <= 100, `FAIL_ON_UNKNOWN_PROPERTIES=true`.
- Error pages: `server.error.include-message|stacktrace|binding-errors|exception=never`.
- Actuator: not a dependency today; **do not add it in #26**. When the container needs a health
  check: expose only `health` (liveness/readiness), `show-details=never`, on a separate management
  port not published by the proxy; never `env`, `beans`, `heapdump`, `threaddump`, `loggers`,
  `mappings`. Same for any OpenAPI UI: off under `prod`.
- Logging: ids only (association, member, user, request); MDC carries `requestId`. Never log
  `Authorization`, `Cookie`/`Set-Cookie`, bodies, passwords, tokens, emails, names, phones.
  `spring.mvc.log-request-details=false`; no Hibernate bind logging; `org.springframework.security`
  stays at INFO. Security events (login success/failure, refresh reuse, password reset, token
  consumed, role grant/revoke, deactivation, payment record/reverse) are single structured lines
  with ids; the client IP appears **only** in auth-failure and rate-limit events, retained
  <= 30 days (an IP address is personal data). Mention it in the privacy notice.

## 8. Privacy decisions (RGPD)

- Minimisation: the token holds ids only; no PII in claims, in logs or in error bodies.
- Credentials are personal data: `app_user.email` and the hash are deleted on erasure (§6);
  `account_token` and `refresh_token` rows are purged after expiry (daily job or on write).
- Consent: unchanged (stored at join, US-05). Residual risk R2 (consent given by a non-owner
  of the mailbox) is why the join-request confirmation link is a pre-go-live follow-up.
- Auth-failure IP logging: legitimate interest (security), 30-day retention, stated in the notice.
- EU hosting and TLS: a Release/Operate concern, not #26 (nothing is deployed).

## 9. Prioritized implementation checklist

Suggested split of #26 into linked sub-issues (the `github-issues` skill creates them; not done
here): **26a** security foundation, **26b** credentials/sessions, **26c** controllers. 26a first,
because 26c cannot be reviewed without the principal and the error mapping.

**P0: required to merge #26**

1. (26a) Dependencies (§11) and `SecurityFilterChain` in `infrastructure.security`: stateless, default-deny,
   explicit public list, CSRF handling per D-7a, security headers, 401/403 JSON handlers.
2. (26a) Pinned ES256 `JwtDecoder` (custom Nimbus processor) and `JwtEncoder`, `kid` public-key set, `iss`/`aud`/`exp`
   validators, skew 30 s; fail-fast on bad/missing keys under `prod`; dev-only ephemeral keypair (D-1..D-4).
3. (26a) `PrincipalResolver` + `AuthenticatedActor` + the only `new Actor(...)` (D-5, D-6).
4. (26a) `@RestControllerAdvice` with the §7 table; log hygiene (E2); `server.error.*`.
5. (26b) Flyway V9: `app_user`, `membership`, `refresh_token`, `account_token` (hashes only, composite FK).
6. (26b) Login, refresh (rotation, reuse detection, grace window), logout, logout-all, activate,
   password-reset request/confirm; Argon2id; policy; dummy-hash; rate limiter (D-7..D-11).
7. (26b) Provisioner on founder registration and `memberApproved`; dev/test `AccountLinkSender` (D-11b).
8. (26c) Controllers for the existing use cases, request DTOs without tenant fields, `*WebMapper`s;
   join request uniform `202` (D-14); no `GenerateSessions` endpoint.
9. Tests of §10, including the route-inventory test.
10. Update `docs/architecture.md` §11 with D-1..D-11 summary and the §8 exception for `app_user`.

**P1: before the first deployment (separate issues, label `security`)**

- SMTP adapter implementing `AccountLinkSender` and the `Notifier` (G3).
- Join-request email confirmation (R2) and the notice to the mailbox owner when a duplicate is attempted.
- `prod` profile checks (D-2, D-3), proxy/forwarded-header config, HSTS at the edge, CORS review.
- Branch protection on `main` once CI has run green on GitHub (still unset).
- Trivy / Dockerfile work already planned; non-root, secrets from the platform store.
- Erasure and export use cases with `CredentialsEraser` and `AdminGuard` (§6).

**P2: later**

- Multi-membership selection at login; magic-link login; breached-password check; shared rate-limit
  store if more than one instance runs; alerting on auth-failure spikes (needs a deployed log sink);
  DAST (ZAP) once a staging instance exists; Cosign signing.

## 10. Security tests #26 must include

Functional security tests in the suite come before any scanner (agent policy). Unit-level tests use
the existing style (AAA, `assertThrows`, AssertJ). The tests that prove isolation use the **real filter
chain** (`@SpringBootTest` + MockMvc/WebTestClient + Testcontainers); `@WebMvcTest` with mocked use
cases cannot prove IDOR.

1. **401/403 matrix:** every route in `RequestMappingHandlerMapping` is classified public or
   authenticated in one test table; an unclassified route fails the build (route-inventory test). No
   token -> 401 on every authenticated route; wrong role -> 403 on every admin/coach route.
2. **Token validity:** expired, not-yet-valid beyond skew, wrong `iss`, wrong `aud`, missing `aid`/`mid`/`sv`,
   wrong signature, **`alg: none`**, `alg: HS256` using the public key bytes as the secret, `alg: RS256`, unknown `kid`,
   token in query string, token in form body -> all 401 with the same body.
3. **Immediate revocation:** deactivate member -> next call with a still-valid token is 401; anonymised member
   -> 401; password reset/change/logout-all -> old access token 401 (stamp); membership `PENDING` -> login 401.
4. **Refresh:** rotation returns a new token and invalidates the old; reuse of a rotated token revokes the
   family (and the newest token then fails); grace window does not revoke; expired/absolute-lifetime
   limits; deactivated member cannot refresh; cookie flags (`HttpOnly`, `Secure`, `SameSite=Strict`,
   `Path`); `/auth/refresh` with a foreign `Origin` -> rejected.
5. **Cross-tenant IDOR per endpoint:** two associations seeded; for every endpoint with an id in the path or
   body, call it with tenant B's token and tenant A's ids -> 404 and **no state change** in A (assert rows).
   Also assert no DTO/request record has a component named `associationId`/`tenantId`/`roles`/`version`
   (plain JUnit scan, consistent with `OnionArchitectureTest`), and that `new Actor(` appears only in
   `infrastructure.security`.
6. **Same-tenant authorization per endpoint:** member -> admin route = 403; coach on another coach's session =
   403; member cancelling another member's booking = 403 (already in use cases; re-proved over HTTP).
7. **Enumeration uniformity:** login (unknown email vs wrong password vs inactive member): identical status,
   body and headers; join request (new / existing member / pending): identical `202` and body; password-reset
   request (known / unknown email): identical; unknown email still performs a hash verification (assert via
   a spy on the encoder).
8. **Rate limit:** the sixth login in the window -> 429 with `Retry-After`, for unknown email too; join request,
   register and reset limits; limiter does not trust `X-Forwarded-For` from a non-trusted source.
9. **Error mapping:** one test per row of the §7 table; 500 body contains no exception text; validation error
   body contains field names but not the submitted value; **log-capture test** (Logback `ListAppender`)
   proving a rejected email value, a password, a token and an `Authorization` header never reach the log.
10. **Credentials:** Argon2id hash stored, bcrypt hash upgraded on login, 9-character and common passwords
    refused, activation token single use / expired / reused, newer token invalidates older, token never
    appears in logs, cannot set a password without a valid token (pre-hijack test).
11. **Booking over HTTP:** concurrent last-seat request test through the controller (adds to the existing
    use-case race tests).
12. **Config safety:** `prod` profile with a missing, non-P-256, kid-less or mismatched signing key, or the default DB
    password, fails to start; the ephemeral dev keypair exists only under profile `dev`; rotation overlap works (token
    signed by the old key verifies while both public keys are configured and fails once the old one is removed).

## 11. New dependencies and trade-offs

| Dependency | Why | Trade-off |
|---|---|---|
| `spring-boot-starter-security` (+ the Boot 4.1 security test starter) | filter chain, password encoders | confined to `infrastructure.security` by `OnionArchitectureTest`; verify exact artifact names against Boot 4.1's BOM before editing the pom |
| OAuth2 resource server starter (brings `spring-security-oauth2-jose` -> `nimbus-jose-jwt`) | decode/validate and encode JWTs with a maintained library | one dependency family and no hand-rolled JWT parsing; Nimbus supports ES256 natively (`ECKey`, `ECDSASigner`/`ECDSAVerifier`), but Spring's `NimbusJwtDecoder.withPublicKey` shortcut is RSA-only, so the EC decoder is a ~30-line custom `JWTProcessor` (algorithm-pinned key selector) that we own and test; EC keys need no extra crypto provider (JDK 21 has P-256). Avoid adding a second JWT lib (jjwt, java-jwt) |
| BouncyCastle (`bcprov`), required by Spring's `Argon2PasswordEncoder` | Argon2id | extra supply-chain surface (Dependabot + SBOM cover it). Fallback if rejected: bcrypt cost 12, which needs nothing extra but is not memory-hard |
| `bucket4j-core` (+ Caffeine for a bounded key cache) | rate limiting | in-memory: counters reset on restart and are per instance; fine for one container and one pilot. Not BOM-managed: Dependabot will track it. Alternative is ~60 lines of own code, which we would then own and test |

No Spring Session, no OAuth2 authorization server, no external identity provider: out of proportion
for one pilot association.

## 12. Residual risks (accepted for now)

- **R1** Access-token theft is usable for up to 10 minutes unless the stamp changes (password change,
  logout-all, reset). Accepted.
- **R2** Join requests are not email-confirmed in #26: a stranger can file a pending request (and a
  consent) under another person's address. Mitigated by admin approval, rate limits and tenant-only
  visibility; fixed by the P1 confirmation link.
- **R3** Founder provisioning is not atomic with association creation (G2/P7). Logged by id; operator
  recovery at pilot scale.
- **R4** The ES256 private key lives in the app's environment or secret store; anyone who can read it can mint
  tokens for any tenant and role. Asymmetry limits the blast radius (public keys and a future verifier hold
  nothing secret) but does not protect against reading the signing key itself. Mitigation: secret store with
  restricted access, key never in the repo/image/logs, rotation by overlap on suspicion of leak (D-2; within
  15 minutes the old key is gone). An HSM/KMS-backed signer is out of proportion for one pilot.
- **R5** Rate-limit state is in memory and per instance.
- **R6** ~~Real onboarding cannot happen until the SMTP adapter exists (G3).~~ Closed by issue #40 (section 14); what remains is operating a real mail server (SPF / DKIM / DMARC for the sender domain, bounce handling).

## 13. Delivery notes for 26c (issue #32)

What was built against this model, and where it differs.

- **Delivered:** controllers, DTOs and web mappers for every Phase 1 use case (architecture.md section 14); the public page, registration and
  join request with uniform answers (P1, D-14); the per-(association, email) join limit (3 / day) and the authenticated per-user limit
  (U7, 300 / min); the route inventory, error-mapping and cross-tenant IDOR matrices (10.1, 10.5, 10.6), enumeration uniformity (10.7),
  rate limits (10.8), log capture over a whole HTTP journey (10.9), the last seat over HTTP (10.11) and the audit lines of M5.
- **Deviations:**
  1. Input an aggregate refuses is an `InvalidFieldException` (422, naming the field): a plan whose terms do not fit its type, a blank plan
     name, and a level order that is not a permutation of the levels (`levelIds`). The IDOR matrix showed the last one was a 500 a client could
     trigger with another association's level id. Every `Invalid*Exception` invariant stays a generic 500 (section 7 is unchanged); only
     reconstitution invariants (for example a negative version) still throw them.
  2. Registration answers `201 {"shortName"}` and nothing else (no ids), so the answer cannot differ with the founder's email (D-10). A taken
     short name is still a 409 (public information).
  3. M7 (page size at most 100) is met by bounding the two lists that could grow instead of paginating them: pending join requests are capped
     at 100 oldest first, and the payment-status list and CSV are limited to a window of at most two years on the subscription's end date.
  4. `POST /members/{id}/activation-links` (admin re-sends an activation link, section 6 and P7) was built in issue #38
     (`ResendActivationLinkService`): admin only, `202 {"status":"RECEIVED"}` whatever was found (a PENDING member, one who has already
     activated, a disabled account, a deactivated member), the work done off the request thread (`BackgroundWork`, as for password-reset
     requests, D-10, but in its own `ADMIN_RESEND` lane so resends cannot starve reset mail or drop it), 30 per hour per association, 3 per hour
     per member and 6 mails per hour per recipient address across associations (`ASSOCIATION_RESEND`, `ACTIVATION_RESEND`,
     `LINK_MAIL_PER_ADDRESS`; 429 `Retry-After`), the new link superseding the old one and
     mailed to the *account's* address. A member with no membership at all (provisioning failed after the commit, P7) is provisioned again
     through the idempotent `AccountProvisioner`. The only distinction a caller can observe is 404 for an id outside their own association,
     as on every other route (D-12).
  5. `GET /sessions/{id}/roster` (issue #38, `GetSessionRosterService`) gives the session's coach or an administrator the live bookings with
     booking ids, member ids and display names (no email, no phone), so a coach can call `PUT /sessions/{id}/attendance`.
  6. The OpenAPI document is generated at build time (springdoc in test scope) and committed; the application serves no documentation
     endpoint, so "off under `prod`" holds by construction (section 7, HTTP hygiene).
- **Added after review:** every refusal that depends on the input alone now precedes the throttle and every lookup in `SubmitJoinRequestService`
  (it used to follow the member / pending lookup, which made a missing consent an enumeration oracle, P1); join requests undecided after 30 days
  are anonymised daily (S1); dates a client types are bounded to 2000 to 2100 (S4); the CSV guard looks past invisible leading characters (S3);
  the IDOR matrix covers mixed own / foreign ids, a coach caller, the credential tables in its fingerprint and the notifier and mailer (S2).
- **Found by the new tests and fixed:** `RateLimiter` read `Clock.millis()`, which throws `ArithmeticException` on the production clock
  (`Clock.tick(systemUTC, 1 microsecond)`), so every rate-limited call would have been a 500 outside the tests (which use a fixed clock). The
  meter now reads `instant()`.

## 14. Delivery notes for the email adapter (issue #40)

Closes G3 and R6 as far as the code goes, and the registration item of the go-live list (#35).

- **Delivered:** `SmtpAccountLinkMailer` (activation and password-reset links) and `SmtpNotifier` (promoted from the waitlist, session
  cancelled, no-show warning to the member and the active administrators, join request approved / rejected) behind the existing ports, over
  Spring's `JavaMailSender`; asynchronous on a bounded two-thread sender (500 tasks, then drop and log) with three retries (5 s, 30 s,
  2 min) and no thread held while waiting; the SMTP settings from the environment with STARTTLS *required* (or implicit TLS) and the server
  name checked; start-up refuses a profile other than `dev` / `test` without a complete, encrypted configuration and an https
  `WEB_ORIGIN`; Mailpit for development; GreenMail in the tests.
- **Links (D-11).** `WEB_ORIGIN/activate#token=...` and `WEB_ORIGIN/reset-password#token=...`, the token in the fragment and
  percent-encoded, so it is in no access log or `Referer`. Account links go to `UserAccount.email()` (#31 S1); the SMTP adapter takes the
  address from the call and never looks up a member.
- **Abuse by visitor-controlled text (#35).** The association's name and a member's name are typed by visitors. Subjects are fixed; no
  greeting uses a name (the mail can reach an address that is not the person's); values are cleaned of control, format and line-separator
  characters, cut at 200 characters and HTML-escaped; links must be http(s). The administrators' no-show warning names the member, and a
  cancellation prints the coach's reason, both cleaned and escaped; the administrator's rejection reason is not passed on by the port.
- **Logs.** Only ids, the link's reference, the attempt number and the exception *class* (`LogCapture` tests over success, failure and
  skipped recipients: no token, address, name, reason or body). JavaMail's debug output is off.
- **Registration per founder email (#35).** `RateLimitRule.REGISTER_EMAIL`: 3 per day on the hash of the founder's email
  (`AttemptThrottle.checkRegistration`), taken after the input is validated and before the short name is looked up, so every attempt counts and the
  limit tells a stranger nothing. Without it, many addresses could flood one mailbox with activation links.
- **Review follow-ups.** The no-show warning resolves and renders everything before queuing any mailbox, so a failing lookup can no longer
  make the member receive two copies. `SMTP_SECURITY=NONE` is refused for any host except `localhost`, loopback and `mailpit`, under every
  profile (one validation, `MailSettings.structuralProblems()`, shared by the sender and the production guard). A task that dies with an
  `Error` frees its queue slot. Permanent failures (an address that does not parse, an unfillable template, an SMTP 5xx) are attempted once.
  Account links and notices have separate queues (100 and 500), so a burst of notices cannot displace a link.
- **Visitor text in mails (N1), decision.** A visitor chooses the association's name (and an applicant's name), and a mail from us carries
  our reputation. Two options were weighed: reject URL-like association names at registration, or neutralise them when printing. Rejecting
  changes a domain invariant (and the public API) and still prints names that merely look odd; neutralising is local to the mail layer and
  covers every visitor-typed value, so `MailTemplates.clean` breaks `://`, `www.` and `@` in text values with a zero-width space (the text
  stays readable, mail clients stop auto-linking it). It is best effort - a bare `evil.example` may still be linked by some clients - so
  the join notices, which reach an address a stranger typed, also say "If you did not ask to join, ignore this message".
- **Accepted:**
  - The queue is in memory (a restart loses pending mail; the user asks again), and the reset path has two asynchronous hops
    (`BackgroundWork`, then the mail dispatcher), each of which can drop under load.
  - Delivery is at-least-once: when the server accepts a message but the connection fails before the client sees the reply, the retry
    sends a second copy. A permanent refusal other than a clean 5xx reply is retried three times before it is given up.
  - `REGISTER_EMAIL` counts the exact address, so `ana+1@x` and `ana+2@x` have a bucket each; the per-IP limit and the activation links'
    single-use rule bound the damage, and a stricter canonical form would also merge distinct people at some providers.
  - The frontend must scrub the token from the address bar right after reading the fragment (`history.replaceState`), or it stays in the
    browser history and in anything that records the URL.
  - The sender domain's SPF / DKIM / DMARC are not the application's. Before go-live the SMTP provider must be hosted in the EU and a data
    processing agreement (DPA) signed: the provider sees recipients' addresses and every link token.
  - The default port is 587 (STARTTLS); `SMTP_SECURITY=TLS` on 587 or STARTTLS on 465 is only a startup warning, since a provider may
    deviate from the usual ports.
