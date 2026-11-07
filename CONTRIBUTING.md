# 贡献指南

感谢关注 ContentSafe！欢迎以任何形式参与贡献：报告缺陷、补充文档、新增供应商适配、改进核心能力。

## 提交 Issue

- 缺陷报告请附：依赖版本、JDK/Spring Boot 版本、最小复现配置与代码、完整报错信息（请脱敏，**不得包含 accessKey/secret/用户内容原文**）。
- 功能建议请说明目标供应商/场景，以及期望的 API 形态。

## 开发环境

- JDK 17+、Maven 3.8+（推荐使用仓库自带 `./mvnw`）
- 首次构建：`./mvnw clean verify`（含 ArchUnit 架构测试与全部单元测试）

## 编码规范

- 遵循《阿里巴巴 Java 开发手册》，仓库内留档见 `docs/alibaba.md`。
- 设计风格对齐 Spring 源码思想：接口 + SPI 扩展点、`@ConditionalOnMissingBean` 可替换默认实现、约定优于配置。
- 提交信息格式：`type(scope): summary`（如 `feat(provider-shumei): 图片异步审核`）。

## 架构铁律（违反将被 ArchUnit 测试拒绝）

1. 模块依赖方向固定：`api ← spi ← model ← core ← provider-* ← spring-boot-starter`，禁止反向依赖。
2. 云厂商 SDK 类型不得出现在 `api/spi/model/core` 的任何签名中；provider 内部实现类放 `internal` 包。
3. 云 SDK 依赖必须声明 `optional`，默认零传递依赖。
4. 日志与异常消息中禁止出现密钥、签名 URL、用户内容原文。

## 新增供应商检查单

- 实现 `AuditOperationProvider` + 操作类（参考现有 provider 模块）。
- 声明 `ProviderPlan` 与 `AuditCapabilities`（能力先行，不支持的能力快速失败）。
- 结果归一化为统一 `AuditResult`；错误归一化为统一错误码。
- 提供 `contracts/*.json` 契约测试样本（官方文档打码样本，注明来源与查阅日期）。
- 更新 `docs/provider-feature-matrix.md` 与供应商 README。

## 提交 Pull Request

- 基于 `master` 分支开发，一次 PR 聚焦一个主题。
- 提交前确保 `./mvnw clean verify` 全绿；新增功能必须带单元测试。
- 参考 `.gitee/PULL_REQUEST_TEMPLATE.zh-CN.md` 描述变更与测试用例。

## License

贡献代码默认以 [Apache-2.0](LICENSE) 许可证开源。
