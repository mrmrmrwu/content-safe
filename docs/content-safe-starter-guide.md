# content-safe 接入指南

多供应商内容安全审核 Starter 的使用说明。覆盖：快速开始、场景、供应商接入、治理配置、可观测性。

- 模块总览与架构：见根 [README](../README.md)
- 完整配置形态与设计约束：[content-safe-starter-design.md](content-safe-starter-design.md)
- 错误码：[error-code-table.md](error-code-table.md)
- 供应商能力矩阵：[provider-feature-matrix.md](provider-feature-matrix.md)
- 可运行样例：[../samples](../samples)

## 1. 快速开始

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
        aigc-scenes:                  # 可选：AIGC 识别（Type=IMAGE_AIGC，仅 IMAGE 场景）
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
- **熔断**：失败率超阈值 OPEN → 冷却 → 半开探测；触发发布 `ProviderDegradedEvent`。
- **限流**：本地令牌桶（QPS）+ 信号量（并发），拒绝即 failover 并发布 `ProviderThrottledEvent`——不静默降级。
- **异步/回调**：`submitAsync`/`query`/`handleCallback`；回调验签 + 幂等（重复回调不产生重复事件），验签失败进死信（`CallbackDeadLetterEvent`）。装配 `CallbackVerifier` Bean 即开启回调分发器。端到端演示见 [../samples/content-safe-samples-async-callback](../samples/content-safe-samples-async-callback)。
- **异步任务存储**：默认 `InMemoryAuditTaskStore`（容量/过期可配），生产环境建议自行实现 `AuditTaskStore`（Redis/DB）并声明为 Bean。

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

**健康检查**：`ContentSafeHealthIndicator`（引入 actuator 后自动装配）——UP/OPEN 熔断器明细、限流器快照（`rateLimiters` detail）；`health()` 不做远程探测。

**事件**：实现 `AuditListener` Bean 即可订阅全部事件（`AuditEvent`、`RiskHitEvent`、`ReviewEvent`、`ProviderDegradedEvent`、`ProviderThrottledEvent`、`CallbackDeadLetterEvent`），订阅异常不影响主流程。多个 listener 聚合派发，Spring 容器中的 Bean 会被自动收集。

## 6. 扩展

- **自定义 provider**：实现 `AuditOperationProvider`（一 provider 一类，可出多个 `AuditOperation`）+ 提供 `@AutoConfiguration` 与 `META-INF/spring/...AutoConfiguration.imports`，声明能力（`AuditCapabilities`）与限额（`OperationLimits`）。最小演示见 [../samples/content-safe-samples-basic](../samples/content-safe-samples-basic)。
- **默认 Bean 全部 `@ConditionalOnMissingBean`**：`AuditRouter`、`AuditArbitrationPolicy`、`AuditTaskStore`、`CredentialsProvider`、`ContentRefNormalizer`、`EvidenceRedactor`、`AuditListener`、`RateLimiterRegistry`、`CircuitBreakerRegistry` 均可覆盖。
- **Java SPI**：`META-INF/services` 注册 `AuditOperationProvider`/`SceneDefinitionProvider`/`AuditResultNormalizer` 等扩展点（见设计文档 §7.1）。

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
