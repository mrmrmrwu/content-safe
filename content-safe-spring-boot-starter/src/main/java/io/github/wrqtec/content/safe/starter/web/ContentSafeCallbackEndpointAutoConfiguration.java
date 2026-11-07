package io.github.wrqtec.content.safe.starter.web;

import io.github.wrqtec.content.safe.api.ContentSafeTemplate;
import io.github.wrqtec.content.safe.starter.config.ContentSafeAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

/**
 * 可选回调接收端点装配：content.safe.callback.expose=true 且存在 Servlet Web 环境时生效，
 * 默认关闭，业务方通常自建 Controller 以叠加自身鉴权与网络防护
 *
 * @author L.David
 */
@AutoConfiguration(after = ContentSafeAutoConfiguration.class)
@ConditionalOnClass(RequestMappingHandlerAdapter.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "content.safe.callback", name = "expose", havingValue = "true")
public class ContentSafeCallbackEndpointAutoConfiguration {

    @Bean
    @ConditionalOnBean(ContentSafeTemplate.class)
    @ConditionalOnMissingBean
    public ContentSafeCallbackController contentSafeCallbackController(ContentSafeTemplate template) {
        return new ContentSafeCallbackController(template);
    }
}
