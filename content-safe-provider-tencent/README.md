# content-safe-provider-tencent

腾讯云内容安全适配模块：文本 TMS `TextModeration` + 图片 IMS `ImageModeration` 同步审核（API 版本均 2020-12-29）
+ 视频 VM `CreateVideoModerationTask`/`DescribeTaskDetail` 异步审核（API 版本 2021-09-22）。

## 引入

```xml
<dependency>
    <groupId>io.github.wrqtec</groupId>
    <artifactId>content-safe-provider-tencent</artifactId>
</dependency>
<!-- SDK 为 optional 依赖，需显式引入 -->
<dependency>
    <groupId>com.tencentcloudapi</groupId>
    <artifactId>tencentcloud-sdk-java</artifactId>
    <version>3.2.52</version>
</dependency>
```

## 配置

```yaml
content:
  safe:
    providers:
      tencent:
        enabled: true
        secret-id: ${TENCENT_SECRET_ID}        # 或 env CONTENT_SAFE_TENCENT_AK
        secret-key: ${TENCENT_SECRET_KEY}      # 或 env CONTENT_SAFE_TENCENT_SK
        session-token:                         # 可选（临时凭证，env CONTENT_SAFE_TENCENT_STSTOKEN）
        region: ap-guangzhou                   # 默认广州
        scenes:                                # 场景 code → BizType（普通审核）
          nickname: your-nickname-biztype
          comment: your-comment-biztype
        aigc-scenes:                           # 场景 code → BizType（AIGC 识别，Type=IMAGE_AIGC，仅 IMAGE 场景）
          ai-art: your-aigc-biztype
        extra:
          gifInterval: 2                       # GIF 截帧间隔（可选）
          gifMaxFrames: 5                      # GIF 最大截帧数（可选）
          read-timeout-seconds: 10             # SDK 读超时（秒，可选）
          # callback-url: https://biz.example.com/callback/tencent   # 图片异步回调地址（可选，配置后注册图片异步操作）
```

- 默认不装配：`enabled=true` 且 SDK 在类路径上才生效；缺凭证启动即失败（fail-fast）。
- 未映射的场景返回空操作列表，由路由跳过（不产生调用）。
- 凭证解析链：自定义 `CredentialsProvider`（provider()=="tencent"）→ 配置 ak/sk → 环境变量回退。
- **图片异步**（`CreateImageModerationAsyncTask`，仅回调推送，官方无查询接口）：
  配置 `extra.callback-url` 后自动注册异步操作；提交时请求**必须携带业务侧 `dataId`**
  （回调报文无任务 ID，按 dataId 关联任务）；官方回调**无签名机制**，
  `TencentCallbackVerifier` 仅做结构校验，防伪须依赖回调接收侧 IP 白名单等业务手段；限频 20QPS。
- **视频异步**（VM，仅 URL）：`scenes` 中把 VIDEO 场景映射到 BizType 即注册视频操作，
  `submitAsync` 返回 `taskId`（`DescribeTaskDetail` 轮询，建议 30 秒间隔）。
  `Status=PENDING/RUNNING` 为处理中、`FINISH` 终态、`ERROR/CANCELLED` 抛 `ProviderCallException`
  （消息仅含 errorType + 脱敏后的 errorDescription）。终态命中含任务级标签、音频段与截帧段三类 hit
  （`labelPath=Label/SubLabel`，`hit.raw.offsetTime` 标记偏移；音频转写原文、片段/截帧地址、
  HitSnippetInfo 均不透传）。可选 `extra.callback-url`（+ `callback-seed`）配置后回调推送与
  查询响应同构，同样按 `TaskId`（缺失回退 `DataId`）关联。
终态 hits 按 confidence 降序最多保留 50 条，截断时 `raw.truncated=true`、`raw.totalHits` 为原始命中数。
- 操作已声明建议轮询参数 30s 间隔 / 30 分钟超时：单参 submitAndWait 自动生效，无需显式三参。

## 能力声明

| 能力 | 文本 | 图片 |
| --- | --- | --- |
| 同步审核 | ✅ | ✅ |
| URL | — | ✅ |
| base64 / bytes | — | ✅（bytes 内部编码为 base64） |
| 上限 | 10000 字符 | base64 ≤10MB / URL 源图 ≤30MB |
| 默认 QPS | 1000 | 100 |

完整能力矩阵见 [docs/provider-feature-matrix.md](../docs/provider-feature-matrix.md)。

## 安全约束（CLAUDE.md §5.6 / §9）

- 响应归一化只保留 `{RequestId, BizType}` 于 `raw`；OCR 文本原文、TMS ContextText 等用户内容一律丢弃。
- TMS Keywords（敏感词原文）只进 `RiskHit.raw`（raw 按约定不进日志/审计正文），不进 labels/evidence。
- SDK 异常只保留 `errorCode` + `requestId`，message（可能含签名材料）不外泄。

## 契约测试

响应样本位于 `src/test/resources/contracts/tencent/`（键为 PascalCase，与腾讯云 API 一致），由
`TencentAuditResultNormalizerContractTest` 驱动 `AbstractModel.fromJsonString` 还原 SDK 模型验证归一化输出。
禁止在测试中真实调用云接口。
