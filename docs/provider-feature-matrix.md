# 供应商能力矩阵（Provider Feature Matrix）

记录各 provider 模块已实现的能力与限额。新增供应商或新增能力时必须同步本表（CLAUDE.md §6 checklist）。

> 标注口径：✅ 已实现；⏳ 已预留抽象、待后续任务；❌ 供应商不支持。
> 信息来源：阿里云内容安全 2.0（增强版）官方文档（help.aliyun.com，查阅日期 2026-11-06）。

## aliyun（content-safe-provider-aliyun）

SDK：`com.aliyun:green2022032` → 实际坐标 `com.aliyun:green20220302:3.6.6`（optional 依赖）。

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| 文本同步审核（TextModeration） | ✅ | service 由 `scenes.<sceneCode>` 映射 |
| 图片同步审核（ImageModeration） | ✅ | 仅公网 URL（`ContentRefType.URL`） |
| base64 / bytes 内容 | ❌ | 2.0 同步接口仅 URL；能力校验直接拒绝 |
| 批量审核 | ❌ | `maxBatchSize=1` |
| 异步查询（轮询） | ✅ | `ImageAsyncModeration` 提交 + `DescribeImageModerationResult` 按 reqId 轮询（AuditMode.ASYNC_QUERY）；仅 `ContentRefType.URL`（异步接口仅支持 imageUrl/OSS）；响应 code=280 为检测中间态；结果保留 3 天（来源：help.aliyun.com 文档 2638108，查阅日期 2026-11-07） |
| 异步回调 | ✅ | 提交参数携带 `callback`/`seed`（extra `callback-url` / `callback-seed` 配置）；回调表单 Checksum=SHA256(主账号UID+seed+Content)；数据以回调到达为准时无需轮询 |
| 视频审核（VideoModeration → VideoModerationResult，1.2.0） | ✅ | Service 固定 `videoDetection`；仅公网 URL（≤500MB 云侧校验）；业务码 280 检测中/288 排队中→处理中；帧命中/语音命中归一 RiskHit（hit.raw 只放 offset/timestamp 等技术元数据，截帧 URL 与语音转写原文丢弃）；结果保留 24 小时，建议 30s 轮询；视频回调本期未实现（asyncCallback=false）（来源：help.aliyun.com 文档 2505810，查阅日期 2026-11-11） |
| OCR | ⏳ | 2.0 service 能力，未接入 |
| AIGC 检测 | ✅ | 配置期：场景 service 配为 `aigcDetector`/`aigcDetectorFull` 时操作自动声明 aigcDetect 能力位，请求期开关按能力位门控（来源：help.aliyun.com 文档 2642717，查阅日期 2026-11-06） |
| 自定义名单 | ⏳ | 控制台配置，SDK 侧无感 |
| 回调验签（CallbackVerifier） | ✅ | `AliyunCallbackVerifier`：SHA256(uid+seed+Content) 恒时比较；uid 经 extra `callback-uid` 配置（须主账号 UID），seed 缺省回退全局 `content.safe.callback-secret`；仅支持 SHA256（不做 SM3） |
| suggestion 映射 | ✅ | riskLevel 三档：none/low→PASS，medium→REVIEW，high→REJECT；缺失时回退标签判空 |

### 限额（`AliyunLimits`，声明值，以官方文档为准）

| 项 | 文本 | 图片 | 视频（1.2.0） |
| --- | --- | --- | --- |
| 单条大小 | 600 字（实现按 2400 字节保守折算） | 20 MB | ≤500 MB（云侧校验） |
| 像素 | — | 单边 ≤16384、总像素 ≤2.5 亿 | — |
| 默认 QPS | 100 | 50 | 未声明（云侧排队；提交/查询本地超时 10s） |
| URL 要求 | — | 公网可达，3 秒内可下载 | 公网 URL |

### 已知服务码（serviceCode 示例，非穷举）

`nickname_detection`、`comment_detection`、`chat_detection`、`pgc_detection`、`ad_compliance_detection`、`ai_art_detection`、图片 `baselineCheck` 等。业务侧只配场景 code → serviceCode 映射，云厂商标识不进 api/core（铁律 §5.3）。

## tencent（content-safe-provider-tencent）

