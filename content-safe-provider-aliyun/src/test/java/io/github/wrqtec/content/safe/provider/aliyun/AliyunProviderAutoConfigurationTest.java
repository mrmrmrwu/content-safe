package io.github.wrqtec.content.safe.provider.aliyun;

import io.github.wrqtec.content.safe.spi.AuditOperationProvider;
import io.github.wrqtec.content.safe.spi.CallbackVerifier;
import io.github.wrqtec.content.safe.provider.aliyun.internal.GreenClientInvoker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link AliyunProviderAutoConfiguration} 装配测试（ApplicationContextRunner，RULE §7）：
 * 未启用不装配、缺场景/缺凭据 fail-fast、未引 SDK 不装配、用户 Bean 可覆盖默认 Bean
 *
 * @author L.David
 */
class AliyunProviderAutoConfigurationTest {

    private static final String PREFIX = "content.safe.providers.aliyun";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AliyunProviderAutoConfiguration.class));

    @Test
    void notEnabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(GreenClientInvoker.class);
            assertThat(context).doesNotHaveBean(AuditOperationProvider.class);
        });
    }

    @Test
    void notLoadedWhenSdkMissing() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("com.aliyun.green20220302"))
                .withPropertyValues(PREFIX + ".enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(GreenClientInvoker.class);
                    assertThat(context).doesNotHaveBean(AuditOperationProvider.class);
                });
    }

    @Test
    void failsFastWhenScenesMissing() {
        contextRunner
                .withPropertyValues(PREFIX + ".enabled=true", PREFIX + ".ak=test-ak", PREFIX + ".sk=test-sk")
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void failsFastWhenCredentialsMissing() {
        contextRunner
                .withPropertyValues(PREFIX + ".enabled=true", PREFIX + ".scenes.comment=comment_detection")
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void wiresBeansWhenFullyConfigured() {
        contextRunner
                .withPropertyValues(
                        PREFIX + ".enabled=true",
                        PREFIX + ".ak=test-ak",
                        PREFIX + ".sk=test-sk",
                        PREFIX + ".scenes.comment=comment_detection")
                .run(context -> {
                    assertThat(context).hasSingleBean(GreenClientInvoker.class);
                    assertThat(context).hasSingleBean(AuditOperationProvider.class);
                    assertThat(context.getBean(AuditOperationProvider.class).provider()).isEqualTo("aliyun");
                    assertThat(context).hasSingleBean(CallbackVerifier.class);
                });
    }

    @Test
    void userBeanOverridesDefaultInvoker() {
        GreenClientInvoker custom = mock(GreenClientInvoker.class);
        contextRunner
                .withBean(GreenClientInvoker.class, () -> custom)
                .withPropertyValues(
                        PREFIX + ".enabled=true",
                        PREFIX + ".scenes.comment=comment_detection")
                .run(context -> {
                    assertThat(context).hasSingleBean(GreenClientInvoker.class);
                    assertThat(context.getBean(GreenClientInvoker.class)).isSameAs(custom);
                    assertThat(context).hasSingleBean(AuditOperationProvider.class);
                });
    }
}
