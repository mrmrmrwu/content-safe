package io.github.wrqtec.content.safe.api.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 错误码是对外契约，修改必须走评审——本测试守护错误码字符串不被无意改动
 *
 * @author L.David
 */
class ErrorCodeContractTest {

    @Test
    void subclassCarriesStableCode() {
        assertEquals("INVALID_REQUEST", new InvalidRequestException("m").getCode());
        assertEquals("SCENE_NOT_CONFIGURED", new SceneNotConfiguredException("m").getCode());
        assertEquals("UNSUPPORTED_CONTENT", new UnsupportedContentException("m").getCode());
        assertEquals("UNSUPPORTED_FEATURE", new UnsupportedFeatureException("m").getCode());
        assertEquals("PROVIDER_NOT_AVAILABLE", new ProviderNotAvailableException("m").getCode());
        assertEquals("PROVIDER_CALL_ERROR", new ProviderCallException("m").getCode());
        assertEquals("CALLBACK_VERIFY_FAILED", new CallbackVerifyException("m").getCode());
        assertEquals("ALL_PROVIDER_FAILED", new AllProvidersFailedException("m").getCode());
    }

    @Test
    void codesMatchDocumentedValues() {
        assertEquals("INVALID_REQUEST", ErrorCodes.INVALID_REQUEST);
        assertEquals("SCENE_NOT_CONFIGURED", ErrorCodes.SCENE_NOT_CONFIGURED);
        assertEquals("UNSUPPORTED_CONTENT", ErrorCodes.UNSUPPORTED_CONTENT);
        assertEquals("UNSUPPORTED_FEATURE", ErrorCodes.UNSUPPORTED_FEATURE);
        assertEquals("PROVIDER_NOT_AVAILABLE", ErrorCodes.PROVIDER_NOT_AVAILABLE);
        assertEquals("PROVIDER_CALL_ERROR", ErrorCodes.PROVIDER_CALL_ERROR);
        assertEquals("CALLBACK_VERIFY_FAILED", ErrorCodes.CALLBACK_VERIFY_FAILED);
        assertEquals("ALL_PROVIDER_FAILED", ErrorCodes.ALL_PROVIDER_FAILED);
    }

    @Test
    void causeIsPreserved() {
        RuntimeException cause = new RuntimeException("root");
        ProviderCallException exception = new ProviderCallException("call failed", cause);
        assertEquals(cause, exception.getCause());
    }
}
