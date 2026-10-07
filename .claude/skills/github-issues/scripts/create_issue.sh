#!/usr/bin/env bash
# Creates a GitHub issue (and, if present, real linked sub-issues) from a JSON spec.
#
# Usage: create_issue.sh <path-to-spec.json>
#
# Spec format:
# {
#   "repo": "owner/name",
#   "title": "Parent issue title",
#   "body": "Parent issue body (markdown)",
#   "labels": ["optional", "labels"],
#   "sub_issues": [
#     { "title": "Sub-issue title", "body": "Sub-issue body (markdown)" }
#   ]
# }
#
# Sub-issues are linked via GitHub's native sub-issues API (not just mentioned in the body),
# so they show up as a progress checklist on the parent and can be tracked/closed independently.
#
# Prints a JSON summary to stdout:
# { "parent": "https://github.com/...", "sub_issues": ["https://github.com/...", ...] }

set -euo pipefail

SPEC_FILE="${1:?Usage: create_issue.sh <path-to-spec.json>}"

if ! command -v gh >/dev/null 2>&1; then
  echo "error: gh CLI not found" >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "error: jq not found" >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "error: gh is not authenticated. Run 'gh auth login' first." >&2
  exit 1
fi

REPO=$(jq -r '.repo' "$SPEC_FILE")
TITLE=$(jq -r '.title' "$SPEC_FILE")
LABELS=$(jq -r '.labels // [] | join(",")' "$SPEC_FILE")

BODY_FILE=$(mktemp)
jq -r '.body' "$SPEC_FILE" > "$BODY_FILE"

CREATE_ARGS=(issue create --repo "$REPO" --title "$TITLE" --body-file "$BODY_FILE")
if [[ -n "$LABELS" ]]; then
  CREATE_ARGS+=(--label "$LABELS")
fi

PARENT_URL=$(gh "${CREATE_ARGS[@]}")
rm -f "$BODY_FILE"
PARENT_NUMBER=$(basename "$PARENT_URL")
PARENT_ID=$(gh api "repos/$REPO/issues/$PARENT_NUMBER" --jq '.id')

echo "Created parent issue: $PARENT_URL" >&2

SUB_URLS=()
SUB_COUNT=$(jq '.sub_issues // [] | length' "$SPEC_FILE")

for ((i = 0; i < SUB_COUNT; i++)); do
  SUB_TITLE=$(jq -r ".sub_issues[$i].title" "$SPEC_FILE")
  SUB_BODY_FILE=$(mktemp)
  jq -r ".sub_issues[$i].body" "$SPEC_FILE" > "$SUB_BODY_FILE"

  SUB_URL=$(gh issue create --repo "$REPO" --title "$SUB_TITLE" --body-file "$SUB_BODY_FILE")
  rm -f "$SUB_BODY_FILE"
  SUB_NUMBER=$(basename "$SUB_URL")
  SUB_ID=$(gh api "repos/$REPO/issues/$SUB_NUMBER" --jq '.id')

  gh api "repos/$REPO/issues/$PARENT_NUMBER/sub_issues" -F sub_issue_id="$SUB_ID" >/dev/null

  echo "Created and linked sub-issue: $SUB_URL" >&2
  SUB_URLS+=("$SUB_URL")
done

jq -n --arg parent "$PARENT_URL" --argjson subs "$(printf '%s\n' "${SUB_URLS[@]:-}" | jq -R . | jq -s 'map(select(length > 0))')" \
  '{parent: $parent, sub_issues: $subs}'
