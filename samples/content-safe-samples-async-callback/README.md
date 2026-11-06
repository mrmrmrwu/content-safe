# content-safe-samples-async-callback

异步图片审核样例：注册一个 mock 异步供应商，演示：

- `submitAsync` 提交图片审核任务（任务落 `AuditTaskStore`，状态 PENDING）
- `query` 轮询中间态（`finalResult=false`，任务保持 PENDING）
- 回调验签（`CallbackVerifier` + `content.safe.callback-secret`）后推送终态结果，任务转终态
- 重复回调幂等受理（不产生重复 `AuditEvent`）；验签失败进死信（`CallbackDeadLetterEvent`）

## 运行

```bash
mvn -pl samples/content-safe-samples-async-callback -am spring-boot:run -pl samples/content-safe-samples-async-callback
```

或先 `mvn clean verify` 再运行 `SampleAsyncCallbackApplication` 主类。期望日志：

```
submitAsync -> taskId=xxxx
query -> pending: finalResult=false (task stays PENDING until callback)
event: AUDIT
callback -> accepted=true
query -> terminal: suggestion=PASS source=ASYNC_CALLBACK
duplicate callback -> accepted=true (idempotent, audit events=1)
```

> mock 规则：providerTaskId = `fake-task-<dataId>`；回调体格式 `taskId=<providerTaskId>&suggestion=<PASS|REJECT>`；
> 验签为 header `x-callback-token` 等于 `content.safe.callback-secret`（样例配置 `demo-callback-secret`）。
> 无任何真实云调用，无需配置密钥。
