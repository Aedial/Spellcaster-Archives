#!/usr/bin/env bash
set -euo pipefail
# build_release_notes.sh
# Generates release notes combining annotated tag message and CHANGELOG.md section.
# Usage: build_release_notes.sh <tag> <repo_owner> <repo_name> <commit_sha> <github_server_url>
# Outputs markdown to STDOUT.

TAG=${1:?tag required}
REPO_OWNER=${2:?owner required}
REPO_NAME=${3:?name required}
TAG_COMMIT=${4:?commit sha required}
GITHUB_SERVER_URL=${5:-https://github.com}

# Normalize version core (strip leading v and any suffix like -rc1)
VERSION_CORE=$(echo "$TAG" | sed -E 's/^v//; s/^([0-9]+(\.[0-9]+)*).*/\1/')

TAG_MESSAGE=$(git tag -l --format='%(contents)' "$TAG" || true)
# Remove potential trailing whitespace in tag message
TAG_MESSAGE=$(printf '%s' "${TAG_MESSAGE}" | sed -E ':a; /\n$/ {N; ba}; s/[[:space:]]+$//')

# Extract the section from CHANGELOG.md belonging to this version
CHANGELOG_CONTENT=""
if git show "${TAG_COMMIT}:CHANGELOG.md" > /tmp/CHANGELOG_ALL 2>/dev/null; then
  # Grab section starting after heading line matching version until next heading
  EXTRACTED=$(awk -v ver="${VERSION_CORE}" '
    BEGIN{found=0}
    $0 ~ "^##[[:space:]]*\\[" ver "\\]" {found=1; next}
    found && $0 ~ "^##[[:space:]]*\\[" {exit}
    found {print}
  ' /tmp/CHANGELOG_ALL)
  if [ -n "${EXTRACTED}" ]; then
    CHANGELOG_CONTENT="${EXTRACTED}"
  else
    CHANGELOG_CONTENT=$(cat /tmp/CHANGELOG_ALL)
  fi
fi

# Filter to sections Added / Changed / Fixed / Security, keep their content blocks until next heading of same depth (### )
if [ -n "${CHANGELOG_CONTENT}" ]; then
  FILTERED=$(awk '
    BEGIN{cur=""; keep=0}
    /^###[[:space:]]+Added/ {cur="Added"; keep=1; print; next}
    /^###[[:space:]]+Changed/ {cur="Changed"; keep=1; print; next}
    /^###[[:space:]]+Fixed/ {cur="Fixed"; keep=1; print; next}
    /^###[[:space:]]+Security/ {cur="Security"; keep=1; print; next}
    /^###/ {cur=""; keep=0}
    {if(keep){print}}
  ' <<<"${CHANGELOG_CONTENT}")
  # If filtering yielded something non-empty use it, else keep original
  if [ -n "${FILTERED}" ]; then
    CHANGELOG_CONTENT="${FILTERED}"
  fi
fi

# Trim leading/trailing blank lines
trim_blank() { sed -E '/^[[:space:]]*$/{$d;}; 1{/^[[:space:]]*$/d;}' ; }
CHANGELOG_CONTENT=$(printf '%s\n' "${CHANGELOG_CONTENT}" | sed -E ':a;/^$/{$d;N;ba};' | sed -E '1{/^$/d;}') || true

# Build compare link (fallback to comparing previous tag if available)
COMPARE_LINK=""

# Determine previous tag (sorted by version/date via git tag --sort=-creatordate)
PREV_TAG=$(git tag --sort=-creatordate | grep -Fvx "${TAG}" | head -n1 || true)
if [ -n "${PREV_TAG}" ]; then
  COMPARE_URL="${GITHUB_SERVER_URL}/${REPO_OWNER}/${REPO_NAME}/compare/${PREV_TAG}...${TAG}"
  COMPARE_LINK="Full changelog: ${COMPARE_URL}"
fi

# Assemble final notes without embedding literal \n sequences
segments=()
if [ -n "${TAG_MESSAGE}" ]; then
  segments+=("${TAG_MESSAGE}")
fi
if [ -n "${CHANGELOG_CONTENT}" ]; then
  segments+=("${CHANGELOG_CONTENT}")
fi
if [ -n "${COMPARE_LINK}" ]; then
  segments+=("${COMPARE_LINK}")
fi

# Join segments with a blank line
if [ ${#segments[@]} -gt 0 ]; then
  # Print each segment separated by a blank line
  {
    for i in "${!segments[@]}"; do
      printf '%s' "${segments[$i]}"
      if [ "$i" -lt $((${#segments[@]} - 1)) ]; then
        printf '\n\n'
      else
        printf '\n'
      fi
    done
  } | sed -E ':a;/^$/{$d;N;ba};' # trim trailing blank lines
fi
