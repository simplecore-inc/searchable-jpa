#!/usr/bin/env bash
# Links the repository's commit-msg hook into this clone's hooks directory.
# An existing commit-msg hook is left in place and reported, never overwritten.
set -euo pipefail

root=$(git rev-parse --show-toplevel)
hooks_dir=$(git rev-parse --git-common-dir)/hooks
target="$hooks_dir/commit-msg"
source_hook="$root/scripts/git-hooks/commit-msg"

mkdir -p "$hooks_dir"
if [[ -e "$target" || -L "$target" ]]; then
  if [[ "$(readlink "$target" 2>/dev/null || true)" == "$source_hook" ]]; then
    echo "✔ commit-msg hook already installed"
    exit 0
  fi
  echo "✖ $target already exists; call $source_hook from it to chain the two" >&2
  exit 1
fi
chmod +x "$source_hook" "$root/scripts/branch-sync/check.sh"
ln -s "$source_hook" "$target"
echo "✔ commit-msg hook installed: $target -> $source_hook"
