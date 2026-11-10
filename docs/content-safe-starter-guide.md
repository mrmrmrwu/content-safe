# content-safe 接入指南

多供应商内容安全审核 Starter 的使用说明。覆盖：快速开始、场景、供应商接入、治理配置、可观测性。

- 模块总览与架构：见根 [README](../README.md)
- 错误码：[error-code-table.md](error-code-table.md)
- 供应商能力矩阵：[provider-feature-matrix.md](provider-feature-matrix.md)
- 可运行样例：[../samples](../samples)

## 1. 快速开始

环境要求：JDK 17+、Spring Boot 3.3.x（starter 按 Boot 3 自动装配规范构建，不兼容 Boot 2.x）。

依赖（版本由 `content-safe-bom` 统一管理）：

```xml
<dependency>
    <groupId>io.github.wrqtec</groupId>
    <artifactId>content-safe-spring-boot-starter</artifactId>
</dependency>
<!-- 按需引入 provider（云 SDK 为 optional，仅启用时才需要） -->
<dependency>
    <groupId>io.github.wrqtec</groupId>
    <artifactId>content-safe-provider-aliyun</artifactId>
</dependency>
```

最小配置（阿里云为例）：

```yaml
content:
  safe:
    providers:
      aliyun:
        enabled: true
        region-id: cn-hangzhou
        ak: ${ALIYUN_AK}        # 也可省略，回退环境变量 CONTENT_SAFE_ALIYUN_AK
        sk: ${ALIYUN_SK}
        scenes:
          comment: comment_detection
```

注入 `ContentSafeTemplate` 调用：

```java
AuditResult result = template.text(Scenes.COMMENT, "用户输入的文本");
result.pass();        // ok && suggestion == PASS
result.suggestion();  // PASS / REVIEW / REJECT / ERROR
result.hits();        // 命中的风险明细（证据已脱敏）
```

## 2. 场景（Scene）

业务代码只传 scene code，云厂商场景 ID 只出现在 yml 的 `providers.<name>.scenes` 映射里。

**内置场景**（`Scenes` 常量类，自动注册）：

| code | 类型 | 说明 |
|---|---|---|
| `nickname` | TEXT | 昵称 |
| `comment` | TEXT | 评论 |
| `post` | TEXT | 发帖 |
| `im_message` | TEXT | 私信/IM |
| `avatar` | IMAGE | 头像 |
| `product_image` | IMAGE | 商品图 |

**自定义场景**：`src/main/resources/content-safe/scenes.json`（JSON 数组，元素含 `code`/`type`/`defaultSeverity`），与内置场景合并，重复 code 以自定义为准；也可直接声明 `SceneRegistry` Bean 全量接管。

**多场景**：`template.auditMulti(sceneCodes, request)` 一次请求跨多个场景审核，按仲裁策略（默认 `FIRST_NON_PASS`）汇总。

## 3. 供应商接入

### 3.1 阿里云（content-safe-provider-aliyun）

```yaml
content:
  safe:
    providers:
      aliyun:
        enabled: true
        region-id: cn-hangzhou        # endpoint 缺省推导 green-cip.<regionId>.aliyuncs.com
        ak: xxx                       # 缺省回退 CONTENT_SAFE_ALIYUN_AK
        sk: xxx                       # 缺省回退 CONTENT_SAFE_ALIYUN_SK
        # security-token: 可选（STS），缺省回退 CONTENT_SAFE_ALIYUN_STSTOKEN
        scenes:
          comment: comment_detection  # 业务场景 → 阿里云 serviceCode
          avatar: baselineCheck
        extra:
          connect-timeout-ms: "3000"    # 可选，HTTP 超时（毫秒）
          read-timeout-ms: "5000"
```

能力：文本（URL/base64 不支持，仅原文 ≤600 字）、图片（仅公网 URL，≤20MB，总像素 ≤2.5 亿）。
未映射到 `scenes` 的场景不会注册 operation，路由自动跳过——不会"先调了再说"。

### 3.2 腾讯云（content-safe-provider-tencent）

