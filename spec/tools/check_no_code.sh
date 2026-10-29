#!/bin/sh
# CCRM-92 (Shared Spec): spec/ holds Markdown and JSON only. Fails if any other file type lands
# outside spec/tools/. Run from the repo root (CI does): sh spec/tools/check_no_code.sh
set -eu
root="${1:-spec}"
bad=$(find "$root" -type f ! -path "$root/tools/*" \
    ! -name '*.md' ! -name '*.json' ! -name '*.jsonl' ! -name '.DS_Store' -print)
if [ -n "$bad" ]; then
    echo "spec/ must hold only Markdown and JSON; found:" >&2
    echo "$bad" >&2
    exit 1
fi
echo "spec/: no code"
