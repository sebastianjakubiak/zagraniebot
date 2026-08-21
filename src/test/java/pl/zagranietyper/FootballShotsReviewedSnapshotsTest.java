package pl.zagranietyper;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.*;
import pl.zagranietyper.repository.FootballSettlementRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class FootballShotsReviewedSnapshotsTest {
    @Test void exactSnapshotsPassAndWrongCountHashOrIdsFail() {
        for (var type : List.of(FootballFixtureStatisticType.SHOTS_TOTAL,
                FootballFixtureStatisticType.SHOTS_ON_TARGET)) {
            var values = sample(type);
            var exact = boundary(type, values, 3, FootballShotsReviewedSnapshots.hash(values), ids(values));
            assertTrue(FootballShotsReviewedSnapshots.verify(values, exact).applyReady());
            assertFalse(FootballShotsReviewedSnapshots.verify(values,
                    boundary(type, values, 4, exact.sha256(), ids(values))).countGate());
            assertFalse(FootballShotsReviewedSnapshots.verify(values,
                    boundary(type, values, 3, "0".repeat(64), ids(values))).hashGate());
            assertFalse(FootballShotsReviewedSnapshots.verify(values,
                    boundary(type, values, 3, exact.sha256(), Set.of(1L, 2L, 4L))).legSetGate());
        }
    }

    @Test void statisticsCannotUseEachOthersSnapshot() {
        var total = sample(FootballFixtureStatisticType.SHOTS_TOTAL);
        var target = sample(FootballFixtureStatisticType.SHOTS_ON_TARGET);
        var targetBoundary = boundary(FootballFixtureStatisticType.SHOTS_ON_TARGET, target, 3,
                FootballShotsReviewedSnapshots.hash(target), ids(target));
        var totalBoundary = boundary(FootballFixtureStatisticType.SHOTS_TOTAL, total, 3,
                FootballShotsReviewedSnapshots.hash(total), ids(total));
        assertFalse(FootballShotsReviewedSnapshots.verify(total, targetBoundary).applyReady());
        assertFalse(FootballShotsReviewedSnapshots.verify(target, totalBoundary).applyReady());
    }

    @Test void defaultAndFailedGatePerformZeroWrites() {
        AtomicInteger calls = new AtomicInteger();
        assertEquals(FootballShotsCoverageDryRunMain.ApplyMode.DRY_RUN,
                FootballShotsCoverageDryRunMain.parseMode(new String[0]));
        assertNull(FootballShotsCoverageDryRunMain.applyIfRequested(
                FootballShotsCoverageDryRunMain.ApplyMode.DRY_RUN, gate(true), gate(true),
                List.of(), List.of(), updates -> { calls.incrementAndGet(); return totalSuccess(); }));
        assertThrows(IllegalStateException.class, () -> FootballShotsCoverageDryRunMain.applyIfRequested(
                FootballShotsCoverageDryRunMain.ApplyMode.APPLY_SHOTS_TOTAL, gate(false), gate(true),
                List.of(), List.of(), updates -> { calls.incrementAndGet(); return totalSuccess(); }));
        assertThrows(IllegalStateException.class, () -> FootballShotsCoverageDryRunMain.applyIfRequested(
                FootballShotsCoverageDryRunMain.ApplyMode.APPLY_SHOTS_ON_TARGET, gate(true), gate(false),
                List.of(), List.of(), updates -> { calls.incrementAndGet(); return targetSuccess(); }));
        assertEquals(0, calls.get());
    }

    @Test void eachSuccessfulModeDelegatesOnceToApplyExact() {
        AtomicInteger calls = new AtomicInteger();
        FootballShotsCoverageDryRunMain.applyIfRequested(
                FootballShotsCoverageDryRunMain.ApplyMode.APPLY_SHOTS_TOTAL, gate(true), gate(true),
                List.of(), List.of(), updates -> { calls.incrementAndGet(); return totalSuccess(); });
        FootballShotsCoverageDryRunMain.applyIfRequested(
                FootballShotsCoverageDryRunMain.ApplyMode.APPLY_SHOTS_ON_TARGET, gate(true), gate(true),
                List.of(), List.of(), updates -> { calls.incrementAndGet(); return targetSuccess(); });
        assertEquals(2, calls.get());
    }

    private static List<FootballShotsReviewedSnapshots.Candidate> sample(FootballFixtureStatisticType type) {
        return List.of(candidate(1, type, SettlementDecision.W), candidate(2, type, SettlementDecision.L),
                candidate(3, type, SettlementDecision.V));
    }

    private static FootballShotsReviewedSnapshots.Candidate candidate(long id,
            FootballFixtureStatisticType type, SettlementDecision decision) {
        return new FootballShotsReviewedSnapshots.Candidate(id, id + 100,
                FootballFixtureStatisticCondition.threshold(type,
                        FootballFixtureStatisticCondition.Subject.MATCH,
                        FootballFixtureStatisticCondition.Comparison.OVER,
                        new BigDecimal("4.5")), decision, true);
    }

    private static FootballShotsReviewedSnapshots.Boundary boundary(FootballFixtureStatisticType type,
            List<FootballShotsReviewedSnapshots.Candidate> values, int count, String hash, Set<Long> ids) {
        return new FootballShotsReviewedSnapshots.Boundary(type, count, 1, 1, 1, hash, ids);
    }

    private static Set<Long> ids(List<FootballShotsReviewedSnapshots.Candidate> values) {
        return values.stream().map(FootballShotsReviewedSnapshots.Candidate::legId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static FootballShotsReviewedSnapshots.GateResult gate(boolean pass) {
        return new FootballShotsReviewedSnapshots.GateResult(0, 0, 0, 0, "hash", Set.of(), Set.of(),
                Set.of(), pass, pass, pass, pass);
    }

    private static FootballSettlementRepository.ApplyResult totalSuccess() {
        return new FootballSettlementRepository.ApplyResult(23, 0, 11, 12, 0, 20, 8, 12, 0, 0, 0);
    }

    private static FootballSettlementRepository.ApplyResult targetSuccess() {
        return new FootballSettlementRepository.ApplyResult(16, 0, 9, 7, 0, 14, 7, 7, 0, 0, 0);
    }
}
