# Content Safe Starter 设计文档

- 文档版本：v0.1-draft
- 日期：2026-11-05
- 状态：待评审

## 1. 设计原则

1. 稳定内核：业务只依赖 `api`；云 SDK 不进入 api/core。
2. 能力声明：路由前用 `AuditCapabilities` 过滤，不调用后报错。
3. 配置映射：业务场景与云场景解耦，yml 是主配置。
4. 扩展开放：供应商、场景、归一化、路由、监听、凭据、任务存储均可扩展。
5. 默认安全：fail-fast、脱敏、回调验签、最小化留存、EOL 告警。

## 2. 总体架构

```text
业务代码
  |  ContentSafeTemplate
  v
core: validate -> normalize -> route -> capabilities -> audit -> arbitrate -> event/audit/metrics
  |                         |                 |
  v                         v                 v
spi: AuditOperation     AuditRouter     AuditArbitrationPolicy
  |                         |
  v                         v
provider-* (aliyun/tencent/baidu/volcengine/shumei)
  |
  v
cloud SDK (optional)
```

Spring Boot Starter 仅负责：配置绑定、条件装配、Bean 排序、健康暴露、指标绑定、FailureAnalyzer。

## 3. 模块划分

| 模块 | 职责 | 对外承诺 |
|---|---|---|
| `content-safe-api` | DTO/枚举/异常/门面 | 稳定 |
| `content-safe-spi` | 扩展接口 | 稳定 |
| `content-safe-model` | plan/capabilities/scene/event/health | 尽量稳定 |
| `content-safe-core` | 路由、归一化、熔断、审计、输入治理 | 少直接依赖 |
| `content-safe-provider-*` | 云适配 | 随云 SDK 演进 |
| `content-safe-spring-boot-starter` | 自动装配 | 内部细节不承诺 |
| `content-safe-bom` | 版本管理 | 稳定 |
| `content-safe-samples-*` | 示例 | 参考实现 |

## 4. 核心模型

### 4.1 请求与内容

```java
public record AuditRequest(
    String scene,
    ContentType type,
    ContentRef content,
    String dataId,
    AuditContext context,
    AuditOptions options,
    Map<String, Object> passThrough
) {}
```

```java
public record ContentRef(ContentRefType type, String value, byte[] bytes, Map<String, Object> meta) {
    public static ContentRef text(String text);
    public static ContentRef url(String url);
    public static ContentRef base64(String base64);
    public static ContentRef bytes(byte[] bytes);
    public static ContentRef objectKey(String objectKey);
}
```

### 4.2 结果与命中

```java
public record AuditResult(
    boolean ok,
    RiskSuggestion suggestion,
    boolean finalResult,
    AuditSource source,
    List<RiskHit> hits,
    String requestId,
    String dataId,
    String provider,
    String product,
    String service,
    Duration cost,
    Map<String, Object> raw
) {}
```

```java
public record RiskHit(
    String labelPath,
    List<String> labels,
    Double confidence,
    ConfidenceScale confidenceScale,
    HitSource source,
    String evidence,
    String coordinates,
    Map<String, Object> raw
) {}
```

### 4.3 场景与供应商计划

```java
public record SceneDefinition(
    String code,
    ContentType type,
    String description,
    Severity defaultSeverity,
    Map<String, Object> ext
) {}
```

```java
public record ProviderPlan(
    String provider,
    String product,
    String apiVersion,
    String operation,
    String service,
    AuditMode mode,
    OperationLimits limits,
    ProviderLifecycle lifecycle
) {}
```

```java
public record AuditCapabilities(
    Set<ContentType> contentTypes,
    boolean sync,
    boolean asyncQuery,
    boolean asyncCallback,
    boolean batch,
    boolean url,
    boolean base64,
    boolean bytes,
    boolean objectKey,
    boolean ocr,
    boolean aigcDetect,
    boolean faceDetect,
    boolean customList,
    boolean manualReview,
    boolean maskResult,
    int maxBatchSize,
    DataSize maxUrlSize,
    DataSize maxBase64Size
) {
    public boolean supports(AuditRequest request);
}
```

## 5. 关键流程

### 5.1 同步文本/图片审核

1. `ContentSafeTemplate.audit(request)`
2. scene 校验：存在、type 匹配、severity 默认填充
3. `ContentRefNormalizer` 输入治理：大小、URL、SSRF、MIME、GIF
4. `AuditRouter.resolve` 候选 operations
5. capability filter：type、contentRef、batch、OCR/AIGC
6. 凭据解析：`CredentialsProvider`
7. provider 调用 + normalizer 归一化
8. 多结果 `AuditArbitrationPolicy` 聚合（若开启）
9. 事件：audit/hit/review/degraded
10. metrics + audit log（脱敏）

### 5.2 图片异步与回调

