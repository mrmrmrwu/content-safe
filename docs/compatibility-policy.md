# 兼容与 Deprecation 政策

本文是兼容与 deprecation 约定全文；CI 手段（japicmp）见文末。安全与工程约束见 [CLAUDE.md](../CLAUDE.md)。

## 1. 版本语义（SemVer）

- `主版本.次版本.修订号`，起始 `1.0.0`；`0.x` 阶段允许小范围实验性调整，但对外契约（api/spi）同样遵守下述规则。
- **主版本**：api/spi 二进制不兼容变更（删类、删方法、改签名、改继承层次）；新增能改变产品方向的功能。
- **次版本**：向下兼容的新增（新类、新接口、新默认方法、新配置项）。
- **修订号**：修复缺陷，不修改任何已发布签名。

## 2. 对外契约范围

对外承诺兼容的 artifact：`content-safe-api`、`content-safe-spi`、`content-safe-model`、
`content-safe-core`、`content-safe-spring-boot-starter`、各 `content-safe-provider-*`、`content-safe-bom`。

- `internal` 包与 `impl` 结尾的包**不承诺任何兼容性**，可在任意版本变更（japicmp 默认排除）。
- 配置 schema（`content.safe.*`）对外承诺向后兼容；破坏式变更必须升 `content.safe.schema-version`
  并提供 migration note（接入指南 + release notes）。
- 错误码（`ErrorCodes`）与事件类型对外稳定：语义变更视同破坏式变更；新增错误码必须先更新错误码表文档。
- BOM 约束 provider 与 core 的版本组合；使用方不得自行混搭未在 BOM 中声明的组合。

## 3. Deprecation 生命周期

1. 标记阶段：`@Deprecated`（携带 `@deprecated` Javadoc 说明替代物与计划移除版本）+ 保持旧行为可用。
2. 过渡阶段：至少保留一个完整次版本周期；期间日志告警（级别 WARN，不打点错误）。
3. 移除阶段：仅在主版本（或重大破坏说明的次版本）移除，release notes 提供 migration note。
4. 例外：`internal` 包的废弃物可直接删除。

## 4. CI 兼容检查（japicmp）

- 覆盖模块：`content-safe-api`、`content-safe-spi`（对外契约的根）。
- CI 强制执行：`.github/workflows/ci.yml` 的 `compat` 作业在每次 push/PR 时运行
  `./mvnw -Pcompat-check -Dcompat.baseline.version=<上一发布版本> -s .mvn/ci-settings.xml verify -DskipTests`；
  基线版本取上一发布 tag 对应的版本号（当前 `1.1.0`），发布新版本时随 release-guide 检查单一并更新。
  基线 artifact 托管在 GitHub Packages（公开包拉取也要认证），凭据由 workflow 注入 `GITHUB_TOKEN`
  （`.mvn/ci-settings.xml` 内引用 `${env.GITHUB_TOKEN}`，不含任何真实凭据）。
- 判定规则：**二进制不兼容修改直接构建失败（硬门禁）**；源码级不兼容（如默认方法新增）仅生成报告，由人工评审。
- `*.internal.*` 与 `*.impl.*` 默认排除。
- 本地执行需自行提供可认证拉取基线的 settings（参考 `.mvn/ci-settings.xml`）。
