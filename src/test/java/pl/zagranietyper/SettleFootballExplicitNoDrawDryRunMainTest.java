package pl.zagranietyper;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.SettlementDecision;
import pl.zagranietyper.repository.FootballSettlementRepository;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettleFootballExplicitNoDrawDryRunMainTest {

    @Test
    void drawLoses() {
        assertEquals(
                SettlementDecision.L,
                SettleFootballExplicitNoDrawDryRunMain.settle(2, 2)
        );
    }

    @Test
    void homeWinWins() {
        assertEquals(
                SettlementDecision.W,
                SettleFootballExplicitNoDrawDryRunMain.settle(1, 0)
        );
    }

    @Test
    void awayWinWins() {
        assertEquals(
                SettlementDecision.W,
                SettleFootballExplicitNoDrawDryRunMain.settle(0, 3)
        );
    }

    @Test
    void defaultsToDryRunAndRequiresExactApplyFlag() {
        assertFalse(
                SettleFootballExplicitNoDrawDryRunMain.parseApplyFlag(
                        new String[0]
                )
        );
        assertTrue(
                SettleFootballExplicitNoDrawDryRunMain.parseApplyFlag(
                        new String[]{"--apply"}
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> SettleFootballExplicitNoDrawDryRunMain.parseApplyFlag(
                        new String[]{"apply"}
                )
        );
    }

    @Test
    void acceptsOnlyTheApprovedSnapshot() {
        assertDoesNotThrow(
                () -> SettleFootballExplicitNoDrawDryRunMain
                        .validateApplySafety(approvedSnapshot())
        );
    }

    @Test
    void refusesCountDecisionAndUpdateLegDrift() {
        SettleFootballExplicitNoDrawDryRunMain.AuditSnapshot approved =
                approvedSnapshot();

        assertThrows(
                IllegalStateException.class,
                () -> SettleFootballExplicitNoDrawDryRunMain
                        .validateApplySafety(
                                new SettleFootballExplicitNoDrawDryRunMain
                                        .AuditSnapshot(
                                        4,
                                        approved.parsed(),
                                        approved.wins(),
                                        approved.losses(),
                                        approved.rejected(),
                                        approved.skippedFixture(),
                                        approved.missingFulltime(),
                                        approved.decisions(),
                                        approved.updates()
                                )
                        )
        );

        assertThrows(
                IllegalStateException.class,
                () -> SettleFootballExplicitNoDrawDryRunMain
                        .validateApplySafety(
                                snapshotWith(
                                        Map.of(
                                                7603L, SettlementDecision.W,
                                                17150L, SettlementDecision.W,
                                                18829L, SettlementDecision.L
                                        ),
                                        approved.updates()
                                )
                        )
        );

        assertThrows(
                IllegalStateException.class,
                () -> SettleFootballExplicitNoDrawDryRunMain
                        .validateApplySafety(
                                snapshotWith(
                                        approved.decisions(),
                                        approved.updates().subList(1, 3)
                                )
                        )
        );
    }

    @Test
    void refusesUnsupportedDecision() {
        List<FootballSettlementRepository.SettlementUpdate> updates =
                List.of(
                        new FootballSettlementRepository.SettlementUpdate(
                                7603L,
                                5918L,
                                SettlementDecision.UNSUPPORTED
                        ),
                        approvedSnapshot().updates().get(1),
                        approvedSnapshot().updates().get(2)
                );

        assertThrows(
                IllegalStateException.class,
                () -> SettleFootballExplicitNoDrawDryRunMain
                        .validateApplySafety(
                                snapshotWith(
                                        approvedSnapshot().decisions(),
                                        updates
                                )
                        )
        );
    }

    private static SettleFootballExplicitNoDrawDryRunMain.AuditSnapshot
    approvedSnapshot() {
        Map<Long, SettlementDecision> decisions =
                Map.of(
                        7603L, SettlementDecision.L,
                        17150L, SettlementDecision.W,
                        18829L, SettlementDecision.W
                );

        return snapshotWith(
                decisions,
                List.of(
                        new FootballSettlementRepository.SettlementUpdate(
                                7603L,
                                5918L,
                                SettlementDecision.L
                        ),
                        new FootballSettlementRepository.SettlementUpdate(
                                17150L,
                                12854L,
                                SettlementDecision.W
                        ),
                        new FootballSettlementRepository.SettlementUpdate(
                                18829L,
                                14056L,
                                SettlementDecision.W
                        )
                )
        );
    }

    private static SettleFootballExplicitNoDrawDryRunMain.AuditSnapshot
    snapshotWith(
            Map<Long, SettlementDecision> decisions,
            List<FootballSettlementRepository.SettlementUpdate> updates
    ) {
        return new SettleFootballExplicitNoDrawDryRunMain.AuditSnapshot(
                3,
                3,
                2,
                1,
                0,
                0,
                0,
                decisions,
                updates
        );
    }
}
