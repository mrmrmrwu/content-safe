package io.github.wrqtec.content.safe.api.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorMessagesTest {

    @Test
    void blankMessagesSanitizeToEmpty() {
        assertEquals("", ErrorMessages.sanitize(null));
        assertEquals("", ErrorMessages.sanitize("   "));
    }

    @Test
    void longTokenLikeFragmentsAreMasked() {
        String masked = ErrorMessages.sanitize("failed: abcdef1234567890ABCDEF end");
        assertTrue(masked.contains("***"));
        assertFalse(masked.contains("abcdef1234567890"));
        assertTrue(masked.contains("failed:"));
        assertTrue(masked.contains("end"));
    }

    @Test
    void dotJoinedTokenFragmentsAreMasked() {
        // 含点号的拼接形态（域名片段、版本化签名段）同样按敏感片段遮蔽
        String masked = ErrorMessages.sanitize("GET https://e-yyyy.cn-shanghai.green.aliyuncs.com/audit?sig=AbCdEf1234567890abcdEf12 failed");
        assertFalse(masked.contains("cn-shanghai"));
        assertFalse(masked.contains("AbCdEf1234567890"));
    }

    @Test
    void shortHumanWordsSurvive() {
        assertEquals("qps over limit on svc-a", ErrorMessages.sanitize("qps over limit on svc-a"));
    }

    @Test
    void messagesAreTruncated() {
        String longMessage = "word ".repeat(100);
        assertEquals(120, ErrorMessages.sanitize(longMessage).length());
    }
}
