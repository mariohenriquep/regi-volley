# Architecture

This is the source of truth for how regi-volley is structured. It inherits the conventions of
task-manager-api (same layering, same three-model split, same testing style) and adds the
concerns a multi-tenant booking system brings: tenant isolation, time zones and concurrent
booking. Keep it accurate; don't let it drift from what the code actually does. Product scope
and business rules (`US-xx`, `RN-xx`) live in [`requirements.md`](requirements.md).

## 1. Layers and dependency direction

Three concentric layers, dependencies only ever point **inward**:

```
infrastructure  →  application  →  domain
```

- **`domain`** (`com.regivolley.api.domain`) — aggregates, entities, value objects, domain
  services, factories, domain exceptions and port interfaces (repositories, notifier). Nothing outside this package may be imported here.
- **`application`** (`com.regivolley.api.application`) — one use case per operation, plus
  command records. Depends only on `domain`.
- **`infrastructure`** (`com.regivolley.api.infrastructure`) — everything that talks to the
  outside world: `web`, `persistence`, `security`, `notification`, `config`. Depends on both
  inner layers; nothing may depend on it.

**The request flow** (decided by the product owner, 8/10/2026; the factory step is issue #34):

```
Controller  ->  UseCase (interface)  ->  Service (application)  ->  Repository (port)  ->  Adapter  ->  Factory
```

A controller (`infrastructure.web.controller`) only translates HTTP to a command and a result back to a DTO, and calls a `*UseCase`
interface; it never names a `*Service`, a repository port, a factory, an entity or anything in `infrastructure.persistence`. The
`*Service` in `application.usecase` *manages the request*: transaction and retry, authorization, loading through ports, **creating new
aggregates through factories**, invoking domain behaviour, saving, notifications after the commit. Ports are implemented by adapters
in infrastructure, whose mappers **reconstitute** stored aggregates only through factories (`*Factory.reconstitute...`). The
**factories** (`domain.factory`) are therefore the only place an aggregate is created or reconstituted. `OnionArchitectureTest` pins
all of it: a `*Service` lives only in `application.usecase` or `domain.service`; a controller may depend only on `application.usecase`
`*UseCase` interfaces, `application.command`, `application.result`, `web.dto`, `web.mapper`, its own package, and `CurrentActor` /
`AuthenticatedActor` / `RequestIds` from `infrastructure.security`; and an aggregate root or entity is constructed (`new X(`) only
inside `domain.factory`, its own class, or - for an internal entity - its root (section 4, *Factory*).

Mechanically enforced by
`src/test/java/com/regivolley/api/architecture/OnionArchitectureTest.java` - plain JUnit 5, no
architecture library (NFR "Qualidade"). It scans `src/main/java` with `JavaSourceFile`, which
resolves each file's package and every package-qualified reference (imports, static and wildcard
imports, inline fully qualified names; comments and strings ignored). `JavaSourceFileTest` proves
the scanner itself detects violations, so a rule can't pass vacuously. A new rule is a new `@Test`
in `OnionArchitectureTest`. `JavaSourceFile` also reports the top-level type's name, kind
(class/record/enum/interface), modifiers and implemented interfaces, which the building-block
rules in §4 use.

## 2. Domain purity

`domain/` is plain Java with **zero framework dependencies** — no Spring, no JPA/Hibernate, no
Jackson, no Lombok. Aggregates hand-write constructors, accessors, `equals`/`hashCode`.
`@Service`/`@Component` on application-layer classes is accepted as a DI marker. Nothing from
`org.springframework..`, `jakarta.persistence..` or `jakarta.validation..` may appear in
`domain/`; nothing from `org.springframework.security..` may appear in `domain/` or
`application/`.

## 3. The three models, never mixed

Every concept has three distinct types: the domain aggregate (`Booking`), the persistence record
(`BookingJpaEntity`, `infrastructure.persistence.entity`) and the wire shape (`BookingResponse`
and request DTOs, `infrastructure.web.dto`). Translation happens only in explicit mappers
(`*PersistenceMapper`, `*WebMapper`). A controller never returns a JPA entity, and a JPA entity
never appears in `domain/`.

## 4. Package placement

