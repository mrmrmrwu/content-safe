# 供应商能力矩阵（Provider Feature Matrix）

记录各 provider 模块已实现的能力与限额。新增供应商或新增能力时必须同步本表（CLAUDE.md §6 checklist）。

> 标注口径：✅ 已实现；⏳ 已预留抽象、待后续任务；❌ 供应商不支持。
> 信息来源：阿里云内容安全 2.0（增强版）官方文档（help.aliyun.com，查阅日期 2026-11-06）。

## aliyun（content-safe-provider-aliyun，T-013）

SDK：`com.aliyun:green2022032` → 实际坐标 `com.aliyun:green20220302:3.6.6`（optional 依赖）。

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| 文本同步审核（TextModeration） | ✅ | service 由 `scenes.<sceneCode>` 映射 |
| 图片同步审核（ImageModeration） | ✅ | 仅公网 URL（`ContentRefType.URL`） |
| base64 / bytes 内容 | ❌ | 2.0 同步接口仅 URL；能力校验直接拒绝 |
| 批量审核 | ❌ | `maxBatchSize=1` |
| 异步查询 / 回调 | ⏳ | 待 T-019（ImageAsyncScan 系列） |
| OCR | ⏳ | 2.0 service 能力，未接入 |
| AIGC 检测 | ⏳ | service `ai_art_detection` 预留 |
| 自定义名单 | ⏳ | 控制台配置，SDK 侧无感 |
| 回调验签（CallbackVerifier） | ⏳ | 待 T-019 |
| suggestion 映射 | ✅ | riskLevel 三档：none/low→PASS，medium→REVIEW，high→REJECT；缺失时回退标签判空 |

### 限额（`AliyunLimits`，声明值，以官方文档为准）

| 项 | 文本 | 图片 |
| --- | --- | --- |
| 单条大小 | 600 字（实现按 2400 字节保守折算） | 20 MB |
| 像素 | — | 单边 ≤16384、总像素 ≤2.5 亿 |
| 默认 QPS | 100 | 50 |
| URL 要求 | — | 公网可达，3 秒内可下载 |

### 已知服务码（serviceCode 示例，非穷举）

`nickname_detection`、`comment_detection`、`chat_detection`、`pgc_detection`、`ad_compliance_detection`、`ai_art_detection`、图片 `baselineCheck` 等。业务侧只配场景 code → serviceCode 映射，云厂商标识不进 api/core（铁律 §5.3）。

## tencent / huawei / volcengine / 数美

⏳ 未实现（规划见 docs/content-safe-starter-design.md §8）。
