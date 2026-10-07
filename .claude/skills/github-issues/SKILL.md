---
name: github-issues
description: Create and triage GitHub issues for the regi-volley repo via the gh CLI, including real linked GitHub sub-issues for work big enough to split up, traced back to the user stories (US-xx) and business rules (RN-xx) in docs/requirements.md. Use whenever the user asks to file a bug, request a feature, open an issue, turn an epic or user story into issues, wants to see what's open, or asks to triage/organize/clean up the issue tracker. Every commit and PR in this repo references an issue, so this is also the first step before any implementation work that doesn't already have one.
---

# Issues in regi-volley

## Creating an issue

### 1. Gather the content

- **Title** - short imperative phrase in English, e.g. "Promote first waitlisted booking when a
  seat frees up".
- **Requirements** - the `US-xx` and `RN-xx` from `docs/requirements.md` this issue implements.
  Read the file fresh; quote the acceptance criteria from the user story rather than
  paraphrasing them loosely. Work with no matching story (tooling, refactor, CI) simply omits
  this section.
- **Description** - a paragraph explaining the issue, referencing the architecture layer(s)
  involved if useful.
- **Sub-issues** - titles for discrete pieces of work, if the issue is big enough to warrant
  breaking up. The natural split here follows the layers: domain rule → use case → persistence
  (migration + adapter + isolation test) → web endpoint. Not every issue needs these.
- **Acceptance criteria** - a checklist of concrete, verifiable conditions. Prefer "booking a
  full session returns status `WAITLISTED` with position 3" over "handle full sessions".
  Include the cross-cutting checks when they apply:
  - new business table/query → "data from another association is never returned" (§8)
  - deadline/schedule logic → "tested with a fixed clock, including a DST-change week" (§9)
  - booking/capacity path → "concurrent bookings for the last seat confirm exactly one" (§10)
  - personal data → "no personal data in logs" and RGPD export/erasure considered

Ask the user directly for anything unclear rather than guessing at scope - especially where the
open questions at the end of `docs/requirements.md` affect the issue.

### 2. Format the body

```markdown
## Requirements
US-14 · RN-06, RN-07, RN-08

## Description
What the problem or request is, referencing the relevant architecture layer if useful.

## Sub-issues
<title of sub-issue 1>
<title of sub-issue 2>

## Acceptance Criteria
- [ ] Concrete, checkable condition
- [ ] Another one

## Related
Relates to #456, if applicable.
```

Omit "Requirements" or "Sub-issues" entirely if there are none - don't leave an empty header.
Each sub-issue gets its own short body (a sentence or two plus its own acceptance criteria).

### 3. Preview and get explicit confirmation

Filing an issue publishes content, same bar as opening a PR - show the user the fully rendered
title, body, labels and (if any) sub-issue titles exactly as they'll be created, and **do not
create anything until they explicitly confirm.** Moving on to another topic is not confirmation.
For a batch (e.g. a whole epic), preview the whole batch and confirm once.

### 4. Create it

Once confirmed, build a JSON spec and hand it to the bundled script - it creates the issue *and*
links any sub-issues via GitHub's native sub-issues API (which needs each issue's numeric id, not
just its number, and can't be done through `gh issue create` flags alone):

```json
{
  "repo": "mariohenriquep/regi-volley",
  "title": "Book a seat in a session",
  "body": "## Requirements\nUS-14 · RN-06, RN-07, RN-08\n\n## Description\n...\n\n## Acceptance Criteria\n- [ ] ...\n",
  "labels": ["enhancement", "domain", "E4-bookings"],
  "sub_issues": [
    { "title": "Add booking eligibility and capacity rules to Session", "body": "..." },
    { "title": "Add BookSessionService use case", "body": "..." }
  ]
}
```

Write it to a temp file (the session scratchpad, not the repo), then run:

```bash
.claude/skills/github-issues/scripts/create_issue.sh /path/to/spec.json
```

The script checks `gh auth status` itself and fails with a clear message if not logged in -
don't attempt to authenticate on the user's behalf, just relay the message. On success it prints
a JSON summary with the parent URL and each sub-issue URL - relay those links back.

Labels are only applied to the parent; add them to sub-issues afterwards with `gh issue edit`
if useful. A label must already exist on the repo before it can be applied - check first with
`gh label list`; if missing, create it from the taxonomy below
(`gh label create "X" --description "..." --color "RRGGBB"`) or ask the user, rather than
letting the whole script fail partway through.

## Labels

This repo's taxonomy, kept small on purpose:

- **Type**: `bug`, `enhancement`, `refactor`, `docs`, `testing`, `security`
- **Area**: `domain`, `application`, `infrastructure`, `web` - mirrors the onion architecture
  layers
- **Epic**: `E1-association`, `E2-members`, `E3-groups-sessions`, `E4-bookings`,
  `E5-plans-payments`, `E6-outreach` - mirrors the epics in `docs/requirements.md`
- **Cross-cutting** (only when it's the point of the issue): `multi-tenant`, `rgpd`

Don't invent new labels ad hoc without checking what already exists (`gh label list`).

## Triaging

```bash
gh issue list                                   # what's open
gh issue list --label "E4-bookings"             # one epic
gh issue view <number>                          # read one in full
gh issue edit <number> --add-label "bug,domain" # label it
gh issue close <number> --comment "<why>"       # close with a reason, never silently
```

When triaging a batch: read each issue, propose type + area (+ epic) labels, and apply the
unambiguous ones. For anything genuinely unclear (unreproducible bug report, vague feature ask,
an issue that depends on an unanswered open question), summarize it back to the user instead of
guessing at a label or closing it - triage should surface judgment calls, not paper over them.
