# 供应商能力矩阵（Provider Feature Matrix）

记录各 provider 模块已实现的能力与限额。新增供应商或新增能力时必须同步本表（CLAUDE.md §6 checklist）。

> 标注口径：✅ 已实现；⏳ 已预留抽象、待后续任务；❌ 供应商不支持。
> 信息来源：阿里云内容安全 2.0（增强版）官方文档（help.aliyun.com，查阅日期 2026-11-06）。

## aliyun（content-safe-provider-aliyun，T-013）

SDK：`com.aliyun:green2022032` → 实际坐标 `com.aliyun:green20220302:3.6.6`（optional 依赖）。

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| 文本同步审核（TextModeration） | ✅ | service 由 `scenes.<sceneCode>` 映射 |
| 图片同步审核（ImageModeration） | ✅ | 仅公网 URL（`ContentRefType.URL`） |
| base64 / bytes 内容 | ❌ | 2.0 同步接口仅 URL；能力校验直接拒绝 |
| 批量审核 | ❌ | `maxBatchSize=1` |
| 异步查询 / 回调 | ⏳ | 待 T-019（ImageAsyncScan 系列） |
| OCR | ⏳ | 2.0 service 能力，未接入 |
| AIGC 检测 | ⏳ | service `ai_art_detection` 预留 |
| 自定义名单 | ⏳ | 控制台配置，SDK 侧无感 |
| 回调验签（CallbackVerifier） | ⏳ | 待 T-019 |
| suggestion 映射 | ✅ | riskLevel 三档：none/low→PASS，medium→REVIEW，high→REJECT；缺失时回退标签判空 |

### 限额（`AliyunLimits`，声明值，以官方文档为准）

| 项 | 文本 | 图片 |
| --- | --- | --- |
| 单条大小 | 600 字（实现按 2400 字节保守折算） | 20 MB |
| 像素 | — | 单边 ≤16384、总像素 ≤2.5 亿 |
| 默认 QPS | 100 | 50 |
| URL 要求 | — | 公网可达，3 秒内可下载 |

### 已知服务码（serviceCode 示例，非穷举）

`nickname_detection`、`comment_detection`、`chat_detection`、`pgc_detection`、`ad_compliance_detection`、`ai_art_detection`、图片 `baselineCheck` 等。业务侧只配场景 code → serviceCode 映射，云厂商标识不进 api/core（铁律 §5.3）。

## tencent（content-safe-provider-tencent，T-014）

SDK：`com.tencentcloudapi:tencentcloud-sdk-java:3.2.52`（总包，optional 依赖；文本 TMS + 图片 IMS 均为 API 版本 2020-12-29）。

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| 文本同步审核（TMS TextModeration） | ✅ | BizType 由 `scenes.<sceneCode>` 映射；Content 自动 Base64(UTF-8) 编码 |
| 图片同步审核（IMS ImageModeration） | ✅ | URL（FileUrl）/ base64（FileContent）/ bytes（内部编码）三通道 |
| AIGC 检测 | ✅ | `aigcScenes.<sceneCode>` 独立配置，Type=IMAGE_AIGC，仅 IMAGE 场景生效 |
| 风控字段映射（User/Device） | ✅ | `AuditContext.userId/deviceId/ip` → TMS/IMS User/Device |
| GIF 截帧 | ✅ | extra 配置 `gifInterval` / `gifMaxFrames` |
| 批量审核 | ❌ | `maxBatchSize=1` |
| 异步查询 / 回调 | ⏳ | SDK 有 CreateImageModerationAsyncTask，待 T-019 |
| OCR | ✅ | IMS 响应 OcrResults 命中归一为标签；**文本原文（用户内容）按安全约束丢弃，不进 evidence/raw/日志** |
| 自定义名单 | ⏳ | 控制台配置，SDK 侧无感 |
| 回调验签（CallbackVerifier） | ⏳ | 待 T-019 |
| suggestion 映射 | ✅ | Pass→PASS，Review→REVIEW，Block→REJECT；缺失时回退 Label 判 Normal |
| 用户内容安全 | ✅ | TMS Keywords（敏感词原文）只进 `hit.raw`（raw 按约定不进日志/审计正文） |

### 限额（`TencentLimits`，声明值，以官方文档为准）

| 项 | 文本（TMS） | 图片（IMS） |
| --- | --- | --- |
| 单条大小 | 10000 Unicode 字符 | Base64 ≤10 MB；URL 源图 ≤30 MB |
| 默认 QPS | 1000 | 100 |

### BizType 说明

BizType 为腾讯云控制台创建的策略标识，业务侧只配场景 code → BizType 映射，云厂商标识不进 api/core（铁律 §5.3）。信息来源：腾讯云 TMS/IMS 官方文档（cloud.tencent.com，查阅日期 2026-11-06）。

## baidu（content-safe-provider-baidu，T-015）

无官方现代 SDK（`com.baidu.aip:java-sdk` 停留在 4.16.18 / ~2022），**自研 HTTP 客户端**（JDK HttpClient + Gson，均为 optional 依赖）。接口为内容审核平台 v2（text_censor/img_censor user_defined）。

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| 文本同步审核 | ✅ | form 参数 `text`，≤20000 字节（UTF-8） |
| 图片同步审核 | ✅ | URL（`imgUrl`）/ base64（`image`）/ bytes（内部转 base64）三通道；base64 后 ≤4MB |
| 策略中心 | ✅ | `scenes.<sceneCode>` = 策略中心 strategyId（数字）；空字符串走账号默认策略 |
| access_token 管理 | ✅ | client_credentials 本地缓存（30 天，提前 5 分钟刷新）；110/111 错误自动失效重取并重试一次 |
| 批量审核 | ❌ | 单次单条 |
| 异步查询 / 回调 | ❌ | v2 接口为同步模型 |
| GIF 动图 | ⏳ | `imgType=1` 参数未透传（按静态图处理） |
| suggestion 映射 | ✅ | conclusionType 1→PASS、2→REJECT、3→REVIEW、4→ERROR（审核失败是业务结论，ok 保持 true） |
| 用户内容安全 | ✅ | 命中词 hits[].words 进 `hit.evidence`（由 core EvidenceRedactor 脱敏）；`raw` 只放 `{log_id}` |

