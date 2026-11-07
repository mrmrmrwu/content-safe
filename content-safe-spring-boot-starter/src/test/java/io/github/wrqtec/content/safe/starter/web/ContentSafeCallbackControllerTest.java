package io.github.wrqtec.content.safe.starter.web;

import java.util.Map;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.CallbackRequest;
import io.github.wrqtec.content.safe.api.CallbackResult;
import io.github.wrqtec.content.safe.api.ContentSafeTemplate;
import io.github.wrqtec.content.safe.api.exception.CallbackVerifyException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ContentSafeCallbackControllerTest {

    private final ContentSafeTemplate template = Mockito.mock(ContentSafeTemplate.class);

    private final ContentSafeCallbackController controller = new ContentSafeCallbackController(template);

    @Test
    void acceptedCallbackRespondsOk() {
        Mockito.when(template.handleCallback(Mockito.any()))
                .thenReturn(CallbackResult.accepted("task-1", AuditResult.builder().ok(true).build()));

        ResponseEntity<String> response = controller.handleCallback(
                "shumei", Map.of("Content-Type", "application/json"), "{\"code\":1100}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("ok");
    }

    @Test
    void rejectedCallbackRespondsBadRequest() {
        Mockito.when(template.handleCallback(Mockito.any()))
                .thenReturn(CallbackResult.rejected("invalid payload"));

        ResponseEntity<String> response = controller.handleCallback("shumei", Map.of(), "{}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("rejected");
    }

    @Test
    void verifyFailureRespondsUnauthorized() {
        Mockito.when(template.handleCallback(Mockito.any()))
                .thenThrow(new CallbackVerifyException("checksum mismatch"));

        ResponseEntity<String> response = controller.handleCallback("aliyun", Map.of(), "ReqId=x");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void headerKeysLowercasedBeforeDispatch() {
        Mockito.when(template.handleCallback(Mockito.any()))
                .thenReturn(CallbackResult.accepted("task-1", AuditResult.builder().ok(true).build()));

        controller.handleCallback("shumei",
                Map.of("X-Custom-Header", "v1", "CONTENT-TYPE", "application/json"), "{}");

        ArgumentCaptor<CallbackRequest> captor = ArgumentCaptor.forClass(CallbackRequest.class);
        Mockito.verify(template).handleCallback(captor.capture());
        assertThat(captor.getValue().headers()).containsKeys("x-custom-header", "content-type");
        assertThat(captor.getValue().provider()).isEqualTo("shumei");
    }
}
