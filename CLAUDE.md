# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

RegiVolley: session-by-session booking for amateur indoor volleyball associations. Product scope,
user stories (`US-xx`) and business rules (`RN-xx`) are in `docs/requirements.md` (PT-PT);
structural rules are in `docs/architecture.md`. Read both before a structural change. Code,
identifiers, technical docs **and all user-facing text** (messages, errors, emails) are in English
- the product docs in `docs/requirements.md` stay in PT-PT.

This project follows the conventions of `../../TaskManager/task-manager-api` (same stack, same
layering, same test style). When in doubt about an idiom, look there.

## Commands

The build enforces JDK 21 via Maven Toolchains - needs `~/.m2/toolchains.xml` (template:
`docs/toolchains.sample.xml`).

```bash
./mvnw test                                   # full suite (unit + integration + architecture)
./mvnw verify                                 # test + JaCoCo coverage gate (85% line/branch)
./mvnw test -Dtest=OnionArchitectureTest      # architecture rules only
./mvnw test -Dtest='BookingTest$Cancel#...'   # single method in a @Nested class
./mvnw spring-boot:run                        # run the app (needs Postgres)
docker compose up -d                          # local Postgres for spring-boot:run
./mvnw cyclonedx:makeBom                      # CycloneDX SBOM -> target/regi-volley-sbom.json (CI only otherwise)
```

Testcontainers (`AbstractPostgresIntegrationTest`, `postgres:16-alpine`) needs Docker running.

## Architecture in one paragraph

Onion architecture, `infrastructure → application → domain`, enforced by plain JUnit 5 tests
(`OnionArchitectureTest` - no ArchUnit). `domain` is
pure Java and split by DDD building block: aggregate roots and their internal entities in
`domain.model.entity` (`Session` + `Booking`, `Association` + `Level`, `Subscription`, ... implement the
`AggregateRoot`/`Entity` markers from `domain.shared`), value objects (records/enums implementing
`ValueObject`, never depending on entities) in `domain.model.valueobject`, operation outcomes in
`domain.model.result`, domain services (`BookingEligibility`) in `domain.service`. Immutable
aggregates own their state machines and business rules; ports live in
`domain.repository`/`domain.port`. `application` has
one `UseCase<IN, OUT>` class per operation. `infrastructure` holds web (`controller`/`dto`/`mapper`/`exception`), persistence
(`entity`/`mapper`/`adapter`), security and notification adapters; the architecture test pins which
class kind lives in which package (§4 of architecture.md). Three models per concept (domain / JPA entity / web DTO), translated only
in mappers. Non-negotiables specific to this project: every business table and every repository
read is scoped by `association_id` (§8), times are UTC in storage and `Europe/Lisbon` for
schedules (§9, always via an injected `Clock`), and the last seat is protected by optimistic
locking + DB constraints (§10).

Spring Boot 4.1 gotchas (modular test autoconfig, Jackson 3 under `tools.jackson`, `@MockitoBean`
instead of `@MockBean`, Testcontainers 2.x module names) are the same as in task-manager-api -
see its CLAUDE.md.

## Tests

Domain/application: plain JUnit 5 + Mockito, no Spring. Persistence: `@DataJpaTest` +
Testcontainers, including a tenant-isolation case. Web: `@WebMvcTest`. Every test uses
`// Arrange` / `// Act` / `// Assert`; exceptions via `assertThrows`, values via AssertJ.
Write the failing test first. Architecture rules are plain JUnit 5 too (`OnionArchitectureTest` over
`JavaSourceFile`) - do not add ArchUnit or any other architecture-testing library.

## GitHub workflow

Repo: `mariohenriquep/regi-volley`. Every commit references an issue:
`<type>(<scope>): <subject>, closes|fixes|refs #<N>`. Work on a feature branch, not `main`.
No PR is merged by Claude - that's always a human decision.

These conventions are encoded in `.claude/`:

- Skills: `github-issues` (issues + linked sub-issues, traced to `US-xx`/`RN-xx`),
  `github-commit`, `github-pr`, `architecture-review`.
- Agents: `task-developer` (implements features, TDD), `unit-tester` (deepens tests, coverage,
  isolation/DST/concurrency tests), `architecture-reviewer` (read-only review),
  `devsecops-agent` (CI, security gates, threat modelling, RGPD), `pr-manager`,
  `issue-triager`.
