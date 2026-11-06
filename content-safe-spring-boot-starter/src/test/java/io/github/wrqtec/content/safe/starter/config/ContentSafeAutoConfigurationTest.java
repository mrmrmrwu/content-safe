package io.github.wrqtec.content.safe.starter.config;

import java.util.Map;

import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.ContentSafeTemplate;
import io.github.wrqtec.content.safe.api.exception.ConfigInvalidException;
import io.github.wrqtec.content.safe.core.SceneRegistry;
import io.github.wrqtec.content.safe.core.callback.CallbackDispatcher;
import io.github.wrqtec.content.safe.core.degrade.CircuitBreakerRegistry;
import io.github.wrqtec.content.safe.core.listener.CompositeAuditListener;
import io.github.wrqtec.content.safe.spi.AuditRouter;
import io.github.wrqtec.content.safe.spi.AuditTaskStore;
import io.github.wrqtec.content.safe.spi.CallbackVerifier;
import io.github.wrqtec.content.safe.spi.EvidenceRedactor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class ContentSafeAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ContentSafeAutoConfiguration.class));

    @Test
    void defaultContextProvidesCoreBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ContentSafeTemplate.class);
            assertThat(context).hasSingleBean(SceneRegistry.class);
            assertThat(context).hasSingleBean(AuditRouter.class);
            assertThat(context).hasSingleBean(CircuitBreakerRegistry.class);
            assertThat(context).hasSingleBean(AuditTaskStore.class);
            assertThat(context).hasSingleBean(EvidenceRedactor.class);
            assertThat(context).hasSingleBean(ArbitrationPolicyRegistry.class);
            assertThat(context).hasSingleBean(CompositeAuditListener.class);
            // 无 CallbackVerifier 时不装配回调分发器
            assertThat(context).doesNotHaveBean(CallbackDispatcher.class);
        });
    }

    @Test
    void builtInScenesAvailableWithoutScenesJson() {
        runner.run(context -> {
            SceneRegistry registry = context.getBean(SceneRegistry.class);
            assertThat(registry.all().keySet())
                    .contains("nickname", "avatar", "comment", "post", "product_image", "im_message");
        });
    }

    @Test
    void userDefinedTemplateTakesPrecedence() {
        runner.withUserConfiguration(CustomTemplateConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ContentSafeTemplate.class);
                    assertThat(context.getBean(ContentSafeTemplate.class))
                            .isSameAs(context.getBean("customContentSafeTemplate"));
                });
    }

    @Test
    void callbackDispatcherPresentWhenVerifierExists() {
        runner.withUserConfiguration(VerifierConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(CallbackDispatcher.class));
    }

    @Test
    void unknownArbitrationPolicyFailsStartup() {
        runner.withPropertyValues("content.safe.defaults.arbitration=NOPE")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(ConfigInvalidException.class);
                });
    }

    @Test
    void unsupportedSchemaVersionFailsFast() {
        runner.withPropertyValues("content.safe.schema-version=99")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(ConfigInvalidException.class);
                    assertThat(context.getStartupFailure().getCause())
                            .hasMessageContaining("content.safe.schema-version");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomTemplateConfiguration {

        @Bean
        ContentSafeTemplate customContentSafeTemplate() {
            return Mockito.mock(ContentSafeTemplate.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class VerifierConfiguration {

        @Bean
        CallbackVerifier fakeVerifier() {
            return new CallbackVerifier() {
                @Override
                public String provider() {
                    return "fake";
                }

                @Override
                public boolean verify(CallbackRequest request, String secret, Map<String, String> ext) {
                    return true;
                }
            };
        }
    }
}
