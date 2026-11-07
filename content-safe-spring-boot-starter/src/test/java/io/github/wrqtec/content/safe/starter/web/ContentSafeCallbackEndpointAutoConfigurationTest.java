package io.github.wrqtec.content.safe.starter.web;

import io.github.wrqtec.content.safe.starter.config.ContentSafeAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import static org.assertj.core.api.Assertions.assertThat;

class ContentSafeCallbackEndpointAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ContentSafeAutoConfiguration.class,
                    ContentSafeCallbackEndpointAutoConfiguration.class));

    @Test
    void endpointExposedWhenCallbackExposeTrue() {
        runner.withPropertyValues("content.safe.callback.expose=true")
                .run(context -> assertThat(context).hasSingleBean(ContentSafeCallbackController.class));
    }

    @Test
    void endpointNotExposedByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean(ContentSafeCallbackController.class));
    }

    @Test
    void nonServletEnvironmentNotExposed() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ContentSafeCallbackEndpointAutoConfiguration.class))
                .withPropertyValues("content.safe.callback.expose=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ContentSafeCallbackController.class);
                    // Servlet Web 环境判定生效（RequestMappingHandlerAdapter 在类路径但非 Web 上下文）
                    assertThat(context.getBeanFactory()
                            .getBeanNamesForType(RequestMappingHandlerAdapter.class)).isEmpty();
                });
    }
}
