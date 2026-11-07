# content-safe-bom

版本对齐 BOM：业务方只 import 本 BOM，即可获得全部模块的受管版本，
不依赖任何模块的构建产物。

## 使用

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
```

之后按需引入（通常只需要第一个）：

| 构件 | 场景 |
| --- | --- |
| `content-safe-spring-boot-starter` | Spring Boot 业务方（starter 传递 core/api/spi/model） |
| `content-safe-provider-aliyun` / `-tencent` / `-baidu` / `-volcengine` / `-shumei` | 按需叠加供应商接入 |
| `content-safe-core` | 非 Spring 环境手工装配 |
| `content-safe-api` / `content-safe-spi` | 仅编译期对接（SPI 实现方） |

版本策略与升级兼容性见 [compatibility-policy.md](../docs/compatibility-policy.md)。
