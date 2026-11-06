# content-safe-provider-baidu

百度智能云内容审核接入（T-015）：文本 / 图像同步审核（内容审核平台 v2）。

**无官方现代 SDK**（`com.baidu.aip:java-sdk` 停留在 4.16.18 / ~2022），本模块内置
自研 HTTP 客户端（JDK `HttpClient` + Gson，均为 optional 依赖，不进入使用方编译期传递依赖）。

## 能力

| 能力 | 状态 |
| --- | --- |
| 文本同步审核（text_censor v2） | ✅ ≤20000 字节（UTF-8） |
| 图像同步审核（img_censor v2） | ✅ URL / base64 / bytes 三通道，base64 后 ≤4MB |
| 策略中心（strategyId） | ✅ `scenes` 映射，空字符串走默认策略 |
| access_token 缓存与自动刷新 | ✅ 30 天有效期，提前 5 分钟刷新；110/111 自动重取重试一次 |
| 批量 / 异步 / 回调 | ❌ v2 为同步模型 |

suggestion 归一：`conclusionType` 1→PASS、2→REJECT、3→REVIEW、4→ERROR
（审核失败是业务结论而非调用失败，`ok` 保持 true）。

## 配置

```yaml
content:
  safe:
    providers:
      baidu:
        enabled: true
        api-key: xxx              # 缺省回退环境变量 CONTENT_SAFE_BAIDU_AK
        secret-key: xxx           # 缺省回退 CONTENT_SAFE_BAIDU_SK
        scenes:
          comment: "101"          # 业务场景 code → 策略中心 strategyId（数字）
          avatar: ""              # 空字符串 = 不传 strategyId，走账号默认策略
        extra:
          connectTimeoutMs: "3000"
          readTimeoutMs: "10000"
          # baseUrl: https://aip.baidubce.com   # 可选：覆盖默认 API 地址
```

未映射的场景不会注册操作（与其他 provider 一致，由路由跳过）。

## 安全约束

- 命中关键词（`hits[].words`）进入 `RiskHit.evidence`，由 core `EvidenceRedactor` 统一脱敏；
- `AuditResult.raw` 只放 `{log_id}`，不回传任何用户内容或策略详情；
- 日志与异常消息不含 apiKey/secretKey/access_token（长 token 一律掩码）。

## 测试

- 契约样本：`src/test/resources/contracts/baidu/`（pass / review / reject / token 过期）；
- 单元测试：normalizer 映射、错误映射（含脱敏）、两个 Operation 的参数装配与限制校验；
- 装配测试：`ApplicationContextRunner`（默认不装配、缺凭证 fail-fast、用户 Bean 覆盖）；
- ArchUnit：core/starter 隔离；Gson 仅限 `internal` 包。

## 参考

- [内容审核平台-文本审核](https://cloud.baidu.com/doc/ANTIPORN/s/tk3h6xdji)
- [内容审核平台-图像审核](https://ai.baidu.com/ai-doc/ANTIPORN/tk3h6xdji)

（查阅日期：2026-11-06）