### 限额（`BaiduLimits`，声明值，以官方文档为准）

| 项 | 文本 | 图片 |
| --- | --- | --- |
| 单条大小 | 20000 字节（UTF-8） | base64 后 ≤4MB |
| 默认 QPS | 200 | 50 |

### strategyId 说明

strategyId 为百度内容审核平台"策略中心"创建的策略 ID（数字字符串）。业务侧只配场景 code → strategyId 映射；值为空字符串表示不传 strategyId、走账号默认策略。错误码 18/19 为限流、110/111 为 token 问题。信息来源：百度智能云内容审核官方文档（cloud.baidu.com/doc/ANTIPORN，查阅日期 2026-11-06）。

## volcengine（content-safe-provider-volcengine，T-016）

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
| 异步检测（AsyncImageRisk + GetImageResult） | ⏳ | SDK 有建模，待 v1.1 |
| veImageX 智能审核（CreateImageContentTask） | ⏳ | 异步任务制产品线，待 v1.1 |
| 新版文本（TextSliceRisk，2022-11-07） | ⏳ | 官方标注 TextRisk 的替代品，待 v1.1 评估切换 |
| 批量审核 | ❌ | envelope 单条载荷，`maxBatchSize=1` |
| 回调验签（CallbackVerifier） | ⏳ | 待 T-019 延伸任务 |
| 用户内容安全 | ✅ | matchedWords/ocrText 均不进 evidence/raw 之外的字段与日志 |

### 限额（`VolcengineLimits`，声明值，以官方文档/控制台为准）

| 项 | 文本 | 图片 |
| --- | --- | --- |
| 批量 | 单次单条 | 单次单条 |
| 超时 | SDK ServiceInfo 默认 5 秒（connect/socket） | 同左 |
| QPS / 大小 | 未声明（官方公开文档无确定配额，配额随账号与服务配置变化） | 未声明 |

### 约束说明

SDK 客户端 `ContentSecurityServiceImpl.getInstance()` 为进程级单例，凭证经 setter 注入；
同进程多账号需自行隔离。parameters 内层键名（`parameters-text-key` / `parameters-image-key`）
由使用方按其 Service 的控制台文档显式配置，缺失即装配期 fail-fast（经 FailureAnalyzer 输出可读报告）。

## shumei（content-safe-provider-shumei，T-017）

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
| region | ✅ | `region` 属性默认 `sh`（文本 bj/sh/fjny/xjp，图片 bj/sh/gg/xjp）；`extra.text-base-url` / `extra.image-base-url` 可整体覆盖 |
| riskLevel 映射 | ✅ | PASS/REVIEW/REJECT（大小写不敏感）；缺失/未知值保守回退 REVIEW |
| 命中标签 | ✅ | allLabels[].riskLabel1/2/3 → `labelPath` + `labels`，probability(0~1) → ZERO_TO_ONE；条目缺 riskDetail 时回落根级 riskDetail |
| 命中词 | ✅ | riskDetail.matchedLists[].words[]（用户内容片段）只进 `hit.raw.matchedWords`，不进 evidence/labels |
| 风险来源 | ✅ | riskDetail.riskSource（1000/1001/1002）进 `hit.raw` |
| 打码文本 / 联系方式 / 人脸 | ✅ 安全处理 | auxInfo.filteredText、contactResult、faces 人名等用户内容一律丢弃不透传 |
| tokenId | ✅ | 解析链 context.userId → dataId → 随机 UUID（官方建议无 uid 时用唯一数据标识） |
| 动图截帧（interval/maxFrame） | ✅ | 经场景 ext 传入，与保留键撞名装配期 fail-fast |
| 异步检测 / 流式 | ⏳ | 待 v1.1 |
| 批量审核 | ❌ | 单条载荷，`maxBatchSize=1` |
| 回调验签（CallbackVerifier） | ⏳ | 待 T-019 延伸任务 |
| 用户内容安全 | ✅ | matchedWords 不进 raw 之外的字段与日志；riskSegments/faces/auxInfo 整体不透传 |

### 限额（`ShumeiLimits`，声明值，以官方文档为准）

| 项 | 文本 | 图片 |
| --- | --- | --- |
| 批量 | 单次单条 | 单次单条 |
| 超时 | 5 秒（extra.connect-timeout-ms 默认 3000 / read-timeout-ms 默认 5000） | 同左 |
| 字数 / 体积 | 文本 ≤1 万字；data 整体 ≤1MB（官方约束） | 官方未声明单图大小上限 |

### 约束说明

数美仅单凭证 `accessKey`（不走 AK/SK 双要素），凭证解析链为：显式配置
`content.safe.providers.shumei.access-key` → 自定义 `CredentialsProvider`（provider=`shumei`）→
环境变量 `CONTENT_SAFE_SHUMEI_AK`。文本接口官方建议超时 1 秒，默认放宽到 5 秒以覆盖弱网；
返回码 1901 为 QPS 超限、1902 参数不合法、1903 服务失败、1905 字数超限、9101 无权限、
1911（图片）下载失败，统一映射 ProviderCallException（message 脱敏，不含原文）。

## huawei

⏳ 未实现（规划见 docs/content-safe-starter-design.md §8）。
