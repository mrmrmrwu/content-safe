# Content Safe Starter 任务文档

- 文档版本：v0.1-draft
- 日期：2026-11-05
- 状态：待拆分进入迭代

## 1. 里程碑

| 里程碑 | 目标 | 退出标准 |
|---|---|---|
| M0 设计冻结 | PRD/设计/任务评审通过 | 范围、API、配置 schema、发布仓库确定 |
| M1 骨架可用 | api/spi/model/core/starter 骨架 | `content-safe-samples-basic` 能启动并触发 mock provider |
| M2 首家 provider | 阿里或腾讯文本+图片同步 | 真实/沙箱接口契约测试通过 |
| M3 多云与异步 | 第二家 provider、仲裁、图片异步/回调 | 主备切换、重复回调幂等、验签失败死信 |
| M4 治理完善 | 健康、熔断、凭据链、指标、错误目录 | 故障演练通过，指标接入 Micrometer |
| M5 发布与生态 | samples、文档、BOM、Maven Central | 发布 artifacts，文档与 Feature Matrix 完整 |

## 2. WBS 任务分解

| ID | 任务 | 产出 | 优先级 | 依赖 | 验收 |
|---|---|---|---|---|---|
| T-001 | 定义 groupId/artifactId/包名/BOM | 仓库结构与命名 | P0 | - | 模块可构建，BOM 可 import |
| T-002 | api 模型与门面 | api 模块 | P0 | T-001 | Checkstyle/ArchUnit：不依赖 Spring/云 SDK |
| T-003 | spi 扩展接口 | spi 模块 | P0 | T-002 | 自定义 provider/scene 可编译实现 |
| T-004 | model：plan/capabilities/scene/event/health | model 模块 | P0 | T-002 | 序列化与不可变对象测试通过 |
| T-005 | core 校验与输入治理 | validator/normalizer/redactor | P0 | T-004 | URL/Base64/MIME/GIF 预检测试通过 |
| T-006 | core 路由与能力过滤 | router/capability filter | P0 | T-004 | 不支持能力不进入 provider |
| T-007 | core 熔断限流抽象 | breaker/limiter SPI + 默认实现 | P0 | T-006 | 超时/429/5xx 触发 fallback |
| T-008 | core 仲裁策略 | arbitration policy | P1 | T-006 | 多结果聚合单测覆盖 |
| T-009 | core 事件与审计 | AuditEvent/Listener | P0 | T-004 | listener 有序执行，异常隔离 |
| T-010 | starter 配置绑定 | ContentSafeProperties | P0 | T-001 | yml 补全、@Validated、fail-fast |
| T-011 | starter 自动装配 | AutoConfiguration.imports | P0 | T-010 | 无云 SDK 不炸，@ConditionalOnMissingBean 可覆盖 |
| T-012 | starter 健康与指标 | HealthIndicator/Metrics | P0 | T-011 | Actuator 暴露，health 不重远程调用 |
| T-013 | provider-aliyun | text/image operation | P0 | T-003/T-006 | 契约测试与限制校验通过 |
| T-014 | provider-tencent | tms/ims operation | P1 | T-013 | BizType/Type/GIF/OCR 映射通过 |
| T-015 | provider-baidu | text/image operation | P1 | T-013 | access_token 与策略映射通过 ✅ |
| T-016 | provider-volcengine | veImageX/risk operation ✅（risk-text/risk-image 同步，veImageX 异步待 v1.1） | P2 | T-013 | product split 与回调通过 |
| T-017 | provider-shumei | text/image operation ✅（供应商 extra 扩展键统一 kebab-case） | P2 | T-013 | tokenId/finalResult/riskSource 映射通过 |
| T-018 | async task store | 内存实现 + SPI | P1 | T-003 | save/find/markDone/markFailed |
| T-019 | callback verifier | 阿里云/腾讯/数美/火山实现 | P1 | T-018 | 验签失败死信，重复回调幂等 |
| T-020 | credentials provider | static/STS/CAM/KMS 抽象 | P0 | T-003 | 密钥不进日志，可替换实现 |
| T-021 | error catalog + failure analyzer | 错误码与启动诊断 ✅ | P1 | T-013 | 配置错误可读提示 |
| T-022 | samples | basic/routing-failover/async-callback ✅ | P0 | M2 | README 可复现 |
| T-023 | 文档 | quick start/config reference/guides/FAQ ✅ | P0 | M4 | 含限制、合规、Feature Matrix |
| T-024 | 发布流水线 | GPG/SBOM/Central/GitHub Packages ✅（凭据注入需发布时配置，见 docs/release-guide.md） | P0 | M4 | artifacts 发布与校验 |
| T-025 | 兼容与 deprecation policy | 文档 + japicmp/revapi ✅ | P1 | M4 | CI 检查 api/spi 破坏变更 |
| T-026 | AIGC 检测开关 | AuditOptions 请求期开关 + 能力位单向门控 + 腾讯/阿里接入 ✅ | P1 | T-013/T-014 | 支持面见 Feature Matrix 平台级能力节 |
| T-027 | 人审闭环 | ReviewEvent 发布 + resolveReview 终态归一 + ReviewResultSink 回写 ✅ | P1 | T-007 | MANUAL 终态、sink 先行、任务幂等迁移 |
| T-028 | 个人依赖仓库发布环境 | `.mvn/settings-release-template.xml`（凭据模板，env 注入）+ release-guide §5（云效/Nexus altDeploymentRepository 实操）+ 全模块 README ✅ | P2 | T-024 | 模板入库无真实凭据；本地副本被 .gitignore 忽略 |

