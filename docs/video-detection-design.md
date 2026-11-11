# 视频审核设计（1.2.0）

> 状态：设计定稿（2026-11-11）。所有接缝签名、SDK 类、官方接口均已核实，来源与查阅日期见各节。
> 定版依据：SemVer 按兼容性影响判定——本设计 100% 增量（新工厂方法、新常量、新 provider 分支、新场景常量），零删除零签名变更，japicmp 对 1.1.0 基线零不兼容 → **minor：v1.2.0**。

## 0. 决策记录

| # | 决策 | 理由 |
|---|------|------|
| D-1 | 同步视频不做 | 各供应商视频本质都是"任务提交+回调/轮询"，无真实同步语义；`audit()` 对 VIDEO 请求自然路由不到同步操作，不封禁 |
| D-2 | 直播流出 1.2.0 范围 | 流式审核有 start/close 生命周期，现有 `AuditOperation` 模型装不下；1.3.0 单独立项 |
| D-3 | 交付顺序：aliyun → tencent（P1）→ baidu → shumei（P2）→ volcengine（P3） | 阿里与现有 image async 同构成本最低；腾讯覆盖"轮询+回调双模式"路径；火山需先核实文档 |
| D-4 | 视频入参只承诺 URL（capabilities url=true） | 视频文件过大不声明 BYTES/base64；OSS/COS bucket 引用各家私有，走 extra 透传 |
| D-5 | 视频场景建议 `arbitration=OFF` | 仲裁=多候选全量采集，视频单价高会放大成本；写进接入指南 |

## 1. 现状结论：SPI 与 core 零改动

当初的抽象就是内容类型无关的，视频不需要动任何已发布接口。接缝清单（全部今日核实）：

| 接缝 | 现状 | 视频 |
|------|------|------|
| `ContentType.VIDEO` | 枚举值已预留（api/enums/ContentType.java:20） | 直接使用 |
| `AuditOperation` SPI | `plan()/capabilities()/audit()/submitAsync()/query()/handleCallback()/order()/supports()` 全部内容类型无关 | provider 新增 VIDEO 操作即可 |
| `AuditCapabilities` | `contentTypes/url/objectKey/asyncQuery/asyncCallback/maxUrlSize` 等齐全 | `contentTypes(VIDEO).url(true).asyncQuery(true)` |
| `audit()` 路由 | 过滤 `mode != ASYNC_QUERY && != ASYNC_CALLBACK`（DefaultContentSafeTemplate.java:222） | VIDEO 请求无同步候选 → `ProviderNotAvailableException`，预期语义（D-1） |
| `submitAsync()` 路由 | 过滤 `asyncQuery() \|\| asyncCallback()` + `supports()` → router → `AuditTask.pending(taskId, ref, scene.code(), provider, planKey)` | 透明复用 |
| `query(taskId)` | planKey 精确路由 + `asyncQuery()` 能力位过滤（DefaultContentSafeTemplate.java:552） | 透明复用 |
| 回调链路 | verifier → 限流 → `findCallbackOperation`（`_callback` 占位场景探测，取首个 `asyncCallback()` 操作）→ `handleCallback` → 脱敏 → `findByRef(providerTaskId, dataId)` → `markTerminal` + AuditEvent/ReviewEvent | 透明复用，provider 实现 `handleCallback` |
| in-progress 契约 | `AuditResult{ok=true, suggestion=null, finalResult=false}`（阿里图片异步 280 同款） | 沿用；业务轮询 `query(taskId)` 至 `finalResult=true` |
| starter | 场景 `type: VIDEO` 是枚举绑定，现在就能配 | 零改动 |

模板没有 `awaitResult` 阻塞轮询 API——业务自己循环 `query()`，接入指南 §9 已文档化，不新增。

## 2. 契约增量（api/core 唯一改动，T-026）

全部为新增，无签名变更：

1. `AuditRequest.video(String scene, ContentRef content)` — 对齐 `image()` 快捷构造；dataId 自动 32 位无杠 UUID（`AuditRequest.newDataId()` 已私有复用）。
2. `ContentSafeTemplate.videoAudit(String scene, String url)` — default 便捷方法，对齐 `imageAudit(scene, url)`；接口加 default 方法 japicmp 兼容。
3. `Scenes.SHORT_VIDEO = "short_video"` — 内置视频场景常量；`SceneRegistry` 注册 `SceneDefinition.of(SHORT_VIDEO, ContentType.VIDEO, null)`（SceneRegistry.java:31-36 处追加）。
4. `validateContent` 不加 VIDEO 特殊分支——"视频仅 URL"由 provider 的 `capabilities().supports(request)` 表达（铁律 §5.2 能力先行），core 只做通用 URL 形状校验（已有：scheme http/https + host 非空）。理由：未来 OBJECT_KEY（OSS/COS 引用）对视频是合法形态。