```yaml
content:
  safe:
    providers:
      tencent:
        enabled: true
        secret-id: xxx                # 缺省回退 CONTENT_SAFE_TENCENT_AK
        secret-key: xxx               # 缺省回退 CONTENT_SAFE_TENCENT_SK
        region: ap-guangzhou
        scenes:
          comment: your-biztype       # 业务场景 → 腾讯云 BizType（控制台配置）
        aigc-scenes:                  # 可选：AIGC 识别独立路由（TEXT/IMAGE 均生效，Type=TEXT_AIGC/IMAGE_AIGC）
          ai-art: your-aigc-biztype
        extra:
          gifInterval: "2"            # 可选：GIF 截帧
          gifMaxFrames: "5"
```

能力：文本（≤10000 字符，经 Base64 传输）、图片（URL ≤30MB / base64 ≤10MB）、GIF 截帧、AIGC 识别。

### 3.3 百度智能云（content-safe-provider-baidu）

```yaml
content:
  safe:
    providers:
      baidu:
        enabled: true
        api-key: xxx                  # 缺省回退 CONTENT_SAFE_BAIDU_AK
        secret-key: xxx               # 缺省回退 CONTENT_SAFE_BAIDU_SK
        scenes:
          comment: "101"              # 业务场景 → 策略中心 strategyId；空字符串 = 默认策略
          avatar: ""                  # 不传 strategyId，走账号默认策略
        extra:
          connect-timeout-ms: "3000"  # 可选：HTTP 连接/读取超时
          read-timeout-ms: "10000"
```

无官方 SDK，模块内置 JDK HttpClient 自研客户端；access_token 自动获取与缓存（30 天，遇 110/111 自动重取重试一次）。能力：文本（≤20000 字节）、图片（URL / base64 ≤4MB）。strategyId 在内容审核平台"策略中心"创建。

### 3.4 火山引擎（content-safe-provider-volcengine）

```yaml
content:
  safe:
    providers:
      volcengine:
        enabled: true
        ak: xxx                       # 缺省回退 CONTENT_SAFE_VOLCENGINE_AK
        sk: xxx                       # 缺省回退 CONTENT_SAFE_VOLCENGINE_SK
        scenes:
          nickname: "svc-nickname"    # 业务场景 → 内容安全 Service 标识（控制台申请）
          avatar: "svc-avatar"
        extra:
          app-id: "6801"              # 必填：内容安全控制台应用 ID（整型）
          parameters-text-key: "text"   # 必填：文本在 parameters 内层 JSON 的键名
          parameters-image-key: "image" # 必填：图片在 parameters 内层 JSON 的键名
```

基于官方 SDK `com.volcengine:volc-sdk-java`（optional）。能力：文本检测（TextRisk）、
图片检测（ImageContentRiskV2，URL / base64 / bytes）。decision 归一：pass→PASS、
suspect→REVIEW、reject→REJECT，未知值保守回退 REVIEW；命中词与 OCR 文本按安全约束
只进 raw 或丢弃。parameters 内层键名由所配 Service 的控制台文档定义（见
[provider-feature-matrix.md](provider-feature-matrix.md) volcengine 节），缺失即启动 fail-fast。

### 3.5 数美（content-safe-provider-shumei）

```yaml
content:
  safe:
    providers:
      shumei:
        enabled: true
        access-key: xxx               # 缺省回退 CONTENT_SAFE_SHUMEI_AK（数美仅单凭证 accessKey）
        app-id: "default"             # 必填：数美控制台 appid
        region: sh                    # 可选：默认 sh（文本 bj/sh/fjny/xjp，图片 bj/sh/gg/xjp）
        scenes:
          comment: "text"             # 业务场景 → 数美 eventId（与控制台事件约定一致）
          avatar: "image"
```

无官方现代 SDK，模块内置 JDK HttpClient 自研客户端。能力：文本（text/v4，≤1 万字）、
图片（image/v4，URL / base64 / bytes）。riskLevel 归一：PASS/REVIEW/REJECT，未知值
保守回退 REVIEW；命中词（matchedLists）只进 `hit.raw`，打码文本/联系方式/人脸等用户
内容一律不透传。检测类型与动图截帧参数可经 `extra.text-type` / `extra.image-type` /
场景 ext 配置（见 [provider-feature-matrix.md](provider-feature-matrix.md) shumei 节）。

### 3.6 凭证解析链

每个 provider 的凭证按以下顺序解析，全部缺失则启动 fail-fast（`CONFIG_INVALID`）：

1. 用户自定义 `CredentialsProvider` Bean（`provider()` 匹配者优先）；
2. yml 直接配置（`ak`/`sk`、`secret-id`/`secret-key`、`access-key`）；
3. 环境变量 `CONTENT_SAFE_<PROVIDER>_AK` / `_SK` / `_STSTOKEN`。

