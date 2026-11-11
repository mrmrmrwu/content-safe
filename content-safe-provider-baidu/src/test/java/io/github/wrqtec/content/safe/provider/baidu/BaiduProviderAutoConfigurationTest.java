package io.github.wrqtec.content.safe.provider.baidu;

import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.provider.baidu.internal.BaiduCensorClient;
import io.github.wrqtec.content.safe.provider.baidu.internal.BaiduCensorResponse;
import io.github.wrqtec.content.safe.provider.baidu.internal.BaiduVideoPullResponse;
import io.github.wrqtec.content.safe.provider.baidu.internal.BaiduVideoSubmitResponse;
import io.github.wrqtec.content.safe.spi.AuditOperationProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BaiduProviderAutoConfiguration} 装配条件测试
 *
 * @author L.David
 */
class BaiduProviderAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BaiduProviderAutoConfiguration.class));

    @Test
    void notAssembledByDefault() {
        runner.run(context -> assertThat(context)
                .doesNotHaveBean(BaiduCensorClient.class)
                .doesNotHaveBean(BaiduAuditOperationProvider.class));
    }

    @Test
    void notAssembledWhenDisabled() {
        runner.withPropertyValues("content.safe.providers.baidu.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(BaiduCensorClient.class));
    }

    @Test
    void failsFastWithoutCredentials() {
        runner.withPropertyValues(
                        "content.safe.providers.baidu.enabled=true",
                        "content.safe.providers.baidu.scenes.comment=101")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(ConfigInvalidException.class)
                        .hasMessageContaining("missing credentials for provider 'baidu'"));
    }

    @Test
    void failsFastWithoutScenes() {
        runner.withPropertyValues(
                        "content.safe.providers.baidu.enabled=true",
                        "content.safe.providers.baidu.api-key=ak",
                        "content.safe.providers.baidu.secret-key=sk")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(ConfigInvalidException.class)
                        .hasMessageContaining("scenes"));
    }

    @Test
    void assemblesWithFullConfiguration() {
        runner.withPropertyValues(
                        "content.safe.providers.baidu.enabled=true",
                        "content.safe.providers.baidu.api-key=ak",
                        "content.safe.providers.baidu.secret-key=sk",
                        "content.safe.providers.baidu.scenes.comment=101")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(BaiduCensorClient.class)
                        .hasSingleBean(BaiduAuditOperationProvider.class));
    }

    @Test
    void userProvidedClientOverridesDefault() {
        runner.withUserConfiguration(CustomClientConfiguration.class)
                .withPropertyValues(
                        "content.safe.providers.baidu.enabled=true",
                        "content.safe.providers.baidu.api-key=ak",
                        "content.safe.providers.baidu.secret-key=sk",
                        "content.safe.providers.baidu.scenes.comment=101")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(BaiduCensorClient.class)
                        .getBean(BaiduCensorClient.class)
                        .isSameAs(CustomClientConfiguration.CLIENT));
    }

    @Test
    void providerRegisteredAsAuditOperationProvider() {
        runner.withPropertyValues(
                        "content.safe.providers.baidu.enabled=true",
                        "content.safe.providers.baidu.api-key=ak",
                        "content.safe.providers.baidu.secret-key=sk",
                        "content.safe.providers.baidu.scenes.comment=101")
                .run(context -> assertThat(context)
                        .hasSingleBean(AuditOperationProvider.class));
    }

    /**
     * 用户自定义客户端覆盖默认装配
     */
    @Configuration(proxyBeanMethods = false)
    static class CustomClientConfiguration {

        static final BaiduCensorClient CLIENT = new BaiduCensorClient() {
            @Override
            public BaiduCensorResponse textCensor(String text, String strategyId) {
                return new BaiduCensorResponse();
            }

            @Override
            public BaiduCensorResponse imageCensorByBase64(String base64, String strategyId) {
                return new BaiduCensorResponse();
            }

            @Override
            public BaiduCensorResponse imageCensorByUrl(String imgUrl, String strategyId) {
                return new BaiduCensorResponse();
            }

            @Override
            public BaiduVideoSubmitResponse videoSubmit(String url, String extId, String strategyId,
                                                         String noticeUrl, String subEvents) {
                return new BaiduVideoSubmitResponse();
            }

            @Override
            public BaiduVideoPullResponse videoPull(String taskId) {
                return new BaiduVideoPullResponse();
            }
        };

        @Bean
        BaiduCensorClient baiduCensorClient() {
            return CLIENT;
        }
    }
}
