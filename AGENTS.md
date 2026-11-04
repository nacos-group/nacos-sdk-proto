# AGENTS.md

Project context for AI coding agents (Claude Code, Cursor, Copilot, Gemini, Codex, …).

## Project Overview

Shared Protocol Buffers definitions for Nacos multi-language SDKs. A Java reflection-based generator reads nacos-api classes and produces `.proto` files, which are then compiled to Go, Node.js (ts-proto), and Python.

## Build Commands

### Full sync (clone nacos → build → generate → verify)
```bash
make sync          # skips if proto/VERSION SHA matches nacos HEAD
FORCE=1 make sync  # force re-sync
```

### Individual steps
```bash
make setup             # clone/fetch nacos, mvn install nacos-api
make clean             # remove generated files (preserves hand-written ones)
make generate-proto    # Java reflection → .proto files
make generate          # protoc → Go + Node.js + Python
make generate-go       # Go only
make generate-nodejs   # Node.js only
make generate-python   # Python only
```

### Verification
```bash
make verify-build   # Java unit tests + Go build + tsc --noEmit
make verify         # verify-build + idempotency check (re-generate, expect no diff)
```

### Proto generator tests (Java)
```bash
cd tools/proto-generator && mvn test
```

### Node.js type check
```bash
cd nodejs && npx tsc --noEmit
```

## Architecture

### Code generation pipeline

1. **proto-generator** (`tools/proto-generator/`): Java tool that uses reflection to scan `nacos-api` Payload classes, extracts fields from the full class hierarchy (flattening inheritance), and writes `.proto` files. Field numbers are managed by `field-numbers.json` lock file to ensure wire compatibility.

2. **protoc compilation** (`Makefile`): Runs `protoc` with language-specific plugins to produce Go (`go/`), Node.js/TypeScript (`nodejs/src/`), and Python (`python/nacos_sdk_proto/`) code.

3. **nacos_grpc_service.proto** (`proto/nacos_grpc_service.proto`): Hand-maintained transport layer defining `Payload`, `Metadata`, and gRPC services. NOT auto-generated — do not delete during `make clean`.

### Wire format

All business messages are wrapped in a `Payload` envelope. The `body` field is `google.protobuf.Any` containing **protojson-encoded JSON bytes** (not standard protobuf binary). The `metadata.type` field carries the Java SimpleName for routing.

### Module organization

`.proto` files are grouped into module subdirectories (`proto/ai/`, `proto/common/`, `proto/config/`, `proto/lock/`, `proto/naming/`). `ModuleClassifier` assigns each class to a module by its nacos-api package (`.api.config.` → `config`, `.api.naming.` → `naming`, etc.; everything else → `common`). `Request`/`Response` classes go into `<module>_request.proto` / `<module>_response.proto`; other domain objects each get their own `<simplename>.proto`; inner classes are written into their enclosing class's file. `docs/type-registry.json` is a hand-curated index mapping each message type to its module, proto location, message direction (client-to-server / server-to-client), and `since` version.

### Proto conventions

- Field names use **camelCase** (not snake_case) to match Java field names exactly — required for protojson compatibility with the Nacos server. `buf.yaml` excludes `FIELD_LOWER_SNAKE_CASE` lint rule.
- Java inheritance is flattened: common fields (`requestId`, `resultCode`, `errorCode`, `message`) are repeated in each message.

### Field number stability

`tools/proto-generator/field-numbers.json` locks field-to-number assignments. This ensures wire compatibility across versions. The `FieldNumberManager` assigns numbers and tracks reserved fields for removed entries.

## Branch Strategy

- **main**: stable releases only, updated via release workflow
- **develop**: weekly auto-sync from Nacos develop branch

## CI

- PR to main triggers `ci.yml`: runs `make verify-build` (Java tests + Go build + tsc)
- `sync-proto.yml`: weekly cron syncs from nacos develop → develop branch
- `release.yml` + `publish.yml`: two-phase release (prepare PR → publish on merge to main, pushes tags, publishes to PyPI and npm)

## Repository Transfer

Change `REPO_OWNER` in `Makefile` and run `make migrate` to update all paths (go_package, module, package URLs).
