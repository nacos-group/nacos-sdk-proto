#!/usr/bin/env bash
# Rebuilds the Version Compatibility table between markers in README.md /
# README_zh.md, derived from released v* tags (each tag's proto/VERSION).
# Optional $1: a not-yet-tagged release version (e.g. 1.0.0-beta.9) whose row
# is rendered from the current working-tree proto/VERSION.
set -euo pipefail

TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT

row_from_json() {
    local label="$1" json="$2"
    local ref commit date
    ref=$(echo "$json" | jq -r .nacos_ref)
    commit=$(echo "$json" | jq -r .nacos_commit | cut -c1-7)
    date=$(echo "$json" | jq -r .generated_at | cut -c1-10)
    echo "| ${label} | ${ref} | ${commit} | ${date} |"
}

{
    echo "| nacos-sdk-proto | Nacos | Nacos commit | Generated |"
    echo "|---|---|---|---|"
    if [ $# -ge 1 ]; then
        row_from_json "v$1" "$(cat proto/VERSION)"
    fi
    for TAG in $(git -c versionsort.suffix=- tag -l 'v*' --sort=-v:refname); do
        if [ $# -ge 1 ] && [ "$TAG" = "v$1" ]; then continue; fi
        if JSON=$(git show "${TAG}:proto/VERSION" 2>/dev/null); then
            row_from_json "$TAG" "$JSON"
        fi
    done
} > "$TMP"

for F in README.md README_zh.md; do
    [ -f "$F" ] || continue
    grep -q 'version-compat:begin' "$F" || continue
    if ! grep -q 'version-compat:end' "$F"; then
        echo "error: $F has a begin marker but no end marker — refusing to truncate" >&2
        exit 1
    fi
    awk -v table="$TMP" '
        /<!-- version-compat:begin -->/ {
            print
            while ((getline line < table) > 0) print line
            close(table)
            skip = 1
            next
        }
        /<!-- version-compat:end -->/ { skip = 0 }
        !skip
    ' "$F" > "$F.new" && mv "$F.new" "$F"
done

echo "Compatibility table rebuilt."