## 3. 最近两周启动计划

| 天数 | 任务 | 产出 |
|---|---|---|
| D1-D2 | T-001/T-002 | 仓库骨架、api DTO/枚举/异常、ArchUnit 规则 |
| D3-D4 | T-003/T-004 | spi、model、SceneDefinition/ProviderPlan/Capabilities |
| D5-D7 | T-005/T-006/T-010 | 输入治理、路由、配置属性与 fail-fast |
| D8-D10 | T-011/T-020 | starter 装配、凭据链、ApplicationContextRunner 测试 |
| D11-D12 | T-009/T-012 | 事件、HealthIndicator、Micrometer binder |
| D13-D14 | T-022 最小样例 + mock provider | content-safe-samples-basic 跑通，准备 M2 接首家真实 provider |

## 4. Definition of Done

- 每个 PR：单元测试、契约样本、文档片段、配置 metadata 更新。
- provider PR：特性矩阵更新、错误码映射、限制校验、故障注入用例。
- starter PR：ApplicationContextRunner 覆盖条件装配与 fail-fast。
- 发布 PR：GPG 签名、sources/javadoc、SBOM、release notes、migration note（如配置破坏变更）。

## 5. 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| 供应商接口/场景 ID 变化 | 适配失效 | ProviderPlan 版本化 + 契约测试 + Feature Matrix |
| 重 SDK 依赖冲突 | 引入方构建失败 | provider optional、BOM、依赖收敛、shading 禁用优先 |
| 异步回调乱序/重复 | 状态不一致 | task state machine、幂等键、终态版本、死信 |
| 合规投诉 | 企业不可用 | 默认脱敏、最小化留存、文档责任边界、证据访问控制 |
| 供应商 EOL | 迁移成本 | lifecycle 元数据、启动告警、migration guide |
| 需求膨胀到音视频 | v1 延期 | scope gate：v1 只承诺 text/image，其他 abstract-only |

## 6. 立即开始的 5 个 issue

1. #1 初始化多模块 Maven 工程与 BOM。
2. #2 定义 `AuditRequest/AuditResult/ContentRef/SceneDefinition/ProviderPlan`。
3. #3 定义 spi：`AuditOperationProvider/AuditOperation/AuditRouter/AuditTaskStore/CallbackVerifier`。
4. #4 starter：`ContentSafeProperties` + `ContentSafeAutoConfiguration` + `ApplicationContextRunner` 测试。
5. #5 `content-safe-samples-basic`：用 mock provider 跑通 nickname 文本审核与 avatar 图片审核。