SDK：`com.tencentcloudapi:tencentcloud-sdk-java:3.2.52`（总包，optional 依赖；文本 TMS + 图片 IMS 均为 API 版本 2020-12-29）。

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| 文本同步审核（TMS TextModeration） | ✅ | BizType 由 `scenes.<sceneCode>` 映射；Content 自动 Base64(UTF-8) 编码 |
| 图片同步审核（IMS ImageModeration） | ✅ | URL（FileUrl）/ base64（FileContent）/ bytes（内部编码）三通道 |
| AIGC 检测 | ✅ | 双通道：`aigcScenes.<sceneCode>` 配置期路由（TEXT/IMAGE 均生效，Type=TEXT_AIGC/IMAGE_AIGC）+ 请求期 `AuditOptions.withAigcDetect(true)` 切换 Type（来源：cloud.tencent.com/product/1124 文档 51860，查阅日期 2026-11-06） |
| 风控字段映射（User/Device） | ✅ | `AuditContext.userId/deviceId/ip` → TMS/IMS User/Device |
| GIF 截帧 | ✅ | extra 配置 `gifInterval` / `gifMaxFrames` |
| 批量审核 | ❌ | `maxBatchSize=1` |
| 异步任务（仅回调推送） | ✅ | IMS `CreateImageModerationAsyncTask`（AuditMode.ASYNC_CALLBACK）；官方无独立任务 ID/查询接口，关联键为业务侧 `dataId`（必填）；回调地址经 extra `callback-url` 配置（CallbackUrl 必填）；限频 20QPS（来源：cloud.tencent.com 文档 1125/87021，查阅日期 2026-11-07） |
| 视频审核（VM CreateVideoModerationTask/DescribeTaskDetail，1.2.0） | ✅ | BizType 同 `scenes.<sceneCode>` 映射；仅 URL 入参（StorageInfo Type=URL）；Status PENDING/RUNNING→处理中、FINISH→终态、ERROR/CANCELLED→ProviderCallException；命中归一：顶层 Labels + 音频/图片分段（分段原文、截图与片段 URL、AudioText 全部丢弃）；可选回调：extra `callback-url` 配置时 asyncCallback=true，回调报文与 DescribeTaskDetail 响应同构（API 版本 2021-09-22；来源：cloud.tencent.com 文档 1265/79255、1265/80018、1265/80019，查阅日期 2026-11-11） |
| OCR | ✅ | IMS 响应 OcrResults 命中归一为标签；**文本原文（用户内容）按安全约束丢弃，不进 evidence/raw/日志** |
| 自定义名单 | ⏳ | 控制台配置，SDK 侧无感 |
| 回调验签（CallbackVerifier） | ✅ | `TencentCallbackVerifier`：官方回调**无签名机制**，仅做结构校验（ImageModeration 同构 JSON 可解析即通过）；防伪须依赖回调接收侧 IP 白名单等业务手段 |
| suggestion 映射 | ✅ | Pass→PASS，Review→REVIEW，Block→REJECT；缺失时回退 Label 判 Normal |
| 用户内容安全 | ✅ | TMS Keywords（敏感词原文）只进 `hit.raw`（raw 按约定不进日志/审计正文） |

### 限额（`TencentLimits`，声明值，以官方文档为准）

| 项 | 文本（TMS） | 图片（IMS） | 视频（VM，1.2.0） |
| --- | --- | --- | --- |
| 单条大小 | 10000 Unicode 字符 | Base64 ≤10 MB；URL 源图 ≤30 MB | 官方未公示确定上限（本次未核实到，以控制台为准） |
| 默认 QPS | 1000 | 100 | 未声明（提交/查询本地超时 10s） |

### BizType 说明

BizType 为腾讯云控制台创建的策略标识，业务侧只配场景 code → BizType 映射，云厂商标识不进 api/core（铁律 §5.3）。信息来源：腾讯云 TMS/IMS 官方文档（cloud.tencent.com，查阅日期 2026-11-06）。

## baidu（content-safe-provider-baidu）

