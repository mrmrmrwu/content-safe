# content-safe-samples-routing-failover

路由与故障转移样例：注册两个 mock 供应商，演示：

- `PriorityAuditRouter` 按 `AuditOperation.order()`（数值小优先）选择主供应商 `fake-primary`
- 主供应商调用抛异常时，core 自动 failover 到备用供应商 `fake-secondary` 并返回其结果
- failover 过程发布 `ProviderDegradedEvent`，`AuditEventRecorder`（`AuditListener` Bean）实时可观测

## 运行

```bash
mvn -pl samples/content-safe-samples-routing-failover -am spring-boot:run -pl samples/content-safe-samples-routing-failover
```

或先 `mvn clean verify` 再运行 `SampleRoutingFailoverApplication` 主类。期望日志：

```
event: PROVIDER_DEGRADED provider=fake-primary operation=textScan reason=ProviderCallException
comment normal -> pass=true, suggestion=PASS, provider=fake-secondary, hits=0
comment risky  -> pass=false, suggestion=REJECT, provider=fake-secondary, hits=1
failover events: 2 (primary degraded, requests served by fake-secondary)
```

> mock 规则：`fake-primary`（order=0）每次调用都抛 `ProviderCallException` 模拟故障；
> `fake-secondary`（order=10）正常工作，文本包含"违禁"即判 REJECT。
> 无任何真实云调用，无需配置密钥。
