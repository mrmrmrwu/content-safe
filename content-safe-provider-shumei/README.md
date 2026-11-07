# content-safe-provider-shumei

数美天净（Shumei）内容安全接入：文本 / 图片同步检测（text/v4、image/v4）。

**无官方现代 Java SDK**，本模块内置自研 HTTP 客户端（JDK `HttpClient` + Gson，均为
optional 依赖，不进入使用方编译期传递依赖）。

## 能力

| 能力 | 状态 |
| --- | --- |
| 文本同步检测（text/v4） | ✅ ≤1 万字（UTF-8），data 整体 ≤1MB |
| 图片同步检测（image/v4） | ✅ URL / base64 / bytes 三通道（bytes 自动编码） |
| 事件（eventId） | ✅ `scenes` 映射，与数美控制台事件约定一致 |
| region | ✅ 默认 `sh`；文本 bj/sh/fjny/xjp，图片 bj/sh/gg/xjp；extra 可整体覆盖地址 |
| 动图截帧（interval/maxFrame） | ✅ 经场景 ext 传入（maxFrame 官方上限 20） |
| 图片异步（轮询 + 回调） | ✅ 提交 `/v4/saas/async/img`（≤30MB），查询 `/v4/image/query` 按 requestId 轮询；回调地址在数美控制台配置（不经过本库） |
| 批量 / 异步文本 | ❌ 数美文本域仅同步接口 |

suggestion 归一：`riskLevel` PASS/REVIEW/REJECT（大小写不敏感）；
缺失/未知值保守回退 REVIEW。`finalResult`（1=最终）进 `AuditResult.finalResult`，
`resultType`（0=机审/1=人审）进命中来源 `HitSource.MACHINE/MANUAL`。

## 配置

```yaml
content:
  safe:
    providers:
      shumei:
        enabled: true
        access-key: xxx           # 数美仅单凭证；缺省回退环境变量 CONTENT_SAFE_SHUMEI_AK
        app-id: "default"         # 必填：数美控制台 appid
        region: sh                # 可选：默认 sh
        scenes:
          comment: "text"         # 业务场景 code → 数美 eventId
          avatar: "image"
        extra:
          connect-timeout-ms: "3000"
          read-timeout-ms: "5000"
          # text-type: TEXTRISK                          # 可选：默认 TEXTRISK
          # image-type: POLITY_EROTIC_VIOLENT_QRCODE_ADVERT_IMGTEXTRISK
          # text-base-url: https://api-text-sh.fengkongcloud.com/text/v4   # 可选：整体覆盖
          # image-base-url: https://api-img-sh.fengkongcloud.com/image/v4
```

`tokenId`（设备/用户上下文标识，官方强约定必传）解析链：`context.userId` →
`dataId` → 随机 UUID。场景 ext 中的 `interval`/`maxFrame` 等键会并入请求 data，
与保留键（`img`/`text`/`tokenId`/`dataId`/`ip`/`deviceId`）撞名即装配期 fail-fast。
未映射的场景不会注册操作（与其他 provider 一致，由路由跳过）。

## 安全约束

- 命中词（`riskDetail.matchedLists[].words[]`）只进 `RiskHit.raw.matchedWords`，
  由 core `EvidenceRedactor` 统一脱敏，不进日志；
- `riskSegments` 片段、`auxInfo.filteredText`（打码原文）、`contactResult`（联系方式）、
  `faces`（人脸名）等用户内容一律丢弃不透传；
- `AuditResult.raw` 只放 `{code, finalResult, resultType}`；命中 raw 只放
  `{riskLevel, riskSource, matchedWords}`；
- 日志与异常消息不含 accessKey 与请求原文（长 token 一律掩码，message 截断 120 字符）。

## 异步与回调

- **轮询**：`template.submitAsync(request)` 提交（返回内部 taskId），`template.query(taskId)` 按
  requestId 轮询，`contents` 未就绪时返回 `finalResult=false` 的中间态；
- **回调**：报文与同步 image/v4 响应同构，回调地址在数美控制台配置；官方**无签名机制**
  （HTTP 200 确认，否则按 [1,2,3,4,5,6,7,8] 秒重试 8 次），本模块 `ShumeiCallbackVerifier`
  仅做结构校验，防伪须依赖接收侧 IP 白名单等业务手段。

## 测试

- 契约样本：`src/test/resources/contracts/shumei/`（text pass/review/reject、
  image pass/reject、QPS 超限 1901，取自官方文档示例并打码）；
- 单元测试：normalizer 映射（含 riskSource/命中词过滤、根级 riskDetail 回落）、
  错误映射（含脱敏）、两个 Operation 的参数装配与限制校验；
- 装配测试：`ApplicationContextRunner`（默认不装配、缺凭证 fail-fast、用户 Bean 覆盖）；
- ArchUnit：core/starter 隔离；Gson 仅限 `internal` 包。

## 参考

- [数美智能文本识别（同步）API 文档 V4](https://help.ishumei.com/docs/tj/text/versionV4/sync/developDoc)
- [官方 API 文档仓库（github.com/ishumei/api-doc）](https://github.com/ishumei/api-doc)

（查阅日期：2026-11-06）