无官方现代 SDK（`com.baidu.aip:java-sdk` 停留在 4.16.18 / ~2022），**自研 HTTP 客户端**（JDK HttpClient + Gson，均为 optional 依赖）。接口为内容审核平台 v2（text_censor/img_censor user_defined）。

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| 文本同步审核 | ✅ | form 参数 `text`，≤20000 字节（UTF-8） |
| 图片同步审核 | ✅ | URL（`imgUrl`）/ base64（`image`）/ bytes（内部转 base64）三通道；base64 后 ≤4MB |
| 策略中心 | ✅ | `scenes.<sceneCode>` = 策略中心 strategyId（数字）；空字符串走账号默认策略 |
| access_token 管理 | ✅ | client_credentials 本地缓存（30 天，提前 5 分钟刷新）；110/111 错误自动失效重取并重试一次 |
| 批量审核 | ❌ | 单次单条 |
| 异步查询 / 回调 | ❌ | v2 文本/图片接口为同步模型（长视频审核走独立 solution 接口，见下行） |
| 长视频审核（video_censor submit/pull，1.2.0） | ✅ | 仅公网 URL（≤3h/≤2G）；extId=业务 dataId 作幂等键（appId+extId 组合唯一，重复提交报 282005，dataId 空白在提交前 fail-fast）；拉取固定 subEvents=1,2,3,4（官方默认 2,3 会漏合规结论）；conclusionType 缺省=处理中、4=审核失败（视频域抛 `ProviderCallException`，任务标记 FAILED，与同步域 `4→ERROR` 不同）；帧/语音命中归一 RiskHit（帧截图 URL、语音转写原文丢弃）；可选回调：extra `notice-url` 配置时 asyncCallback=true，官方默认只推 2,3（违规/疑似）——PASS 任务永远等不到回调，模块自动兜底 sub-events=1,2,3,4（与拉取口径一致），显式 extra `sub-events` 原样透传；回调 POST 业务地址且官方无签名机制（BaiduCallbackVerifier 仅结构校验）（来源：cloud.baidu.com 文档 jksy7j3jv/Pksy7jxr2/8ksy7dc4b，查阅日期 2026-11-11） |
| GIF 动图 | ⏳ | `imgType=1` 参数未透传（按静态图处理） |
| suggestion 映射 | ✅ | conclusionType 1→PASS、2→REJECT、3→REVIEW、4→ERROR（审核失败是业务结论，ok 保持 true） |
| 用户内容安全 | ✅ | 命中词 hits[].words 进 `hit.evidence`（由 core EvidenceRedactor 脱敏）；`raw` 只放 `{log_id}` |

### 限额（`BaiduLimits`，声明值，以官方文档为准）

| 项 | 文本 | 图片 | 视频（1.2.0） |
| --- | --- | --- | --- |
| 单条大小 | 20000 字节（UTF-8） | base64 后 ≤4MB | ≤3 小时且 ≤2GB（官方建议值，云侧校验） |
| 默认 QPS | 200 | 50 | 提交 10（本地保护性缺省，官方未声明）；拉取建议 30s 间隔 |

### strategyId 说明

strategyId 为百度内容审核平台"策略中心"创建的策略 ID（数字字符串）。业务侧只配场景 code → strategyId 映射；值为空字符串表示不传 strategyId、走账号默认策略。错误码 18/19 为限流、110/111 为 token 问题。信息来源：百度智能云内容审核官方文档（cloud.baidu.com/doc/ANTIPORN，查阅日期 2026-11-06）。

## volcengine（content-safe-provider-volcengine）

