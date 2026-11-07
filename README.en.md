# ContentSafe

**Content moderation, so easy.**

[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![JDK](https://img.shields.io/badge/JDK-17%2B-orange.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)]()

> 中文文档：[README.md](README.md)（本项目详细文档为中文，本文件是其英文概览。）

ContentSafe is a **content moderation toolkit** packaged as a Spring Boot Starter. It wraps the text/image moderation APIs of major cloud providers behind one unified API, so business code never touches vendor SDKs or scene-ID details again — just add a dependency, write a bit of YAML, and call one method.

## Why ContentSafe?

1. **Unified**: one API across multiple cloud moderation providers (text + image). Business code stays provider-agnostic.
2. **Simple**: the API is designed to be minimal — pass a scene name (e.g. `nickname`, `avatar`), and the provider-specific scene IDs live in configuration, not in your code.

## Features

- **Unified audit API**: one integration, multi-cloud routing; business code is unaware of provider differences.
- **Scene decoupling**: built-in common scenes; extensible via YAML / Java / SPI without code changes.
- **Capability declaration**: providers declare what they support (URL/Base64/batch/OCR/AIGC...); unsupported combinations fail fast instead of silently degrading.
- **Multi-cloud routing**: primary/backup failover, cost-first routing, multi-result arbitration (e.g. `FIRST_NON_PASS`).
- **Governance built in**: health probing, circuit breaking & rate limiting (operation-level), unified error codes, audit events, Micrometer metrics.
- **AIGC detection**: request-time switch (`AuditOptions.withAigcDetect`) gated by declared capabilities.
- **Manual-review loop**: machine `REVIEW` results flow out via `ReviewEvent`, get finalized through `resolveReview` (source=MANUAL, finalResult=true), and are written back through `ReviewResultSink`.
- **Async & callbacks**: image async submit/query/callback (Aliyun/Tencent/Shumei) with signature verification, idempotency and dead-letter handling.
- **Clean dependencies**: zero transitive HTTP/JSON dependencies by default (JDK HttpClient + SPI replaceable); all cloud SDKs are optional.

## Quick Start

### Add dependencies

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

> `{latest-version}`: see the [releases page](https://github.com/mrmrmrwu/content-safe/releases). SNAPSHOT builds are published to GitHub Packages for preview only — do not use them in production.

#### Pulling from GitHub Packages (SNAPSHOT / preview)

Versions are currently hosted on GitHub Packages. Note a GitHub quirk: **even for a public repository, Maven requires a token with the `read:packages` scope to resolve packages**.

1. Declare the repository in your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/mrmrmrwu/content-safe</url>
    </repository>
</repositories>
```

2. Configure credentials in `~/.m2/settings.xml` (create the PAT under GitHub → Settings → Developer settings → Personal access tokens, with `read:packages` checked):

```xml
<settings>
    <servers>
        <server>
            <id>github</id>
            <username>your-github-username</username>
            <password>your-read-packages-pat</password>
        </server>
    </servers>
</settings>
```

Gradle (Kotlin DSL) equivalent:

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

> Once releases are published to Maven Central, this configuration can be dropped and dependencies resolved from Central directly.

### Configure

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

### Call the API

```java
// Text moderation
AuditResult result = contentSafeTemplate.audit(
    AuditRequest.text("nickname", nickname)
);

// Image moderation (by URL)
AuditResult avatarResult = contentSafeTemplate.audit(
    AuditRequest.image("avatar", ContentRef.url(avatarUrl))
);
// PASS -> allow; REVIEW -> manual review; REJECT -> block; ERROR -> apply your fail-safe policy

// Builder style with full context
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

// Async image moderation
String taskId = contentSafeTemplate.submitAsync(
    AuditRequest.image("avatar", ContentRef.url(avatarUrl))
);
AuditResult result = contentSafeTemplate.query(taskId);
```

## Supported Providers

| Provider | Text | Image | Async/Callback | Status |
|---|---|---|---|---|
| Alibaba Cloud Content Moderation | ✅ | ✅ | ✅ image polling + callback (SHA-256 signature) | supported |
| Tencent Cloud TMS/IMS | ✅ | ✅ | ✅ image callback push (structural validation) | supported |
| Baidu Cloud Content Censorship | ✅ | ✅ | ❌ no async API upstream | supported |
| Volcengine Content Security | ✅ | ✅ | ❌ no async API upstream | supported |
| Shumei Technology | ✅ | ✅ | ✅ image polling + callback (structural validation) | supported |
| Huawei Cloud Moderation | - | - | - | at lifecycle risk, not enabled by default |

> v1 covers **text + image** moderation; abstractions for audio, video, file and live-streaming are reserved.

## Extending

- **Custom provider**: implement the `AuditOperationProvider` SPI — see the integration guide (Chinese).
- **Custom routing / arbitration / credentials / task store / callback verification**: override the corresponding SPI bean; every default bean can be replaced via `@ConditionalOnMissingBean`.

## Documentation (Chinese)

- [Integration guide](docs/content-safe-starter-guide.md)
- [Provider feature matrix](docs/provider-feature-matrix.md)
- [Error code table](docs/error-code-table.md)
- [Release guide](docs/release-guide.md) / [Compatibility policy](docs/compatibility-policy.md)

## Contributing

Issues and pull requests are welcome. Please run `./mvnw clean verify` before submitting.

## Acknowledgements

- The API design of this project draws inspiration from [JustAuth](https://gitee.com/yadong.zhang/JustAuth).

## License

[Apache-2.0](LICENSE)
