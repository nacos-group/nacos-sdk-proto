#!/usr/bin/env bash
set -euo pipefail

NACOS_DIR=".nacos"
PROTO_VERSION="proto/VERSION"

# 1. Clone or fetch
if [ -d "$NACOS_DIR" ]; then
    echo "Fetching nacos develop HEAD..."
    git -C "$NACOS_DIR" fetch origin develop --depth=1 -q
    git -C "$NACOS_DIR" reset --hard FETCH_HEAD -q
else
    echo "Cloning nacos..."
    git clone --depth=1 --branch develop https://github.com/alibaba/nacos.git "$NACOS_DIR"
fi

# 2. Quick skip check
REMOTE_SHA=$(git -C "$NACOS_DIR" rev-parse HEAD)
LOCAL_SHA=""
if [ -f "$PROTO_VERSION" ]; then
    LOCAL_SHA=$(jq -r .nacos_commit "$PROTO_VERSION" 2>/dev/null || echo "")
fi

# Double check: SHA must match AND generated files must exist
HAS_GENERATED=true
if [ -z "$(find go -name '*.pb.go' 2>/dev/null | head -1)" ]; then
    HAS_GENERATED=false
fi

if [ "${FORCE:-}" != "1" ] && [ "$REMOTE_SHA" = "$LOCAL_SHA" ] && [ "$HAS_GENERATED" = "true" ]; then
    echo "Already up to date ($REMOTE_SHA)."
    exit 0
fi

echo "Syncing from nacos@${REMOTE_SHA:0:7}..."

# 3. Build nacos-api
make setup

# 4. Clean → VERSION → Generate → Verify
# (VERSION must exist before `make generate`: generate-version renders from it)
make clean
make update-version
make generate-proto
make generate

# 5. Verify (stage first: verify's idempotency diff compares against the index,
# so unstaged legitimate changes would abort the script)
git add -A
make verify

echo "Sync complete. Review changes and commit."
