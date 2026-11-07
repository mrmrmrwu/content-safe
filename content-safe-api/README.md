# content-safe-api

业务方唯一直接依赖的 API 模块：`ContentSafeTemplate` 门面、审核请求/结果模型、
异常体系与稳定错误码。不依赖本工程任何其他模块（依赖方向的最顶层），
是 [兼容性政策](../docs/compatibility-policy.md)（japicmp 二进制兼容检查）的守护对象。

## 关键类型

| 类型 | 说明 |
| --- | --- |
| `ContentSafeTemplate` | 统一门面：`audit` / `auditMulti` / `submitAsync` / `query` / `handleCallback` / `resolveReview`，附 `text()` / `image()` 便捷方法 |
| `AuditRequest` | 审核请求：scene + type + `ContentRef` + 可选 dataId/context/options/passThrough |
| `AuditResult` | 归一化终态结果：`pass()` / suggestion / finalResult / source / hits / provider 等 |
| `ContentRef` | 内容载体：URL / BASE64 / BYTES / TEXT 四通道 |
| `AuditOptions` | 请求期选项：AIGC 识别开关（`withAigcDetect`）等 |
| `ReviewResolution` / `ReviewDecision` | 人审终态回写入参（APPROVE / REJECT），配合 `resolveReview` |
| `RiskHit` | 命中项：标签路径、置信分、来源、证据（原始证据只进 `raw`，不进日志） |
| `Scenes` | 内置业务场景常量（COMMENT / NICKNAME / AVATAR 等 6 个） |
| `exception.*` + `ErrorCodes` | `ContentSafeException` 体系与稳定错误码，业务侧按码处理（[错误码表](../docs/error-codes.md)） |

## 快速使用

```java
AuditResult result = template.text(Scenes.COMMENT, "用户评论文本");
if (!result.pass()) { /* 按 result.suggestion() 处理 */ }
```

完整配置与场景说明见 [接入指南](../docs/content-safe-starter-guide.md)。
