# nacos-sdk-proto

[中文](README_zh.md)

Shared Protocol Buffers definitions for Nacos multi-language SDKs.

This repository provides a single source of truth for all gRPC message types used in Nacos client-server communication. Multi-language SDKs (Go, Python, Node.js, etc.) can generate native code from these proto files instead of manually maintaining JSON serialization logic.

**Proposal**: [alibaba/nacos#14683](https://github.com/alibaba/nacos/issues/14683)

## Repository Structure

```
nacos-sdk-proto/
├── proto/                          # Generated .proto files (by proto-generator)
│   ├── VERSION                     # Source tracking (nacos ref, commit SHA)
│   ├── nacos_grpc_service.proto    # Transport layer: Payload, Metadata, gRPC services (hand-maintained)
│   ├── common/                     # Connection lifecycle messages
│   ├── config/                     # Config request/response messages
│   ├── naming/                     # Naming request/response messages
│   ├── ai/                         # AI/MCP related messages
│   └── lock/                       # Distributed lock messages
├── go/                             # Generated Go code + go.mod
│   ├── go.mod
│   └── go.sum
├── python/                         # Python package skeleton
│   ├── pyproject.toml
│   └── nacos_sdk_proto/
├── nodejs/                         # Generated Node.js/TypeScript code (ts-proto)
│   ├── package.json
│   ├── tsconfig.json
│   └── src/                        # ts-proto generated .ts files
├── tools/proto-generator/          # Java reflection-based proto generator
├── docs/
│   └── type-registry.json          # Registry of metadata.type values
├── Makefile                        # Build orchestration (REPO_OWNER parameterized)
├── buf.yaml                        # Buf linter config
└── .github/workflows/
    ├── ci.yml                      # PR verification (Go build + tsc)
    ├── sync-proto.yml              # Weekly auto-sync from nacos develop → develop branch
    ├── release.yml                 # Release prepare: generate + create PR
    └── publish.yml                 # Auto-publish on release commit merge to main
```

## Message Types

112 messages across 5 modules, auto-generated from 73 Nacos Payload classes:

| Module | Description |
|--------|-------------|
| common | Connection setup, health check, server check, error |
| config | Config CRUD, batch listen, change notification |
| naming | Instance register/deregister, service query, subscribe |
| ai     | MCP server/tool/endpoint, AI agent registry |
| lock   | Distributed lock acquire/release |

See [`docs/type-registry.json`](docs/type-registry.json) for the complete registry with direction and version info.

## Quick Start

### Prerequisites

- Java 17+ and Maven (for proto-generator)
- Go 1.20+ with `protoc-gen-go`, `protoc-gen-go-grpc`
- Python 3.11+ with `grpcio-tools` (for Python code generation)
- Node.js 20+ and npm (for ts-proto)
- `protoc` (Protocol Buffers compiler)

```bash
go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest
pip install grpcio-tools
npm install   # installs ts-proto in root
```

### One-Command Sync (recommended)

```bash
make sync    # clone nacos → build api → clean → generate all → verify
```

Automatically clones Nacos develop HEAD, builds nacos-api, cleans all generated files, regenerates proto + Go + Node.js + Python code, and runs full verification (Go build, TypeScript compile, idempotency check). Skips if already up to date.

### Manual Steps

```bash
make clean             # Remove all generated files (preserves hand-written files)
make generate-proto    # Java reflection → .proto files
make generate          # protoc → Go + Node.js (ts-proto) + Python
make verify            # Go build + tsc --noEmit + idempotency check
```

## Consuming the Packages

### Go

```bash
go get github.com/nacos-group/nacos-sdk-proto/go@latest
```

### Python

```bash
pip install nacos-sdk-proto    # after release to PyPI
```

### Node.js

```bash
npm install @nacos-group/sdk-proto   # after release to npm
```

### Other Languages

Use `protoc` with the appropriate language plugin. All proto files are under the `proto/` directory.

## Version Compatibility

Each release is generated from a specific Nacos version. The exact provenance also ships inside each package (`go/version.go`, `src/version.ts`, `nacos_sdk_proto/version.py`) and in `proto/VERSION`.

<!-- version-compat:begin -->
| nacos-sdk-proto | Nacos | Nacos commit | Generated |
|---|---|---|---|
| v1.0.0-beta.9 | 3.2.1 | fa05d6e | 2026-08-06 |
| v1.0.0-beta.8 | 3.2.1 | fa05d6e | 2026-05-22 |
| v1.0.0-beta.7 | 3.2.1 | fa05d6e | 2026-05-21 |
| v1.0.0-beta.6 | 3.2.1 | fa05d6e | 2026-05-21 |
| v1.0.0-beta.5 | 3.2.1 | fa05d6e | 2026-05-21 |
| v1.0.0-beta.4 | 3.2.1 | fa05d6e | 2026-05-21 |
| v1.0.0-beta.3 | 3.2.1 | fa05d6e | 2026-05-21 |
| v1.0.0-beta.2 | 3.2.1 | fa05d6e | 2026-05-21 |
<!-- version-compat:end -->

## Branch Strategy

- **main** — stable, only updated via release workflow (corresponds to a specific Nacos tag)
- **develop** — latest, weekly auto-sync from Nacos develop branch

## CI/CD

### Automated Sync (`sync-proto.yml`)

Runs weekly (Monday UTC 00:00) or manually via `workflow_dispatch`. Syncs from Nacos develop branch to the `develop` branch. Compares `proto/VERSION` commit SHA with Nacos develop HEAD — skips if unchanged.

Flow: clone nacos → `mvn install -pl api` → `make clean` → `make generate-proto` → `make generate` → `make verify-build` (Go build + tsc) → create PR targeting `develop` if diff exists.

### Release (`release.yml` + `publish.yml`)

Two-phase release process:

1. **Prepare** (`release.yml`): Manual trigger with inputs `nacos_tag` (required), `version` (optional), `dry_run` (default: true). Generates from a specific Nacos release tag, creates a PR with version bump.
2. **Publish** (`publish.yml`): Automatically triggered when the release PR is merged to main. Pushes `v{version}` and `go/v{version}` tags, publishes to PyPI and npm (OIDC Trusted Publishing), and creates a GitHub Release.

## Repository Transfer

The `REPO_OWNER` variable in `Makefile` controls all paths (go_package, module path, package URLs). To fork and adapt:

```bash
sed -i 's/REPO_OWNER     := nacos-group/REPO_OWNER     := your-org/' Makefile
make migrate
```

## Proto Naming Conventions

### Field Names

Field names use **camelCase** to match Java field names exactly. This is intentional — the Nacos server uses Protobuf JSON (protojson) serialization where field names map directly to JSON keys. Using camelCase in proto ensures the generated code produces JSON that the server understands without custom field mappings.

Example: `requestId`, `dataId`, `clientVersion`, `abilityTable`

> Note: This deviates from the proto3 convention of `snake_case` field names. The `buf.yaml` config excludes `FIELD_LOWER_SNAKE_CASE` lint rule for this reason.

### Flattened Inheritance

Java Nacos uses class inheritance (e.g., `ConfigQueryRequest extends ConfigRequest extends Request`). Proto3 does not support inheritance, so all fields from the class hierarchy are flattened into a single message. Common fields like `requestId`, `resultCode`, `errorCode`, and `message` are repeated in each message where they appear.

## Wire Format

The Nacos gRPC protocol wraps all business messages in a `Payload` envelope:

```
Payload {
  metadata: Metadata {
    type: "ConfigQueryRequest"    // Java SimpleName, used for deserialization routing
    clientIp: "10.0.0.1"
    headers: { ... }
  }
  body: Any {                     // google.protobuf.Any containing protojson-encoded bytes
    value: <JSON bytes>
  }
}
```

The `body.value` is **not** standard protobuf binary encoding — it is JSON bytes serialized with protojson. This allows the server to deserialize using its existing Jackson-based JSON infrastructure while SDKs can use protojson for type-safe serialization.

## License

Apache License 2.0
