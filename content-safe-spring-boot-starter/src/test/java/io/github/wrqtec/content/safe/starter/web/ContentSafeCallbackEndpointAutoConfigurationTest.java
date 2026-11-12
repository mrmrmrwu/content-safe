package io.github.wrqtec.content.safe.starter.web;

import java.util.Map;

import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.starter.config.ContentSafeAutoConfiguration;
import io.github.wrqtec.content.safe.spi.CallbackVerifier;
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
        // 控制器条件挂在 CallbackDispatcher 上（而非恒存在的模板）：
        // 只有装出分发器（存在 verifier）时端点才有意义
        runner.withPropertyValues("content.safe.callback.expose=true")
                .withBean("stubVerifier", CallbackVerifier.class,
                        () -> new StubVerifier())
                .run(context -> assertThat(context).hasSingleBean(ContentSafeCallbackController.class));
    }

    @Test
    void endpointHiddenWhenNoDispatcher() {
        // 无 CallbackVerifier → 无 CallbackDispatcher → 端点不应暴露，
        // 否则供应商每笔回调都打到缺少分发器的模板上抛 500
        runner.withPropertyValues("content.safe.callback.expose=true")
                .run(context -> assertThat(context).doesNotHaveBean(ContentSafeCallbackController.class));
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

    static class StubVerifier implements CallbackVerifier {
        @Override
        public String provider() {
            return "fake";
        }

        @Override
        public boolean verify(CallbackRequest request, String secret, Map<String, String> ext) {
            return true;
        }
    }
}
