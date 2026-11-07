# 发布指南（Release Guide）

正式发布走 Maven Central（central-publishing-maven-plugin）；SNAPSHOT 预览走 GitHub Packages（CI 自动部署）；国内加速或私有分发可另配云效/Nexus 个人依赖仓库，见 §5。

## 1. 前置准备（一次性）

| 项 | 说明 |
|---|---|
| GPG 密钥 | `gpg --gen-key`，公钥分发至 keyserver.ubuntu.com；签名口令不入库 |
| Central 凭据 | Maven Central（central.sonatype.com）账号 + 用户令牌，写入 `~/.m2/settings.xml` 的 `<server><id>central</id>` |
| GitHub Packages 凭据 | GitHub 账号 + `write:packages` PAT，写入 `<server><id>github</id>`（仅 CI 预览/SNAPSHOT 用） |

> groupId `io.github.wrqtec` 需在 Central Portal 完成命名空间验证；
> 仓库托管在 GitHub（`github.com/mrmrmrwu/content-safe`，父 pom `github.repository` 已指向该仓库）；Gitee（origin）为同步镜像，GitHub Actions 自动将 SNAPSHOT 发布到 GitHub Packages。

## 2. 发布流程

```bash
# 1. 兼容检查：api/spi 对比基线版本，二进制不兼容即失败
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

## 5. 发布到个人依赖仓库（云效 Packages / Nexus / Artifactory，可选）

仓库默认 profile 不绑定任何私有仓库；分发到个人依赖云端使用
`-DaltDeploymentRepository` 在命令行指定，零 pom 改动。凭据环境文件走模板：

```bash
# 1. 复制模板（模板不含真实凭据，已入库；本地副本被 .gitignore 忽略，不会误提交）
cp .mvn/settings-release-template.xml .mvn/settings-local.xml

# 2. 在发布机注入环境变量（建议写入 profile / CI secrets，不要进 shell 历史）
export REPO_USERNAME=xxx REPO_PASSWORD=xxx          # personal 仓库凭据
export CENTRAL_USERNAME=xxx CENTRAL_TOKEN=xxx       # 走 Central 时需要
export GPG_PASSPHRASE=xxx                           # 走 Central（-Prelease 签名）时需要

# 3. 分发到个人依赖仓库（仓库上传地址在云效 Packages / Nexus 页面复制）
./mvnw -s .mvn/settings-local.xml clean deploy \
  -DaltDeploymentRepository=personal::default::<仓库上传地址>
# 云效示例：https://packages.aliyun.com/maven/repository/xxxx-release/maven
# Nexus 示例：http://nexus.local:8081/repository/maven-releases/
```

需要签名/SBOM/sources 等完整发布产物时叠加 `-Prelease`（此时还需 `GPG_PASSPHRASE`，
且 personal 仓库需支持校验签名，否则可省略）。使用方拉取侧：将仓库地址加入业务工程
`<repositories>`（或 `<mirrorOf>`），下载凭据使用同一 `<server id>` 约定。

> `-DaltDeploymentRepository` 的 `personal` 即 `.mvn/settings-release-template.xml`
> 中预置的 `<server id>`，更换 id 时两处保持一致即可。
