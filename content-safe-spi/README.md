# content-safe-spi

扩展点（SPI）契约模块：供应商接入与核心机制替换的全部接口。
仅依赖 `content-safe-api`；云 SDK 类型**禁止**出现在任何 SPI 签名中（ArchUnit 强制），
云 SDK 依赖必须 `optional`。

## SPI 一览

| 接口 | 谁来实现 | 说明 |
| --- | --- | --- |
| `AuditOperationProvider` | 供应商模块 | 按场景产出 `AuditOperation` 列表（provider 接入入口） |
| `AuditOperation` | 供应商模块 | 单个可执行操作：`plan()`（`ProviderPlan`）+ `capabilities()` + `audit()` |
| `AuditRouter` | 可替换（默认 `PriorityAuditRouter`） | 候选列表中选主（按 order 升序，能力过滤后取首） |
| `AuditArbitrationPolicy` | 可扩展 | 多结果仲裁策略（如 `FIRST_NON_PASS`） |
| `AuditResultNormalizer` | 供应商模块（可选） | 原始响应 → `AuditResult` 归一 |
| `AuditListener` | 业务侧 | 审计事件出口：`AuditEvent` / `RiskHitEvent` / `ReviewEvent` / 治理事件 |
| `ReviewResultSink` | 业务侧 | 人审终态回写出口，`resolveReview` 先回写后迁移任务终态 |
| `AuditTaskStore` | 可替换（core 提供内存默认） | 异步任务存储（幂等终态迁移） |
| `CallbackVerifier` | 可替换 | 异步回调验签 |
| `CredentialsProvider` / `CredentialsSupport` | 业务侧（可选） / 工具 | 自定义凭证注入（如 KMS）；静态解析工具 |
| `EvidenceRedactor` | 业务侧（可选） | 命中证据脱敏 |
| `ContentRefNormalizer` | 可扩展 | 内容载体预处理 |
| `ProviderHealthProbe` | 供应商模块（可选） | 健康探测 |
| `SceneDefinitionProvider` | 可扩展（starter 提供 JSON 实现） | 场景定义来源 |

供应商接入清单与安全约束见 [CLAUDE.md](../CLAUDE.md)；接口设计见
[设计文档](../docs/content-safe-starter-design.md)。
