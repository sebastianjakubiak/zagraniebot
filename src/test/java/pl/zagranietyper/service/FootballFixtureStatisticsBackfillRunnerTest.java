package pl.zagranietyper.service;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.FootballFixtureStatisticsSnapshot;
import pl.zagranietyper.repository.FootballFixtureStatisticsRepository;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FootballFixtureStatisticsBackfillRunnerTest {
    @Test
    void completeIsSkippedAndUntouchedFixtureIsAttempted() {
        Map<Long, FootballFixtureStatisticsSnapshot.FetchStatus> statuses = new HashMap<>();
        statuses.put(1L, FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE);
        List<Long> calls = new ArrayList<>();
        var runner = runner(List.of(1L, 2L), statuses, fixtureId -> {
            calls.add(fixtureId);
            return snapshot(fixtureId, FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE);
        });

        var summary = runner.run(options(10, false));

        assertEquals(List.of(2L), calls);
        assertEquals(1, summary.skippedComplete());
        assertEquals(1, summary.attempted());
        assertEquals(1, summary.complete());
    }

    @Test
    void limitCountsAttemptsNotCompleteOrIncompleteSkips() {
        Map<Long, FootballFixtureStatisticsSnapshot.FetchStatus> statuses = Map.of(
                1L, FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE,
                2L, FootballFixtureStatisticsSnapshot.FetchStatus.FETCH_FAILED);
        List<Long> calls = new ArrayList<>();
        var runner = runner(List.of(1L, 2L, 3L, 4L), statuses, fixtureId -> {
            calls.add(fixtureId);
            return snapshot(fixtureId, FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE);
        });

        var summary = runner.run(options(1, false));

        assertEquals(List.of(3L), calls);
        assertEquals(1, summary.skippedComplete());
        assertEquals(1, summary.skippedIncomplete());
        assertEquals(1, summary.remainingUntouched());
    }

    @Test
    void restartResumesAfterFixtureBecameComplete() {
        Map<Long, FootballFixtureStatisticsSnapshot.FetchStatus> statuses = new HashMap<>();
        List<Long> firstCalls = new ArrayList<>();
        runner(List.of(10L, 20L), statuses, fixtureId -> {
            firstCalls.add(fixtureId);
            statuses.put(fixtureId, FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE);
            return snapshot(fixtureId, FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE);
        }).run(options(1, false));

        List<Long> restartCalls = new ArrayList<>();
        var summary = runner(List.of(10L, 20L), statuses, fixtureId -> {
            restartCalls.add(fixtureId);
            return snapshot(fixtureId, FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE);
        }).run(options(1, false));

        assertEquals(List.of(10L), firstCalls);
        assertEquals(List.of(20L), restartCalls);
        assertEquals(1, summary.skippedComplete());
    }

    @Test
    void incompleteStatesAreSkippedUnlessRetryIsExplicit() {
        Map<Long, FootballFixtureStatisticsSnapshot.FetchStatus> statuses = Map.of(
                1L, FootballFixtureStatisticsSnapshot.FetchStatus.PARTIAL,
                2L, FootballFixtureStatisticsSnapshot.FetchStatus.API_ERROR,
                3L, FootballFixtureStatisticsSnapshot.FetchStatus.PARSE_ERROR);
        List<Long> defaultCalls = new ArrayList<>();
        var skipped = runner(List.of(1L, 2L, 3L), statuses, fixtureId -> {
            defaultCalls.add(fixtureId);
            return snapshot(fixtureId, FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE);
        }).run(options(10, false));
        assertTrue(defaultCalls.isEmpty());
        assertEquals(3, skipped.skippedIncomplete());

        List<Long> retryCalls = new ArrayList<>();
        runner(List.of(1L, 2L, 3L), statuses, fixtureId -> {
            retryCalls.add(fixtureId);
            return snapshot(fixtureId, FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE);
        }).run(options(10, true));
        assertEquals(List.of(1L, 2L, 3L), retryCalls);
    }

    @Test
    void oneFixtureFailureDoesNotAbortSubsequentFixturesAndCountersAreCorrect() {
        var runner = runner(List.of(1L, 2L, 3L), Map.of(), fixtureId -> {
            if (fixtureId == 1L) return snapshot(fixtureId,
                    FootballFixtureStatisticsSnapshot.FetchStatus.FETCH_FAILED);
            if (fixtureId == 2L) return snapshot(fixtureId,
                    FootballFixtureStatisticsSnapshot.FetchStatus.PARTIAL, Set.of("Odd label"));
            return snapshot(fixtureId, FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE,
                    Set.of("Odd label", "Another label"));
        });

        var summary = runner.run(options(3, false));

        assertEquals(3, summary.attempted());
        assertEquals(3, summary.requestsAttempted());
        assertEquals(1, summary.fetchFailed());
        assertEquals(1, summary.partial());
        assertEquals(1, summary.complete());
        assertEquals(Map.of("Odd label", 2, "Another label", 1), summary.unknownLabels());
    }

    @Test
    void auditDoesNotCallIngestor() {
        int[] calls = {0};
        var runner = runner(List.of(1L), Map.of(), fixtureId -> {
            calls[0]++;
            return snapshot(fixtureId, FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE);
        });

        var audit = runner.audit();

        assertEquals(0, calls[0]);
        assertEquals(1, audit.totalTargets());
        assertEquals(1, audit.needsFetch());
    }

    @Test
    void completeSnapshotCannotBeDowngradedByRepositoryPolicy() {
        assertFalse(FootballFixtureStatisticsRepository.shouldReplace(
                FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE,
                FootballFixtureStatisticsSnapshot.FetchStatus.PARTIAL));
        assertTrue(FootballFixtureStatisticsRepository.shouldReplace(
                FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE,
                FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE));
    }

    private static FootballFixtureStatisticsBackfillRunner runner(
            List<Long> targets,
            Map<Long, FootballFixtureStatisticsSnapshot.FetchStatus> statuses,
            FootballFixtureStatisticsBackfillRunner.FixtureIngestor ingestor) {
        return new FootballFixtureStatisticsBackfillRunner(
                () -> targets,
                fixtureId -> Optional.ofNullable(statuses.get(fixtureId)),
                ingestor,
                new PrintStream(new ByteArrayOutputStream()));
    }

    private static FootballFixtureStatisticsBackfillRunner.RunOptions options(
            int limit, boolean retry) {
        return new FootballFixtureStatisticsBackfillRunner.RunOptions(limit, retry);
    }

    private static FootballFixtureStatisticsSnapshot snapshot(
            long fixtureId, FootballFixtureStatisticsSnapshot.FetchStatus status) {
        return snapshot(fixtureId, status, Set.of());
    }

    private static FootballFixtureStatisticsSnapshot snapshot(
            long fixtureId, FootballFixtureStatisticsSnapshot.FetchStatus status,
            Set<String> unknownLabels) {
        return new FootballFixtureStatisticsSnapshot(
                fixtureId, status, "API_FOOTBALL", 200, 2, null,
                unknownLabels, "{}", Instant.now(), 1, List.of());
    }
}
