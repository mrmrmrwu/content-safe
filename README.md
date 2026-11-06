# ContentSafe

**Content moderation, so easy.**

[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![JDK](https://img.shields.io/badge/JDK-17%2B-orange.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)]()

帮助文档：见 `docs/` 目录（设计文档 / 使用指南持续完善中）

ContentSafe，如你所见，它仅仅是一个 **内容安全审核** 的 **工具类库（Spring Boot Starter）**，它可以让我们脱离繁琐的各家云厂商内容安全 SDK 与场景 ID 细节，让文本和图片审核变得 **So easy!**

ContentSafe 计划集成阿里云、腾讯云、百度智能云、火山引擎、数美等主流内容安全供应商。更多请参考 [已支持的供应商](#已支持的供应商)。

## 有哪些特点？

1. **全**：统一封装多家云供应商的文本/图片内容安全能力，业务方只引依赖、只写配置，仍然还在持续扩展中（[开发计划](docs/content-safe-starter-tasks.md)）！
2. **简**：API 就是奔着最简单去设计的（见后面 `快速开始`），业务代码只传场景（如 `nickname`、`avatar`），云厂商的场景 ID 交给配置，尽量让您用起来没有障碍感！

## 有哪些功能？

- 统一审核 API：一次接入，多云可用，业务代码不感知供应商差异。
- 场景解耦：内置常用业务场景，支持通过 yml / Java / SPI 自定义扩展。
- 能力声明：审核前按供应商能力（URL/Base64/批量/OCR/AIGC...）过滤，不支持快速失败。
- 多云路由：主备切换、成本优先、多结果仲裁（如 `FIRST_NON_PASS`）。
- 异步任务：图片异步提交/查询/回调，回调验签、幂等、死信处理。
- 治理内建：健康探测、熔断限流（operation 级）、统一错误码、审计事件、Micrometer 指标。
- 依赖干净：默认零 HTTP/JSON 传递依赖（JDK HttpClient + SPI 可替换），云 SDK 全部 optional。
- 更多... 见 [设计文档](docs/content-safe-starter-design.md)

## 快速开始

### 引入依赖

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>your.group</groupId>
            <artifactId>content-safe-bom</artifactId>
            <version>{latest-version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>your.group</groupId>
        <artifactId>content-safe-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>your.group</groupId>
        <artifactId>content-safe-provider-aliyun</artifactId>
    </dependency>
</dependencies>
```

> **latest-version** 以发布页为准；坐标 `your.group` 为占位，正式发布前确定。
>
> 注意：快照版本是功能的尝鲜，并不保证稳定性，请勿在生产环境中使用。

### 配置

```yaml
content.safe:
  providers:
    aliyun:
      enabled: true
      type: aliyun
      region: cn-hangzhou
      credentials:
        type: static
        access-key-id: ${ALIYUN_AK}
        access-key-secret: ${ALIYUN_SK}
      scenes:
        nickname: nickname_detection
        avatar: baselineCheck_face
```

### 调用 API

#### 普通方式

```java
// 文本审核
AuditResult result = contentSafeTemplate.audit(
    AuditRequest.text("nickname", nickname)
);

// 图片审核（URL）
AuditResult avatarResult = contentSafeTemplate.audit(
    AuditRequest.image("avatar", ContentRef.url(avatarUrl))
);
// PASS -> 放行；REVIEW -> 人工复审；REJECT -> 拦截；ERROR -> 按 fail-safe 策略处理
```

#### Builder 方式

```java
AuditResult result = contentSafeTemplate.audit(
    AuditRequest.builder()
        .scene("avatar")
        .type(ContentType.IMAGE)
        .content(ContentRef.url(avatarUrl))
        .dataId(userId)
        .context(AuditContext.ofUser(userId, deviceId))
        .options(AuditOptions.enableOcr(true))
        .build()
);
```

#### 异步图片审核

```java
String taskId = contentSafeTemplate.submitAsync(
    AuditRequest.image("avatar", ContentRef.url(avatarUrl))
);
AuditResult result = contentSafeTemplate.query(taskId);
```

#### 自定义场景（无需改代码）

```yaml
content.safe:
  scenes:
    resume_title:
      type: text
      default-severity: medium
  providers:
    aliyun:
      scenes:
        resume_title: resume_title_detection
```

```java
contentSafeTemplate.audit(AuditRequest.text("resume_title", title));
```

## 已支持的供应商

| 供应商 | 文本审核 | 图片审核 | 异步/回调 | 状态 |
|---|---|---|---|---|
| 阿里云内容安全 | ✅ 规划中 | ✅ 规划中 | ✅ 规划中 | M2 |
| 腾讯云内容安全 TMS/IMS | ✅ 规划中 | ✅ 规划中 | ✅ 规划中 | M3 |
| 百度智能云内容审核 | ✅ 规划中 | ✅ 规划中 | 部分 | M3 |
| 火山引擎内容安全 | ✅ 规划中 | ✅ 规划中 | ✅ 规划中 | M3 |
| 数美科技 | ✅ 规划中 | ✅ 规划中 | ✅ 规划中 | M3 |
| 华为云 Moderation | - | - | - | 生命周期风险，暂不默认支持 |

> v1 只承诺 **文本 + 图片** 内容安全；音频、视频、文件、直播等能力预留抽象，见 [需求文档](docs/content-safe-starter-prd.md)。

## 扩展

- 自定义供应商：实现 `AuditOperationProvider`（SPI），详见 [设计文档](docs/content-safe-starter-design.md)。
- 自定义路由/仲裁/凭据/任务存储/回调验签：覆盖对应 SPI Bean 即可，所有默认 Bean 均支持 `@ConditionalOnMissingBean` 替换。

## 其他

- [需求文档](docs/content-safe-starter-prd.md)
- [设计文档](docs/content-safe-starter-design.md)
- [任务与计划](docs/content-safe-starter-tasks.md)
- [编码规范](CLAUDE.md)

## 贡献

欢迎提交 Issue 和 Pull Request。提交前请阅读 [编码规范](CLAUDE.md)，并确保 `./mvnw clean verify` 通过。

## 鸣谢

- 本项目 API 设计理念参考 [JustAuth](https://gitee.com/yadong.zhang/JustAuth)。

## License

[Apache-2.0](LICENSE)