| What | Package |
|---|---|
| Aggregate roots (`Session`, `Plan`, `Subscription`, `Payment`, `Association`, `Member`, `JoinRequest`, `TrainingGroup`, `Venue`) and the entities inside them (`Booking`, `Level`) | `domain.model.entity` |
| Factories (one `*Factory` per aggregate root: `SessionFactory`, `PlanFactory`, `SubscriptionFactory`, `PaymentFactory`, `AssociationFactory`, `MemberFactory`, `JoinRequestFactory`, `TrainingGroupFactory`, `VenueFactory`) | `domain.factory` |
| Value objects: ids, `Money`, `BookingPolicy`, `ContactDetails`, status/type/role enums, ... | `domain.model.valueobject` |
| Outcomes returned by aggregates that contain entities (`BookingResult`, `CapacityChange`, ...) | `domain.model.result` |
| Domain services and their inputs/outputs (`BookingEligibility`, `BookingTarget`, `PaymentLedger`, ...) | `domain.service` |
| `AggregateRoot` / `Entity` / `ValueObject` markers, shared validation (`FieldRules`) | `domain.shared` |
| Domain exceptions | `domain.exception` |
| Repository ports (interfaces only, one per aggregate root) | `domain.repository` |
| Other outbound ports (e.g. `Notifier`) | `domain.port` |
| Use case interface (`*UseCase`) + implementation (`*Service`) and their package-private helpers (`UnitOfWork`, `SeatPromoter`, ...) | `application.usecase` |
| Outbound ports the application owns that are not about the domain (`TransactionRunner`, the credential `*Store`s, `PasswordHasher`, `AccessTokenIssuer`, `PrincipalVerifier`, `CredentialAttemptThrottle`, `SecretGenerator`, `BackgroundWork`, `CommonPasswordList`, `AccountLinkMailer`, `AccountProvisioner`, `CredentialsEraser`); interfaces only, no Spring | `application.port` |
| Application-owned credential vocabulary: `UserAccount`, `Membership`, `RefreshToken`, `EmailLink`, `AccessToken`, `AccountLink`, their status enums and lifetimes (`RefreshTokenPolicy`, `EmailLinkPolicy`); plain Java, no Spring (an architecture rule) | `application.identity` |
| Exceptions the application raises for its own refusals (`InvalidCredentialsException`, `InvalidRefreshTokenException`, `InvalidLinkException`, `RateLimitExceededException`, `ServiceBusyException`, `AccountAlreadyExistsException`, `LinkAlreadyIssuedException`) | `application.exception` |
| Use case input records | `application.command` |
| Use case output records | `application.result` |
| REST controllers | `infrastructure.web.controller` |
| Request/response DTOs, error shape | `infrastructure.web.dto` |
| `@RestControllerAdvice` exception mapping (`*ExceptionHandler`) | `infrastructure.web.exception` |
| Domain ↔ DTO translation (`*WebMapper`) | `infrastructure.web.mapper` |
| JPA entities (`*JpaEntity`) | `infrastructure.persistence.entity` |
| Spring Data repository interfaces (`*JpaRepository`, package-private) and repository port implementations (adapters) | `infrastructure.persistence.adapter` |
| Entity ↔ domain translation (`*PersistenceMapper`) | `infrastructure.persistence.mapper` |
| Spring Security configuration, filters (`*Filter`), entry point / access-denied handler, JWT keys, decoder and issuer, `PrincipalResolver`, `AuthenticatedActor`, `SecurityAccountLookup` (the per-request account read that `persistence.adapter` implements), the JWT adapters (`JwtAccessTokenIssuer`), the password and secret adapters (`Argon2PasswordHasher`, `SecureSecretGenerator`, `BundledCommonPasswordList`), `RateLimiter` and its filter, the cookie guard, `ExecutorBackgroundWork` | `infrastructure.security` |
| Notification sending (adapter for `Notifier`; logs ids only until the email adapter of Phase 2) | `infrastructure.notification` |
| `TransactionRunner` implementation (`REQUIRES_NEW` template) | `infrastructure.persistence.adapter` |
| Spring `@Configuration` beans that are not security (clock, scheduler, the start-up guard on the database secret) | `infrastructure.config` |
| The credential `*Store` adapters and `SecurityAccountLookupAdapter` (`*StoreAdapter`) | `infrastructure.persistence.adapter` |
| `app_user` / `membership` / `refresh_token` / `email_link` JPA entities (`UserAccountJpaEntity`, ...) and their `*PersistenceMapper`s | `infrastructure.persistence.entity` / `.mapper` |
| Credential endpoints (`AuthController`), its package-private `RefreshCookie` helper, the request/response records (`LoginRequest`, `AccessTokenResponse`, ...) and `AuthWebMapper` | `infrastructure.web.controller` / `.dto` / `.mapper` |
| Mail for account links (`AccountLinkMailer` adapter; logs the link reference only until the SMTP adapter of Phase 2) | `infrastructure.notification` |

A class that doesn't fit one of these rows is a signal to reconsider the design — flag it rather
than inventing a package ad hoc. If `domain.model.entity` grows unwieldy, splitting it per
aggregate (`domain.model.entity.booking`, ...) is a deliberate change to this table, not a
drive-by.

### DDD building blocks

The packages are the DDD building blocks, and `OnionArchitectureTest` enforces them (§1):

- **Aggregate root** (`implements AggregateRoot`) - owns a consistency boundary and its
  invariants and state machine; the only thing a repository loads or saves, and the only thing
  other aggregates may point to (by id). Lives in `domain.model.entity`.
- **Internal entity** (`implements Entity`) - has an identity but exists inside one aggregate
  (`Booking` in `Session`, `Level` in `Association`). It sits in the same package as its root, so
  its transition methods stay package-private and reachable only through the root. Its constructor is `public` (the factory of
  its root reconstitutes it from storage, and Java cannot grant a class in another package less than that); the root creates
  new ones, and `OnionArchitectureTest` lets nothing else call it (see *Factory*). No repository of its own.
- **Value object** (`implements ValueObject`) - immutable, defined by its values, validates
  itself. A record or an enum (a final immutable class only when an accessor must differ from
  the component, e.g. `ContactDetails.phone()` is an `Optional`). Never depends on entities,
  results or domain services. Lives in `domain.model.valueobject`.
- **Result** - the record an aggregate method returns when one operation changes the root and
  its inner entities together (`BookingResult`, `CapacityChange`, ...). `domain.model.result`.
- **Domain service** - domain logic that spans aggregates and fits none of them
  (`BookingEligibility`); it takes plain facts, no ports. Most are stateless; `PaymentLedger` is an immutable
  value-holding service, built from the facts of one subscription (its price and payments) and answering questions
  about them - accepted explicitly, as it still holds no ports and no mutable state. Types named `*Service`,
  `*Eligibility`, `*Calculator`, `*Evaluator` or `*Specification` live only in `domain.service`,
  and nothing there is an entity or value object.
