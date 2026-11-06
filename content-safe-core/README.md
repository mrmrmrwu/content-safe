# content-safe-core

内容安全审核内核：路由、仲裁、治理（熔断/限流）、异步任务与回调、事件派发、证据脱敏。
不依赖任何云 SDK，对 Spring 的依赖保持 optional（由 `content-safe-spring-boot-starter` 完成装配）。

## 结构

```
io.github.wrqtec.content.safe.core
├── DefaultContentSafeTemplate     # ContentSafeTemplate 默认实现：audit/auditMulti/submitAsync/query/handleCallback
├── SceneRegistry                  # 场景注册表（内置 6 场景 + scenes.json 合并）
├── router/                        # AuditRouter、PriorityAuditRouter（按 order 升序选主）
├── arbitration/                   # AuditArbitrationPolicy、ArbitrationPolicyRegistry（多场景汇总策略）
├── degrade/                       # CircuitBreaker、SlidingWindowCircuitBreaker、CircuitBreakerRegistry（按 planKey 隔离）
├── limiter/                       # RateLimiter（Permit 归还语义）、TokenBucket/Semaphore/Composite、RateLimiterRegistry
├── listener/                      # CompositeAuditListener（多 listener 聚合派发，订阅异常不影响主流程）
├── callback/                      # CallbackDispatcher（验签 → 解析 → 反查任务 → 幂等迁移 → 事件）
├── task/                          # InMemoryAuditTaskStore（容量/过期可配的默认异步任务存储）
└── security/                      # DefaultEvidenceRedactor（命中证据脱敏，raw 不入日志）
```

## 主流程

```
audit(request)
  └─ SceneRegistry 校验场景（未注册 → SCENE_NOT_CONFIGURED）
  └─ AuditRouter 选出候选 operation 列表（按 order 升序，过滤 lifecycle 与能力）
  └─ 逐候选执行：
       1. RateLimiterRegistry 按 planKey 限流（拒绝 → ProviderThrottledEvent + failover）
       2. CircuitBreakerRegistry 按 planKey 熔断（OPEN → failover；半开探测）
       3. 能力门控（AuditCapabilities.supports，不通过 → UNSUPPORTED_CONTENT）
       4. operation.audit() 调用
       5. EvidenceRedactor 脱敏命中证据
       6. 成功 → AuditEvent/RiskHitEvent/ReviewEvent；失败 → ProviderDegradedEvent + 熔断记账 + failover
  └─ 全部失败 → AllProvidersFailedException（message 携带各 planKey 失败原因）
```

治理原则：**不静默降级**——限流、熔断、failover、回调死信全部发布事件并可经
`ContentSafeMetrics`（starter）转化为 Micrometer 指标。

## 治理组件

| 组件 | 粒度 | 默认实现 | 说明 |
|---|---|---|---|
| 熔断 | planKey（`provider:operation`） | 滑窗（20 次/50%/最小 10 样本），OPEN 30s 后半开探测 3 次 | 失败记账，OPEN 期间直接 failover |
| 限流 | planKey | 令牌桶（QPS）+ 信号量（并发）组合 | 额度取 `ProviderPlan.limits()` 声明；并发许可 try/finally 归还 |
| 仲裁 | scene | `FIRST_NON_PASS` | `auditMulti` 多场景结果汇总策略 |
| 异步任务 | taskId / providerTaskId | 内存存储（默认容量 10 万、24h 过期） | `markTerminal` 为 CAS，重复回调/乱序回调幂等 |
| 回调 | provider | `CallbackDispatcher` | 验签失败进死信（`CallbackDeadLetterEvent`）；按 providerTaskId 反查任务 |

## 扩展方式

core 的全部治理组件都是可替换的接口/registry 注入，生产环境典型替换点：

- `AuditTaskStore`：换成 Redis/DB 实现（异步任务跨实例共享）；
- `RateLimiterRegistry` 工厂：对接配置中心动态额度；
- `AuditListener`：接审计日志、告警、数据埋点。

Spring Boot 使用方无需直接构造本模块，见 [content-safe-spring-boot-starter](../content-safe-spring-boot-starter)
与[接入指南](../docs/content-safe-starter-guide.md)。