## 4. 治理配置

```yaml
content:
  safe:
    defaults:
      circuit-breaker:              # 按 planKey（provider:operation）隔离
        window-size: 20
        failure-rate-threshold: 0.5
        min-calls: 10
        open-duration: 30s
        half-open-max-calls: 3
      rate-limiter:
        enabled: true               # false 时全部不限流
        # qps: 100                  # 全局默认 QPS；缺省用 plan.limits() 声明值
        # max-concurrency: 4        # 全局默认并发上限（占用至调用结束，finally 归还）
        overrides:                  # 按 planKey 覆盖
          "[aliyun:TextModeration]":
            qps: 50
            max-concurrency: 2
```

- **路由**：`PriorityAuditRouter` 按 `order()`（数值小优先）选主，主失败自动 failover 下一候选。
- **仲裁**：`content.safe.defaults.arbitration` 选多供应商结果仲裁策略，默认 `OFF`（主备 failover，成本最优）；
  配置为 `FIRST_NON_PASS` 或自定义 `AuditArbitrationPolicy` Bean 名称后，同步审核改为全量候选并行采集 +
  仲裁合并（成本 = 每个候选各调用一次），结果 `source=ARBITRATION`。场景可用
  `content.safe.scenes.<code>.default-arbitration` 单独覆盖，配 `OFF`/`NONE` 表示该场景不仲裁。
- **熔断**：失败率超阈值 OPEN → 冷却 → 半开探测；触发发布 `ProviderDegradedEvent`。
- **限流**：本地令牌桶（QPS）+ 信号量（并发），拒绝即 failover 并发布 `ProviderThrottledEvent`——不静默降级。
- **异步/回调**：`submitAsync`/`query`/`handleCallback`；回调验签 + 幂等（重复回调不产生重复事件），验签失败进死信（`CallbackDeadLetterEvent`）。装配 `CallbackVerifier` Bean 即开启回调分发器。端到端演示见 [../samples/content-safe-samples-async-callback](../samples/content-safe-samples-async-callback)。
- **异步任务存储**：默认 `InMemoryAuditTaskStore`（容量/过期可配），生产环境建议自行实现 `AuditTaskStore`（Redis/DB）并声明为 Bean。

```yaml
content:
  safe:
    defaults:
      async:
        poll-interval: 2s     # submitAndWait() 默认轮询间隔
        poll-timeout: 5m      # submitAndWait() 默认等待超时
        max-tasks: 10000      # 仅默认 InMemoryAuditTaskStore 生效：容量上限
        expire-after: 1h      # 任务过期时间（超限逐出最旧，读取时惰性过期）
```

## 5. 可观测性

**指标**（`ContentSafeMetrics`，MeterRegistry）：

| 指标 | 标签 | 说明 |
|---|---|---|
| `content.safe.audit` | scene, suggestion | 审核结果计数 |
| `content.safe.risk.hit` | scene | 风险命中计数 |
| `content.safe.review` | scene | 人审计数 |
| `content.safe.provider.degraded` | provider | 供应商降级/熔断 |
| `content.safe.provider.throttled` | provider, operation | 本地限流拒绝 |
| `content.safe.callback.deadletter` | provider | 回调死信 |

**健康检查**：`ContentSafeHealthIndicator`（引入 actuator 后自动装配）——UP/OPEN 熔断器明细、限流器快照（`rateLimiters` detail）、场景到供应商映射表（`sceneMappings` detail，排查"场景无供应商映射"首查此处）；`health()` 不做远程探测。

**事件**：实现 `AuditListener` Bean 即可订阅全部事件（`AuditEvent`、`RiskHitEvent`、`ReviewEvent`、`ProviderDegradedEvent`、`ProviderThrottledEvent`、`CallbackDeadLetterEvent`），订阅异常不影响主流程。多个 listener 聚合派发，Spring 容器中的 Bean 会被自动收集。

## 6. 扩展

