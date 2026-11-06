package io.github.wrqtec.content.safe.provider.volcengine;

import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.provider.volcengine.internal.VolcengineClientInvoker;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 火山引擎自动装配条件测试（铁律 §7：starter 装配必测三项——
 * 默认不装配、缺 SDK 不装配、用户 Bean 可覆盖）
 *
 * @author L.David
 */
class VolcengineProviderAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(VolcengineProviderAutoConfiguration.class));

    @Test
    void notAssembledByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(VolcengineAuditOperationProvider.class);
            assertThat(context).doesNotHaveBean(VolcengineClientInvoker.class);
        });
    }

    @Test
    void notAssembledWithoutSdk() {
        runner.withClassLoader(new FilteredClassLoader("com.volcengine"))
                .withPropertyValues(
                        "content.safe.providers.volcengine.enabled=true",
                        "content.safe.providers.volcengine.ak=ak",
                        "content.safe.providers.volcengine.sk=sk",
                        "content.safe.providers.volcengine.extra.app-id=123")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(VolcengineAuditOperationProvider.class));
    }

    @Test
    void failsFastWithoutCredentials() {
        runner.withPropertyValues(
                        "content.safe.providers.volcengine.enabled=true",
                        "content.safe.providers.volcengine.extra.app-id=123")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(ConfigInvalidException.class);
                });
    }

    @Test
    void failsFastWithoutAppId() {
        runner.withPropertyValues(
                        "content.safe.providers.volcengine.enabled=true",
                        "content.safe.providers.volcengine.ak=ak",
                        "content.safe.providers.volcengine.sk=sk")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(ConfigInvalidException.class);
                    assertThat(context.getStartupFailure().getCause())
                            .hasMessageContaining("app-id");
                });
    }

    @Test
    void assemblesWithFullConfig() {
        runner.withPropertyValues(
                        "content.safe.providers.volcengine.enabled=true",
                        "content.safe.providers.volcengine.ak=ak",
                        "content.safe.providers.volcengine.sk=sk",
                        "content.safe.providers.volcengine.extra.app-id=123",
                        "content.safe.providers.volcengine.scenes.nickname=svc-nickname")
                .run(context -> {
                    assertThat(context).hasSingleBean(VolcengineAuditOperationProvider.class);
                    assertThat(context).hasSingleBean(VolcengineClientInvoker.class);
                    VolcengineAuditOperationProvider provider =
                            context.getBean(VolcengineAuditOperationProvider.class);
                    assertThat(provider.provider()).isEqualTo("volcengine");
                });
    }

    @Test
    void userInvokerOverridesDefault() {
        VolcengineClientInvoker customInvoker = Mockito.mock(VolcengineClientInvoker.class);
        runner.withPropertyValues(
                        "content.safe.providers.volcengine.enabled=true",
                        "content.safe.providers.volcengine.ak=ak",
                        "content.safe.providers.volcengine.sk=sk",
                        "content.safe.providers.volcengine.extra.app-id=123")
                .withBean(VolcengineClientInvoker.class, () -> customInvoker)
                .run(context -> {
                    assertThat(context).hasSingleBean(VolcengineClientInvoker.class);
                    assertThat(context.getBean(VolcengineClientInvoker.class)).isSameAs(customInvoker);
                    assertThat(context).hasSingleBean(VolcengineAuditOperationProvider.class);
                });
    }
}