SDK：`com.volcengine:volc-sdk-java:1.0.290`（总包，optional 依赖）。产品线为内容安全/风控
（host `riskcontrol.volcengineapi.com`，签名 service `BusinessSecurity`，region `cn-north-1`）。
接口契约来源：官方 Java SDK 1.0.290 与官方 Python SDK
`volcengine/content_security/ContentSecurityService.py`（查阅日期 2026-11-06）；
请求 envelope 为 `{AppId, Service, Parameters}`（Parameters 为 JSON 字符串，键名由所配
Service 的控制台文档定义，不进公共抽象）。

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| 文本同步检测（TextRisk，Version 2022-01-26） | ✅ | `scenes.<sceneCode>` = Service 标识；文本原文写入 `extra.parameters-text-key` 指定键 |
| 图片同步检测（ImageContentRiskV2，Version 2021-11-29） | ✅ | URL 原文 / base64 / bytes 三通道写入 `extra.parameters-image-key` 指定键（V1 已被官方标记 deprecated） |
| app-id | ✅ | `extra.app-id`（整型，控制台申请），装配期 fail-fast 校验 |
| decision 映射 | ✅ | pass→PASS、suspect→REVIEW、reject→REJECT（大小写不敏感）；缺失/未知值保守回退 REVIEW；完整枚举以官方文档为准 |
| 文本命中词 | ✅ | contexts.matchedWords（用户内容片段）只进 `hit.raw`，不进 evidence/labels |
| OCR 文本 / 帧签名 URL | ✅ 安全处理 | `data.ocrText` 与 `frames[].url` 为用户内容/签名 URL，一律丢弃不透传 |
| 异步检测 | ✅ | 视频 AsyncVideoRisk 提交 + VideoResult 按 dataId 轮询（Version 2021-11-29，AuditMode.ASYNC_QUERY）；仅 URL 入参；任务关联键=业务 dataId 随 Parameters 提交（键名经 extra `parameters-video-data-id-key` 配置，缺省 dataId）；视频 URL 键经 extra `parameters-video-key` 必配；官方未公开回调接口，asyncCallback 恒 false；帧/音频命中归一 RiskHit（帧签名 URL、帧内文字、语音转写原文丢弃，matchedWords 进 hit.raw）；文本/图片仍为同步接口（来源：官方 Python SDK ContentSecurityService.py，查阅日期 2026-11-11） |
| veImageX 智能审核（CreateImageContentTask） | ⏳ | 异步任务制产品线，待评估 |
| 新版文本（TextSliceRisk，2022-11-07） | ⏳ | 官方标注 TextRisk 的替代品，待评估切换 |
| 批量审核 | ❌ | envelope 单条载荷，`maxBatchSize=1` |
| 回调验签（CallbackVerifier） | ❌ | 图片/视频均未公开回调接口，无验签需求 |
| 用户内容安全 | ✅ | matchedWords/ocrText 均不进 evidence/raw 之外的字段与日志 |

### 限额（`VolcengineLimits`，声明值，以官方文档/控制台为准）

| 项 | 文本 | 图片 | 视频（1.2.0） |
| --- | --- | --- | --- |
| 批量 | 单次单条 | 单次单条 | 单次单条 |
| 超时 | SDK ServiceInfo 默认 5 秒（connect/socket） | 同左 | 同左 |
| QPS / 大小 | 未声明（官方公开文档无确定配额，配额随账号与服务配置变化） | 未声明 | 未声明（中间态按"查询 Data 缺失"保守推断，超时由 core poll-timeout 兜底） |

### 约束说明

SDK 客户端 `ContentSecurityServiceImpl.getInstance()` 为进程级单例，凭证经 setter 注入；
同进程多账号需自行隔离。parameters 内层键名（`parameters-text-key` / `parameters-image-key` /
`parameters-video-key`）由使用方按其 Service 的控制台文档显式配置，缺失即装配期 fail-fast
（经 FailureAnalyzer 输出可读报告）。

## shumei（content-safe-provider-shumei）