- **Factory** - creates a **new** aggregate (id generation, defaults, the initial state, creation rules that span several
  aggregates) and **reconstitutes** a stored one (`create...` / `reconstitute...`); the only place either happens. One final,
  stateless `*Factory` class per aggregate root in `domain.factory`, with static methods (no state, no collaborators, no
  wiring: ids come from `XId.generate()` and time from the `Clock` the caller passes in). The reconstitution of an internal
  entity goes through its root's factory (`SessionFactory.reconstituteBooking`, `AssociationFactory.reconstituteLevel`). Creation
  rules that need other aggregates live here: `SubscriptionFactory` (no overlap with the member's active subscriptions, RN-16, and
  the snapshot of the plan's terms and price), `MemberFactory.fromApprovedJoinRequest` (the member a join request becomes, at the
  entry level, RN-20), `SessionFactory.createSessionsFor` (one session per occurrence a `TrainingGroup` reports as missing,
  `TrainingGroup.occurrencesToGenerate`), `PaymentFactory` (a payment and its reversal). A factory depends only on
  `domain.model.*`, `domain.shared`, `domain.exception` and other factories in `domain.factory`; a domain service may use one (`PaymentLedger` hands back the payment
  the factory builds), but the model never does.
  **Constructors validate and are public.** Every invariant of an aggregate is checked in its constructor, and every
  transition builds its result through the same constructor, so no path yields an invalid aggregate. Java has no "friend" for a
  class in another package, so the constructor is `public` for `domain.factory` to reach it; the price is that anything could
  call it, which is why `OnionArchitectureTest` closes every other way in (all on `src/main`, each with a non-vacuity check and a
  planted-violation test):
  - `new X(` / `X::new` for a type of `domain.model.entity` is allowed only in `XFactory`, in `X` itself and - for the internal entities
    `Booking` and `Level` - in their root (`Session.book` makes the `Booking`, `Association.addLevel` the `Level`: the root guards its
    parts). So a factory builds only its own root and that root's internal entities, never another aggregate.
  - A type in `domain.model.entity` declares **no non-private static method** except an explicit allowlist (today only
    `Subscription.holdingPlaceFor`, a finder over a collection that creates nothing). Package-private counts as non-private, so a
    `static X create(...)`, `of(...)`, `duplicate(...)` or any other static route to a new instance is refused whatever it is called.
  - `XId.generate()` appears in `domain.model.entity` only as `BookingId` in `Session` and `LevelId` in `Association`: a root numbers
    the parts it creates, and cannot mint a new instance of itself (a `Plan.duplicate()` would need `PlanId.generate()`).
  - Reflection and method handles (`java.lang.reflect`, `java.lang.invoke`, `getDeclaredConstructor`, `newInstance`, `setAccessible`,
    `Class.forName`, ...) are used only in `infrastructure`.
  - A factory is a stateless utility class: no instance field, no instance method, only a private constructor.
- **Repository** - one port per aggregate root in `domain.repository`; adapters in
  `infrastructure.persistence.adapter`.
- **Shared kernel** - `domain.shared` holds the three markers and the validation helper
  `FieldRules`; it depends on nothing in the project except `domain.exception`.

The same test also pins the factories: `*Factory` types in the domain only in `domain.factory` and only final classes there, one for
every aggregate root, depending only on the model, the shared kernel, the domain exceptions and other factories (never on ports or
domain services), never used by the model, and the construction, static-method, id, reflection and statelessness rules above. The "persistence mappers reconstitute through
factories" and "services create only through factories" rules each carry a non-vacuity check and a planted-violation test.

The same test also pins the infrastructure naming: `*Request`/`*Response`/`*Dto` only in
`infrastructure.web.dto` (and no record there other than a `*Response`, nested ones included, has a component named `associationId`,
`tenantId`, `roles` or `version`: the tenant comes from the token), `*Controller` only in `infrastructure.web.controller`, `*ExceptionHandler`
only in `infrastructure.web.exception`, servlet filters (anything touching `jakarta.servlet.Filter` or
`org.springframework.web.filter`) and `*PrincipalResolver` only in `infrastructure.security`; `com.nimbusds` only in
`infrastructure.security`, and `org.springframework.security` only there plus exactly `AccessDeniedException` and
`AuthenticationException` in the exception advice; `infrastructure.web` never touches persistence or
the repository ports; exactly one class, `AuthenticatedActor`, calls `new Actor(`; `*WebMapper` in `infrastructure.web.mapper`, `*PersistenceMapper` in
`infrastructure.persistence.mapper`, `*JpaEntity` in `infrastructure.persistence.entity`,
`*JpaRepository` in `infrastructure.persistence.adapter`; and every domain type must sit in one
of the domain packages above.

### Domain vocabulary (code is in English)

| Requirements (PT) | Code |
|---|---|
| Associação | `Association` |
| Pavilhão | `Venue` |
| Nível | `Level` |
| Turma | `TrainingGroup` (`Class`/`Group` clash with Java/SQL) |
| Sessão | `Session` |
| Membro | `Member` |
| Reserva | `Booking` |
| Lista de espera | `Booking` in status `WAITLISTED` |
| Plano | `Plan` |
| Subscrição | `Subscription` |
| Pagamento / Estorno | `Payment` / reversal (a `Payment` with `reversalOf` set; never `Refund`) |
| Falta | `NO_SHOW` |
| Evento | `Event` |
| Pedido de adesão | `JoinRequest` |
| Nome curto (URL) | `ShortName` |
| Consentimento RGPD | `GdprConsent` |

## 5. Patterns in use

- **Repository (port/adapter)** — ports in `domain.repository`, adapters in
  `infrastructure.persistence.adapter`. Application code never touches Spring Data or JPA.
- **Command pattern for use cases** — `UseCase<IN, OUT>`, one class per operation, reached through its interface from the controller
  (request flow, §1). Helpers that several services share (`SessionIssuer`, `EmailLinkIssuer`, `CredentialLinkConsumer`) are
  package-private in `application.usecase`.
- **Factory (static)** — `domain.factory`, one per aggregate root; services create through them, persistence mappers
  reconstitute through them (section 4, *Factory*).
