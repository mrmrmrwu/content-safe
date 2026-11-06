# sample-basic

最小可运行样例：引入 `content-safe-spring-boot-starter` + 一个 mock 供应商，演示：

- 注入 `ContentSafeTemplate` 并调用 `template.text(scene, text)`
- 内置场景（`Scenes.COMMENT` 等 6 个）开箱即用
- `src/main/resources/content-safe/scenes.json` 声明自定义场景 `product_title`，启动时自动导入
- `FakeAuditOperationProvider` 演示供应商 SPI 接入方式（真实云供应商在 `provider-*` 模块中同法接入）

## 运行

```bash
mvn -pl samples/sample-basic -am spring-boot:run -pl samples/sample-basic
```

或先 `mvn clean verify` 再运行 `SampleBasicApplication` 主类。期望日志：

```
comment normal   -> pass=true, suggestion=PASS, provider=fake, hits=0
comment risky    -> pass=false, suggestion=REJECT, provider=fake, hits=1
product_title    -> pass=true, suggestion=PASS, provider=fake, hits=0
```

> mock 规则：文本包含"违禁"即判 REJECT。无任何真实云调用，无需配置密钥。
