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

- **`domain`** (`com.regivolley.api.domain`) — aggregates, value objects, domain exceptions and
  port interfaces (repositories, notifier). Nothing outside this package may be imported here.
- **`application`** (`com.regivolley.api.application`) — one use case per operation, plus
  command records. Depends only on `domain`.
- **`infrastructure`** (`com.regivolley.api.infrastructure`) — everything that talks to the
  outside world: `web`, `persistence`, `security`, `notification`, `config`. Depends on both
  inner layers; nothing may depend on it.

Mechanically enforced by
`src/test/java/com/regivolley/api/architecture/OnionArchitectureTest.java` - plain JUnit 5, no
architecture library (NFR "Qualidade"). It scans `src/main/java` with `JavaSourceFile`, which
resolves each file's package and every package-qualified reference (imports, static and wildcard
imports, inline fully qualified names; comments and strings ignored). `JavaSourceFileTest` proves
the scanner itself detects violations, so a rule can't pass vacuously. A new rule is a new `@Test`
in `OnionArchitectureTest`.

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
| Aggregates, value objects, enums | `domain.model` |
| Domain exceptions | `domain.exception` |
| Repository ports (interfaces only) | `domain.repository` |
| Other outbound ports (e.g. `Notifier`) | `domain.port` |
| Use case interface + implementation | `application.usecase` |
| Use case input records | `application.command` |
| REST controllers | `infrastructure.web.controller` |
| Request/response DTOs, error shape | `infrastructure.web.dto` |
| `@RestControllerAdvice` exception mapping | `infrastructure.web.exception` |
| Domain ↔ DTO translation | `infrastructure.web.mapper` |
| JPA entities | `infrastructure.persistence.entity` |
| Spring Data repository interfaces | `infrastructure.persistence` (package-private) |
| Repository port implementations (adapters) | `infrastructure.persistence` |
| Entity ↔ domain translation | `infrastructure.persistence.mapper` |
| Users, credentials, JWT/cookies, role checks | `infrastructure.security` |
| Email sending (adapter for `Notifier`) | `infrastructure.notification` |
| Spring `@Configuration` beans | `infrastructure.config` |

A class that doesn't fit one of these rows is a signal to reconsider the design — flag it rather
than inventing a package ad hoc. If `domain.model` grows unwieldy, splitting it per aggregate
(`domain.model.booking`, ...) is a deliberate change to this table, not a drive-by.

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
| Pagamento / Estorno | `Payment` / `Refund` |
| Falta | `NO_SHOW` |
| Evento | `Event` |
| Pedido de adesão | `JoinRequest` |
| Nome curto (URL) | `ShortName` |
| Consentimento RGPD | `GdprConsent` |

## 5. Patterns in use

- **Repository (port/adapter)** — ports in `domain.repository`, adapters in
  `infrastructure.persistence`. Application code never touches Spring Data or JPA.
- **Command pattern for use cases** — `UseCase<IN, OUT>`, one class per operation.
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
- **Web** — `@WebMvcTest` + MockMvc, use cases mocked.
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

## 9. Time

- Instants are stored in UTC (`timestamptz`, `hibernate.jdbc.time_zone: UTC`).
- Recurring schedules (`TrainingGroup`) are expressed in local time — day of week + `LocalTime`
  in `Europe/Lisbon` — and converted to instants when sessions are generated (RN-01), so a 20:00
  class stays at 20:00 across DST changes.
- Domain and application code never call `Instant.now()`; they receive a `Clock` (bean in
  `ClockConfig`). Every deadline rule (RN-03, RN-10, RN-11) is tested with a fixed clock,
  including a case on a DST-change week.

## 10. Concurrency

The last seat must never go to two people (NFR "Concorrência"). Capacity is enforced with
**optimistic locking on `Session`** (`@Version` on the JPA entity, mapped to a version field on
the aggregate) plus database constraints as the backstop: a partial unique index
`(session_id, member_id) WHERE status <> 'CANCELLED'` on bookings (RN-07: one live booking per
member per session; rebooking after a cancellation is allowed). Bookings live inside the
`Session` aggregate and are persisted only through `SessionRepository.save(Session)` - there is
no booking repository. Because inserting a booking row does not touch the session row, the
adapter must force the session version increment (`OPTIMISTIC_FORCE_INCREMENT` or an explicit
session update) on every save, otherwise two different members could both take the last seat.
The waitlist is read in `requested_at, id` order (deterministic FIFO). A conflicting write surfaces as a domain-level conflict the use case retries
or turns into a waitlist entry. The booking path has a Testcontainers test firing simultaneous
bookings at the last seat.

## 11. Identity and authentication

- **Infrastructure** (`infrastructure.security`): users, credentials, login, sessions/JWT, and
  the mapping from a user to their `Member` in each association (`user_id` exists only there).
  It resolves the principal to `(AssociationId, MemberId, roles)` through a port and checks roles
  server-side on every request, before the use case is called.
- **Domain**: a person's role *within an association* (`MEMBER`, `COACH`, `ADMIN`) is tenant
  business data recorded on `Member` - a person can hold several, the founder becomes admin
  (US-01). The domain records roles but never authorises with them.
- Rules about the actor's relationship to the data ("coach of this group", "owner of this
  booking") are decided by the domain or use case from the actor's `MemberId`.

Personal data (name, email, phone) is never written to logs or `toString` (NFR "Operação"/RGPD).
Erasure on request anonymises every aggregate holding it (`Member`, `JoinRequest`) while keeping
ids and history.

## 12. Domain exceptions and messages

Rule violations a user can trigger (booking window closed, session full, duplicate booking, ...)
are domain exceptions extending a common `BusinessRuleException`, carrying structured data
(ids, instants) plus an English message that the web layer may show as-is; times in those messages
are formatted in `Europe/Lisbon` as `dd/MM/yyyy HH:mm`, never raw UTC. Invariant and programming errors (invalid
reconstruct data, null arguments) use English messages and are never shown to users - they map
to a generic error.
