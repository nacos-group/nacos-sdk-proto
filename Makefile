.PHONY: generate generate-proto generate-go generate-python generate-nodejs generate-version \
        clean verify verify-build sync-go-mod migrate sync setup update-version

# === 仓库配置（转移时只改这一行） ===
REPO_OWNER     := nacos-group
REPO_NAME      := nacos-sdk-proto
GO_MODULE_BASE := github.com/$(REPO_OWNER)/$(REPO_NAME)/go

# === Nacos 源码 ===
NACOS_REPO     := https://github.com/alibaba/nacos.git
NACOS_DIR      := .nacos

# === 动态版本（从 .nacos/pom.xml 读取） ===
NACOS_VERSION  = $(shell grep -m1 '<revision>' $(NACOS_DIR)/pom.xml 2>/dev/null | sed 's/.*<revision>\(.*\)<\/revision>.*/\1/')

# === 路径 ===
PROTO_DIR      := proto
GO_OUT         := go
PYTHON_OUT     := python
NODEJS_OUT     := nodejs
GENERATOR_DIR  := tools/proto-generator
LOCK_FILE      := $(GENERATOR_DIR)/field-numbers.json

# === 一键同步（本地入口） ===
sync:
	@FORCE=$(FORCE) ./scripts/sync.sh

# === 拉取 Nacos 源码 + 构建 nacos-api ===
setup:
	@if [ -d $(NACOS_DIR) ]; then \
		echo "Fetching nacos develop HEAD..."; \
		git -C $(NACOS_DIR) fetch origin develop --depth=1 -q; \
		git -C $(NACOS_DIR) reset --hard FETCH_HEAD -q; \
	else \
		echo "Cloning nacos..."; \
		git clone --depth=1 --branch develop $(NACOS_REPO) $(NACOS_DIR); \
	fi
	cd $(NACOS_DIR) && mvn install -pl api -am -DskipTests -Drat.skip=true -q

# === Proto 生成（Java 反射 → .proto） ===
generate-proto:
	cd $(GENERATOR_DIR) && mvn -q compile exec:java \
		$(if $(NACOS_VERSION),-Dnacos.version=$(NACOS_VERSION),) \
		-Dexec.mainClass="com.alibaba.nacos.proto.generator.ProtoGenerator" \
		-Dexec.args="--output ../../$(PROTO_DIR) --lockfile ../../$(LOCK_FILE) \
		  --go-module-base $(GO_MODULE_BASE)"

# === 各语言代码生成 ===
generate: generate-version generate-go generate-nodejs generate-python

generate-version:
	./scripts/render-version.sh

generate-go:
	find $(PROTO_DIR) -name '*.proto' | xargs protoc \
		--proto_path=$(PROTO_DIR) \
		--go_out=$(GO_OUT) --go_opt=paths=source_relative \
		--go-grpc_out=$(GO_OUT) --go-grpc_opt=paths=source_relative

generate-python:
	mkdir -p $(PYTHON_OUT)/nacos_sdk_proto
	python -m grpc_tools.protoc \
		--proto_path=$(PROTO_DIR) \
		--python_out=$(PYTHON_OUT)/nacos_sdk_proto \
		--grpc_python_out=$(PYTHON_OUT)/nacos_sdk_proto \
		$$(find $(PROTO_DIR) -name '*.proto')
	find $(PYTHON_OUT)/nacos_sdk_proto -type d -exec touch {}/__init__.py \;

generate-nodejs:
	mkdir -p $(NODEJS_OUT)/src
	find $(PROTO_DIR) -name '*.proto' -not -name 'nacos_grpc_service.proto' | xargs protoc \
		--plugin=./node_modules/.bin/protoc-gen-ts_proto \
		--ts_proto_out=$(NODEJS_OUT)/src \
		--ts_proto_opt=outputJsonMethods=true,outputEncodeMethods=false,outputClientImpl=false,exportCommonSymbols=false,useJsonName=true \
		--proto_path=$(PROTO_DIR)
	cd $(NODEJS_OUT)/src && find . -name '*.ts' -not -name 'index.ts' | sort | \
		sed 's|^\./||; s|\.ts$$||; s|^|export * from "./|; s|$$|";|' > index.ts

clean:
	# Proto（保留手写文件，删除 VERSION 使 skip check 失效）
	find $(PROTO_DIR) -name '*.proto' -not -name 'nacos_grpc_service.proto' -delete 2>/dev/null || true
	find $(PROTO_DIR) -type d -empty -delete 2>/dev/null || true
	rm -f $(PROTO_DIR)/VERSION
	# Go
	find $(GO_OUT) -name '*.pb.go' -delete
	# Node.js
	rm -rf $(NODEJS_OUT)/src/*
	# Python
	if [ -d $(PYTHON_OUT)/nacos_sdk_proto ]; then \
		find $(PYTHON_OUT)/nacos_sdk_proto -name '*_pb2*.py' -delete; \
		find $(PYTHON_OUT)/nacos_sdk_proto -name '__init__.py' -delete; \
		find $(PYTHON_OUT)/nacos_sdk_proto -type d -empty -delete; \
	fi
	@echo "Clean complete. Hand-written files preserved."

sync-go-mod:
	cd $(GO_OUT) && go mod edit -module $(GO_MODULE_BASE)

# === 更新 VERSION 溯源文件 ===
update-version:
	@SHA=$$(git -C $(NACOS_DIR) rev-parse HEAD); \
	DATE=$$(date -u +%Y-%m-%dT%H:%M:%SZ); \
	printf '{\n  "source": "local",\n  "nacos_ref": "develop",\n  "nacos_commit": "%s",\n  "generated_at": "%s"\n}\n' \
		"$$SHA" "$$DATE" > $(PROTO_DIR)/VERSION

verify-build:
	# 单元测试（含字段一致性校验）
	cd $(GENERATOR_DIR) && mvn -q test \
		$(if $(NACOS_VERSION),-Dnacos.version=$(NACOS_VERSION),)
	# Go 编译
	cd $(GO_OUT) && go build ./...
	# Node.js TypeScript 编译
	cd $(NODEJS_OUT) && npx tsc --noEmit

verify: verify-build
	# 幂等性检查：重新生成 proto，不应有 diff
	$(MAKE) generate-proto
	git diff --exit-code -- $(PROTO_DIR)/ ':!$(PROTO_DIR)/VERSION'

migrate:
	$(MAKE) generate-proto
	$(MAKE) generate
	$(MAKE) sync-go-mod
	sed -i '' 's|github.com/[^/]*/$(REPO_NAME)|github.com/$(REPO_OWNER)/$(REPO_NAME)|g' \
		$(PROTO_DIR)/nacos_grpc_service.proto \
		$(NODEJS_OUT)/package.json $(PYTHON_OUT)/pyproject.toml \
		README.md README_zh.md
	@echo "Done. Review changes and commit."
