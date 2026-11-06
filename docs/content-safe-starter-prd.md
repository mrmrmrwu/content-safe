# Content Safe Starter 需求文档（PRD）

- 文档版本：v0.1-draft
- 日期：2026-11-05
- 状态：待评审
- 读者：业务开发、框架开发、SRE、安全合规、审核运营、架构师

## 1. 背景

业务系统存在大量 UGC 内容：昵称、头像、评论、帖子、商品图文、IM 消息等。主流云厂商均提供内容安全能力，但接口、鉴权、场景 ID、返回结构、计费方式、输入限制、异步模式差异很大。业务方直接对接多家供应商会出现：代码耦合云 SDK、场景字符串散落、无法统一审计、无法做多云容灾与成本路由、供应商 EOL 时迁移困难。

本项目提供一个可引入的 Spring Boot Starter dependency，把多家供应商的文本与图片内容安全能力统一成稳定 API，并通过 yml/SPI 支持业务场景扩展与新供应商扩展。

## 2. 目标与非目标

### 2.1 目标

- 业务方通过引入 starter + 写 yml 完成文本/图片审核接入。
- 统一抽象：请求、结果、命中详情、场景、供应商计划、能力、异步任务。
- 支持主流供应商：阿里云、腾讯云、百度智能云、火山引擎、数美；预留华为云生命周期处理。
- 支持同步审核、图片批量/异步、回调验签、任务查询。
- 支持 provider 级与 operation 级健康、熔断、限流、成本路由。
- 支持业务场景注册：内置常量 + yml + Java/SPI 扩展。
- 支持扩展：自定义供应商、归一化器、路由策略、监听、凭据、任务存储、回调验签。
- 默认安全：输入 URL/内容治理、证据脱敏、错误 fail-fast、审计事件。

### 2.2 v1 非目标

- 不承诺音视频、文件、直播实时流核心能力；仅预留多模态抽象。
- 不内置复杂人工审核工作流，仅输出复审/命中事件。
- 不强制引入 Spring Cloud；限流/熔断可接 Resilience4j 但保持 optional。
- 不做内容安全算法本身；本库是供应商适配与治理层。

## 3. 用户角色

| 角色 | 诉求 |
|---|---|
| 业务开发者 | 少代码接入，稳定 API，清晰错误 |
| 扩展开发者 | 低成本新增 provider/scene/normalizer/listener |
| SRE/平台 | 健康、指标、熔断、成本、EOL 预警 |
| 安全合规 | 数据最小化、脱敏、审计、密钥管理 |
| 审核运营 | 命中证据、复审事件、申诉闭环可扩展 |

## 4. 典型用例

- UC-01 评论发布前同步文本审核。
- UC-02 头像上传图片审核，支持 URL/Base64。
- UC-03 商品主图批量同步审核。
- UC-04 图片异步提交、回调接收、任务查询、重复回调幂等。
- UC-05 同一图片多供应商仲裁：阿里 REVIEW、腾讯 PASS、数美 REJECT。
- UC-06 新增业务场景 `resume_title`，不改代码只改 yml。
- UC-07 新增私有供应商，实现 SPI 并打成二方包。
- UC-08 供应商限流/超时后自动切换备用供应商。
- UC-09 所有审核记录输出审计事件与指标，不落敏感明文。
- UC-10 华为云等 EOL 供应商启动告警并建议迁移。

## 5. 功能需求

| 编号 | 需求 | 优先级 | 验收要点 |
|---|---|---|---|
| FR-001 | 提供 `ContentSafeTemplate` 门面 | P0 | 注入即用，支持 audit/text/image/submitAsync/query/handleCallback |
| FR-002 | 统一请求模型 `AuditRequest` | P0 | 支持 scene/type/content/dataId/context/options/passThrough |
| FR-003 | 统一结果模型 `AuditResult` | P0 | PASS/REVIEW/REJECT/ERROR，final/source/hits/requestId/provider |
| FR-004 | 内容引用 `ContentRef` | P0 | TEXT/URL/BASE64/BYTES/OBJECT_KEY，按 provider 能力校验 |
| FR-005 | 场景注册 SceneDefinition | P0 | 内置常量 + yml + SPI；启动校验 |
| FR-006 | 供应商映射 | P0 | scene -> provider plan/service；未知映射 fail-fast |
| FR-007 | ProviderPlan/Capabilities | P0 | provider+product+apiVersion+operation+service+mode+limits |
| FR-008 | 文本同步审核 | P0 | 至少接入一家供应商 |
| FR-009 | 图片同步审核 | P0 | 至少接入一家供应商 |
| FR-010 | 图片批量审核 | P1 | 按 maxBatchSize 分片，部分失败可追踪 |
| FR-011 | 图片异步提交/查询/回调 | P1 | taskId/providerTaskId/dataId 映射，重复回调幂等 |
| FR-012 | 回调验签 CallbackVerifier | P0 | SPI + 默认实现，验签失败进死信 |
| FR-013 | 健康探测 ProviderHealthProbe | P0 | UP/DEGRADED/DOWN/NOT_CONFIGURED，后台异步刷新 |
| FR-014 | operation 级熔断/限流 | P0 | 按 plan 隔离，支持半开恢复 |
| FR-015 | 路由策略 AuditRouter | P0 | 主备、按场景、按成本、能力过滤 |
| FR-016 | 仲裁策略 AuditArbitrationPolicy | P1 | 多供应商结果聚合：FIRST_NON_PASS/VOTE/PRIORITY/WEIGHTED |
| FR-017 | 凭据链 CredentialsProvider | P0 | static/STS/CAM/KMS/secret-ref，多租户扩展 |
| FR-018 | 审计事件 AuditListener | P0 | audit/hit/review/degraded 事件，可接 MQ/DB |
| FR-019 | 指标 Micrometer | P0 | provider/scene/suggestion/cost/fallback/callback 指标 |
| FR-020 | 错误目录 ProviderErrorCatalog | P1 | 云错误码归一，FailureAnalyzer 提示 |
| FR-021 | 输入治理 ContentRefNormalizer | P0 | URL allowlist、禁内网、大小/MIME/GIF 预检、objectKey 转签名 URL |
| FR-022 | 数据脱敏 EvidenceRedactor/PiiMasker | P1 | 证据截断/掩码，raw 默认不进日志 |
| FR-023 | 生命周期 LifecycleStatus | P1 | ACTIVE/DEPRECATED/EOL/REMOVED，装配告警 |
| FR-024 | 配置 schema-version | P1 | 配置破坏式变更可迁移 |
| FR-025 | BOM 与模块发布 | P0 | api/spi/core/starter/provider/bom 分 artifact 发布 |

