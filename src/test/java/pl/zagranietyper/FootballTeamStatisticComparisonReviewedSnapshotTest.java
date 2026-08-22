package pl.zagranietyper;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.repository.FootballSettlementRepository;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class FootballTeamStatisticComparisonReviewedSnapshotTest {
    @Test void immutableBoundaryIsExact() {
        assertEquals(3, FootballTeamStatisticComparisonReviewedSnapshot.EXPECTED_COUNT);
        assertEquals(Set.of(841L, 14182L, 15239L), FootballTeamStatisticComparisonReviewedSnapshot.APPROVED_IDS);
        assertEquals("6b657e5d8410f1c3b7427cf7cb6a47a6eda7330af20aa09dadc50c03e2a2af3e",
                FootballTeamStatisticComparisonReviewedSnapshot.EXPECTED_SHA256);
    }

    @Test void dryRunAndFailedGateNeverWrite() {
        AtomicInteger calls = new AtomicInteger();
        assertFalse(FootballTeamStatisticComparisonCoverageDryRunMain.parseApply(new String[0]));
        assertNull(FootballTeamStatisticComparisonCoverageDryRunMain.applyIfRequested(false, gate(true),
                List.of(), updates -> { calls.incrementAndGet(); return success(); }));
        assertThrows(IllegalStateException.class, () ->
                FootballTeamStatisticComparisonCoverageDryRunMain.applyIfRequested(true, gate(false),
                        List.of(), updates -> { calls.incrementAndGet(); return success(); }));
        assertEquals(0, calls.get());
    }

    @Test void passingGateDelegatesToExactApply() {
        AtomicInteger calls = new AtomicInteger();
        assertEquals(3, FootballTeamStatisticComparisonCoverageDryRunMain.applyIfRequested(true, gate(true),
                List.of(), updates -> { calls.incrementAndGet(); return success(); }).updatedLegs());
        assertEquals(1, calls.get());
    }

    private static FootballTeamStatisticComparisonReviewedSnapshot.Gate gate(boolean pass) {
        return new FootballTeamStatisticComparisonReviewedSnapshot.Gate(3, 3, 0, 0, "hash",
                Set.of(), Set.of(), pass, pass, pass, pass);
    }

    private static FootballSettlementRepository.ApplyResult success() {
        return new FootballSettlementRepository.ApplyResult(3, 0, 3, 0, 0, 3, 3, 0, 0, 0, 0);
    }
}