- **自定义 provider**：实现 `AuditOperationProvider`（一 provider 一类，可出多个 `AuditOperation`）+ 提供 `@AutoConfiguration` 与 `META-INF/spring/...AutoConfiguration.imports`，声明能力（`AuditCapabilities`）与限额（`OperationLimits`）。最小演示见 [../samples/content-safe-samples-basic](../samples/content-safe-samples-basic)。
- **默认 Bean 全部 `@ConditionalOnMissingBean`**：`AuditRouter`、`AuditArbitrationPolicy`、`AuditTaskStore`、`CredentialsProvider`、`ContentRefNormalizer`、`EvidenceRedactor`、`AuditListener`、`RateLimiterRegistry`、`CircuitBreakerRegistry` 均可覆盖。
- **Java SPI**：`META-INF/services` 注册 `AuditOperationProvider`/`SceneDefinitionProvider`/`AuditResultNormalizer` 等扩展点。

## 7. 启动失败诊断（FailureAnalyzer）

`content.safe.*` 配置非法时，starter 启动即 fail-fast（错误码见 [error-code-table.md](error-code-table.md)），并由内置的
`ContentSafeFailureAnalyzer` 把异常翻译为 Spring Boot 标准的 `APPLICATION FAILED TO START` 报告，格式为
"错误码 + 出错 Bean + 原因 + 修复动作"，例如：

```text
APPLICATION FAILED TO START
***************************
Description:
    content-safe startup failed with code CONFIG_INVALID (while creating bean 'arbitrationPolicyRegistry')
    unknown arbitration policy: NOPE (available: [FIRST_NON_PASS])
Action:
    Set content.safe.defaults.arbitration (or scenes.<code>.defaultArbitration) to a registered policy
    such as FIRST_NON_PASS, or register an AuditArbitrationPolicy bean whose name() matches the configured value.
```

覆盖的诊断场景：配置缺失（凭据/仲裁策略/schema 版本）、场景未注册（`SCENE_NOT_CONFIGURED`）、
特性未实现（`UNSUPPORTED_FEATURE`）、内容形态不支持（`UNSUPPORTED_CONTENT`）、provider 不可用等。
报告只含错误码与配置键名，绝不输出 accessKey/secret/用户内容原文。

## 8. AIGC 检测与人审闭环

### 8.1 AIGC 检测开关（请求期）

单次审核要求同时识别 AI 生成内容（AIGC 灌水/AI 生成评论等）：

```java
AuditResult result = contentSafeTemplate.audit(AuditRequest.builder()
        .scene("comment")
        .type(ContentType.TEXT)
        .content(ContentRef.text(text))
        .options(AuditOptions.defaults().withAigcDetect(true))
        .build());
```

语义为**单向门控**（详见 [provider-feature-matrix.md](provider-feature-matrix.md) 平台级能力节）：

- 开关打开而当前供应商操作未声明 `aigcDetect` 能力位时，该操作被路由过滤——不会静默降级为普通审核；
- 开关关闭/缺省不影响任何路由，配置期 AIGC 独立场景（如腾讯 `aigc-scenes`、阿里 `aigcDetector` service 码）不受开关影响；
- 腾讯：开关在请求期把 Type 切换为 `TEXT_AIGC`/`IMAGE_AIGC`；阿里：AIGC 是独立 Service 码（`aigcDetector`），走场景配置路由，操作自动声明能力位；
- baidu/shumei/volcengine 未声明该能力位（未验证不编造），打开开关会直接路由失败。

### 8.2 人审闭环（REVIEW 终态归一）

机审 `REVIEW` 不再是死端，三步走完闭环：

**1）订阅人审事件**：实现 `AuditListener`，消费 `ReviewEvent`（eventType=`REVIEW`），转人审队列。
同步/查询/回调三条路径都会发布（回调由 `CallbackDispatcher` 在任务终态迁移成功后发布）。

**2）人审完成后回写终态**：

```java
AuditResult manual = contentSafeTemplate.resolveReview(ReviewResolution.of(
        "comment",              // scene
        "dataId-123",           // 机审结果 dataId
        taskId,                 // 异步任务 ID，同步审核产生的 REVIEW 传 null
        ReviewDecision.REJECT,  // APPROVE→终态 PASS / REJECT→终态 REJECT
        "alice",                // 复核人，可空
        "确认违规"));            // 备注，可空
```

归一后的终态结果：`source=AuditSource.MANUAL`、`finalResult=true`、suggestion 按决议取
`PASS`/`REJECT`；`taskId` 存在时同步迁移异步任务终态（幂等，已终态抛 `InvalidRequestException`），
并发布 `AuditEvent`。

