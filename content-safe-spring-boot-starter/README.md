# content-safe-spring-boot-starter

Spring Boot 3.x 自动装配：业务方引依赖 + 写配置即可获得 `ContentSafeTemplate`。
不依赖任何 provider 模块——供应商按需叠加（云 SDK 全部 optional）。

## 自动装配清单

| 组件 | 说明 |
| --- | --- |
| `ContentSafeAutoConfiguration` | 装配 `ContentSafeTemplate`（含人审回写出口 0..1）、场景注册、路由/熔断/限流注册表、凭证解析 |
| `JsonSceneDefinitionProvider` | `classpath:content-safe/scenes.json` 自定义场景导入（schema-version 校验） |
| `ContentSafeConfigValidator` | 启动期校验 `content.safe.schema-version`；凭证缺失在 provider 装配链快速失败 |
| `ContentSafeFailureAnalyzer` | 启动失败人性化诊断（缺哪项配置、怎么改） |
| `ContentSafeActuatorAutoConfiguration` | Actuator `health` 融合供应商健康 + `ContentSafeMetrics`（Micrometer 指标） |

## 配置骨架

```yaml
content:
  safe:
    schema-version: 1
    callback-secret: xxx                    # 异步回调验签
    defaults:
      arbitration: FIRST_NON_PASS
      failover: true
      circuit-breaker: { window-size: 20, failure-rate-threshold: 0.5 }
      rate-limiter: { enabled: true }
    scenes:
      comment: { type: TEXT }   # 场景路由由各 provider 的 scenes 映射决定，引入谁就多谁参与候选
    providers:
      tencent:
        enabled: true
        secret-id: xxx
        secret-key: xxx
        scenes: { comment: "bizType-x" }
```

- 凭证解析链：显式配置 → 自定义 `CredentialsProvider` → 环境变量
  （如 `CONTENT_SAFE_SHUMEI_AK`），密钥不落日志/异常。
- 人审闭环：注册 `ReviewResultSink` Bean 即自动装配进模板；
  监听 `ReviewEvent` 后调 `template.resolveReview(...)` 归一终态。
- AIGC：请求期 `AuditOptions.defaults().withAigcDetect(true)`，能力位单向门控。

完整配置项、场景 schema、诊断示例见 [接入指南](../docs/content-safe-starter-guide.md)。
