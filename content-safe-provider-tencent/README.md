# content-safe-provider-tencent

腾讯云内容安全适配模块：文本 TMS `TextModeration` + 图片 IMS `ImageModeration` 同步审核（API 版本均 2020-12-29）。

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
          readTimeoutSeconds: 10               # SDK 读超时（秒，可选）
```

- 默认不装配：`enabled=true` 且 SDK 在类路径上才生效；缺凭证启动即失败（fail-fast）。
- 未映射的场景返回空操作列表，由路由跳过（不产生调用）。
- 凭证解析链：自定义 `CredentialsProvider`（provider()=="tencent"）→ 配置 ak/sk → 环境变量回退。

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
