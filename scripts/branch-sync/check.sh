#!/usr/bin/env bash
# Verifies that every patch on one release line has reached the other, or is declared not to.
#
# Usage: scripts/branch-sync/check.sh [--local]
#   --local  compare the local branches instead of the remote-tracking ones
#
# Exits 1 and lists each commit that lacks a declaration or whose counterpart is missing.
set -euo pipefail

root=$(git rev-parse --show-toplevel)
# shellcheck source=lib.sh
source "$root/scripts/branch-sync/lib.sh"
branch_lines_load

if [[ "${1:-}" == "--local" ]]; then
  mainline_ref="refs/heads/$BL_MAINLINE"
  maintenance_ref="refs/heads/$BL_MAINTENANCE"
else
  mainline_ref="refs/remotes/$BL_REMOTE/$BL_MAINLINE"
  maintenance_ref="refs/remotes/$BL_REMOTE/$BL_MAINTENANCE"
fi

for ref in "$mainline_ref" "$maintenance_ref"; do
  if ! git rev-parse --verify --quiet "$ref" >/dev/null; then
    echo "✖ branch-sync: $ref does not exist; fetch both release lines first" >&2
    exit 1
  fi
done

fork_point=$(git merge-base "$mainline_ref" "$maintenance_ref")
problems=0

# Reports commits on the source line that break the rules or are not yet on the target line.
check_line() {
  local branch="$1" source_ref="$2" target_ref="$3" target sha message verdict
  target=$(branch_lines_target_for "$branch")
  while read -r sha; do
    [[ -z "$sha" ]] && continue
    message=$(git log -1 --format=%B "$sha")
    if ! verdict=$(branch_lines_validate_message "$branch" "$message"); then
      echo "✖ $branch $(git log -1 --format='%h %s' "$sha")"
      echo "    $verdict"
      problems=$((problems + 1))
      continue
    fi
    [[ "$verdict" == "carry" ]] || continue

    if git log --format=%H --grep="^(cherry picked from commit $sha)\$" "$fork_point..$target_ref" | grep -q .; then
      continue
    fi
    if git cherry "$target_ref" "$sha" "$sha~1" | grep -q '^-'; then
      continue
    fi
    echo "✖ $branch $(git log -1 --format='%h %s' "$sha")"
    echo "    not yet on $target: git switch $target && git cherry-pick -x $sha"
    problems=$((problems + 1))
  done < <(git rev-list --no-merges --reverse "$fork_point..$source_ref")
}

check_line "$BL_MAINLINE" "$mainline_ref" "$maintenance_ref"
check_line "$BL_MAINTENANCE" "$maintenance_ref" "$mainline_ref"

if (( problems > 0 )); then
  echo "✖ branch-sync: $problems commit(s) since $(git log -1 --format=%h "$fork_point") need attention"
  exit 1
fi
echo "✔ branch-sync: $BL_MAINLINE and $BL_MAINTENANCE carry each other's patches since $(git log -1 --format=%h "$fork_point")"
