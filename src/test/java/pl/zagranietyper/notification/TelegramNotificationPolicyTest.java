package pl.zagranietyper.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramNotificationPolicyTest {

    @Test
    void dryRunNeverSends() {
        assertFalse(
                TelegramNotificationPolicy.shouldSend(
                        true,
                        true,
                        false,
                        true
                )
        );
    }

    @Test
    void bootstrapIsSuppressedByDefault() {
        assertFalse(
                TelegramNotificationPolicy.shouldSend(
                        true,
                        false,
                        true,
                        false
                )
        );
    }

    @Test
    void normalNewBetIsSent() {
        assertTrue(
                TelegramNotificationPolicy.shouldSend(
                        true,
                        false,
                        false,
                        false
                )
        );
    }

    @Test
    void bootstrapCanBeExplicitlyEnabled() {
        assertTrue(
                TelegramNotificationPolicy.shouldSend(
                        true,
                        false,
                        true,
                        true
                )
        );
    }
}
