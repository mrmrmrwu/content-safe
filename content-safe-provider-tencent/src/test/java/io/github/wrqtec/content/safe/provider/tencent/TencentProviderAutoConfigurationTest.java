package io.github.wrqtec.content.safe.provider.tencent;

import io.github.wrqtec.content.safe.provider.tencent.internal.TencentClientInvoker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 腾讯云自动装配条件测试（铁律 §7：starter 装配必测三项——
 * 默认不装配、缺 SDK 不装配、用户 Bean 可覆盖）
 *
 * @author L.David
 */
class TencentProviderAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TencentProviderAutoConfiguration.class));

    @Test
    void notAssembledByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(TencentAuditOperationProvider.class);
            assertThat(context).doesNotHaveBean(TencentClientInvoker.class);
        });
    }

    @Test
    void notAssembledWithoutSdk() {
        runner.withClassLoader(new FilteredClassLoader("com.tencentcloudapi"))
                .withPropertyValues(
                        "content.safe.providers.tencent.enabled=true",
                        "content.safe.providers.tencent.secret-id=sid",
                        "content.safe.providers.tencent.secret-key=skey")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(TencentAuditOperationProvider.class));
    }

    @Test
    void failsFastWithoutCredentials() {
        runner.withPropertyValues("content.safe.providers.tencent.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(
                                    io.github.wrqtec.content.safe.api.exception.ConfigInvalidException.class);
                });
    }

    @Test
    void assemblesWithFullConfig() {
        runner.withPropertyValues(
                        "content.safe.providers.tencent.enabled=true",
                        "content.safe.providers.tencent.secret-id=sid",
                        "content.safe.providers.tencent.secret-key=skey",
                        "content.safe.providers.tencent.region=ap-shanghai",
                        "content.safe.providers.tencent.scenes.comment=biz-comment")
                .run(context -> {
                    assertThat(context).hasSingleBean(TencentAuditOperationProvider.class);
                    assertThat(context).hasSingleBean(TencentClientInvoker.class);
                    TencentAuditOperationProvider provider =
                            context.getBean(TencentAuditOperationProvider.class);
                    assertThat(provider.provider()).isEqualTo("tencent");
                });
    }

    @Test
    void userInvokerOverridesDefault() {
        TencentClientInvoker customInvoker = org.mockito.Mockito.mock(TencentClientInvoker.class);
        runner.withPropertyValues(
                        "content.safe.providers.tencent.enabled=true",
                        "content.safe.providers.tencent.secret-id=sid",
                        "content.safe.providers.tencent.secret-key=skey")
                .withBean(TencentClientInvoker.class, () -> customInvoker)
                .run(context -> {
                    assertThat(context).hasSingleBean(TencentClientInvoker.class);
                    assertThat(context.getBean(TencentClientInvoker.class)).isSameAs(customInvoker);
                    assertThat(context).hasSingleBean(TencentAuditOperationProvider.class);
                });
    }
}
