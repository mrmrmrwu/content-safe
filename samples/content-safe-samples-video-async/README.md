# content-safe-samples-video-async

视频异步审核样例：`video()` 提交任务 → 轮询 `query()` 到终态，或 `submitAndWait()` 一步阻塞等待。
mock 供应商 `FakeVideoAsyncProvider` 第 1 次查询返回处理中、第 2 次起返回终态 REJECT（帧命中 + 语音命中各一条）。

## 运行

```bash
# 在仓库根目录
./mvnw -pl samples/content-safe-samples-video-async -am spring-boot:run
```

启动即由 `DemoRunner` 自动演示两种用法并打印事件计数。

## 演示要点

| 要点 | 说明 |
| --- | --- |
| 提交 | `template.video(scene, url)` 返回 `taskId`（委托 `submitAsync`）；视频入参只承诺 URL |
| 轮询 | `template.query(taskId)`：处理中返回 `finalResult=false` 且 `suggestion=null`，终态返回 `finalResult=true` |
| 阻塞等待 | `template.submitAndWait(request, pollInterval, timeout)` 内部复用同一任务链路 |
| 事件 | 轮询到终态时发布 `AuditEvent` / `RiskHitEvent`，由 `AuditEventRecorder` 收集 |
| 脱敏 | hit.raw 只放技术元数据（offset/sliceId/startTime 等），不含帧截图 URL、语音转写等用户内容 |

## 切换真实供应商

以阿里云为例：引入 `content-safe-provider-aliyun` 依赖，停用本样例的
`FakeVideoAsyncProvider`（加 `@Profile("fake")`），按 `application.yml` 末尾注释配置
provider 与轮询节奏（生产建议 30s 轮询；阿里视频结果保留 24 小时）。
凭证走环境变量 `CONTENT_SAFE_ALIYUN_AK` / `CONTENT_SAFE_ALIYUN_SK`。