**3）（可选）注入回写出口**：实现 `ReviewResultSink` Bean，starter 按 0..1 装配；
执行顺序为**先回写后迁移**——sink 失败时任务保持 PENDING，业务可整体重试；
未注入 sink 时仅做终态归一与事件发布。

## 9. 异步审核与回调

供应商对异步的支持面与验签方式不同（详见 [provider-feature-matrix.md](provider-feature-matrix.md)），
本节给出统一用法；能力由 `AuditCapabilities` 声明，不支持异步的操作路由直接失败，不静默降级。

### 9.1 提交与轮询（阿里 / 数美图片）

```java
// 提交：返回内部 taskId，任务进入 AuditTaskStore（PENDING）
String taskId = contentSafeTemplate.submitAsync(
        AuditRequest.builder()
                .scene("avatar")
                .type(ContentType.IMAGE)
                .content(ContentRef.url(imageUrl))
                .dataId("img-1001")        // 业务侧数据标识，回调/查询关联键
                .build());

// 轮询：中间态返回 finalResult=false 的结果且任务保持 PENDING；终态自动迁移 SUCCEEDED 并发布事件
AuditResult result = contentSafeTemplate.query(taskId);

// 或一步到位：submitAndWait 按配置的 poll-interval/poll-timeout 自动轮询（超时抛 ProviderCallException，任务仍在可继续 query）
AuditResult waited = contentSafeTemplate.submitAndWait(
        AuditRequest.builder()
                .scene("avatar")
                .type(ContentType.IMAGE)
                .content(ContentRef.url(imageUrl))
                .build());
```

约束：阿里异步仅支持 URL 内容（异步接口不支持 base64）；数美异步提交单图 ≤30MB；
阿里结果保留 3 天（官方建议 30 秒轮询间隔）。
`AuditRequest.text()/image()` 工厂默认自动生成 32 位 `dataId`（对账场景请用 builder 显式传入）。

**提交幂等**：显式传入的 `dataId` 同时是提交幂等键——同一场景下存在同 `dataId` 的
PENDING 任务时，重复 `submitAsync` 直接返回既有 `taskId`，不再调用供应商（避免超时重试
在云侧产生重复计费任务）。已终态的任务不参与复用，重新提交会创建新任务。语义为尽力而为：
查重与落库之间无事务边界，极端并发下仍可能重复提交；需要严格幂等时应在业务侧加分布式锁。
默认内存存储已支持该查重；自定义 `AuditTaskStore`（DB/Redis）建议覆盖
`findActiveByDataId`，未覆盖时幂等去重自动失效（不影响正确性）。

### 9.2 回调接入（三家通用）

**方式一：内置端点（推荐）**。引入 Servlet Web 环境后开启即可，无需自写 Controller：

```yaml
content:
  safe:
    callback:
      expose: true                # 默认 false
      path: /content-safe/callback
      rate-limit: 100             # 每 provider 回调 QPS 上限（令牌桶），≤0 不限；默认 100
```

开启后 starter 提供 `POST {path}/{provider}` 端点：验签失败回 401、拒绝受理回 400、成功回 200 `ok`。
返回非 200 时供应商会按各自策略重试（阿里重推 / 腾讯不明确 / 数美 1~8 秒递增重试 8 次）。
`rate-limit` 是每 provider 的回调处理速率兜底：截获重放、验签配置泄漏、供应商故障重推风暴等
场景下限制解析成本，被限流的请求按"拒绝受理"应答且<b>不发死信事件</b>（洪泛下事件本身
会被放大成风暴，可观测性靠 WARN 日志）。

**方式二：自写 Controller**（非 Servlet Web 环境、或需自定义路径/鉴权时）：

```java
@PostMapping("/content-safe/callback/{provider}")
public ResponseEntity<String> onCallback(@PathVariable String provider,
                                         @RequestHeader Map<String, String> headers,
                                         @RequestBody String rawBody) {
    contentSafeTemplate.handleCallback(new CallbackRequest(provider, headers, rawBody));
    return ResponseEntity.ok("ok");   // 返回 200 即确认；非 200 供应商会按策略重试
}
```

