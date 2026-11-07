# content-safe-provider-volcengine

火山引擎内容安全（Volcengine Content Security）接入：文本风险检测
（TextRisk，Version `2022-01-26`）与图片风险检测（ImageContentRiskV2，Version
`2021-11-29`），走官方 `volcengine-java-sdk-contentsecurity`（optional，隔离在
`internal` 包，ArchUnit 强制不外泄）。

签名参数：host `riskcontrol.volcengineapi.com`，签名服务 `BusinessSecurity`，
区域 `cn-north-1`；请求信封 `{AppId, Service, Parameters(JSON 字符串)}`。

## 能力

| 能力 | 状态 |
| --- | --- |
| 文本同步检测（TextRisk） | ✅ 经场景 ext 透传参数 |
| 图片同步检测（ImageContentRiskV2） | ✅ URL / base64 / bytes 三通道 |
| 决策归一 | ✅ `pass→PASS` / `suspect→REVIEW` / `reject→REJECT`，缺失/未知保守回退 REVIEW |
| 命中证据 | ✅ 命中词进 `hit.raw.matchedWords`（文本）/ 命中详情进 `hit.raw`（图片） |
| OCR / 截帧 URL | ❌ 判用户内容原文，整体丢弃不透传 |
| 批量 / 异步 / 回调 | ❌ 同步模型 |

## 配置

```yaml
content:
  safe:
    providers:
      volcengine:
        enabled: true
        ak: xxx
        sk: xxx                      # 缺省回退环境变量 CONTENT_SAFE_VOLCENGINE_AK / _SK / _STSTOKEN
        # session-token: xxx         # 可选：STS 临时凭证
        region: cn-north-1           # 可选：默认 cn-north-1
        scenes:
          comment: "textrisk_service"    # 业务场景 code → 火山 Service 码
        extra:
          app-id: "123456"               # 必填：火山控制台 AppId，缺失启动即失败
          # parameters-text-key: content     # 可选：文本内容在 Parameters 中的键名，默认 content
          # parameters-image-key: image      # 可选：图片内容键名，默认 image
```

## 注意

- 官方 SDK 的 `ContentSecurityServiceImpl` 为单例持有凭证，多凭证场景请隔离部署
  （`internal/VolcengineSdkClientInvoker` 已在 javadoc 说明）。
- SDK 不暴露连接/读超时设置，超时由 `OperationLimits`（5s）驱动调用层治理。

来源：官方 volc-sdk-java（1.0.290）反编译 + volc-sdk-python `ContentSecurityService.py`
交叉验证，查阅日期 2026-11-06，详见 [provider-feature-matrix.md](../docs/provider-feature-matrix.md)。
