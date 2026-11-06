# CLAUDE.md

本文件是 AI 编码助手与协作者在本仓库工作前必须遵守的项目约定。规则分两级：
- **铁律（IRON）**：违反即评审拒绝，部分由 CI/ArchUnit 强制。
- **规范（RULE）**：以《阿里巴巴Java开发手册》为基线，允许有正当理由的偏离，但需在 PR 中说明。

## 1. 项目定位

多模块 Spring Boot Starter：统一封装多家云供应商的文本/图片内容安全审核能力。
- v1 只承诺：TEXT / IMAGE 内容安全。
- 不承诺但预留抽象：音视频、文件、直播、AIGC 护栏、人工审核工作流。
- 本库不做检测算法本身，只做供应商适配、路由、治理、审计。

## 2. 构建与测试

```bash
./mvnw clean verify                 # 提交前必须本地通过
./mvnw clean verify -pl content-safe-provider-aliyun -am   # 单模块
```

- JDK 17+，Maven Wrapper。
- 提交前跑全量测试；修改 starter 必须跑 `ApplicationContextRunner` 用例。

## 3. 模块依赖铁律（IRON）

```
api  ←  spi  ←  model  ←  core  ←  provider-*  ←  spring-boot-starter
```

1. `content-safe-api`：不依赖 Spring、不依赖任何云 SDK、不依赖 core。只允许 JDK + 标准注解。
2. `content-safe-spi` / `content-safe-model`：同 api，可互相依赖但不得依赖 core/provider/starter。
3. `content-safe-core`：不依赖具体云 SDK；对 Spring 的依赖保持 optional（仅装配辅助类除外，放 starter）。
4. `content-safe-provider-*`：允许依赖对应云 SDK，必须 `optional`；云 SDK 类型不得出现在方法签名与 api/spi 接口中。
5. `spring-boot-starter`：只做自动装配，不放业务逻辑、不放 SDK 调用。
6. `internal` 包不对外承诺兼容；对外能力必须落在 api/spi/model。

> 新增依赖前自问：它会不会进入使用者的编译期传递依赖？会就先降级为 optional/test。

## 4. 编码规范基线

以《阿里巴巴Java开发手册（嵩山版）》为基线，高频项重申：

- 命名：类 `UpperCamelCase`，方法/变量 `lowerCamelCase`，常量 `UPPER_SNAKE_CASE`，杜绝拼音缩写。
- 魔法值一律提取为常量或枚举。
- 字符串循环拼接用 `StringBuilder`；日期时间统一 `java.time`（禁用 `SimpleDateFormat` 共享实例）。
- `equals` 比较：常量在前，`"PASS".equals(status)`。
- 集合初始化预估容量；`Map` 遍历用 `entrySet`。
- 线程池禁止 `Executors.newXxx` 直接创建，统一通过配置构建并命名（`content-safe-xxx-` 前缀）。
- 异常：禁止吞异常（空 catch 块）、禁止 `e.printStackTrace()`；受控异常转 `ContentSafeException` 体系并携带错误码。
- 日志：SLF4J 占位符 `log.info("scene={} provider={}", scene, provider)`；禁止字符串拼接日志；禁止输出密钥、完整 URL 凭证、用户内容原文。
- 空值：对外 API 入参做 `validate`（`Objects.requireNonNull` 或自研 `Assert`）；返回集合/Map 不返回 null，返回空实例。
- `Optional` 只用于返回值与流式处理，禁止作为字段/入参（阿里手册约定）。

## 5. 本项目设计规则（IRON）

1. **不可变模型**：`AuditRequest/AuditResult/ContentRef/ProviderPlan/SceneDefinition` 等一律 `record` 或全 final；变更即新建。
2. **能力先行**：任何 provider 调用前必须过 `AuditCapabilities.supports()`；不支持就抛 `UnsupportedContentException`，禁止"先调了再说"。
3. **场景解耦**：业务代码只传 scene code（如 `nickname`），云厂商场景 ID 只出现在 yml 配置与 provider 内部，禁止硬编码在 api/core。
4. **结果归一化**：provider 返回结构只允许由 `AuditResultNormalizer` 翻译为统一模型；禁止在 core 写 `if (aliyun) {...}` 供应商分支。
5. **错误码**：对外只抛 `ContentSafeException` 子类，必须携带稳定 `code`；新增错误码先更新错误码表文档。
6. **证据脱敏**：`raw` 默认不进日志、不进审计事件正文；命中证据经 `EvidenceRedactor` 处理。
7. **启动轻量**：构造器/ `@PostConstruct` 禁止远程同步探测；健康探测只走后台 `ProviderHealthProbe`。
8. **异步幂等**：回调处理必须验签 + 幂等；重复回调、乱序回调不得产生副作用。
9. **Spring 装配**：对外 Bean 默认 `@ConditionalOnMissingBean`；条件类用 `@ConditionalOnClass(name=...)` 避免硬引用可选 SDK。

## 6. provider 编写规则

新增供应商 checklist：
- [ ] 实现 `AuditOperationProvider`，一个 provider 可注册多个 `AuditOperation`（按 product/版本/模式拆分）。
- [ ] 在 `AuditCapabilities` 如实声明：url/base64/bytes/batch/ocr/aigc/maxBatchSize/maxUrlSize。
- [ ] 在 `OperationLimits` 如实声明：并发、QPS、超时、大小/像素限制、多场景是否重复计费。
- [ ] 实现 `AuditResultNormalizer` 完成错误码与 suggestion 归一（PASS/REVIEW/REJECT/ERROR）。
- [ ] 实现/复用 `CallbackVerifier`，验签失败进死信。
- [ ] 补充契约测试样本：pass / review / reject / 限流 / token 失效 / 回调重复。
- [ ] 更新 docs/provider-feature-matrix.md。

## 7. 测试规则（RULE）

- 新逻辑必须有单元测试；分支覆盖率核心模块 ≥ 80%。
- starter 改动必须补 `ApplicationContextRunner`：缺配置 fail-fast、未引 SDK 不装配、用户 Bean 可覆盖默认 Bean。
- provider 改动必须补契约测试（响应样本放 `src/test/resources/contracts/`）。
- 禁止在测试里真实调用云接口；用 MockServer/响应样本。
- ArchUnit 守护依赖方向，CI 失败即拒绝合入。

## 8. 提交规范（RULE）

```text
type(scope): summary

type: feat | fix | refactor | docs | test | chore | provider
scope: api | spi | core | starter | provider-aliyun | provider-tencent | bom | docs
示例：feat(provider-aliyun): 支持图片批量审核分片
```

- 一个 PR 只做一件事；公共 API 变更必须在描述里给出迁移说明。
- 配置破坏式变更必须同步 migration note 并升 `schema-version`。

## 9. 禁止事项（IRON）

- 禁止把云 SDK 类型写入 api/spi/core 的任何签名。
- 禁止在 core 中新增供应商 if/switch 分支（路由只允许按 plan/capability 数据驱动）。
- 禁止静默降级：所有降级、fallback、熔断触发必须发事件并可观测。
- 禁止在日志/异常 message 中输出 accessKey、secret、签名 URL、用户内容原文。
- 禁止为"临时调试"新增 System.out、printStackTrace、Thread.sleep 到生产路径。
- 禁止未经评审修改 api/spi 中已发布类的签名（此类变更走 major 版本流程）。

## 10. AI 协作提示

生成代码时优先复用已有抽象（`AuditOperation/Normalizer/Router/TaskStore`），不要新造平行体系；
不确定供应商接口细节时先查官方文档并在 PR 描述注明来源与日期，不要凭记忆编造场景 ID、限额或字段名。
