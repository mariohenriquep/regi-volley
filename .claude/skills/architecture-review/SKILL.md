---
name: architecture-review
description: Reviews a GitHub PR or the current working diff on regi-volley against docs/architecture.md — onion-architecture layering, domain purity, the domain/entity/DTO three-model separation, package placement, patterns, SOLID, GRASP, testing conventions, and this project's cross-cutting rules (multi-tenant isolation by association_id, UTC/Europe-Lisbon time with an injected Clock, last-seat concurrency, identity kept out of the domain). Use whenever the user asks to review a PR, review a diff, or check whether a change follows the architecture/clean-architecture/onion-architecture/SOLID/GRASP conventions for this repo — "review PR #4", "check this diff against our architecture", "does this class belong here", "/architecture-review". Trigger even without the word "architecture" — any request to validate a change's structure, layering, or design against this project's conventions qualifies. This is conformance/design review, not correctness or security — use /code-review or the devsecops-agent for those, alongside this one if useful.
effort: high
---

# architecture-review

Checks a diff against the architecture this project has committed to in `docs/architecture.md`,
not just whether the code works. A change can pass every test and still put a JPA entity in the
domain layer, let a controller decide business logic, or add a query that forgets
`association_id` — correctness review naturally misses that class of problem because the code
"works fine" in a single-tenant test. This repo has a mechanical backstop
(`OnionArchitectureTest`, plain JUnit 5) — run it as a cheap first pass, then do the judgment-based
review this skill is actually for.

## Workflow

### 1. Run the mechanical check first

```bash
./mvnw test -Dtest=OnionArchitectureTest
```

If this fails, the layering violation is already proven — cite the specific failing test and the
`file -> dependency` it reports, which
broke and report it as blocking. If it passes, that only clears layering *direction* and
framework leaks; everything below still needs the read-and-judge pass.

### 2. Get the diff

- **PR review:** `gh pr diff <number>`.
- **Working diff (no PR number given):** a plain `git diff` only shows tracked, unstaged
  changes — it silently misses staged changes and brand-new untracked files (a fresh
  `Booking.java` won't appear at all, and new classes are exactly what most needs reviewing).
  To see everything a branch actually adds:
  - Committed work on a branch: `git diff <base>...HEAD` (three dots), `<base>` = `main`
    unless the user says otherwise.
  - Staged but uncommitted: `git diff --staged`.
  - Untracked new files: `git status --porcelain`, then read each one directly.

  If it's ambiguous which of these the user means, ask rather than guessing.

### 3. Read `docs/architecture.md` fresh

Read the whole file before judging anything — don't rely on a remembered version from earlier
in the conversation. It evolves; a stale mental copy produces a review against rules that no
longer apply. If the change claims to implement `US-xx`/`RN-xx`, also read those entries in
`docs/requirements.md` so you can tell whether the rule landed in the aggregate that owns it.
If `docs/architecture.md` is missing, say so and stop rather than inventing rules.

### 4. Walk the diff against each section

For each one, look for concrete violations in the *changed* code only — don't re-review
unrelated existing code, and skip a section entirely if nothing in the diff touches it.

- **§1 Layering** — does `application/` reach into `infrastructure/` directly instead of through
  a port defined in `domain/`? Does a controller hold business logic instead of just translating
  HTTP ↔ use case call?
- **§2 Domain purity** — any framework import/annotation (Spring, JPA, Jackson, Lombok,
  `jakarta.validation`, Spring Security) in `domain/`? The single most common way a "quick fix"
  quietly breaks the architecture.
- **§3 Three models** — is a JPA entity leaking outside `infrastructure.persistence`? Is a domain
  object serialized straight to the wire instead of through a `web.dto` type? Is a new type
  duplicating an existing domain/entity/DTO for the same concept?
- **§4 Package placement** — is every new class where the §4 table says? Does it use the domain
  vocabulary table (`TrainingGroup`, `Venue`, `Booking`, ...) rather than a new synonym? A class
  that fits no row is worth flagging even if it "works."
- **§5 Patterns** — one class per use case (`UseCase<IN,OUT>`)? State machines (RN-05 Session,
  RN-12 Booking, RN-18 Subscription) as an allowed-transitions map inside the aggregate, not an
  `if` chain in a service? Eligibility rules (RN-06/07/11/21) decided by domain objects, with the
  use case only loading facts and persisting the outcome? Outbound integrations (email) through a
  port + adapter?
- **§6 SOLID / GRASP** — SRP, OCP (a new plan type or transition extends a table rather than
  editing branches), DIP (no `new SomeInfrastructureClass()` in application/domain), Information
  Expert (capacity in `Session`, balance in `Subscription`).
- **§7 Testing conventions** — new logic tested at the layer it belongs to? Every test AAA with
  `// Arrange` / `// Act` / `// Assert`, `assertThrows` (not `assertThatThrownBy`), AssertJ for
  values, `verify(..., never()).save(...)` on throwing use cases? Would JaCoCo drop below 85%?
- **§8 Multi-tenancy** — every new business table has non-null `association_id`; every new
  repository read takes the tenant; the tenant comes from the authenticated principal, not the
  request body; each new adapter test has an isolation case. A missing tenant filter is
  **blocking** — it's a data leak between associations, not a style issue.
- **§9 Time** — no `Instant.now()`/`LocalDateTime.now()`/`Clock.systemUTC()` in domain or
  application; storage in UTC; schedules in `Europe/Lisbon` local time; deadline rules tested
  with a fixed clock including a DST week.
- **§10 Concurrency** — any change to booking/capacity keeps the optimistic lock + unique
  constraint and the concurrent last-seat test.
- **§11 Identity** — users/credentials/roles stay in `infrastructure.security`; domain only sees
  `MemberId`/`AssociationId`; role checks happen server-side before the use case; no personal
  data (name, email, phone) logged.

### 5. Report

For each real finding: **file/class → which `docs/architecture.md` section it violates → a
concrete suggested fix**, ranked most-important first — a tenant leak or domain framework leak
outranks a package-placement nit. Separate **blocking** (architecture violations, layering
breaks, tenant leaks, missing tests on real logic) from **non-blocking** (nits, style).

If the change is clean, say so plainly and approve. Don't manufacture nitpicks to look thorough —
an honest "this conforms, ship it" is a valid and valuable review outcome.

## Notes

- This is architecture + design conformance, not general code quality or security. If something
  security-relevant turns up in passing, flag it and point to the `devsecops-agent`.
- `docs/architecture.md` changing changes this skill's behavior automatically next run, since the
  rules are read fresh each time — only touch this file when the *workflow* changes.