无官方维护的现代 Java SDK，**自研 HTTP 客户端**（JDK HttpClient + Gson，均为 optional
依赖）。产品线为数美天净风控同步接口：文本 `POST https://api-text-{region}.fengkongcloud.com/text/v4`、
图片 `POST https://api-img-{region}.fengkongcloud.com/image/v4`。接口契约来源：官方 API 文档
仓库 github.com/ishumei/api-doc（V4 目录《智能文本识别产品API文档-同步》《智能图片识别产品API文档-同步单张》）
与 help.ishumei.com 在线文档（查阅日期 2026-11-06）。

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| 文本同步检测（text/v4） | ✅ | `scenes.<sceneCode>` = eventId；`extra.text-type` 覆盖默认 TEXTRISK |
| 图片同步检测（image/v4） | ✅ | URL / base64 / bytes（自动编码）三通道；`extra.image-type` 覆盖默认组合值 |
| app-id | ✅ | `content.safe.providers.shumei.app-id`（数美控制台 appid），装配期 fail-fast |
| eventId | ✅ | `scenes.<sceneCode>` 的映射值即 eventId，与数美控制台事件约定一致，仅透传不校验 |
| region | ✅ | `region` 属性默认 `sh`（文本 bj/sh/fjny/xjp，图片 bj/sh/gg/xjp）；`extra.text-base-url`（全 URL 原样使用）/ `extra.image-base-url` / `extra.video-base-url`（取 scheme+host 后重拼固定路径）可覆盖 |
| riskLevel 映射 | ✅ | PASS/REVIEW/REJECT（大小写不敏感）；缺失/未知值保守回退 REVIEW |
| 命中标签 | ✅ | allLabels[].riskLabel1/2/3 → `labelPath` + `labels`，probability(0~1) → ZERO_TO_ONE；条目缺 riskDetail 时回落根级 riskDetail |
| 命中词 | ✅ | riskDetail.matchedLists[].words[]（用户内容片段）只进 `hit.raw.matchedWords`，不进 evidence/labels |
| 风险来源 | ✅ | riskDetail.riskSource（1000/1001/1002）进 `hit.raw` |
| 打码文本 / 联系方式 / 人脸 | ✅ 安全处理 | auxInfo.filteredText、contactResult、faces 人名等用户内容一律丢弃不透传 |
| tokenId | ✅ | 解析链 context.userId → dataId → 随机 UUID（官方建议无 uid 时用唯一数据标识） |
| 动图截帧（interval/maxFrame） | ✅ | 经场景 ext 传入，与保留键撞名装配期 fail-fast |
| 图片异步检测（轮询 + 回调） | ✅ | 提交 `POST /v4/saas/async/img`（请求同同步 image/v4，≤30MB）→ requestId 为任务引用；查询 `POST /v4/image/query` 按 requestId 轮询（AuditMode.ASYNC_QUERY）；回调报文与同步响应同构，回调地址在数美控制台配置（不经过本库） |
| 视频审核（video/v4 提交 + /video/query/v4 查询，1.2.0） | ✅ | 仅 URL 入参；host `api-video-{region}.fengkongcloud.com`；提交仅回执，任务关联键=data.btId（取业务 dataId，缺省随机生成）；返回码 1100=有结论、1101=处理中；帧/音频命中归一 RiskHit（hit.raw 带 kind:frame/audio + riskSource，imgUrl/audioUrl/audioText 不透传）；回调与查询响应同构、按 btId 关联，回调地址在数美控制台配置；data.screenshotType 必传（extra `screenshot-type`，默认 0）（来源：help.ishumei.com/docs/tj/video/versionV4，查阅日期 2026-11-11） |
| 异步文本 / 流式 | ❌ | 数美文本域仅同步接口；流式属其它产品线 |
| 批量审核 | ❌ | 单条载荷，`maxBatchSize=1` |
| 回调验签（CallbackVerifier） | ✅ | `ShumeiCallbackVerifier`：官方回调**无签名机制**（HTTP 200 确认，否则按 [1,2,3,4,5,6,7,8] 秒重试 8 次），仅做结构校验；防伪须依赖接收侧 IP 白名单等业务手段 |
| 用户内容安全 | ✅ | matchedWords 不进 raw 之外的字段与日志；riskSegments/faces/auxInfo 整体不透传 |

### 限额（`ShumeiLimits`，声明值，以官方文档为准）

| 项 | 文本 | 图片 | 视频（1.2.0） |
| --- | --- | --- | --- |
| 批量 | 单次单条 | 单次单条 | 单次单条 |
| 超时 | 5 秒（extra.connect-timeout-ms 默认 3000 / read-timeout-ms 默认 5000） | 同左 | 10 秒（官方建议 7s，取余量）；查询建议 30s 间隔 |
| 字数 / 体积 | 文本 ≤1 万字；data 整体 ≤1MB（官方约束） | 官方未声明单图大小上限 | 官方未声明；结果保留近三天 |

### 约束说明

数美仅单凭证 `accessKey`（不走 AK/SK 双要素），凭证解析链为：显式配置
`content.safe.providers.shumei.access-key` → 自定义 `CredentialsProvider`（provider=`shumei`）→
环境变量 `CONTENT_SAFE_SHUMEI_AK`。文本接口官方建议超时 1 秒，默认放宽到 5 秒以覆盖弱网；
返回码 1901 为 QPS 超限、1902 参数不合法、1903 服务失败、1905 字数超限、9101 无权限、
1911（图片）下载失败，统一映射 ProviderCallException（message 脱敏，不含原文）。

