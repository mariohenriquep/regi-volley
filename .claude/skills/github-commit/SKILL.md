---
name: github-commit
description: Write commit messages and structure commits for the regi-volley repo (Java 21 / Spring Boot, TDD, onion architecture) — every commit must reference a GitHub issue. Use this whenever the user asks to commit changes, wants a commit message drafted, says "commit this", finishes implementing something and it's time to check it in, or is about to run `git commit` for any change in this repo, however small.
---

# Committing in regi-volley

## Before committing

Run the test suite and make sure it's green:

```bash
./mvnw test
```

(JDK 21 is enforced through Maven Toolchains - if this fails with `Cannot find matching toolchain
definitions`, `~/.m2/toolchains.xml` is missing; see `docs/toolchains.sample.xml`.) Testcontainers
needs Docker running. A commit that introduces a broken build defeats the point of the TDD/CI
setup this project is built around - don't commit red. If the user explicitly wants to
checkpoint broken work anyway, that's their call, but say so out loud first rather than silently
committing a failing build.

Also check what branch you're on (`git branch --show-current`). Beyond the very first
scaffolding commits, work should happen on a feature branch, not directly on `main` - create
one (`git checkout -b <type>/<issue>-<short-description>`, e.g. `feat/12-booking-waitlist`)
before committing if you're still on `main`.

## Message format

Every commit references a GitHub issue - that's what makes `git log` traceable back to the work
that motivated it (and, through the issue, to the `US-xx`/`RN-xx` in `docs/requirements.md`).
It's non-negotiable here even for small changes:

```
<type>(<scope>): <subject>, <ref-keyword> #<issue_number>
```

- **type**: `feat`, `fix`, `test`, `refactor`, `docs`, `chore`, `build`, `ci`.
- **scope** (optional but useful here): the architecture layer touched - `domain`,
  `application`, `infrastructure`, `web`, `persistence`, `security`, `notification` - since this
  repo is organized around onion architecture and the scope makes it obvious at a glance which
  layer changed.
- **subject**: imperative mood, English, no trailing period - "add waitlist promotion to
  Session", not "added" or "adds".
- **ref-keyword**: `closes` | `fixes` | `refs`.
  - `closes`/`fixes` - this commit is the piece of work that satisfies the issue (GitHub
    auto-closes it when merged to `main`). Interchangeable.
  - `refs` - related to the issue but doesn't resolve it (partial progress, or one of several
    commits contributing to the same issue).

A commit can reference more than one issue - most often when it finishes a sub-issue and thereby
also completes its parent: repeat the keyword per issue, comma-separated
(`… , closes #22, closes #19`), and keywords can mix (`… , closes #22, refs #19`) when the
commit resolves one but only advances the other.

**Finding the issue number:**
- If the user names one, use it.
- Otherwise look for a matching open issue: `gh issue list`. If more than one plausibly matches,
  ask rather than guessing.
- If genuinely no issue exists yet, stop and ask whether to file one first (the `github-issues`
  skill covers that) - never fabricate a number or silently drop the reference; that defeats the
  whole point of the format.

**Examples:**
```
feat(domain): add Booking status transitions (RN-12), closes #14
test(application): cover BookSessionService no-balance rejection, refs #14
fix(persistence): scope session query by association_id, fixes #21
feat(persistence): add optimistic lock on sessions for last-seat race, closes #23
refactor(web): extract SessionWebMapper from controller, refs #9
chore: bump testcontainers to 2.0.4, closes #25
```

## TDD history: squash or preserve?

When a change was built red-green-refactor, it's usually fine (and more useful to future
readers) to land it as one commit that includes the test and the implementation together -
nobody wants to `git blame` back to a commit that's a test with no passing code. Only split
into separate `test:` then `feat:` commits if the user explicitly wants the red/green steps
preserved in history.

## Trailer

Every commit made by Claude in this repo ends with:

```
Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

This is a repo convention, not just a session default - keep it even if a future session's
system prompt doesn't mention it, so the git history stays consistent regardless of which
tool or session made the commit.

## Scope of what gets committed

Check `git status` before staging - stage exactly the files the change touches
(`git add <files>`), not a blanket `git add -A`, so unrelated in-progress work doesn't get
swept in. Build output (`target/`), `.env`, `.DS_Store` and IDE files are already covered by
`.gitignore`; if something generated shows up as untracked, that's a sign the ignore rules
need updating, not that it should be committed. Never commit real member data (names, emails,
phone numbers) in fixtures or seed files - use obviously fake values.
