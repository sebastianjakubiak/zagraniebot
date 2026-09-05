package pl.zagranietyper.service;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.repository.ImportRepository;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    @Test
    void firstRunUsesBootstrapWindow() {
        Instant now =
                Instant.parse(
                        "2026-09-05T12:00:00Z"
                );

        assertEquals(
                Instant.parse(
                        "2026-08-06T12:00:00Z"
                ),
                NewTipsPollingService.computePublishedScanFrom(
                        null,
                        now,
                        Duration.ofHours(
                                720
                        ),
                        Duration.ofHours(
                                72
                        ),
                        Duration.ofSeconds(
                                120
                        )
                )
        );
    }

    @Test
    void steadyPollingAlwaysRescansRecentPublicationsForEdits() {
        Instant now =
                Instant.parse(
                        "2026-09-05T12:00:00Z"
                );

        assertEquals(
                Instant.parse(
                        "2026-09-02T12:00:00Z"
                ),
                NewTipsPollingService.computePublishedScanFrom(
                        Instant.parse(
                                "2026-09-05T11:59:00Z"
                        ),
                        now,
                        Duration.ofHours(
                                720
                        ),
                        Duration.ofHours(
                                72
                        ),
                        Duration.ofSeconds(
                                120
                        )
                )
        );
    }

    @Test
    void longGapCatchesUpFromLastStoredModification() {
        Instant now =
                Instant.parse(
                        "2026-09-05T12:00:00Z"
                );

        assertEquals(
                Instant.parse(
                        "2026-08-10T15:58:00Z"
                ),
                NewTipsPollingService.computePublishedScanFrom(
                        Instant.parse(
                                "2026-08-10T16:00:00Z"
                        ),
                        now,
                        Duration.ofHours(
                                720
                        ),
                        Duration.ofHours(
                                72
                        ),
                        Duration.ofSeconds(
                                120
                        )
                )
        );
    }

}