## 6. 非功能需求

| 类别 | 要求 |
|---|---|
| 性能 | 同步审核默认超时 2s 可配；模板调用额外开销 < 5ms；批量按 provider limit 自动分片 |
| 可用性 | 支持主备切换；单 provider 抖动不影响未熔断 operation；回调失败可重放/死信 |
| 兼容性 | Java 17+；Spring Boot 3.x；api/spi 承诺兼容，core/spring 谨慎演进，internal 不兼容 |
| 安全 | 密钥不进日志；支持凭据提供链；URL SSRF 防护；回调验签；最小化数据留存 |
| 合规 | 文档明确用户授权、第三方上传、跨境/留存、敏感信息处理；默认不保存原文 |
| 可观测 | HealthIndicator、MeterRegistry、结构化日志、审计事件 |
| 可维护 |  provider 模块独立，云 SDK optional；依赖收敛；特性矩阵；samples |
| 文档 | quick start、配置参考、provider guide、custom scene guide、FAQ、限制说明 |

## 7. 对外 API 需求摘要

业务方主要 import：

- `ContentSafeTemplate`
- `AuditRequest` / `ContentRef` / `AuditContext` / `AuditOptions`
- `AuditResult` / `RiskHit`
- 枚举：`ContentType`、`RiskSuggestion`、`AuditSource`、`Severity`
- 常量：`Scenes`

扩展方主要 import：

- `spi.AuditOperationProvider` / `AuditOperation`
- `spi.AuditResultNormalizer`
- `spi.AuditRouter` / `spi.AuditArbitrationPolicy`
- `spi.AuditListener`
- `spi.AuditTaskStore`
- `spi.CallbackVerifier`
- `spi.ProviderHealthProbe`
- `spi.SceneDefinitionProvider`
- `spi.CredentialsProvider`
- `spi.ContentRefNormalizer`
- `spi.EvidenceRedactor`

## 8. 约束与依赖

- 构建：Maven 多模块；发布 BOM。
- 依赖：云 SDK 必须 optional；core 不强依赖 Spring；starter 只做装配。
- 运行：不启动远程同步探测；后台异步健康刷新；关闭时释放线程池。
- 合规：上传用户内容到第三方云需业务方完成用户授权、隐私政策与跨境评估；本库提供脱敏与最小化能力，不替代法务合规。

## 9. 验收标准示例

- AC-01：引入 starter 与 `content-safe-provider-aliyun` 后，配置 AK/SK 与 `nickname` 映射，调用 `contentSafeTemplate.text("nickname", x)` 返回统一 `AuditResult`。
- AC-02：未配置 `nickname` 到当前 provider 的映射时，启动或首次调用给出明确错误，不静默 PASS。
- AC-03：配置 `features.video=true` 但无对应 operation，启动失败并提示未实现。
- AC-04：图片回调重复到达两次，仅首次变更任务状态，第二次幂等返回成功。
- AC-05：阿里超时、腾讯限流时，按路由策略切换到备用供应商；全部失败返回 ERROR 并触发审计事件。
- AC-06：新增 `resume_title` 只需 yml，不改 Java 代码。
- AC-07：自定义 provider jar 放入 classpath 后，通过 `content.safe.providers.my.enabled=true` 生效。

## 10. 范围外说明

音视频/文件/直播/AIGC 统一护栏/人工工作流为后续版本。当前抽象必须预留，但 README 与 Feature Matrix 要明确未承诺。
