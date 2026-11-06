# 发布指南（Release Guide）

对应任务 T-024（发布流水线）。发布目标与分发渠道见
[content-safe-starter-design.md](content-safe-starter-design.md) §13：
正式发布走 Maven Central，CI 预览走 GitHub Packages，国内加速可另配云效/Nexus。

## 1. 前置准备（一次性）

| 项 | 说明 |
|---|---|
| GPG 密钥 | `gpg --gen-key`，公钥分发至 keyserver.ubuntu.com；签名口令不入库 |
| Central 凭据 | Maven Central（central.sonatype.com）账号 + 用户令牌，写入 `~/.m2/settings.xml` 的 `<server><id>central</id>` |
| GitHub Packages 凭据 | GitHub 账号 + `write:packages` PAT，写入 `<server><id>github</id>`（仅 CI 预览/SNAPSHOT 用） |

> groupId `io.github.wrqtec` 需在 Central Portal 完成命名空间验证；
> 仓库托管在 Gitee（`gitee.com/mrwu-ruiqi/content-safe`），若以 GitHub 镜像做 CI 预览，
> 调整父 pom 的 `github.repository` 属性为实际镜像仓。

## 2. 发布流程

```bash
# 1. 兼容检查（T-025）：api/spi 对比基线版本，二进制不兼容即失败
./mvnw -Pcompat-check -Dcompat.baseline.version=<上一发布版本> verify

# 2. 本地全量验证
./mvnw clean verify

# 3. 发布（release profile 自动完成 sources/javadoc/GPG 签名/SBOM）
./mvnw -Prelease clean deploy
```

`release` profile 在父 pom 中定义，包含：

| 插件 | 作用 |
|---|---|
| `maven-source-plugin` | 附带 sources jar（Central 强制） |
| `maven-javadoc-plugin` | 附带 javadoc jar（Central 强制） |
| `maven-gpg-plugin` | verify 阶段签名全部构件 |
| `cyclonedx-maven-plugin` | package 阶段生成聚合 SBOM（CycloneDX JSON/XML） |
| `central-publishing-maven-plugin` | 上传 Maven Central（`publishingServerId=central`，校验通过即自动发布） |

SBOM 产物位于各模块 `target/`（`bom.json` / `bom.xml`），随 release notes 一并归档。

## 3. 发布检查单（Definition of Done 对齐）

- [ ] 版本号符合 SemVer；api/spi 有破坏式变更时必须升 major（见 [compatibility-policy.md](compatibility-policy.md)）
- [ ] `./mvnw -Pcompat-check ... verify` 通过，或破坏变更已走 major 流程
- [ ] 配置破坏式变更已升 `schema-version` 并在 release notes 提供 migration note
- [ ] 全量 `clean verify` 通过；provider 变更已更新 [provider-feature-matrix.md](provider-feature-matrix.md)
- [ ] GPG 签名、sources/javadoc、SBOM 齐备；Central 校验通过
- [ ] release notes：新特性、错误码表变更、Feature Matrix 变更、迁移说明

## 4. SNAPSHOT 预览（CI）

CI（GitHub Actions，`.github/workflows/ci.yml`）在 push 时执行全量 `verify`；
向 GitHub Packages 发布 SNAPSHOT 需要 secrets 注入 `GITHUB_TOKEN`（CI 内自动提供）。
本地如需手动分发 SNAPSHOT：`./mvnw deploy -DskipTests`（走 `distributionManagement` 的 `github` 仓库）。

## 5. 国内加速分发（可选）

云效 Packages / 私有 Nexus 不在默认流水线内：在发布机 `settings.xml` 增加
`<server>` 凭据与 `<mirrorOf>`/`<altDeploymentRepository>` 即可，不要改动本仓库默认 profile。
