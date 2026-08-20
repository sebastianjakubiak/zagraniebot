package pl.zagranietyper;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.SettlementDecision;
import pl.zagranietyper.repository.FootballSettlementRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettleFootballMatchGoalRangeDryRunMainTest {

    @Test
    void defaultsToDryRunAndRequiresExactApplyFlag() {
        assertFalse(
                SettleFootballMatchGoalRangeDryRunMain.parseApplyFlag(new String[0])
        );
        assertTrue(
                SettleFootballMatchGoalRangeDryRunMain.parseApplyFlag(
                        new String[]{"--apply"}
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> SettleFootballMatchGoalRangeDryRunMain.parseApplyFlag(
                        new String[]{"apply"}
                )
        );
    }

    @Test
    void acceptsOnlyTheApprovedSnapshot() {
        assertDoesNotThrow(
                () -> SettleFootballMatchGoalRangeDryRunMain.validateApplySafety(
                        approvedSnapshot()
                )
        );
    }

    @Test
    void refusesCounterDecisionRejectedAndUpdateDrift() {
        SettleFootballMatchGoalRangeDryRunMain.AuditSnapshot approved =
                approvedSnapshot();

        assertThrows(
                IllegalStateException.class,
                () -> SettleFootballMatchGoalRangeDryRunMain.validateApplySafety(
                        new SettleFootballMatchGoalRangeDryRunMain.AuditSnapshot(
                                10,
                                approved.parsed(),
                                approved.wins(),
                                approved.losses(),
                                approved.rejected(),
                                approved.skippedFixture(),
                                approved.missingFulltime(),
                                approved.decisions(),
                                approved.rejectedLegIds(),
                                approved.updates()
                        )
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> SettleFootballMatchGoalRangeDryRunMain.validateApplySafety(
                        snapshotWith(
                                Map.of(2573L, SettlementDecision.W),
                                approved.rejectedLegIds(),
                                approved.updates()
                        )
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> SettleFootballMatchGoalRangeDryRunMain.validateApplySafety(
                        snapshotWith(
                                approved.decisions(),
                                Set.of(20L),
                                approved.updates()
                        )
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> SettleFootballMatchGoalRangeDryRunMain.validateApplySafety(
                        snapshotWith(
                                approved.decisions(),
                                approved.rejectedLegIds(),
                                List.of()
                        )
                )
        );
    }

    @Test
    void refusesUnsupportedUpdate() {
        assertThrows(
                IllegalStateException.class,
                () -> SettleFootballMatchGoalRangeDryRunMain.validateApplySafety(
                        snapshotWith(
                                Map.of(2573L, SettlementDecision.L),
                                rejectedLegIds(),
                                List.of(
                                        new FootballSettlementRepository.SettlementUpdate(
                                                2573L,
                                                2046L,
                                                SettlementDecision.UNSUPPORTED
                                        )
                                )
                        )
                )
        );
    }

    private static SettleFootballMatchGoalRangeDryRunMain.AuditSnapshot approvedSnapshot() {
        return snapshotWith(
                Map.of(2573L, SettlementDecision.L),
                rejectedLegIds(),
                List.of(
                        new FootballSettlementRepository.SettlementUpdate(
                                2573L,
                                2046L,
                                SettlementDecision.L
                        )
                )
        );
    }

    private static Set<Long> rejectedLegIds() {
        return Set.of(
                20L, 142L, 872L, 2156L, 7602L,
                8737L, 9733L, 10171L, 11486L, 11770L
        );
    }

    private static SettleFootballMatchGoalRangeDryRunMain.AuditSnapshot snapshotWith(
            Map<Long, SettlementDecision> decisions,
            Set<Long> rejectedLegIds,
            List<FootballSettlementRepository.SettlementUpdate> updates
    ) {
        return new SettleFootballMatchGoalRangeDryRunMain.AuditSnapshot(
                11,
                1,
                0,
                1,
                10,
                0,
                0,
                decisions,
                rejectedLegIds,
                updates
        );
    }
}
