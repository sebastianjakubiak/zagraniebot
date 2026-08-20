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

class SettleFootballPonadTotalDryRunMainTest {

    @Test
    void defaultsToDryRunAndRequiresExactApplyFlag() {
        assertFalse(
                SettleFootballPonadTotalDryRunMain.parseApplyFlag(new String[0])
        );
        assertTrue(
                SettleFootballPonadTotalDryRunMain.parseApplyFlag(
                        new String[]{"--apply"}
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> SettleFootballPonadTotalDryRunMain.parseApplyFlag(
                        new String[]{"apply"}
                )
        );
    }

    @Test
    void acceptsOnlyTheApprovedSnapshot() {
        assertDoesNotThrow(
                () -> SettleFootballPonadTotalDryRunMain.validateApplySafety(
                        approvedSnapshot()
                )
        );
    }

    @Test
    void refusesCounterDecisionRejectedAndUpdateDrift() {
        SettleFootballPonadTotalDryRunMain.AuditSnapshot approved =
                approvedSnapshot();

        assertThrows(
                IllegalStateException.class,
                () -> SettleFootballPonadTotalDryRunMain.validateApplySafety(
                        new SettleFootballPonadTotalDryRunMain.AuditSnapshot(
                                2,
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
                () -> SettleFootballPonadTotalDryRunMain.validateApplySafety(
                        snapshotWith(
                                Map.of(
                                        1171L, SettlementDecision.L,
                                        9511L, SettlementDecision.W
                                ),
                                approved.rejectedLegIds(),
                                approved.updates()
                        )
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> SettleFootballPonadTotalDryRunMain.validateApplySafety(
                        snapshotWith(
                                approved.decisions(),
                                Set.of(9999L),
                                approved.updates()
                        )
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> SettleFootballPonadTotalDryRunMain.validateApplySafety(
                        snapshotWith(
                                approved.decisions(),
                                approved.rejectedLegIds(),
                                approved.updates().subList(1, 2)
                        )
                )
        );
    }

    @Test
    void refusesUnsupportedUpdate() {
        SettleFootballPonadTotalDryRunMain.AuditSnapshot approved =
                approvedSnapshot();

        List<FootballSettlementRepository.SettlementUpdate> updates =
                List.of(
                        new FootballSettlementRepository.SettlementUpdate(
                                1171L,
                                973L,
                                SettlementDecision.UNSUPPORTED
                        ),
                        approved.updates().get(1)
                );

        assertThrows(
                IllegalStateException.class,
                () -> SettleFootballPonadTotalDryRunMain.validateApplySafety(
                        snapshotWith(
                                approved.decisions(),
                                approved.rejectedLegIds(),
                                updates
                        )
                )
        );
    }

    private static SettleFootballPonadTotalDryRunMain.AuditSnapshot approvedSnapshot() {
        Map<Long, SettlementDecision> decisions =
                Map.of(
                        1171L, SettlementDecision.W,
                        9511L, SettlementDecision.W
                );

        return snapshotWith(
                decisions,
                Set.of(3342L),
                List.of(
                        new FootballSettlementRepository.SettlementUpdate(
                                1171L,
                                973L,
                                SettlementDecision.W
                        ),
                        new FootballSettlementRepository.SettlementUpdate(
                                9511L,
                                7339L,
                                SettlementDecision.W
                        )
                )
        );
    }

    private static SettleFootballPonadTotalDryRunMain.AuditSnapshot snapshotWith(
            Map<Long, SettlementDecision> decisions,
            Set<Long> rejectedLegIds,
            List<FootballSettlementRepository.SettlementUpdate> updates
    ) {
        return new SettleFootballPonadTotalDryRunMain.AuditSnapshot(
                3,
                2,
                2,
                0,
                1,
                0,
                0,
                decisions,
                rejectedLegIds,
                updates
        );
    }
}