## 3. 异步全链路（复用，无 core 改动）

```
业务                        core/provider                     供应商
 │ submitAsync(video url) ──▶ provider op.submitAsync ──────▶ 提交任务
 │ ◀── taskId（统一任务 ID）│ AuditTask.pending(planKey 落库) │ ◀── taskId/dataId
 │ query(taskId) 轮询 ────▶ op.query(ref) ─────────────────▶ 查询结果
 │ ◀── finalResult=false    │ in-progress 契约               │
 │ query(taskId) ──────────▶ op.query(ref) ────────────────▶ 终态
 │ ◀── finalResult=true     │ markTerminal + AuditEvent      │
（或回调路径）
供应商 ──▶ 业务 HTTP endpoint ──▶ template.handleCallback(CallbackRequest{provider,headers,rawBody})
              ──▶ CallbackDispatcher：verifier→限流→op.handleCallback→脱敏
              ──▶ findByRef(providerTaskId, dataId) → markTerminal → AuditEvent/ReviewEvent
```

**关联键契约**（provider 实现必须遵守）：
- `AsyncTaskRef(providerTaskId, dataId)`：providerTaskId 非空（Asserts.notBlank），dataId 可空但强烈建议传——`findByRef` 是双条件 AND 匹配，回调报文必须回传能命中存储记录的组合（腾讯=DataId 回显、阿里=taskId+dataId 双带）。
- `handleCallback` 返回的 `CallbackResult.accepted(taskId, result)` 中 taskId 语义 = 存储 ref 的 providerTaskId。

## 4. provider 公共设计

文件布局对齐既有模式（每家）：

| 文件 | 内容 |
|------|------|
| `internal/XxxVideoAsyncOperation` | `implements AuditOperation`；plan.mode=ASYNC_QUERY；audit() 抛 UnsupportedFeatureException；submitAsync/query/handleCallback（回调可选） |
| `internal/XxxClientInvoker` | 接口加视频方法；SDK 类型不出 internal 包（ArchUnit 强制） |
| `internal/XxxAuditResultNormalizer` | 加 `normalizeVideoResult/normalizeVideoCallback` |
| `internal/XxxLimits` | 加 `videoAsync()` |
| `XxxProviderConstants` | `OPERATION_VIDEO_*`、`EXTRA_VIDEO_*` |
| `XxxAuditOperationProvider` | `operations(SceneDefinition)` 加 VIDEO 分支（scenes.<sceneCode> 映射语义见各家节） |

**能力声明**：`contentTypes(VIDEO).url(true).asyncQuery(true)`；`asyncCallback` 仅当回调配置存在时 true 且**必须**同时覆写 `handleCallback`（防 D-1 缺陷复发）。绝不声明 bytes/base64。

**脱敏红线（视频特有，全部不透传）**：
- 截帧图 URL：阿里 `frames[].tempUrl`、腾讯片段 CosUrl/截图、百度/数美截帧；
- 语音转写文本原文：阿里 `audioResult.sliceDetails[].text`、腾讯 `AudioText`/`Asrs`；
- 腾讯 `HitSnippetInfo.Snippet`（命中片段原文）。
- 进 `hit.raw` 的仅元数据：offset/timestamp/frameNum/score/labels/riskLevel。

**决策映射**：沿用各家 normalizer 现有语义，保守原则（未知/缺失 → REVIEW）。整体结果 = 各家顶层风险字段（阿里 riskLevel / 腾讯 Suggestion）。

**限额**（`OperationLimits(maxConcurrency, qps, timeout, maxContentBytes, maxPixels, multiSceneBilling)`）：提交/查询 timeout 5–10s；maxConcurrency/qps 不声明（云侧排队）；maxPixels 无意义填 0；multiSceneBilling=false。视频文件大小上限在 provider `supports()` 内按 extra 配置校验（maxUrlSize 已有能力位）。

## 5. 阿里云视频（T-027，P1）

**API**：green20220302 `Client.videoModeration` / `videoModerationResult`（本地 jar 3.6.9 javap 验证存在；provider 既有依赖，**零新增**）。
来源：help.aliyun.com/zh/document_detail/2505810（提交）、2505811（查询），查阅 2026-11-11。

| 项 | 值 |
|----|----|
| Service | `videoModeration`（固定值；scenes.<sceneCode> 配置语义 = 开启该场景的视频异步审核，service 默认 videoModeration） |
| 提交 serviceParameters | `videoUrl`（必，公网 URL 或 OSS）、`dataId`（必）、`callback`（选）、`seed`（选） |
| 查询 serviceParameters | `taskId`（必） |
| 提交响应 | `data{taskId, dataId}` → `AsyncTaskRef(taskId, dataId)` |
| 查询响应 | `data{dataId, taskId, riskLevel, frameResult{frames[{offset, timestamp, riskLevel, tempUrl, results[]}], frameSummarys[{label, description, labelSum}]}, audioResult{riskLevel, sliceDetails[]}, extAigcData}` |
| 结果保留 | 3 天；建议 30s 轮询间隔 |

