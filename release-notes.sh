#!/usr/bin/env bash
# Prints Markdown release notes for a tag from the Conventional Commits since the previous tag.
# The app, KernelSU and Magisk all show these notes, and GitHub's own generated notes list
# only pull requests, which this repository does not use.
set -euo pipefail
cd "$(dirname "$0")"
TAG=${1:?usage: release-notes.sh <tag>}
REPO=${GITHUB_REPOSITORY:-satotek/cellscope}
PREV=$(git describe --tags --abbrev=0 --match 'v*' "$TAG^" 2>/dev/null || true)
RANGE=${PREV:+$PREV..}$TAG

section() {
  local title=$1 pattern=$2 lines
  # Subjects look like "feat(scope): text"; the scope becomes a bold prefix.
  lines=$(git log --no-merges --reverse --format='%h %s' "$RANGE" | awk -v type="$pattern" '{
    hash = $1; subject = substr($0, length(hash) + 2)
    if (!match(subject, "^" type "(\\([^)]*\\))?!?: ")) next
    head = substr(subject, 1, RLENGTH - 2); text = substr(subject, RLENGTH + 1)
    scope = ""
    if (index(head, "(")) { scope = substr(head, index(head, "(") + 1); sub(/\)!?$/, "", scope) }
    printf "- %s%s (%s)\n", (scope == "" ? "" : "**" scope "**: "), text, hash
  }')
  [ -n "$lines" ] && printf '## %s\n\n%s\n\n' "$title" "$lines"
  return 0
}

section Features feat
section Fixes fix
section Performance perf
if [ -n "$PREV" ]; then
  echo "**Full changelog**: https://github.com/$REPO/compare/$PREV...$TAG"
fi