- **Immutable aggregates with self-validating transitions** — state machines (RN-05 for
  `Session`, RN-12 for `Booking`, RN-18 for `Subscription`) live in the aggregate as an
  allowed-transitions map, exactly like `Task` in task-manager-api. A service never decides from
  outside whether a transition is legal.
- **Domain decides, application orchestrates** — "can this member book?" (RN-06/07/11/18/21) is
  answered by domain objects given the facts; the use case only loads those facts through ports
  and persists the outcome.
- **Explicit mappers** — plain static methods, no MapStruct/ModelMapper.

## 6. SOLID / GRASP, applied

- **SRP** — one use case per operation; controllers only translate HTTP ↔ use case.
- **OCP** — a new plan type (RN-13) or transition is a new case/entry, not a rewrite.
- **DIP** — application depends on ports it owns in `domain`; Spring wires the adapters.
- **Information Expert** — the aggregate that owns the data enforces the rule (capacity lives in
  `Session`, balance in `Subscription`).

## 7. Testing conventions

Identical to task-manager-api:

- **Domain** — plain JUnit 5, no Spring context.
- **Application** — JUnit 5 + Mockito, ports mocked, no Spring context.
- **Persistence** — `@DataJpaTest` + Testcontainers (real PostgreSQL), extending
  `AbstractPostgresIntegrationTest`.
- **Web** — `@WebMvcTest` + MockMvc, use cases mocked (the `*UseCase` interfaces, never the services). Security and error-mapping tests use the **real filter chain**,
  advice and controllers (`AbstractSecuredWebTest`: `@WebMvcTest` importing `SecurityConfiguration`, a mocked
  `MemberRepository` and the in-memory `SecurityAccountLookup`); test-only controllers live outside `com.regivolley.api`
  and are `@Import`ed so they never enter the route inventory.
- **Architecture** — plain JUnit 5 (`OnionArchitectureTest`, see §1).
- **Assertions** — JUnit `assertThrows` for code that must throw, AssertJ `assertThat` for
  everything else; no `assertThatThrownBy`. When a use case throws, verify nothing was saved.
- **Arrange-Act-Assert** — every test has `// Arrange`, `// Act`, `// Assert` blocks; for
  throwing code, Arrange declares `Executable act = () -> ...` and Act is
  `assertThrows(Type.class, act)`. Exempt: the empty `contextLoads` smoke test.
- **Coverage** — JaCoCo ≥ 85% line and branch, enforced by `mvn verify`.

Additionally required here (see §8–§10): a **tenant-isolation test** for every repository adapter
and a **concurrent-booking test** for the booking path.

TDD flow: failing test first, at the layer the logic belongs to, then the minimum to pass.

## 8. Multi-tenancy

