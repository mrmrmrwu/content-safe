# content-safe-samples

可运行样例聚合（均为 Spring Boot 应用，mock 供应商，不产生云费用）：

| 模块 | 演示内容 |
| --- | --- |
| [content-safe-samples-basic](content-safe-samples-basic/README.md) | 最小接入：内置/自定义场景审核、供应商 SPI 接入、AIGC 开关、人审闭环（REVIEW → `resolveReview`） |
| [content-safe-samples-routing-failover](content-safe-samples-routing-failover/README.md) | 主备路由与故障转移：order 选主、异常 failover、`ProviderDegradedEvent` 可观测 |
| [content-safe-samples-async-callback](content-safe-samples-async-callback/README.md) | 异步图片审核：submitAsync/query、回调验签、幂等受理、死信事件 |

## 运行

```bash
# 任一样例（在仓库根目录）
./mvnw -pl samples/content-safe-samples-basic -am spring-boot:run
```

启动即由 `DemoRunner` 自动执行演示调用，日志输出每次审核的
suggestion / provider / hits；人审闭环样例还会打印 `resolveReview` 归一后的
`source=MANUAL, finalResult=true` 终态。
