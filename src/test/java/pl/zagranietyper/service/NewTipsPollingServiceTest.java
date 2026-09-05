package pl.zagranietyper.service;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.repository.ImportRepository;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewTipsPollingServiceTest {

    @Test
    void newPostIsProcessed() {
        assertTrue(
                NewTipsPollingService.shouldProcess(
                        null,
                        Instant.parse(
                                "2026-09-05T12:00:00Z"
                        )
                )
        );
    }

    @Test
    void unchangedPostIsSkipped() {
        ImportRepository.PostVersion existing =
                new ImportRepository.PostVersion(
                        Instant.parse(
                                "2026-09-05T12:00:00Z"
                        ),
                        "hash"
                );

        assertFalse(
                NewTipsPollingService.shouldProcess(
                        existing,
                        Instant.parse(
                                "2026-09-05T12:00:00Z"
                        )
                )
        );
    }

    @Test
    void newerModifiedPostIsProcessed() {
        ImportRepository.PostVersion existing =
                new ImportRepository.PostVersion(
                        Instant.parse(
                                "2026-09-05T12:00:00Z"
                        ),
                        "hash"
                );

        assertTrue(
                NewTipsPollingService.shouldProcess(
                        existing,
                        Instant.parse(
                                "2026-09-05T12:01:00Z"
                        )
                )
        );
    }
}
