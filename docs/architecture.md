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
  services, domain exceptions and port interfaces (repositories, notifier). Nothing outside this package may be imported here.
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
| Value objects: ids, `Money`, `BookingPolicy`, `ContactDetails`, status/type/role enums, ... | `domain.model.valueobject` |
| Outcomes returned by aggregates that contain entities (`BookingResult`, `CapacityChange`, ...) | `domain.model.result` |
| Domain services and their inputs/outputs (`BookingEligibility`, `BookingTarget`, `PaymentLedger`, ...) | `domain.service` |
| `AggregateRoot` / `Entity` / `ValueObject` markers, shared validation (`FieldRules`) | `domain.shared` |
| Domain exceptions | `domain.exception` |
| Repository ports (interfaces only, one per aggregate root) | `domain.repository` |
| Other outbound ports (e.g. `Notifier`) | `domain.port` |
| Use case interface (`*UseCase`) + implementation (`*Service`) and their package-private helpers (`UnitOfWork`, `SeatPromoter`, ...) | `application.usecase` |
| Outbound ports the application owns that are not about the domain (`TransactionRunner`); interfaces only, no Spring | `application.port` |
| Use case input records | `application.command` |
| Use case output records | `application.result` |
| REST controllers | `infrastructure.web.controller` |
| Request/response DTOs, error shape | `infrastructure.web.dto` |
| `@RestControllerAdvice` exception mapping | `infrastructure.web.exception` |
| Domain ↔ DTO translation (`*WebMapper`) | `infrastructure.web.mapper` |
| JPA entities (`*JpaEntity`) | `infrastructure.persistence.entity` |
| Spring Data repository interfaces (`*JpaRepository`, package-private) and repository port implementations (adapters) | `infrastructure.persistence.adapter` |
| Entity ↔ domain translation (`*PersistenceMapper`) | `infrastructure.persistence.mapper` |
| Users, credentials, JWT/cookies, role checks | `infrastructure.security` |
| Notification sending (adapter for `Notifier`; logs ids only until the email adapter of Phase 2) | `infrastructure.notification` |
| `TransactionRunner` implementation (`REQUIRES_NEW` template) | `infrastructure.persistence.adapter` |
| Spring `@Configuration` beans | `infrastructure.config` |

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
  its creation and transition methods stay package-private and reachable only through the root.
  No repository of its own.
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
- **Repository** - one port per aggregate root in `domain.repository`; adapters in
  `infrastructure.persistence.adapter`.
- **Shared kernel** - `domain.shared` holds the three markers and the validation helper
  `FieldRules`; it depends on nothing in the project except `domain.exception`.

The same test also pins the infrastructure naming: `*Request`/`*Response`/`*Dto` only in
`infrastructure.web.dto`, `*WebMapper` in `infrastructure.web.mapper`, `*PersistenceMapper` in
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
