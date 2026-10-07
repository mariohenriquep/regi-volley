---
name: github-pr
description: Open, describe, and update pull requests for the regi-volley repo, end to end - pushing the branch, writing the description grounded in the real commits, and running gh pr create/edit. Use whenever the user asks to open a PR, create a pull request, "ship this branch", submit work for review, update/refresh an existing PR's description, or wants a PR description written for the current changes.
---

# Opening a PR in regi-volley

## Before opening

1. Confirm you're not on `main` (`git branch --show-current`) - a PR needs a feature branch.
2. Run the full build and make sure it's green:
   ```bash
   ./mvnw verify
   ```
   (`verify` = tests + architecture tests + the JaCoCo 85% gate, same as CI; needs Docker for
   Testcontainers.) A red PR wastes the reviewer's time. If something is failing and the user
   wants to open the PR anyway (e.g. to get early feedback on a draft), open it as `--draft` and
   say so.
3. Find the base branch rather than assuming `main`:
   ```bash
   gh repo view --json defaultBranchRef -q .defaultBranchRef.name
   ```
4. Read the real commits that will go into the PR - this is the source of truth for the
   description, not a restatement of the conversation that produced them:
   ```bash
   git log --oneline <base>..HEAD
   git diff <base>...HEAD
   ```
   If there are no commits ahead of base, say so and stop rather than opening an empty PR.

## Collecting related issues from the commits

Every commit in this repo carries a `closes|fixes|refs #<N>` reference (the `github-commit`
skill's format). Scan `git log <base>..HEAD` for those and carry them into the PR body verbatim -
`closes`/`fixes` stay as closing keywords so GitHub auto-closes the issue on merge, `refs` stays a
plain reference. Don't invent an issue number if a commit doesn't have one. Pull the `US-xx` /
`RN-xx` from those issues' "Requirements" section into the PR body too.

## Writing the description

```markdown
## What
One or two sentences on what this PR does.

## Related issues
Closes #<N>
Refs #<N>

Requirements: US-xx · RN-xx

## Changes
- Bullet points of the specific changes, grouped by area
- Call out which architecture layer(s) moved: domain / application / infrastructure
- Mention any Flyway migration added, and any files deleted or renamed

## Cross-cutting checks
- Multi-tenant: new tables/queries scoped by association_id, isolation test added (or N/A)
- Time: deadlines use the injected Clock, DST case covered (or N/A)
- Concurrency: last-seat path covered by the concurrent-booking test (or N/A)
- Personal data: nothing personal logged; RGPD export/erasure impact (or N/A)

## Testing
How this was verified - e.g. "84 tests pass (`./mvnw verify`, including JaCoCo's coverage
gate and the architecture tests)".
```

Keep it proportional to the change - a one-file typo fix doesn't need every section, and the
"Cross-cutting checks" section can be dropped entirely when none apply. Never skip
"Related issues" (every commit has one by convention) or the "why"; that's the part a reviewer
can't get from the diff alone.

Every PR body Claude opens or edits ends with:
```
🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

## Opening it

Publishing a PR is outward-facing (notifications go out, CI runs) - show the user the title and
full description before running `gh pr create`, the same way you'd confirm before sending a
message on someone's behalf. Once confirmed:

```bash
git push -u origin "$(git branch --show-current)"
gh pr create --title "<type>: <short summary>" --body-file <file>
```

Write the body to a temp file (the session scratchpad) and use `--body-file` instead of fighting
shell quoting. Relay the resulting PR URL back to the user.

If the repo has no `origin` remote or `gh` isn't authenticated (`gh auth status`), say so and
stop rather than trying to work around it.

## Updating an existing open PR

When more commits land on a branch that already has an open PR, GitHub updates the diff and
commit list on its own, but **the body does not** - a description written for the earlier state
goes stale and starts misleading reviewers. Redo the "collecting related issues" and "writing
the description" steps against the *current* `git log <base>..HEAD`, then:

```bash
git push
gh pr edit <number> --title "<title>" --body-file <file>
```

Find the PR number for the current branch with `gh pr view --json number -q .number` if you
don't already have it. Same confirmation rule as creation - show the new body first.

## This skill never merges

Opening or updating a PR is as far as this goes. A human reviews and approves every merge in
this repo - never run `gh pr merge`, never suggest the branch is "ready to merge" as if that's
your call to make. If the user asks you to merge a PR, tell them that's a step they need to do
themselves, not something this skill performs.