**映射**：
- `data.riskLevel`（none/low/medium/high）→ suggestion，复用 `AliyunAuditResultNormalizer` 现有映射；
- `frames[].results[]` 逐命中 → `RiskHit{labelPath=frame 命中 label, raw={offset, timestamp, frameRiskLevel}}`，`tempUrl` 丢弃；
- `audioResult.sliceDetails[]` → `RiskHit{raw={起止偏移}}`，转写 `text` 丢弃；
- `frameSummarys` → `hit.raw.frameSummarys` 汇总（label+labelSum）。

**模式**：plan.mode=ASYNC_QUERY；本期不做阿里视频回调解析（能力位 false），仅轮询——阿里回调报文结构留 P2 核实后再实现。

## 6. 腾讯云视频（T-028，P1）

**API**：`com.tencentcloudapi.vm.v20210922.VmClient`（全家桶 `tencentcloud-sdk-java:3.2.52` 已含，本地 jar javap 验证，**零新增依赖**）。
来源：cloud.tencent.com/document/product/1265/80017（CreateVideoModerationTask）、80019（数据结构）、DescribeTaskDetail（同产品目录），查阅 2026-11-11。

| 项 | 值 |
|----|----|
| 提交 | `CreateVideoModerationTask{BizType=plan.service, Tasks[{DataId, Input.StorageInfo{Type="URL", Url}}], CallbackUrl?, Seed?}` |
| 提交响应 | `Tasks[].TaskId` → `AsyncTaskRef(taskId, dataId)`（实现时 javap 核对 Response 字段名） |
| 查询 | `DescribeTaskDetail{TaskId, ShowAllSegments?}` |
| 查询响应 | `{Status, Suggestion, Labels[TaskLabel{Label, SubLabel, Score}], VideoSegments[{OffsetTime, Result}], HitSnippetInfos[], AudioText, MediaInfo, ErrorType, ErrorDescription}` |

**映射**：
- `Suggestion`（Pass/Review/Block）→ suggestion，复用 `TencentAuditResultNormalizer` 现有映射；
- in-progress 判定：`Status != "FINISH"` → `finalResult=false, suggestion=null`（Status 字面量实现时以官方数据结构页为准核实）；
- `Labels[]` → hits（labelPath=`Label+"."+SubLabel`，confidence=Score）；`VideoSegments[].OffsetTime` → `hit.raw.offsetTime`；
- `HitSnippetInfo.Snippet`、`AudioText`、片段截图 URL 一律不透传。

**模式**：plan.mode=ASYNC_QUERY；配了 extra callback-url 时 `asyncCallback(true)` + 覆写 `handleCallback`（回调 payload 与 DescribeTaskDetail 响应同构，`AbstractModel.fromJsonString` 解析，`DataId` 为关联键必查——对齐 `TencentImageAsyncOperation.handleCallback` 现有模式）。

## 7. 存量缺陷清单（设计期发现，随视频工作顺带修）

**D-1：AliyunImageAsyncOperation 回调能力位谎报**。
现状：extra 配置 callbackUrl/seed 时 `capabilities.asyncCallback(true)`（AliyunImageAsyncOperation 构造器），但该类从未覆写 `handleCallback`。后果：回调到达 → `CallbackDispatcher.findCallbackOperation` 按能力位选中它 → 默认实现 `rejected("not supported")` → 死信。修复（1.2.0 内二选一，优先 a）：
- a) 实现 `handleCallback`：阿里异步回调报文与查询结果同构，复用 normalizer；
- b) 若官方报文结构核实不齐：能力位改回 false，javadoc 注明"回调仅作提交告知，结果仍需轮询"。

**D-2（记录，不修）**：`audit()` 对 VIDEO 请求抛 `ProviderNotAvailableException`——预期语义（D-1 决策），接入指南写明"视频必须走 submitAsync/query 或回调"。

## 8. P2/P3 概要

