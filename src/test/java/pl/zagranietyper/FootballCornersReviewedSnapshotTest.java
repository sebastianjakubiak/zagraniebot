package pl.zagranietyper;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.FootballFixtureStatisticCondition;
import pl.zagranietyper.model.FootballFixtureStatisticType;
import pl.zagranietyper.model.SettlementDecision;
import pl.zagranietyper.repository.FootballSettlementRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FootballCornersReviewedSnapshotTest {

    @Test void exactSnapshotPassesAllGates() {
        List<FootballCornersReviewedSnapshot.Candidate> values = sample();
        var gate = verify(values, Set.of(1L, 2L, 3L), values.size(),
                FootballCornersReviewedSnapshot.hashCandidates(values));
        assertTrue(gate.applyReady());
    }

    @Test void wrongCountAndWrongHashFailIndependently() {
        List<FootballCornersReviewedSnapshot.Candidate> values = sample();
        assertFalse(verify(values, ids(values), 4,
                FootballCornersReviewedSnapshot.hashCandidates(values)).countGate());
        assertFalse(verify(values, ids(values), 3, "0".repeat(64)).hashGate());
    }

    @Test void missingUnexpectedAndSameSizeDifferentLegSetFail() {
        List<FootballCornersReviewedSnapshot.Candidate> values = sample();
        assertFalse(verify(values.subList(0, 2), ids(values), 3,
                FootballCornersReviewedSnapshot.hashCandidates(values)).legSetGate());
        assertFalse(verify(values, Set.of(1L, 2L, 4L), 3,
                FootballCornersReviewedSnapshot.hashCandidates(values)).legSetGate());
        assertFalse(verify(values, Set.of(1L, 2L, 4L), 3,
                FootballCornersReviewedSnapshot.hashCandidates(values)).applyReady());
    }

    @Test void changedConditionOrDecisionFailsHashWithSameIds() {
        List<FootballCornersReviewedSnapshot.Candidate> values = sample();
        String approvedHash = FootballCornersReviewedSnapshot.hashCandidates(values);
        var changed = List.of(values.get(0), values.get(1), candidate(3, SettlementDecision.W,
                FootballFixtureStatisticCondition.Subject.AWAY, "4.5"));
        var gate = verify(changed, ids(values), 3, approvedHash);
        assertTrue(gate.legSetGate());
        assertFalse(gate.hashGate());
    }

    @Test void intentionallyExcludedLegsCannotEnterReviewedSnapshot() {
        assertFalse(FootballCornersReviewedSnapshot.APPROVED_LEG_IDS.contains(14362L));
        assertFalse(FootballCornersReviewedSnapshot.APPROVED_LEG_IDS.contains(15065L));
        assertEquals(233, FootballCornersReviewedSnapshot.APPROVED_LEG_IDS.size());
    }

    @Test void defaultsToDryRunAndRequiresExactApplyFlag() {
        assertFalse(FootballCornersCoverageDryRunMain.parseApplyFlag(new String[0]));
        assertTrue(FootballCornersCoverageDryRunMain.parseApplyFlag(new String[]{"--apply"}));
        assertThrows(IllegalArgumentException.class,
                () -> FootballCornersCoverageDryRunMain.parseApplyFlag(new String[]{"apply"}));
    }

    @Test void dryRunAndFailedGateNeverCallRepository() {
        AtomicInteger calls = new AtomicInteger();
        var ready = gate(true);
        assertNull(FootballCornersCoverageDryRunMain.applyIfRequested(false, ready, sample(),
                updates -> { calls.incrementAndGet(); return success(); }));
        assertThrows(IllegalStateException.class,
                () -> FootballCornersCoverageDryRunMain.applyIfRequested(true, gate(false), sample(),
                        updates -> { calls.incrementAndGet(); return success(); }));
        assertEquals(0, calls.get());
    }

    @Test void successfulGateAllowsRepositoryApply() {
        AtomicInteger calls = new AtomicInteger();
        var result = FootballCornersCoverageDryRunMain.applyIfRequested(true, gate(true), sample(),
                updates -> { calls.incrementAndGet(); assertEquals(3, updates.size()); return success(); });
        assertEquals(1, calls.get());
        assertEquals(233, result.updatedLegs());
    }

    private static FootballCornersReviewedSnapshot.GateResult verify(
            List<FootballCornersReviewedSnapshot.Candidate> candidates, Set<Long> ids,
            int count, String hash) {
        return FootballCornersReviewedSnapshot.verify(candidates,
                new FootballCornersReviewedSnapshot.Boundary(count, hash, ids, 1, 1, 1));
    }

    private static List<FootballCornersReviewedSnapshot.Candidate> sample() {
        return List.of(
                candidate(1, SettlementDecision.W, FootballFixtureStatisticCondition.Subject.MATCH, "8.5"),
                candidate(2, SettlementDecision.L, FootballFixtureStatisticCondition.Subject.HOME, "4.5"),
                candidate(3, SettlementDecision.V, FootballFixtureStatisticCondition.Subject.AWAY, "5"));
    }

    private static FootballCornersReviewedSnapshot.Candidate candidate(
            long id, SettlementDecision decision,
            FootballFixtureStatisticCondition.Subject subject, String threshold) {
        return new FootballCornersReviewedSnapshot.Candidate(id, id + 100,
                FootballFixtureStatisticCondition.threshold(FootballFixtureStatisticType.CORNERS,
                        subject, FootballFixtureStatisticCondition.Comparison.OVER,
                        new BigDecimal(threshold)), decision, true);
    }

    private static Set<Long> ids(List<FootballCornersReviewedSnapshot.Candidate> values) {
        return values.stream().map(FootballCornersReviewedSnapshot.Candidate::legId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static FootballCornersReviewedSnapshot.GateResult gate(boolean pass) {
        return new FootballCornersReviewedSnapshot.GateResult(233, 121, 112, 0, "hash", Set.of(),
                Set.of(), Set.of(), pass, pass, pass, pass);
    }

    private static FootballSettlementRepository.ApplyResult success() {
        return new FootballSettlementRepository.ApplyResult(
                233, 0, 121, 112, 0, 200, 100, 80, 0, 20, 0);
    }
}
