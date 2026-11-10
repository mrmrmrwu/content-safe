package io.github.wrqtec.content.safe.core.security;

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
    void messagesAreTruncated() {
        String longMessage = "word ".repeat(100);
        assertEquals(120, ErrorMessages.sanitize(longMessage).length());
    }
}
