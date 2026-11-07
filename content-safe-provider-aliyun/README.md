# content-safe-provider-aliyun

阿里云内容安全 2.0（增强版，green-cip）provider 适配模块。

- SDK：`com.aliyun:green20220302:3.6.6`（`optional`，不进入使用方编译期传递依赖）。
- 已实现：文本同步审核（TextModeration）、图片同步审核（ImageModeration，仅公网 URL）、
  图片异步审核（ImageAsyncModeration 提交 + DescribeImageModerationResult 轮询，仅 URL，
  响应 code=280 为检测中间态）与回调验签（`AliyunCallbackVerifier`，SHA256(uid+seed+Content) 恒时比较）。
- 异步回调配置：`extra.callback-url` / `callback-seed` / `callback-uid`（uid 须主账号 UID，
  未配置时验签失败；seed 缺省回退全局 `content.safe.callback-secret`）。
- 未实现：OCR 等扩展 service（可经 `scenes` 映射直接试用，归一化按通用规则处理）。

## 使用

```xml
<dependency>
    <groupId>io.github.wrqtec</groupId>
    <artifactId>content-safe-provider-aliyun</artifactId>
</dependency>
<dependency>
    <groupId>com.aliyun</groupId>
    <artifactId>green20220302</artifactId>
    <version>3.6.6</version>
</dependency>
```

```yaml
content:
  safe:
    providers:
      aliyun:
        enabled: true
        ak: ${ALIYUN_AK}          # 缺省回退环境变量 CONTENT_SAFE_ALIYUN_AK
        sk: ${ALIYUN_SK}          # 缺省回退环境变量 CONTENT_SAFE_ALIYUN_SK
        # security-token: 可选（STS 临时凭据），缺省回退 CONTENT_SAFE_ALIYUN_STSTOKEN
        region-id: cn-hangzhou    # endpoint 缺省推导为 green-cip.<regionId>.aliyuncs.com
        scenes:                   # 业务场景 code → 阿里云 serviceCode（仅此模块可见）
          comment: comment_detection
          avatar: baselineCheck
        extra:                    # 可选：HTTP 超时（毫秒）与异步回调
          connect-timeout-ms: "3000"
          read-timeout-ms: "5000"
          # callback-url: https://biz.example.com/callback/aliyun   # 图片异步回调地址（可选）
          # callback-seed: your-seed          # 回调验签 seed（缺省回退 content.safe.callback-secret）
          # callback-uid: your-main-uid       # 主账号 UID（必配，否则回调验签失败）
```

未映射的场景不会装配该供应商的 operation，由路由自动跳过（数据驱动，铁律 §9.2）。

## 结构

- `AliyunProviderAutoConfiguration`：`@ConditionalOnClass(com.aliyun.green20220302.Client)` + `enabled=true` 才装配；Bean 均可被用户覆盖（`@ConditionalOnMissingBean`）。
- `AliyunAuditOperationProvider`：按 `scenes` 映射生成同步 operation。
- `internal` 包：唯一允许出现 SDK 类型的地方（ArchUnit 强制）——
  `GreenClientInvoker`（可测试接缝）/ `TeaGreenClientInvoker`（SDK 客户端）/
  `AliyunAuditResultNormalizer`（响应归一化）/ `AliyunErrorMapper`（错误映射与消息脱敏）/
  `AliyunLimits`（限额声明）。

## 契约测试

`src/test/resources/contracts/aliyun/` 存放响应样本（pass/review/reject/error-code），
`AliyunAuditResultNormalizerContractTest` 经 `TeaModel.build` 还原为 SDK 模型验证归一化。
禁止真实调用云接口。

## suggestion 归一

riskLevel 三档：`none/low → PASS`，`medium → REVIEW`，`high → REJECT`；
riskLevel 缺失时回退标签判空（空 → PASS，非空 → REJECT）。
