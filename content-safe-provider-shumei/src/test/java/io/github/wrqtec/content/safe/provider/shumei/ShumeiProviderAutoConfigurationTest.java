package io.github.wrqtec.content.safe.provider.shumei;

import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.provider.shumei.internal.ShumeiClientInvoker;
import io.github.wrqtec.content.safe.provider.shumei.internal.ShumeiSyncResponse;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ShumeiProviderAutoConfiguration} 装配条件测试
 *
 * @author L.David
 */
class ShumeiProviderAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ShumeiProviderAutoConfiguration.class));

    @Test
    void notAssembledByDefault() {
        runner.run(context -> assertThat(context)
                .doesNotHaveBean(ShumeiClientInvoker.class)
                .doesNotHaveBean(ShumeiAuditOperationProvider.class));
    }

    @Test
    void notAssembledWhenDisabled() {
        runner.withPropertyValues("content.safe.providers.shumei.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ShumeiClientInvoker.class));
    }

    @Test
    void failsFastWithoutAccessKey() {
        runner.withPropertyValues(
                        "content.safe.providers.shumei.enabled=true",
                        "content.safe.providers.shumei.app-id=default",
                        "content.safe.providers.shumei.scenes.comment=text")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(ConfigInvalidException.class)
                        .hasMessageContaining("shumei accessKey required"));
    }

    @Test
    void failsFastWithoutScenes() {
        runner.withPropertyValues(
                        "content.safe.providers.shumei.enabled=true",
                        "content.safe.providers.shumei.access-key=ak",
                        "content.safe.providers.shumei.app-id=default")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(ConfigInvalidException.class)
                        .hasMessageContaining("scenes"));
    }

    @Test
    void failsFastWithoutAppId() {
        runner.withPropertyValues(
                        "content.safe.providers.shumei.enabled=true",
                        "content.safe.providers.shumei.access-key=ak",
                        "content.safe.providers.shumei.scenes.comment=text")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(ConfigInvalidException.class)
                        .hasMessageContaining("app-id"));
    }

    @Test
    void assemblesWithFullConfiguration() {
        runner.withPropertyValues(
                        "content.safe.providers.shumei.enabled=true",
                        "content.safe.providers.shumei.access-key=ak",
                        "content.safe.providers.shumei.app-id=default",
                        "content.safe.providers.shumei.scenes.comment=text")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(ShumeiClientInvoker.class)
                        .hasSingleBean(ShumeiAuditOperationProvider.class));
    }

    @Test
    void userProvidedInvokerOverridesDefault() {
        runner.withUserConfiguration(CustomInvokerConfiguration.class)
                .withPropertyValues(
                        "content.safe.providers.shumei.enabled=true",
                        "content.safe.providers.shumei.access-key=ak",
                        "content.safe.providers.shumei.app-id=default",
                        "content.safe.providers.shumei.scenes.comment=text")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(ShumeiClientInvoker.class)
                        .getBean(ShumeiClientInvoker.class)
                        .isSameAs(CustomInvokerConfiguration.INVOKER));
    }

    @Test
    void providerRegisteredAsAuditOperationProvider() {
        runner.withPropertyValues(
                        "content.safe.providers.shumei.enabled=true",
                        "content.safe.providers.shumei.access-key=ak",
                        "content.safe.providers.shumei.app-id=default",
                        "content.safe.providers.shumei.scenes.comment=text")
                .run(context -> assertThat(context)
                        .hasSingleBean(AuditOperationProvider.class));
    }

    @Test
    void callbackVerifierAssembledWhenEnabled() {
        runner.withPropertyValues(
                        "content.safe.providers.shumei.enabled=true",
                        "content.safe.providers.shumei.access-key=ak",
                        "content.safe.providers.shumei.app-id=default",
                        "content.safe.providers.shumei.scenes.comment=text")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(io.github.wrqtec.content.safe.spi.CallbackVerifier.class)
                        .getBean(io.github.wrqtec.content.safe.spi.CallbackVerifier.class)
                        .extracting(io.github.wrqtec.content.safe.spi.CallbackVerifier::provider)
                        .isEqualTo("shumei"));
    }

    /**
     * 用户自定义客户端覆盖默认装配
     */
    @Configuration(proxyBeanMethods = false)
    static class CustomInvokerConfiguration {

        static final ShumeiClientInvoker INVOKER = new ShumeiClientInvoker() {
            @Override
            public ShumeiSyncResponse textDetect(String eventId, String type, java.util.Map<String, Object> data) {
                return new ShumeiSyncResponse();
            }

            @Override
            public ShumeiSyncResponse imageDetect(String eventId, String type, java.util.Map<String, Object> data) {
                return new ShumeiSyncResponse();
            }

            @Override
            public ShumeiSyncResponse imageAsyncSubmit(String eventId, String type,
                                                       java.util.Map<String, Object> data) {
                return new ShumeiSyncResponse();
            }

            @Override
            public io.github.wrqtec.content.safe.provider.shumei.internal.ShumeiAsyncQueryResponse imageAsyncQuery(
                    java.util.List<String> requestIds) {
                return new io.github.wrqtec.content.safe.provider.shumei.internal.ShumeiAsyncQueryResponse();
            }
        };

        @Bean
        ShumeiClientInvoker shumeiClientInvoker() {
            return INVOKER;
        }
    }
}
