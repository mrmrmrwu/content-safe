# content-safe-model

跨模块共享的领域模型：场景定义、供应商计划、能力声明、任务与事件体系。
仅依赖 `content-safe-api`（不可反向），api/spi 的签名经由这些类型表达。

## 模型一览

| 类型 | 说明 |
| --- | --- |
| `SceneDefinition` | 业务场景定义：code / type / 严重级别默认值 / 供应商绑定 / ext 透传 |
| `ProviderPlan` | 供应商操作计划：provider / product / apiVersion / operation / service / mode / limits / lifecycle |
| `AuditCapabilities` | 能力声明：内容通道（URL/Base64/bytes/objectKey）、批量、同步/异步、OCR、**aigcDetect**、manualReview；`supports(request)` 是审核前单向门控（AIGC 开关开启而无能力位 → 不支持快速失败） |
| `OperationLimits` | 操作级限额（批量大小 / QPS / 超时 / 内容字节），驱动限流与超时治理 |
| `AuditTask` / `AsyncTaskRef` / `TaskStatus` | 异步任务与状态机（PENDING 唯一非终态，`markTerminal` 幂等迁移） |
| `ProviderHealth` / `HealthStatus` / `ProviderLifecycle` | 健康探测结果与供应商生命周期（ACTIVE / DEPRECATED / EOL） |

## 事件体系（`model.event`）

| 事件 | 发布时机 |
| --- | --- |
| `AuditEvent` | 每次审核终态（机审 / 回调 / 人审归一） |
| `RiskHitEvent` | 非 PASS 结果且有命中项 |
| `ReviewEvent` | suggestion 为 REVIEW（机审、人审回调路径） |
| `ProviderDegradedEvent` / `ProviderThrottledEvent` | 熔断失败 / 限流拒绝（failover 前） |
| `CallbackDeadLetterEvent` | 回调验签失败或解析死信 |

事件全部经 `AuditListener` 派发，订阅异常不影响主流程。
