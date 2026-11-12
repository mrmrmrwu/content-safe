# ContentSafe

**Content moderation, so easy.**

[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![CI](https://github.com/mrmrmrwu/content-safe/actions/workflows/ci.yml/badge.svg)](https://github.com/mrmrmrwu/content-safe/actions/workflows/ci.yml)
[![JDK](https://img.shields.io/badge/JDK-17%2B-orange.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)]()

帮助文档：见 `docs/` 目录（接入指南 / 供应商能力矩阵 / 错误码表 / 发布与兼容政策）

ContentSafe，如你所见，它仅仅是一个 **内容安全审核** 的 **工具类库（Spring Boot Starter）**，它可以让我们脱离繁琐的各家云厂商内容安全 SDK 与场景 ID 细节，让文本、图片和视频审核变得 **So easy!**

ContentSafe 计划集成阿里云、腾讯云、百度智能云、火山引擎、数美等主流内容安全供应商。更多请参考 [已支持的供应商](#已支持的供应商)。

## 有哪些特点？

1. **全**：统一封装多家云供应商的文本/图片/视频内容安全能力，业务方只引依赖、只写配置，仍然还在持续扩展中！
2. **简**：API 就是奔着最简单去设计的（见后面 `快速开始`），业务代码只传场景（如 `nickname`、`avatar`），云厂商的场景 ID 交给配置，尽量让您用起来没有障碍感！

## 有哪些功能？

- 统一审核 API：一次接入，多云可用，业务代码不感知供应商差异。
- 场景解耦：内置常用业务场景，支持通过 yml / Java / SPI 自定义扩展。
- 能力声明：审核前按供应商能力（URL/Base64/批量/OCR/AIGC...）过滤，不支持快速失败。
- 多云路由：主备切换、成本优先、多结果仲裁（如 `FIRST_NON_PASS`）。
- 异步任务：图片/视频异步提交/查询/回调（按供应商能力位），回调验签、幂等、死信处理；操作自声明建议轮询参数，单参 `submitAndWait` 开箱即用。
- 治理内建：健康探测、熔断限流（operation 级）、统一错误码、审计事件、Micrometer 指标。
- AIGC 识别：请求期开关（`AuditOptions.withAigcDetect`），按能力位单向门控，不支持的供应商快速失败而非静默降级。
- 人审闭环：机审 REVIEW 经 `ReviewEvent` 出口、`resolveReview` 终态归一（source=MANUAL、finalResult=true）、`ReviewResultSink` 回写，异步任务幂等迁移。
- 依赖干净：默认零 HTTP/JSON 传递依赖（JDK HttpClient + SPI 可替换），云 SDK 全部 optional。
- 更多... 见 [供应商能力矩阵](docs/provider-feature-matrix.md)

## 快速开始

### 环境要求

- JDK 17+（本项目基于 JDK 17 语法与 API 构建，低版本无法编译运行）
- Spring Boot 3.3.x（starter 按 Boot 3 自动装配规范构建，不兼容 Boot 2.x）
- 可选：Servlet Web 环境（仅启用内置回调接收端点 `content.safe.callback.expose=true` 时需要）

### 引入依赖

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.wrqtec</groupId>
            <artifactId>content-safe-bom</artifactId>
            <version>{latest-version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.wrqtec</groupId>
        <artifactId>content-safe-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.wrqtec</groupId>
        <artifactId>content-safe-provider-aliyun</artifactId>
    </dependency>
</dependencies>
```

> **latest-version** 以[发布页](https://github.com/mrmrmrwu/content-safe/releases)为准。
>
> 注意：快照版本是功能的尝鲜，并不保证稳定性，请勿在生产环境中使用。

#### 从 GitHub Packages 拉取（SNAPSHOT / 预览版）

当前版本托管在 GitHub Packages。注意 GitHub 的特性：**即使仓库公开，Maven 拉取也必须携带具有 `read:packages` 权限的 PAT**。

1. 在工程 `pom.xml` 中声明仓库：

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/mrmrmrwu/content-safe</url>
    </repository>
</repositories>
```

2. 在 `~/.m2/settings.xml` 中配置拉取凭据（PAT 需勾选 `read:packages`，在 GitHub → Settings → Developer settings → Personal access tokens 创建）：

```xml
<settings>
    <servers>
        <server>
            <id>github</id>
            <username>你的 GitHub 用户名</username>
            <password>你的 read:packages PAT</password>
        </server>
    </servers>
</settings>
```

Gradle（Kotlin DSL）等价配置：

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/mrmrmrwu/content-safe")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
    mavenCentral()
}
```

> 正式版后续发布到 Maven Central 后可省略以上配置，直接从 Central 拉取。

### 配置

```yaml
content.safe:
  providers:
    aliyun:
      enabled: true
      region-id: cn-hangzhou
      ak: ${CONTENT_SAFE_ALIYUN_AK}      # 也可不配，走环境变量 CONTENT_SAFE_ALIYUN_AK / CONTENT_SAFE_ALIYUN_SK
      sk: ${CONTENT_SAFE_ALIYUN_SK}
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

#### 视频异步审核

```java
// 提交视频审核任务（1.2.0 起支持，入参为视频 URL）
String taskId = contentSafeTemplate.video("short_video", videoUrl);

// 手动轮询
AuditResult result = contentSafeTemplate.query(taskId);

// 或阻塞等待终态：自动采用操作声明的建议轮询参数（如 30s 间隔 / 30 分钟超时）
AuditResult finalResult = contentSafeTemplate.submitAndWait(
    AuditRequest.video("short_video", ContentRef.url(videoUrl))
);
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
| 阿里云内容安全 | ✅ | ✅ | ✅ 图片异步轮询+回调；视频异步轮询 | 已支持 |
| 腾讯云内容安全 TMS/IMS | ✅ | ✅ | ✅ 图片异步回调推送；视频异步轮询+回调 | 已支持 |
| 百度智能云内容审核 | ✅ | ✅ | ✅ 视频异步轮询+回调（图片仅同步） | 已支持 |
| 火山引擎内容安全 | ✅ | ✅ | ✅ 视频异步轮询（图片仅同步） | 已支持 |
| 数美科技 | ✅ | ✅ | ✅ 图片异步轮询+回调；视频异步轮询+回调 | 已支持 |
| 华为云 Moderation | - | - | - | 生命周期风险，暂不默认支持 |

> 同步审核承诺 **文本 + 图片**；**1.2.0 起支持视频异步审核**（轮询 / 回调，见上表）；音频、文件、直播等能力预留抽象。

## 扩展

- 自定义供应商：实现 `AuditOperationProvider`（SPI），详见 [接入指南](docs/content-safe-starter-guide.md)。
- 自定义路由/仲裁/凭据/任务存储/回调验签：覆盖对应 SPI Bean 即可，所有默认 Bean 均支持 `@ConditionalOnMissingBean` 替换。

## 其他

- [接入指南](docs/content-safe-starter-guide.md)
- [供应商能力矩阵](docs/provider-feature-matrix.md)
- [错误码表](docs/error-code-table.md)
- [发布指南](docs/release-guide.md) / [兼容政策](docs/compatibility-policy.md)
- [编码规范](CLAUDE.md)

## 贡献

欢迎提交 Issue 和 Pull Request。提交前请阅读 [贡献指南](CONTRIBUTING.md)，并确保 `./mvnw clean verify` 通过。安全漏洞请勿公开 Issue，参见 [安全策略](SECURITY.md)。

## 鸣谢

- 本项目 API 设计理念参考 [JustAuth](https://gitee.com/yadong.zhang/JustAuth)。

## License

[Apache-2.0](LICENSE)
