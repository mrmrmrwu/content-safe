# content-safe 错误码表

对外只抛 `ContentSafeException` 子类，每个异常携带稳定 `code`。错误码对外承诺向后兼容：
**新增错误码必须先更新本表**；修改既有错误码语义或抛出条件属破坏式变更，走 major 版本流程。

## 错误码总表

| code | 异常类 | 默认 retryable | 触发条件 |
|---|---|---|---|
| `CONFIG_INVALID` | `ConfigInvalidException` | 否 | 启动期/装配期配置非法：凭证缺失、scene 未映射却启用 provider、endpoint 无法推导等 |
| `SCENE_NOT_CONFIGURED` | `SceneNotConfiguredException` | 否 | 请求的 scene code 未在 `SceneRegistry` 注册（内置场景 + `scenes.json` 都没有） |
| `UNSUPPORTED_CONTENT` | `UnsupportedContentException` | 否 | 内容形态不被任何候选 operation 支持（如 URL 内容传给不支持 url 能力的 provider） |
| `UNSUPPORTED_FEATURE` | `UnsupportedFeatureException` | 否 | 调用了 operation 未实现的能力（batch/async/query/callback 默认实现） |
| `INVALID_REQUEST` | `InvalidRequestException` | 否 | 请求本身非法：未知 taskId、回调未装配（`callback dispatcher not configured`）等 |
| `PROVIDER_NOT_AVAILABLE` | `ProviderNotAvailableException` | 是 | 候选 provider 被熔断（circuit open）或本地限流拒绝后无可用候选 |
| `PROVIDER_CALL_ERROR` | `ProviderCallException` | 是 | provider 调用失败：SDK 异常、网络错误、厂商返回非 200 错误码；`submitAndWait` 轮询超时/中断 |
| `ALL_PROVIDER_FAILED` | `AllProvidersFailedException` | 是 | 所有候选 provider 均失败，message 携带各 planKey 的失败原因 |
| `CALLBACK_VERIFY_FAILED` | `CallbackVerifyException` | 否 | 回调验签失败（同时发布 `CallbackDeadLetterEvent` 进死信） |

## 使用约定

- **启动诊断**：启动/装配期抛出的 `ContentSafeException` 由 starter 内置 `ContentSafeFailureAnalyzer`
  （`META-INF/spring.factories` 注册）翻译为 Spring Boot `APPLICATION FAILED TO START` 可读报告（错误码 + 出错 Bean + 原因 + 修复动作），见接入指南 §7。
- **重试语义**：`retryable` 为是的错误（`PROVIDER_NOT_AVAILABLE` / `PROVIDER_CALL_ERROR` / `ALL_PROVIDER_FAILED`）core 会自动 failover 到下一候选 provider；只有所有候选都失败才抛给调用方，此时可按 `retryable` 决定业务侧重试。
- **业务侧分流**：按 `e.getCode()` 分流处理（示例见接入指南 §10），不要按异常类型逐一 catch。
- **敏感信息**：异常 message 不得包含 accessKey/secret/签名 URL/用户内容原文；provider 侧统一经 `ErrorMapper` 脱敏（长 token 掩码、截断）。
- **观测**：异常不直接落日志即吞，core 路径会同步发布事件（`ProviderDegradedEvent` / `ProviderThrottledEvent` / `CallbackDeadLetterEvent`），指标见 `ContentSafeMetrics`。