- `submitAsync`：生成 taskId，保存 providerTaskId/dataId/scene，返回 taskId。
- 回调：`CallbackIngressVerifier` 验签 -> schema 校验 -> 幂等 -> 更新任务 -> 发事件。
- `query`：优先任务存储，必要时回源 provider。
- 重复回调/晚到回调：以终态与版本号幂等处理。
- 验签失败/格式错误：进入 `AuditDeadLetterHandler`。

### 5.3 健康与熔断

- 启动：仅校验配置完整性，不远程同步探测。
- 运行：后台 `ProviderHealthProbe` 刷新快照。
- 调用：operation 级 circuit breaker + rate limiter；半开试探恢复。
- EOL：`ProviderLifecycle` 启动 warning/failure analyzer。

## 6. 配置设计

```yaml
content.safe:
  schema-version: 1
  scenes:
    nickname:
      type: text
      default-severity: low
    avatar:
      type: image
      default-severity: high
    resume_title:
      type: text
      default-severity: medium

  providers:
    aliyun:
      enabled: true
      region-id: cn-hangzhou        # endpoint 缺省推导 green-cip.<regionId>.aliyuncs.com
      ak: xxx                       # 缺省回退环境变量 CONTENT_SAFE_ALIYUN_AK
      sk: xxx                       # 缺省回退环境变量 CONTENT_SAFE_ALIYUN_SK
      # security-token: 可选（STS），缺省回退 CONTENT_SAFE_ALIYUN_STSTOKEN
      scenes:                       # 业务场景 code → 云厂商 serviceCode（只进 provider）
        nickname: nickname_detection
        avatar: baselineCheck
        resume_title: resume_title_detection
      extra:                        # 可选：HTTP 超时（毫秒）
        connectTimeoutMs: "3000"
        readTimeoutMs: "5000"
      # --- 以下为规划形态，T-013 尚未落地 ---
      credentials:
        type: static                # 规划：static / sts / ram-role / secret-manager
        access-key-id: xxx
        access-key-secret: xxx
      features:
        text: true
        image: true
        audio: false
        video: false
        file: false
        aigc-guard: false
        manual-review: false
      plans:
        text-sync:
          product: green-cip
          api-version: "2022-03-02"
          operation: TextModeration
          mode: SYNC
        image-sync:
          product: green-cip
          api-version: "2022-03-02"
          operation: ImageModeration
          mode: SYNC
        image-async:                # 待 T-019
          product: green-cip
          api-version: "2022-03-02"
          operation: ImageAsyncScan
          mode: ASYNC_CALLBACK
          enabled: false
    tencent:                        # T-014 已落地（TMS TextModeration / IMS ImageModeration，2020-12-29）
      enabled: false
      secret-id: xxx                # 缺省回退环境变量 CONTENT_SAFE_TENCENT_AK
      secret-key: xxx               # 缺省回退 CONTENT_SAFE_TENCENT_SK
      # session-token: 可选（临时凭证），缺省回退 CONTENT_SAFE_TENCENT_STSTOKEN
      region: ap-guangzhou          # endpoint 推导 tms/ims.<region>.tencentcloudapi.com
      scenes:                       # 业务场景 code → BizType（只进 provider）
        comment: your-comment-biztype
        avatar: your-avatar-biztype
      aigc-scenes:                  # AIGC 识别（Type=IMAGE_AIGC，仅 IMAGE 场景生效）
        ai-art: your-aigc-biztype
      extra:                        # 可选：GIF 截帧与 SDK 读超时
        gifInterval: "2"
        gifMaxFrames: "5"
        readTimeoutSeconds: "10"
      # --- 以下为规划形态，T-014 尚未落地 ---
      credentials:
        type: static                # 规划：static / sts / secret-manager
      plans:
        text-sync:
          product: tms
          api-version: "2020-12-29"
          operation: TextModeration
          mode: SYNC
        image-sync:
          product: ims
          api-version: "2020-12-29"
          operation: ImageModeration
          mode: SYNC
        image-async:                # 待 T-019（CreateImageModerationAsyncTask）
          product: ims
          api-version: "2020-12-29"
          operation: CreateImageModerationAsyncTask
          mode: ASYNC_QUERY
          enabled: false

  route:
    default-policy: PRIORITY
    rules:
      avatar:
        chain: [tencent-image, aliyun-image]
    fallback:
      on: [TIMEOUT, RATE_LIMIT, PROVIDER_DOWN, ERROR]
      action: NEXT_PROVIDER
    arbitrate:
      enabled: false
      policy: FIRST_NON_PASS
```

配置类：`ContentSafeProperties`，使用 `@Validated` + configuration processor；启动 fail-fast。

## 7. 扩展设计

### 7.1 Java SPI

