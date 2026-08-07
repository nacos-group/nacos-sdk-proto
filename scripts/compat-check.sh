#!/usr/bin/env bash
# Answers "does this Nacos release need a new nacos-sdk-proto release?".
#
# Regenerates proto on top of an already-released nacos-sdk-proto version, but
# using a different Nacos tag's nacos-api, then diffs the result. Everything
# happens in throwaway worktrees; the current working tree is never touched.
#
#   ./scripts/compat-check.sh 3.2.2                # against the latest v* tag
#   ./scripts/compat-check.sh 3.2.2 v1.0.0-beta.9  # against an explicit baseline
#
# Exit 0: output is identical -> no release needed, the baseline already serves
#         that Nacos release (record it via the README selection rule).
# Exit 1: output differs -> a release is needed.
# Exit 2: usage error.
#
# Note: this installs nacos-api into the shared ~/.m2 under whatever version the
# tag's pom declares in <revision>, which upstream does not always bump (tag
# 3.2.3 still declares 3.2.2). The check itself stays correct — the artifact is
# always rebuilt from the requested tag and resolved by that same coordinate —
# but afterwards ~/.m2 holds the last checked tag's code, not necessarily the
# code matching the coordinate's label.
set -euo pipefail

NACOS_TAG="${1:-}"
if [ -z "$NACOS_TAG" ]; then
    echo "usage: $0 <nacos_tag> [baseline_ref]" >&2
    exit 2
fi

BASELINE="${2:-}"
if [ -z "$BASELINE" ]; then
    BASELINE=$(git -c versionsort.suffix=- tag -l 'v*' --sort=-v:refname | head -1)
    if [ -z "$BASELINE" ]; then
        echo "error: no v* tag found; pass a baseline ref explicitly" >&2
        exit 2
    fi
fi

# proto/VERSION only records provenance (nacos ref, commit, timestamp); it always
# differs and says nothing about the generated API surface.
PATHSPEC=(proto ':!proto/VERSION' tools/proto-generator/field-numbers.json)

REPO_ROOT=$(git rev-parse --show-toplevel)
WORK=$(mktemp -d)
cleanup() {
    git -C "$REPO_ROOT" worktree remove --force "$WORK/baseline" 2>/dev/null || true
    rm -rf "$WORK"
    git -C "$REPO_ROOT" worktree prune
}
trap cleanup EXIT

echo "Baseline:  $BASELINE"
echo "Nacos tag: $NACOS_TAG"

echo "==> Cloning nacos@${NACOS_TAG}"
git -c advice.detachedHead=false clone --depth=1 --branch "$NACOS_TAG" -q \
    https://github.com/alibaba/nacos.git "$WORK/nacos"

echo "==> Building nacos-api"
(cd "$WORK/nacos" && mvn install -pl api -am -DskipTests -Drat.skip=true -q)

echo "==> Regenerating proto on top of ${BASELINE}"
git -C "$REPO_ROOT" worktree add --detach -q "$WORK/baseline" "$BASELINE"
ln -s "$WORK/nacos" "$WORK/baseline/.nacos"
# clean first so that messages dropped upstream surface as deletions
(cd "$WORK/baseline" && make clean >/dev/null && make generate-proto)

echo "==> Diffing against ${BASELINE}"
if [ -z "$(git -C "$WORK/baseline" status --porcelain -- "${PATHSPEC[@]}")" ]; then
    echo
    echo "IDENTICAL: nacos ${NACOS_TAG} generates the same output as ${BASELINE}."
    echo "No release needed — ${BASELINE} already serves Nacos ${NACOS_TAG}."
    exit 0
fi

git -C "$WORK/baseline" add -A -- "${PATHSPEC[@]}"
echo
echo "CHANGED: nacos ${NACOS_TAG} generates different output than ${BASELINE}."
git -C "$WORK/baseline" --no-pager diff --cached --stat -- "${PATHSPEC[@]}"
echo
echo "A release is needed. Run the Release Prepare workflow with nacos_tag=${NACOS_TAG}."
exit 1