- **baidu（T-029）**：长视频异步（提交任务+回调推送）。来源：cloud.baidu.com/doc/ANTIPORN/s/{yksy6xs5y, mkxlraoz5, Pkxlsnvlc}，查阅 2026-11-11。自研 JDK HttpClient（对齐 baidu 模块既有形态），access_token 复用 BaiduAccessTokenManager；截帧 URL 不透传。≤5 分钟短视频同步接口不做（D-1）。
- **shumei（T-030）**：`video/v4` 文件审核（自定义截帧；提交+回调或主动查询 queryInterface）。来源：help.ishumei.com/docs/tj/video/versionV4/{developDoc, queryInterface/developDoc}，查阅 2026-11-11。流式 `video/stream/v4` 不做（D-2）。
- **volcengine（T-031）**：BusinessSecurity VideoRisk。SDK 有异步建模；实现前按铁律重新核实官方文档（本次调研未直接命中文档页），不得凭记忆写契约。
- **T-032**：samples 增 video-async 样例工程（fake provider + 真实 provider 可切换）。
- **T-033**：文档收口——provider-feature-matrix.md 各家视频节、接入指南视频章节（§含 arbitration=OFF 建议、3 天结果保留、30s 轮询）、错误码表核对（预计零新增，复用 PROVIDER_CALL_ERROR 等）。

## 9. 测试设计

| 层 | 内容 |
|----|------|
| contracts | `contracts/<provider>/video/*.json` camelCase 样例（提交响应/查询终态/查询中间态/回调），normalizer 契约测试直接解析断言映射 |
| op 单测 | invoker stub 三路径（提交/查询/回调）+ in-progress 分支 + 脱敏断言（`tempUrl`/`Snippet`/转写文本不出现在 result 任何字段）+ 能力位断言（url=true、bytes=false） |
| core 端到端 | fake VIDEO async op：submitAsync→query（false→true 两段）→taskStore 终态→事件；回调路径 handleCallback→markTerminal→AuditEvent/ReviewEvent |
| starter | VIDEO 场景 yml 装配（`type: VIDEO`）+ 默认场景 short_video 注册断言 |
| ArchUnit | 既有 SDK 隔离/依赖方向规则自动覆盖新代码 |

## 10. 编码防错清单

**环境**：Maven 命令必须前缀 `JAVA_HOME="C:\Program Files\Java\jdk-17"`（默认 JDK8 会报乱码编译错）；grep 是 ugrep（含 `-` 的 pattern 用 `grep -F`）；python 不可用；单模块构建必须 `-am`（本地仓库无 1.2.0-SNAPSHOT 上游）；不带 clean 的 test 可能是增量编译假象——交付前必须 `clean verify` 全量绿；长文本写文件用 Write 工具（heredoc 会被 Bash 截断）；SDK 字段用 javap 核实、不凭记忆猜。

**契约**：
- `Asserts.notNull/notBlank` 抛 `InvalidRequestException`（不是 IllegalArgumentException）；
- 同一 provider 的多个操作 planKey（`provider+":"+operation`）不得重名，否则 query 精确路由歧义；
- `AuditTask.pending` 必须用 5 参重载（带 operation/planKey），4 参会让 query 回退 provider 匹配；
- `AsyncTaskRef.providerTaskId` 非空硬约束；回调报文必须携带可命中 `findByRef` 的关联键，缺失时 rejected 死信（不是 accepted）；
- 脱敏顺序：`EvidenceRedactors.redactResult` 在落库/事件/返回值之前统一调用，provider normalizer 自身不输出敏感字段是第一道防线；
- 未知决策 → REVIEW（保守）；错误消息不回显内容值/凭证；
- SDK 类型不出 internal 包、云 SDK 依赖 optional（ArchUnit + 铁律）；
- 新增错误码先更新 error-code-table.md（本设计预计零新增）；
- 只加不改签名（japicmp 对 1.1.0 基线硬门禁）；
- javadoc 注明官方文档来源 + 查阅日期；commit 规范 `type(scope): summary`（feat(T-0xx)/docs/test）。

## 11. 任务分解（全部进 1.2.0）

| 任务 | 优先级 | 内容 | 依赖 |
|------|--------|------|------|
| T-026 | P0 | 契约打通：`AuditRequest.video()` + `videoAudit` + `Scenes.SHORT_VIDEO` + SceneRegistry 注册 + core 端到端测试（fake VIDEO op 全流程）+ 接入指南视频节 | — |
| T-027 | P1 | provider-aliyun 视频：VideoAsyncOperation（ASYNC_QUERY）+ normalizer + contracts + 测试 | T-026 |
| T-028 | P1 | provider-tencent 视频：VideoAsyncOperation（ASYNC_QUERY + 可选回调 handleCallback）+ normalizer + contracts + 测试 | T-026 |
| T-029 | P2 | provider-baidu 长视频异步 + 回调 verifier/解析 | T-026 |
| T-030 | P2 | provider-shumei video/v4 文件审核 | T-026 |
| T-031 | P3 | provider-volcengine 视频（先核实官方文档） | T-026 |
| T-032 | P2 | video-async 样例工程 | T-027/T-028 |
| T-033 | P2 | 文档收口（feature matrix/接入指南/错误码核对）+ D-1 修复收尾 | 各家 |
