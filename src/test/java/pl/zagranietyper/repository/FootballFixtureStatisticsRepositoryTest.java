package pl.zagranietyper.repository;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.FootballFixtureStatisticsSnapshot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FootballFixtureStatisticsRepositoryTest {
    @Test
    void incompleteStateCanBeReplacedAndCompleteSupersedesIt() {
        assertTrue(FootballFixtureStatisticsRepository.shouldReplace(
                FootballFixtureStatisticsSnapshot.FetchStatus.PARTIAL,
                FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE));
        assertTrue(FootballFixtureStatisticsRepository.shouldReplace(
                FootballFixtureStatisticsSnapshot.FetchStatus.FETCH_FAILED,
                FootballFixtureStatisticsSnapshot.FetchStatus.PARTIAL));
    }

    @Test
    void laterFailedOrIncompleteIngestionCannotEraseKnownGoodData() {
        assertFalse(FootballFixtureStatisticsRepository.shouldReplace(
                FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE,
                FootballFixtureStatisticsSnapshot.FetchStatus.FETCH_FAILED));
        assertFalse(FootballFixtureStatisticsRepository.shouldReplace(
                FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE,
                FootballFixtureStatisticsSnapshot.FetchStatus.PARTIAL));
        assertFalse(FootballFixtureStatisticsRepository.shouldReplace(
                FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE,
                FootballFixtureStatisticsSnapshot.FetchStatus.UNSUPPORTED));
    }

    @Test
    void completeReingestionIsIdempotentlyReplaceableWithoutDuplicateRows() {
        assertTrue(FootballFixtureStatisticsRepository.shouldReplace(
                FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE,
                FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE));
    }
}