```text
META-INF/services/com.xxx.content.safe.spi.AuditOperationProvider
META-INF/services/com.xxx.content.safe.spi.SceneDefinitionProvider
META-INF/services/com.xxx.content.safe.spi.AuditResultNormalizer
...
```

### 7.2 Spring Bean 扩展

starter 中所有默认 Bean 均 `@ConditionalOnMissingBean`，支持用户覆盖：

- `AuditRouter`
- `AuditArbitrationPolicy`
- `AuditTaskStore`
- `CredentialsProvider`
- `ContentRefNormalizer`
- `EvidenceRedactor`
- `AuditListener`

### 7.3 自定义 provider 最小步骤

1. 依赖 `content-safe-api/spi/model/core`
2. 实现 `AuditOperationProvider`
3. 实现 `AuditOperation` + `AuditResultNormalizer`
4. 提供 `AutoConfiguration` 与 `AutoConfiguration.imports`
5. yml 配置 `content.safe.providers.custom.enabled=true`

## 8. 供应商差异与适配要点

| 供应商 | v1 计划 | 差异点 |
|---|---|---|
| 阿里云 | `text-sync` `image-sync` 可选 `image-async` | 图片 URL-only、公网可达、大小/像素限制；同步批量 100；多 scenes 计费；2.0 service 化；DataId/Context 语义 |
| 腾讯云 | `tms-text` `ims-image` | `BizType`/`Type` 区分标准与 AIGC；IMS Base64/URL；GIF Interval/MaxFrames；User/Device；Ocr/Object/Lib 结果 |
| 百度智能云 | `text` `image` | access_token 生命周期；策略控制台/自定义维度/松紧度；图像细分能力 |
| 火山引擎 | `risk-text` `risk-image` `veimagex-image` | 产品线分裂：方舟护栏/风控/veImageX；appid+biztype；BatchImageAudit 限制与回调 |
| 数美 | `text-sync` `image-sync` | tokenId 上下文；riskLevel/riskLabel1-3；resultType/finalResult/riskSource；probability 0~1 |
| 华为云 | 不默认内置 | 生命周期风险；仅保留 legacy/EOL 适配示例与迁移提示 |

所有差异必须收敛在 `provider-*` 与 `ProviderErrorCatalog`，不得进入 `api/core` 分支。

## 9. 安全与合规设计

- URL 治理：allow/deny、禁内网与 metadata、签名 URL 过期检查、下载限制。
- 数据最小化：默认不保存原始文本/图片；证据截断与脱敏；`raw` 默认不序列化。
- 密钥：CredentialsProvider 链；支持 STS/CAM/KMS/secret-ref；日志脱敏。
- 回调：验签、时间窗、nonce、幂等、死信。
- 多租户：scene prefix 与凭据按 tenant 解析，成本标签 passThrough。
- 文档责任：用户授权、隐私政策、跨境与留存由使用方负责；库提供能力而非合规结论。

## 10. 可观测与错误处理

- Health：`ContentSafeHealthIndicator` 聚合 operation 状态，health() 不重远程探测。
- Metrics：provider/scene/suggestion/hit/latency/fallback/callback/cost estimate。
- Events：`AuditEvent`、`RiskHitEvent`、`ReviewEvent`、`ProviderDegradedEvent`、`CallbackDeadLetterEvent`。
- 错误码：`SCENE_NOT_CONFIGURED`、`UNSUPPORTED_CONTENT`、`PROVIDER_NOT_AVAILABLE`、`PROVIDER_CALL_ERROR`、`CALLBACK_VERIFY_FAILED`、`ALL_PROVIDER_FAILED`、`UNSUPPORTED_FEATURE`。
- FailureAnalyzer：配置缺失、EOL provider、feature 未实现、scene 未映射。

## 11. 兼容与版本策略

- SemVer；api/spi 破坏式变更需 major。
- deprecated API 至少一个 minor 标记后删除。
- `internal` 包不承诺兼容。
- 配置 schema-version 破坏式变更需提供 migration note。
- BOM 约束 provider 与 core 版本组合。

## 12. 测试策略

- 单元：normalize、capability、route、arbitrate、error catalog。
- Starter：`ApplicationContextRunner` 验证条件装配、缺失配置 fail-fast、未引 SDK 不炸。
- 契约：各 provider pass/review/reject/限流/token 失效/回调重复样本。
- 故障注入：超时、5xx、429、验签失败、任务 store 丢失。
- 性能：同步 p99、批量分片、异步吞吐、回调延迟、raw 内存上限。

## 13. 发布与分发

- 正式开源：Maven Central（GPG、sources/javadoc、SBOM、license/NOTICE）。
- CI 预览：GitHub Packages。
- 国内企业私有/加速：阿里云云效 Packages 或 Nexus。
- 不将重云 SDK 打入 starter；provider artifact optional。
