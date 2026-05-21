# nacos-sdk-proto

Nacos 多语言 SDK 共享的 Protocol Buffers 定义。

本仓库为 Nacos 客户端-服务端 gRPC 通信中使用的所有消息类型提供统一的 proto 定义。多语言 SDK（Go、Python、Node.js 等）可以直接从这些 proto 文件生成原生代码，无需手动维护 JSON 序列化逻辑。

**提案**: [alibaba/nacos#14683](https://github.com/alibaba/nacos/issues/14683)

## 仓库结构

```
nacos-sdk-proto/
├── proto/                          # 生成的 .proto 文件（由 proto-generator 生成）
│   ├── VERSION                     # 来源追溯（nacos ref、commit SHA）
│   ├── nacos_grpc_service.proto    # 传输层：Payload、Metadata、gRPC services（手动维护）
│   ├── common/                     # 连接生命周期消息
│   ├── config/                     # 配置管理请求/响应消息
│   ├── naming/                     # 服务发现请求/响应消息
│   ├── ai/                         # AI/MCP 相关消息
│   └── lock/                       # 分布式锁消息
├── go/                             # 生成的 Go 代码 + go.mod
│   ├── go.mod
│   └── go.sum
├── python/                         # Python 包骨架
│   ├── pyproject.toml
│   └── nacos_sdk_proto/
├── nodejs/                         # 生成的 Node.js/TypeScript 代码（ts-proto）
│   ├── package.json
│   ├── tsconfig.json
│   └── src/                        # ts-proto 生成的 .ts 文件
├── tools/proto-generator/          # 基于 Java 反射的 proto 生成器
├── docs/
│   └── type-registry.json          # metadata.type 值注册表
├── Makefile                        # 构建编排（REPO_OWNER 参数化）
├── buf.yaml                        # Buf linter 配置
└── .github/workflows/
    ├── sync-proto.yml              # 每周自动同步 nacos develop 分支
    └── release.yml                 # 手动发布（支持 dry-run）
```

## 消息类型

从 73 个 Nacos Payload 类自动生成 112 个 message，分布在 5 个模块：

| 模块 | 说明 |
|------|------|
| common | 连接建立、健康检查、服务端检查、错误响应 |
| config | 配置 CRUD、批量监听、变更通知 |
| naming | 实例注册/注销、服务查询、订阅 |
| ai     | MCP server/tool/endpoint、AI Agent 注册 |
| lock   | 分布式锁获取/释放 |

完整注册表见 [`docs/type-registry.json`](docs/type-registry.json)。

## 快速开始

### 前置依赖

- Java 17+ 和 Maven（用于 proto-generator）
- Go 1.20+，安装 `protoc-gen-go`、`protoc-gen-go-grpc`
- Python 3.11+，安装 `grpcio-tools`（用于 Python 代码生成）
- Node.js 20+ 和 npm（用于 ts-proto）
- `protoc`（Protocol Buffers 编译器）

```bash
go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest
pip install grpcio-tools
npm install   # 安装根目录 ts-proto
```

### 一键同步（推荐）

```bash
make sync    # clone nacos → build api → clean → 生成全部 → 验证
```

自动 clone Nacos develop 最新代码，构建 nacos-api，清理所有生成文件，重新生成 proto + Go + Node.js + Python 代码，并执行完整验证（Go 编译、TypeScript 编译、幂等性检查）。如果已是最新则秒级跳过。

### 手动步骤

```bash
make clean             # 清理所有生成文件（保留手写文件）
make generate-proto    # Java 反射 → .proto 文件
make generate          # protoc → Go + Node.js（ts-proto）+ Python
make verify            # Go 编译 + tsc --noEmit + 幂等性检查
```

## 使用方式

### Go

```bash
go get github.com/nacos-group/nacos-sdk-proto/go@latest
```

### Python

```bash
pip install nacos-sdk-proto    # 发布到 PyPI 后可用
```

### Node.js

```bash
npm install @nacos-group/sdk-proto   # 发布到 npm 后可用
```

### 其他语言

使用 `protoc` 配合对应语言插件即可，所有 proto 文件位于 `proto/` 目录下。

## CI/CD

### 自动同步（`sync-proto.yml`）

每周一 UTC 00:00 定时运行，也支持通过 `workflow_dispatch` 手动触发。运行时先比较 `proto/VERSION` 中的 commit SHA 与 Nacos develop HEAD，相同则跳过。

流程：clone nacos → `mvn install -pl api` → `make clean` → `make generate-proto` → `make generate` → `make verify`（Go 编译 + tsc + 幂等性）→ 有 diff 则创建 PR。

### 发布（`release.yml`）

手动触发，输入参数：`nacos_tag`（必填）、`version`（可选）、`dry_run`（默认 true）。

从指定 Nacos release tag 生成代码，commit 后创建 `v{version}` 和 `go/v{version}` 标签，再发布到 PyPI 和 npm（dry-run 模式下跳过实际发布）。

## 仓库转移

`Makefile` 中的 `REPO_OWNER` 变量控制所有路径（go_package、module path、包 URL）。转移到 `nacos-group` 时只需：

```bash
sed -i 's/REPO_OWNER     := cxhello/REPO_OWNER     := nacos-group/' Makefile
make migrate
```

## Proto 命名约定

### 字段命名

字段名使用 **camelCase**，与 Java 字段名完全一致。这是有意为之——Nacos 服务端使用 Protobuf JSON（protojson）序列化，字段名直接映射为 JSON key。proto 中使用 camelCase 可确保生成的代码产出服务端能识别的 JSON，无需自定义字段映射。

示例：`requestId`、`dataId`、`clientVersion`、`abilityTable`

> 注意：这偏离了 proto3 的 `snake_case` 字段命名惯例。`buf.yaml` 配置中已排除 `FIELD_LOWER_SNAKE_CASE` lint 规则。

### 继承扁平化

Java Nacos 使用类继承（如 `ConfigQueryRequest extends ConfigRequest extends Request`）。Proto3 不支持继承，因此所有父类字段被扁平化到单个 message 中。`requestId`、`resultCode`、`errorCode`、`message` 等公共字段会在每个 message 中重复出现。

## 通信格式

Nacos gRPC 协议将所有业务消息包装在 `Payload` 信封中：

```
Payload {
  metadata: Metadata {
    type: "ConfigQueryRequest"    // Java SimpleName，用于反序列化路由
    clientIp: "10.0.0.1"
    headers: { ... }
  }
  body: Any {                     // google.protobuf.Any，包含 protojson 编码的字节
    value: <JSON bytes>
  }
}
```

`body.value` **不是**标准的 protobuf 二进制编码，而是用 protojson 序列化的 JSON 字节。这使得服务端可以继续使用现有的 Jackson JSON 基础设施进行反序列化，同时 SDK 端可以用 protojson 实现类型安全的序列化。

## 许可证

Apache License 2.0