`handleCallback` 内部完成：验签（`CallbackVerifier`，失败抛 `CallbackVerifyException` 并发布
`CallbackDeadLetterEvent`）→ 归一 `AuditResult`（证据先经 `EvidenceRedactor` 脱敏，落库/事件/返回值均为脱敏副本）→
按 providerTaskId（空白时按 dataId 反查）迁移任务终态 → 发布 `AuditEvent`（REVIEW 结果同时发布 `ReviewEvent`）。
重复回调不产生重复事件（幂等）。
任务引用查不到统一任务（任务被逐出、应用重启或外部直接提交）时：回调仍回 200 受理（供应商重推也无法恢复关联），
但会发布 `CallbackDeadLetterEvent`（原因 `audit task not found ...`）保证可观测，结果不会无声丢失。

### 9.3 各供应商回调配置

| 供应商 | 异步模式 | 配置 | 验签 |
|---|---|---|---|
| 阿里云 | 轮询 + 回调 | `content.safe.providers.aliyun.extra.callback-url` / `callback-seed` / `callback-uid`（uid 须主账号 UID） | SHA256(uid+seed+Content)，恒时比较 |
| 腾讯云 | 仅回调推送（无查询接口） | `content.safe.providers.tencent.extra.callback-url`；`dataId` 必填（回调关联键） | 官方无签名，仅结构校验 |
| 数美 | 轮询 + 回调 | 回调地址在数美控制台配置（不经过本库） | 官方无签名，仅结构校验 |

> 安全提示：腾讯/数美回调官方均无签名机制，防伪须依赖回调接收侧的 IP 白名单等业务手段；
> 回调 URL 为公网地址，请勿在回包中回显任何内容原文。
>
> **防重放**：防重放的第一责任在 `CallbackVerifier` 实现——契约要求校验时间窗/nonce；
> 协议不含时间要素时实现须如实声明"不防重放"。本项目三家内置 verifier 均受官方协议所限
> 不防重放（阿里 Checksum 与腾讯/数美结构校验都不含时间戳），部署方应在网络层补偿：
> 回调地址仅内网可达、来源 IP 白名单、网关限流，并保持 `rate-limit` 开启作为进程内兜底。
> 重复投递本身无害（终态迁移幂等，不产生重复事件），重放的代价是解析成本，由限流兜住。

## 10. 业务侧异常处理

对外只抛 `ContentSafeException` 子类，每个异常携带稳定 `code`（完整总表见
[错误码表](error-code-table.md)）。业务侧按 code 分流，不要按异常类型逐一 catch：

```java
try {
    AuditResult result = contentSafeTemplate.text(Scenes.COMMENT, userText);
    if (!result.pass()) {
        // 建议：不中断主业务，按 suggestion（PASS/REVIEW/REJECT）落库或拦截
    }
} catch (ContentSafeException e) {
    switch (e.getCode()) {
        case ErrorCodes.UNSUPPORTED_CONTENT ->
                // 内容形态供应商不支持（如 base64 传给仅支持 URL 的供应商），属请求侧问题，不要重试
                log.warn("unsupported content: {}", e.getMessage());
        case ErrorCodes.PROVIDER_CALL_ERROR, ErrorCodes.ALL_PROVIDER_FAILED ->
                // 供应商侧瞬时失败（retryable=true），可按业务策略重试或降级放行
                log.error("provider unavailable: {}", e.getMessage());
        case ErrorCodes.INVALID_REQUEST ->
                log.warn("bad request: {}", e.getMessage());
        default -> log.error("audit failed, code={}: {}", e.getCode(), e.getMessage());
    }
}
```

要点：

- 主备 failover 已在框架内完成：只有所有候选供应商都失败才抛 `ALL_PROVIDER_FAILED`，
  此时重试大概率仍失败，建议降级放行 + 记录，而非同步重试。
- 请求侧错误（`INVALID_REQUEST` / `UNSUPPORTED_CONTENT` / `SCENE_NOT_CONFIGURED`）
  `retryable=false`，重试无意义，应修调用方代码。
- 异常消息已脱敏（不含密钥/签名 URL/内容原文），可直接落日志。
- 启动期配置错误（`CONFIG_INVALID`）由 FailureAnalyzer 翻译为可读报告（见 §7），无需业务处理。

输入治理的职责边界：公共形状校验在 core 编排层路由前统一执行一次——内容非空
（TEXT/BASE64/OBJECT_KEY 非空白、BYTES 非空数组）、URL 仅接受带主机的
http/https 绝对地址（`file:` 等协议直接抛 `INVALID_REQUEST`）；供应商特有的
大小/像素/格式限制由各 provider 自行预检。校验错误消息只描述问题，不回显内容值。