## huawei

⏳ 未实现。

## 平台级能力（core/starter，与具体供应商无关）

### 异步审核与回调

- 路由模型：操作的 `ProviderPlan.mode` 声明 `AuditMode.ASYNC_QUERY`（可轮询）/`ASYNC_CALLBACK`（仅回调推送）；
  同步 `audit()` 路由自动排除异步操作（互不干扰、不会误触发熔断）。
- 提交与查询：`template.submitAsync(request)` → 内部 taskId（任务入 `AuditTaskStore`，PENDING）；
  `template.query(taskId)` 轮询终态——中间态返回 `finalResult=false` 的结果、任务保持 PENDING，终态迁移 SUCCEEDED 并发布事件。
  中间态 `suggestion` 为 `null`（1.2.0 起成文），消费方必须以 `finalResult` 门控或判空。
- **视频命中截断（1.2.0+，全供应商统一）**：视频终态 hits 按 confidence 降序最多保留 50 条
  （`RiskHitCaps.DEFAULT_VIDEO_LIMIT`），截断时 `raw.truncated=true`、`raw.totalHits`=原始命中数；
  建议（PASS/REVIEW/REJECT）在截断前推导不受影响；文本/图片同步路径不截断。
- **轮询参数（1.2.0+，视频开箱即用）**：五家视频操作在 `AuditCapabilities` 声明建议轮询参数
  `suggestedPollInterval(30s)` / `suggestedPollTimeout(30m)`，单参 `submitAndWait(request)`
  自动按建议值覆盖模板默认（2s/5m 仅是图片量级的模板回退值）；操作未声明建议值时维持模板配置。
  建议值来源——阿里/百度/数美为官方建议的 30 秒轮询间隔，腾讯/火山官方未公开建议值、
  取平台统一保守值（javadoc 已注明）；超时统一 30 分钟模块值（长视频业务可显式三参覆盖，
  `expire-after` 建议同步加长到 2h~4h）。
- 回调：HTTP 回调打向业务侧地址，业务侧转交 `template.handleCallback(provider, headers, rawBody)`；
  core 按 provider 索引 `CallbackVerifier` 验签，失败抛 `CallbackVerifyException` 并发死信事件；
  通过后归一 `AuditResult` 并按 ref（providerTaskId，空白时按 dataId 反查）迁移任务终态。
- 供应商支持面：阿里图片（轮询+回调，SHA256 验签）、腾讯图片（仅回调推送，结构校验）、
  数美图片（轮询+回调，结构校验）；1.2.0 起视频域五家均异步（阿里轮询、腾讯轮询+可选回调、
  百度轮询+可选回调、数美轮询+控制台回调、火山仅轮询，见各家表）；文本域仍全部仅同步。

### AIGC 检测开关（请求期）

- `AuditOptions.withAigcDetect(true)` 声明本次审核需要 AIGC 识别（AI 生成内容检测）。
- `AuditCapabilities.supports()` 单向门控：开关打开而操作未声明 `aigcDetect` 能力位时直接过滤该操作；
  开关关闭/缺省不影响任何路由（配置期 AIGC 场景不受开关影响）。
- 供应商支持面见上表：腾讯（Type 请求期切换）、阿里（service 码配置期路由）已接入；
  baidu/shumei/volcengine 未声明该能力位（未验证不编造）。

### 人审闭环（REVIEW 终态归一）

- 机审 `REVIEW` 结果发布 `ReviewEvent`（同步/查询路径由 core 模板发布，回调路径由 CallbackDispatcher 发布），
  业务方经 `AuditListener` 消费后转人审队列。
- 业务方调用 `ContentSafeTemplate.resolveReview(ReviewResolution)` 落终态：
  归一为 `AuditResult`（source=`AuditSource.MANUAL`、finalResult=true、suggestion=PASS/REJECT 对应决议）。
- `taskId` 存在时同步迁移异步任务终态（幂等，已终态抛 InvalidRequestException）；
  注入 `ReviewResultSink` SPI 时先回写后迁移（回写失败任务保持 PENDING，可整体重试）。