One shared database. **Every business table has a non-null `association_id`** and every
repository port method that reads business data takes an `AssociationId` — there is no
"find by id" without the tenant. The tenant comes from the authenticated principal in
`infrastructure.security`, never from a request body or path supplied by the client for
anything but public pages (US-24/26, resolved by the association's unique short name).

Each persistence adapter test includes an isolation case: data from association A is never
returned when querying as association B.

**The one exception: the credential tables.** `app_user` is a *person's* identity, not tenant business data, and one person
may later belong to several associations, so it has no `association_id`. Neither have the tables that hang off the account
(`refresh_token`, `email_link`); every association-bound fact lives in `membership(user_id, association_id, member_id, ...)`,
whose composite foreign key to `members(association_id, id)` keeps the member inside its tenant, and both `(association_id,
member_id)` and `(user_id, association_id)` are unique. The only code that reads a membership from a member starts from an
`AssociationId` (`MembershipStore.findByMember`, `SecurityAccountLookup.find`), `PrincipalResolver` asserts the stored tenant
and member equal the token's, and `MembershipStoreAdapterTest` / `SecurityAccountLookupAdapterTest` carry the isolation cases. `MembershipStore.findById` and
`findByUser` are identity-scoped by design (they have no association to filter by) and are reachable only from a credential the
user holds: a refresh token's own membership, or the account being logged in. No endpoint takes a membership or user id from the
client.

## 9. Time

- Instants are stored in UTC (`timestamptz`, `hibernate.jdbc.time_zone: UTC`).
- Recurring schedules (`TrainingGroup`) are expressed in local time — day of week + `LocalTime`
  in `Europe/Lisbon` — and converted to instants when sessions are generated (RN-01), so a 20:00
  class stays at 20:00 across DST changes.
- Domain and application code never call `Instant.now()`; they receive a `Clock` (bean in
  `ClockConfig`). Every deadline rule (RN-03, RN-10, RN-11) is tested with a fixed clock,
  including a case on a DST-change week.

## 10. Concurrency

The last seat must never go to two people, and the last credit must never be spent twice (NFR
"Concorrência"). Every aggregate root has a `version` (domain field, `@Version` column) and every
repository adapter saves through one path (`WriteSupport.write`):

1. lock the root row (`SELECT ... FOR UPDATE`, no outer-join fetch) - writers queue on the root,
   so child rows are always locked after it and two saves can't deadlock;
2. compare the stored version with the aggregate's version - a stale copy is rejected;
3. apply the domain state, flush;
4. if only child rows changed (bookings, levels), force the version increment
   (`PESSIMISTIC_FORCE_INCREMENT`), so every save moves the version by exactly one.

Any lock or version failure is translated to the aggregate's typed
`*ModifiedConcurrentlyException` (an `AggregateModifiedConcurrentlyException`). Use cases retry
in a **new transaction** (re-read, then confirm, waitlist or reject) and keep working with the
aggregate returned by `save`. The loop lives in the package-private `UnitOfWork` (`application.usecase`):
each attempt runs through the `TransactionRunner` port (`application.port`, implemented with a
`REQUIRES_NEW` `TransactionTemplate`, so the application never imports Spring's transaction API), at
most 5 attempts, the last conflict is rethrown (web: 409). Notifications are queued by the attempt and
sent by `UnitOfWork` only after the commit; a failing `Notifier` is logged and swallowed. Booking also takes the
member's row lock first (`MemberRepository.findByIdForUpdate`, inside the attempt's transaction), so one member's
concurrent requests queue up and the RN-07 overlap check (decided by `Session.requireNoOverlapWith` over the
sessions the repository narrows down) sees what the others committed. Database constraints are the backstop: a partial unique index
`(session_id, member_id) WHERE status <> 'CANCELLED'` on bookings (RN-07: one live booking per
member per session; rebooking after a cancellation is allowed) and a unique
`(association_id, training_group_id, starts_at)` on sessions. Bookings live inside the `Session`
aggregate and are persisted only through `SessionRepository.save(Session)`; the waitlist is read in
`requested_at, id` order (deterministic FIFO). Race tests with committed transactions cover the
last seat (2 and 8 contenders), the last credit, cancel-and-promote against a capacity change,
erasure against a stale edit, and approve against reject.

## 11. Identity and authentication

The design and its reasoning are in [`security/threat-model-rest-api.md`](security/threat-model-rest-api.md) (decision ids
`D-n`); this section says what the code does. Issue #26 is delivered in three steps: 26a (the security foundation, #30),
26b (credentials and sessions: users, login, refresh, links, rate limits, #31), 26c (controllers for the use cases).

- **Infrastructure** (`infrastructure.security`): the filter chain, token verification and issuing, and the mapping from a
  token to the caller. The domain never sees any of it.
- **Domain**: a person's role *within an association* (`MEMBER`, `COACH`, `ADMIN`) is tenant
  business data recorded on `Member` - a person can hold several, the founder becomes admin
  (US-01). The domain records roles but never authorises with them. Roles are **not** in the token and the web layer has
  no role matrix: use cases check them with `Permissions` from the actor's `Member` (D-5).
- Rules about the actor's relationship to the data ("coach of this group", "owner of this
  booking") are decided by the domain or use case from the actor's `MemberId`.

**Filter chain** (`SecurityConfiguration`). Stateless, no sessions or cookies, CSRF off (no ambient credential: the token
is in the `Authorization` header; the two cookie endpoints of 26b get Origin and content-type checks, D-7a), form login /
basic / logout off, no actuator. Default deny: `anyRequest().authenticated()`, and `PublicRoutes` is the only way out of it
(`POST /api/v1/public/associations`, `GET .../{shortName}`, `POST .../{shortName}/join-requests`, the six
`/api/v1/auth/*` credential routes of 26b, and `/error`). `RouteInventoryTest` lists every mapped route as `PUBLIC` or
`AUTHENTICATED`; a controller that adds a route without a row fails the build, as does a row that contradicts the chain.
Order: `RequestIdFilter` (server-generated `X-Request-Id`, MDC `requestId` and a request attribute that survives into the
container's error dispatch; the client's value is ignored; the log pattern prints it) then the Spring Security filters, with
`RequestSizeLimitFilter` right after the header writer (413 over 64 KiB or after 64 KiB actually read, 411 for a chunked body;
Tomcat's swallow size is set explicitly) and `SuppressedEndpointsFilter` before CORS, which answers 404 to `/.well-known/**`:
Spring Security 7 would otherwise serve `/.well-known/oauth-protected-resource` to anyone, ahead of authorization and outside
the route inventory. Headers: `Cache-Control: no-store`, `nosniff`, `Referrer-Policy: no-referrer`, `Content-Security-Policy: default-src
'none'; frame-ancestors 'none'`, `X-Frame-Options: DENY`, HSTS one year with subdomains (Spring emits it on HTTPS requests
only, so it needs TLS or forwarded headers in front of the app). They are applied to every response, not only `/api/**`.
CORS is off; only under profile `dev` with `regi-volley.security.cors.allowed-origin` set is that single origin allowed
(explicit methods and headers, credentials only on `/api/v1/auth/**`; `*` is refused at start-up).

**Access token** (D-1, D-4). ES256, 10 minutes, skew 30 s, claims `iss=regi-volley-api`, `aud=regi-volley-web`, `sub` (user
id), `aid`, `mid`, `sv` (security stamp), `iat`, `exp`: ids only. `AccessTokenDecoderFactory` builds Spring's
`NimbusJwtDecoder` over a Nimbus processor whose `Es256KeySelector` returns a key only for `alg: ES256` with a `kid` of the
closed set (so `none`, HS\*, RS\*, a missing or unknown kid get no key before any cryptography); issuer, audience, expiry and
not-before are validated with the injected `Clock`, and a token missing any required claim is rejected. `AccessTokenIssuer`
(a `NimbusJwtEncoder` over the signing `ECKey`) is what 26b's login and refresh call. Only the `Authorization: Bearer`
header is read: a token in a query string or form body is not a credential, and on a public route no token is read at all (a
stale header cannot turn the login into a 401). A token must also have been issued no later than now plus the skew and live
no longer than the TTL plus the skew, whatever its signature says.

**Keys** (D-2, `JwtKeyLoader`, `JwtKeyConfiguration`). `JWT_SIGNING_KEY` (one private key) and `JWT_VERIFICATION_KEYS`
(public keys, always including the active one's) are read from the environment as JWK / JWK Set JSON or as PEM (PKCS#8
`PRIVATE KEY`, X.509 `PUBLIC KEY`) with a `kid: <id>` line before each block, because PEM cannot carry a key id. A PEM
private key has no public half the JDK can read, so it is paired with the verification key of the same kid; a JWK private
key brings its own. Every configuration is checked, under every profile: EC on P-256 only, `kid` present, no duplicate kid or
key, no private key in the verification set, the signing key's public half in the set, and a sign-then-verify probe proving
the pair matches; any failure stops the start. Rotation is by overlap (two public keys during the access lifetime). The
ephemeral key pair exists only under profiles `dev` and `test` and never when `prod` is active too (`prod,dev` still
needs a key); every other profile, including none, refuses to start without one. `SecretsGuardConfiguration`
(`infrastructure.config`, active unless `dev` or `test`) refuses a blank or the default `DB_PASSWORD`. There is no
JWKS endpoint (no second verifier yet). Key material is never logged or printed (`toString` shows kids only).

**Principal** (D-5, D-6). `PrincipalResolver` runs after the signature check on **every** request and is the only code that
turns a token into an identity: the account must exist and be `ACTIVE`, its security stamp must equal `sv`
(constant-time), the membership must be `CONFIRMED` (all three from `SecurityAccountLookup`), and `MemberRepository.findById`
must return that member for that association, active and not anonymised. Any failure is a `PrincipalRejectedException`
(logged by ids and reason) and the client sees the constant 401. No cache, so deactivation, erasure and password change take
effect on the next request. The result is `AuthenticatedActor(userId, associationId, memberId)`; controllers take it with
`@CurrentActor AuthenticatedActor caller` (our name for `@AuthenticationPrincipal`) and call `caller.actor()`, the one
`new Actor(...)` in the codebase.

**The account lookup** (D-6). `SecurityAccountLookup.find(userId, associationId, memberId)` returns
`SecurityAccount(userStatus, securityStamp, membershipStatus, associationId, memberId)` or empty when the user has no
membership at exactly that member. `SecurityAccountLookupAdapter` (`persistence.adapter`) implements it with two indexed reads
(membership by user + association + member, then the account by primary key) and **no cache**, so a password change, a
deactivation or an erasure takes effect on the next request. `PrincipalResolver` additionally asserts the returned association
and member equal the token's (`MEMBERSHIP_MISMATCH`), so a lookup that answered with another tenant's row is never trusted;
`PrincipalResolver` also implements the application port `PrincipalVerifier` (the same checks without a token, answering a reason
code), which login and refresh ask. There is no deny-all fallback any more: without a lookup bean the application does not start. Tests use
`InMemorySecurityAccountLookup` (marked `@Primary` where a full context also has the real adapter).

**Where the credential flows live.** Login, refresh, logout, logout-all, activation, reset and reset-request are ordinary use cases
(request flow, §1): `AuthController` calls `LoginUseCase`, `RefreshSessionUseCase`, `LogoutUseCase`, `LogoutAllUseCase`,
`ActivateAccountUseCase`, `ResetPasswordUseCase` and `RequestPasswordResetUseCase`, implemented by the `*Service`s in
`application.usecase`, with commands in `application.command` and `SessionTokens` as the result. The services know no security
framework: they use the credential models in `application.identity` and the ports in `application.port` (`UserAccountStore`,
`MembershipStore`, `RefreshTokenStore`, `EmailLinkStore`, `PasswordHasher`, `AccessTokenIssuer`, `PrincipalVerifier`,
`CredentialAttemptThrottle`, `SecretGenerator`, `BackgroundWork`, `CommonPasswordList`, `AccountLinkMailer`, `TransactionRunner`),
which `persistence.adapter`, `infrastructure.security` and `infrastructure.notification` implement. `infrastructure.security` keeps
what is security machinery: the filters, `RateLimiter`, `PrincipalResolver` (also the `PrincipalVerifier`), the JWT code, the hasher
and secret adapters, `SecurityAccountLookup`. Transactions go through `TransactionRunner`.

**Credentials** (D-8, `PasswordHasher` port, `Argon2PasswordHasher`, `PasswordPolicy`). `DelegatingPasswordEncoder` with Argon2id as
default id (`{argon2id}`, 19 MiB, t=2, p=1, 16-byte salt, 32-byte hash) via BouncyCastle, `{bcrypt}` (cost 12) accepted as legacy and
replaced at the next successful login (`needsUpgrade`, a compare-and-set so a concurrent reset is never overwritten). At most 4 hashes
run at once; a caller waits for a slot at most 2 seconds, then gets `ServiceBusyException` (503 `SERVICE_BUSY` with `Retry-After`),
and the per-email and per-IP throttles are taken before any hashing. Passwords are NFKC-normalised before hashing. Policy (a
package-private class of `application.usecase` over the `CommonPasswordList` port): 10 to 128 characters, no composition rules, not on
the bundled offline list (`security/common-passwords.txt`, a seed list to be replaced by a vetted ~10k list before go-live), not the
email, its local part or the association's name; a refusal is `InvalidFieldException("password")` (422) and never quotes the value.
`app_user.password_hash` is null until activation; the security stamp is 256 random bits from `SecretGenerator`, never derived from
the password.

**Sessions** (D-7, D-7a, `SessionIssuer`). A refresh token is 256 random bits, stored as a SHA-256 hash in `refresh_token` (family,
parent, issued / absolute-expiry / idle-expiry / used / revoked), valid 30 days idle and 90 days from the login. Every `POST
/api/v1/auth/refresh` rotates it in the same family; presenting an already-rotated token revokes the family (and logs a security event)
unless it was rotated less than 10 seconds ago (a second tab: refused, nothing revoked); two requests racing to rotate the same token
are decided by a conditional update (`markUsed`), the loser being treated like the grace case. Each refresh re-asks the
`PrincipalVerifier`, so a deactivated member cannot refresh and loses the family. `logout` revokes the presented token's family,
`logout-all` (the one authenticated credential route) revokes every family and rotates the stamp, so access tokens die at once; the
link consumption below does the same. Revocations commit although the call then answers 401: the rotation returns an outcome inside
the transaction and the use case throws after it. The cookie is `__Secure-rt`, `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`,
no `Domain`, `Max-Age` as long as the token lives (`RefreshCookie`, a package-private helper beside `AuthController`); the body of
login and refresh carries only the access token (`{accessToken, tokenType: "Bearer", expiresIn: 600}`). `CookieEndpointGuardFilter`
guards `POST /auth/refresh` and `/auth/logout` (paths matched decoded, like Spring MVC does): the body must be `application/json`
(415), a request the browser marks `Sec-Fetch-Site: cross-site` is 403, and an `Origin`, if present, must be the API's own origin or
`regi-volley.security.web-origin` (`WEB_ORIGIN`, needed when TLS ends at a proxy), else 403.

**Login** (D-10, `LoginService`). One `InvalidCredentialsException` (401 `{"code":"INVALID_CREDENTIALS"}`) for unknown or malformed
email, account never activated, wrong password, disabled account, membership not confirmed, inactive or anonymised member and (MVP)
more than one confirmed membership; exactly one hash is verified in every case (a precomputed dummy hash when there is none to
check), and the reason goes to the audit log by id with the client address truncated (IPv4 /24, IPv6 /48; `ClientAddresses`).
Password-reset requests always answer 202 `{"status":"RECEIVED"}`; the work (lookup, link, mail) runs through `BackgroundWork` (a small
bounded executor) so response time says nothing. Join and registration need no email lookup (D-14 and the founder-only-unique
email), so there is nothing to enumerate; their controllers are 26c.

**Emailed links** (D-11, `EmailLinkIssuer`, `CredentialLinkConsumer`). `email_link` holds the SHA-256 of a 256-bit token, a purpose
(`ACTIVATION` 7 days, `PASSWORD_RESET` 30 minutes), an optional membership and `consumed_at`; a newer link of the same membership
(activation) or user (reset) supersedes the older ones in the same transaction, and a user's spent links are cleared when a new one
is issued. Partial unique indexes allow one live activation link per membership and one live reset per user, so two links issued at
the same instant cannot both stay open: the loser gets `LinkAlreadyIssuedException` and its transaction is repeated (`Conflicts`).
Composite foreign keys `(membership_id, user_id)` on `refresh_token` and `email_link` keep a token or link from naming one user with
another user's membership. `POST /auth/activate {token, password}` and `POST /auth/password-resets {token, password}` check the
policy *before* spending the link, consume it with a conditional update (of two parallel uses one wins), set the hashed password,
rotate the stamp and revoke every refresh token; activation also confirms the membership, a reset never does (L4). Every unusable
link is the same 400 `INVALID_LINK`. A reset requested for an account that was never activated re-sends its activation link instead.
**Delivery:** `AccountLinkMailer.send(accountEmail, purpose, link)` mails the link to the *account's* address (`UserAccount.email()`),
never to a member's contact address, which an administrator can edit. The clear token exists only in the `AccountLink`
(`application.identity`) handed to the mailer; `LoggingAccountLinkMailer` logs the link's reference, never its token or the address.
The SMTP adapter is Phase 2 and a go-live blocker.

**Provisioning** (D-11, section 6, `AccountProvisionerService`). `RegisterAssociation` (the founder) and `ApproveJoinRequest` call
the application port `AccountProvisioner.provision(associationId, memberId, email)` after the commit (`AccountProvisioning`: a
failure is logged by ids and never undoes the use case, risk R3). The implementation, itself in `application.usecase`, creates the
account for the email (or reuses the existing one untouched), a PENDING membership and an ACTIVATION link mailed after its own
transaction; it is idempotent and repeats the transaction when it loses a race on the unique email or on the link. `CredentialsEraser`
(`CredentialsEraserService`) is the port seam for the future erasure use case: it deletes the membership and, when it was the user's
only one, the account with its tokens and links.

**Rate limiting** (D-9, `RateLimiter`, `RateLimitFilter`). bucket4j token buckets in bounded, expiring Caffeine caches (one per rule;
100 000 keys for address rules, 20 000 for the email-keyed ones, which an attacker controls; in memory, so a restart resets them and a
second instance would need a shared store, risk R5), on the injected `Clock`. Per IP in `RateLimitFilter`, before the body is parsed
and before any hash: login 30 / 10 min, refresh 60 / min, activation and reset confirmation 30 / h (one bucket), password-reset
requests 10 / h, `POST /public/associations` 3 / h, join requests 5 / h, the public page 120 / min. The key is the address as
`RateLimiter.addressKey` makes it: IPv4 whole, IPv6 by its /64 (a subscriber owns a /64, so rotating inside it must not give a fresh
bucket), IPv4-mapped IPv6 as the IPv4. Per email hash through the `CredentialAttemptThrottle` port (`RateLimitingAttemptThrottle`):
login 5 / 15 min, reset 3 / h (unknown emails count the same); `RateLimiter.joinKey` serves the 3 / day per (association, email) join
limit that the 26c controller will take. The answer is 429 with `Retry-After` and no hard lock-out. **Client address:**
`getRemoteAddr()` only. `server.forward-headers-strategy` is `none` (`FORWARD_HEADERS_STRATEGY`), so `X-Forwarded-For` is ignored;
behind a known proxy set `native` *and* `server.tomcat.remoteip.internal-proxies` to that proxy (Tomcat's default trusts every
private range). The authenticated per-user limit (U7) arrives with the 26c endpoints.

**Errors** (`ApiExceptionHandler`, `infrastructure.web.exception`). One body `{code, message, requestId[, fields]}`.
403 `NotAllowedException`; 404 every `*NotFoundException` (also another tenant's id: same body, no id echoed); 202
`{"status":"RECEIVED"}` for `JoinRequestNotPossibleException` (D-14); 409 `ShortNameAlreadyTaken`, `MemberEmailAlreadyUsed`,
`LastAdministrator`, `DuplicateBooking` and any `AggregateModifiedConcurrentlyException`; 401 `INVALID_CREDENTIALS` (login) and
`UNAUTHENTICATED` (refresh); 400 `INVALID_LINK`; 429 `TOO_MANY_REQUESTS` and 503 `SERVICE_BUSY`, both with `Retry-After`; 422 every other
`BusinessRuleException` with its own English message (`InvalidFieldException` also names its field); 400 malformed,
invalid or unknown-property input with field names only, never values; 404/405/406/415 for the other Spring MVC errors;
500 for everything else, logging the exception class and the place it was thrown, never its message. Spring Security's
`AccessDeniedException`/`AuthenticationException` thrown inside a handler keep their 403/401. Errors raised in the filter
chain (401, 403, 411, 413) are written by the security layer in the same shape, and `ErrorPageController` replaces Boot's
error page so container-level errors have it too. `server.error.include-*` is `never`, and `DefaultHandlerExceptionResolver`
is raised to ERROR so Spring's own warnings never quote rejected input. `LogHygieneTest` captures the log and proves a
rejected email, a password, a token and an `Authorization` header never reach it.

Personal data (name, email, phone) is never written to logs or `toString` (NFR "Operação"/RGPD).
Erasure on request anonymises every aggregate holding it (`Member`, `JoinRequest`) while keeping
ids and history.

## 12. Domain exceptions and messages

Rule violations a user can trigger (booking window closed, session full, duplicate booking, ...)
are domain exceptions extending a common `BusinessRuleException`, carrying structured data
(ids, instants) plus an English message that the web layer may show as-is; times in those messages
are formatted in `Europe/Lisbon` as `dd/MM/yyyy HH:mm`, never raw UTC. Invariant and programming errors (invalid
reconstitution data, null arguments) use English messages and are never shown to users - they map
to a generic error.

## 13. Administration rules that span aggregates

The admin use cases (issue #25) keep these cross-aggregate rules in the application layer, each next to the lock
that makes it safe under concurrency:

- **Last administrator.** An association must keep one active administrator. Every attempt that can remove one
  (deactivating a member, revoking ADMIN) starts with `AdminGuard.serialise`: `AssociationRepository.findByIdForUpdate`
  takes the **association row** lock *before any member is loaded*, which queues such attempts one after the other.
  `AdminGuard.requireAnotherActiveAdmin` then counts with a fresh scalar query (`MemberRepository.findActiveAdminIds`,
  ids rather than entities). Locking the administrators' own rows is not enough: under READ COMMITTED the role rows are
  judged against an old snapshot and Hibernate hands back entities already loaded, so two removals could each count the
  other's administrator as the one that stays (reproduced; the race tests in `AdminFlowIntegrationTest` cover revoke vs
  revoke, revoke vs deactivate and deactivate vs deactivate). **The future erasure use case must go through the same
  guard**: `Member.anonymise` drops every role, so erasing the last administrator would otherwise orphan the association.
- **Venues.** `Venue` is an aggregate root, but `training_groups.venue_id` has no foreign key (V4 is applied). Creating a
  group and deleting a venue both start with `VenueRepository.findByIdForUpdate`, so a venue is never deleted under a group
  being created; deleting is refused while an ACTIVE group runs there (`VenueInUseException`).
- **Group and plan configuration.** What an aggregate cannot check alone lives in the domain where it can: coach eligibility
  is `Member.canCoach()` / `requireCanCoach()` (also used by session generation) and level ownership is
  `Association.requireLevels`. Editing a group is refused first when it is archived.
- **Plan assignment** authorises the administrator, then locks the member's row (as booking does), so two assignments cannot
  both pass the RN-16 overlap check; a deactivated member is refused. The subscription snapshots the plan's terms **and
  price**, so editing a plan never changes what existing subscriptions owe.
- **Payments are append-only (RN-19).** `Payment` has no version, `PaymentRepository` offers only `findById`,
  `findBySubscription` and `add`, the JPA entity is `@Immutable` and database triggers refuse any `UPDATE`, `DELETE` or
  `TRUNCATE` and the insert of a reversal of a reversal. A reversal is a second `Payment` with `reversalOf` set (positive
  amount, counted negatively); a unique constraint allows one reversal per payment and a composite foreign key keeps it on the
  same subscription. A payment cannot be dated after today in Lisbon. `PaymentLedger` (domain service) decides amounts and
  status from the subscription's own price; `PaymentSettling` saves the subscription *first* (its row lock and version
  serialise concurrent payments or reversals of one subscription) and adds the payment second. The adapter turns only the
  constraints that mean "lost a race" (second reversal, cross-subscription reversal, existing id) into
  `PaymentModifiedConcurrentlyException`; any other violation is rethrown as a data-integrity error.
- **Payment status.** PENDING -> OVERDUE is an administrator's decision (`MarkSubscriptionOverdue`); OVERDUE blocks booking
  (RN-18). PAID goes back to PENDING only through a reversal.
- **Join requests.** A partial unique index allows one PENDING request per (association, email); the use case answers a
  duplicate (pending request or existing member) with one generic `JoinRequestNotPossibleException`.
- **Lists** (`MyPlan`, `ListSubscriptionsByPaymentStatus`) load members and plans in one batch (`findByIds`) rather than
  one query per row; a row whose member cannot be loaded is logged by id and left out instead of failing the list.
