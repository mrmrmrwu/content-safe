package io.github.wrqtec.content.safe.starter.web;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.CallbackResult;
import io.github.wrqtec.content.safe.api.ContentSafeTemplate;
import io.github.wrqtec.content.safe.api.exception.CallbackVerifyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 可选内置回调接收端点：POST {path}/{provider}，验签并分发回调后
 * 以供应商约定的状态码应答（200 确认，非 200 由供应商重推）
 *
 * <p>仅做协议转换，不含任何验签或业务逻辑；回调地址暴露公网，
 * 生产环境建议叠加 IP 白名单等网络层防护
 *
 * @author L.David
 */
@Slf4j
@RestController
@RequestMapping("${content.safe.callback.path:/content-safe/callback}")
public class ContentSafeCallbackController {

    private final ContentSafeTemplate template;

    public ContentSafeCallbackController(ContentSafeTemplate template) {
        this.template = template;
    }

    /** 接收供应商回调：验签失败 401，业务拒绝 400，受理 200 */
    @PostMapping("/{provider}")
    public ResponseEntity<String> handleCallback(@PathVariable("provider") String provider,
                                                 @RequestHeader Map<String, String> headers,
                                                 @RequestBody String rawBody) {
        try {
            CallbackResult result = template.handleCallback(
                    new CallbackRequest(provider, lowercaseKeys(headers), rawBody));
            if (result.accepted()) {
                return ResponseEntity.ok("ok");
            }
            log.warn("callback rejected: provider={}, message={}", provider, result.message());
            return ResponseEntity.badRequest().body("rejected");
        } catch (CallbackVerifyException ex) {
            log.warn("callback verify failed: provider={}, message={}", provider, ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("verify failed");
        }
    }

    private static Map<String, String> lowercaseKeys(Map<String, String> headers) {
        Map<String, String> lowercase = new HashMap<>(headers.size());
        headers.forEach((name, value) -> lowercase.put(name.toLowerCase(Locale.ROOT), value));
        return lowercase;
    }
}
