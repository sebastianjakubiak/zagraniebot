package pl.zagranietyper;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainDryRunTest {

    @Test
    void syncDryRunDoesNotInitializeSchema() {
        assertFalse(
                Main.shouldInitializeSchema(
                        "sync-new-tips",
                        Map.of(
                                "dry-run",
                                "true"
                        )
                )
        );
    }

    @Test
    void liveSyncInitializesSchema() {
        assertTrue(
                Main.shouldInitializeSchema(
                        "sync-new-tips",
                        Map.of(
                                "dry-run",
                                "false"
                        )
                )
        );
    }

    @Test
    void otherCommandsStillInitializeSchema() {
        assertTrue(
                Main.shouldInitializeSchema(
                        "backfill",
                        Map.of()
                )
        );
    }
    @Test
    void telegramTestDoesNotInitializeSchema() {
        assertFalse(
                Main.shouldInitializeSchema(
                        "telegram-test",
                        Map.of()
                )
        );
    }

}
