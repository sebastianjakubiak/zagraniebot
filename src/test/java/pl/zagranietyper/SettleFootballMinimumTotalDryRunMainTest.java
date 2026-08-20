package pl.zagranietyper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.zagranietyper.model.SettlementDecision;
import pl.zagranietyper.repository.FootballSettlementRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettleFootballMinimumTotalDryRunMainTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Co najmniej dwa gole",
            "W meczu padnie minimum 3 bramki",
            "Co najmniej 2,5 gola",
            "Co najmniej dwa gole Lazio"
    })
    void auditIncludesBothSupportedAndRejectedMinimumGoalLikeRows(
            String title
    ) {
        assertTrue(
                SettleFootballMinimumTotalDryRunMain.looksMinimumGoalLike(
                        title
                )
        );
    }

    @Test
    void defaultsToDryRunAndRequiresExactApplyFlag() {
        assertFalse(
                SettleFootballMinimumTotalDryRunMain.parseApplyFlag(
                        new String[0]
                )
        );

        assertTrue(
                SettleFootballMinimumTotalDryRunMain.parseApplyFlag(
                        new String[]{"--apply"}
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> SettleFootballMinimumTotalDryRunMain.parseApplyFlag(
                        new String[]{"apply"}
                )
        );
    }

    @Test
    void acceptsOnlyTheExactAuditedSnapshot() {
        assertDoesNotThrow(
                () -> SettleFootballMinimumTotalDryRunMain.validateApplySafety(
                        approvedSnapshot()
                )
        );
    }

    @Test
    void refusesChangedCounterDecisionAndRejectedSet() {
        SettleFootballMinimumTotalDryRunMain.AuditSnapshot approved =
                approvedSnapshot();

        assertThrows(
                IllegalStateException.class,
                () -> SettleFootballMinimumTotalDryRunMain.validateApplySafety(
                        new SettleFootballMinimumTotalDryRunMain.AuditSnapshot(
                                31,
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

        Map<Long, SettlementDecision> changedDecision =
                new LinkedHashMap<>(approved.decisions());
        changedDecision.put(252L, SettlementDecision.L);

        assertThrows(
                IllegalStateException.class,
                () -> SettleFootballMinimumTotalDryRunMain.validateApplySafety(
                        snapshotWith(
                                changedDecision,
                                approved.rejectedLegIds(),
                                approved.updates()
                        )
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> SettleFootballMinimumTotalDryRunMain.validateApplySafety(
                        snapshotWith(
                                approved.decisions(),
                                Set.of(117L),
                                approved.updates()
                        )
                )
        );
    }

    @Test
    void refusesUnsupportedOrChangedUpdateBatch() {
        SettleFootballMinimumTotalDryRunMain.AuditSnapshot approved =
                approvedSnapshot();

        List<FootballSettlementRepository.SettlementUpdate> unsupported =
                new ArrayList<>(approved.updates());
        unsupported.set(
                0,
                new FootballSettlementRepository.SettlementUpdate(
                        252L,
                        1L,
                        SettlementDecision.UNSUPPORTED
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> SettleFootballMinimumTotalDryRunMain.validateApplySafety(
                        snapshotWith(
                                approved.decisions(),
                                approved.rejectedLegIds(),
                                unsupported
                        )
                )
        );

        List<FootballSettlementRepository.SettlementUpdate> missing =
                approved.updates().subList(1, approved.updates().size());

        assertThrows(
                IllegalStateException.class,
                () -> SettleFootballMinimumTotalDryRunMain.validateApplySafety(
                        snapshotWith(
                                approved.decisions(),
                                approved.rejectedLegIds(),
                                missing
                        )
                )
        );
    }

    private static SettleFootballMinimumTotalDryRunMain.AuditSnapshot approvedSnapshot() {
        Map<Long, SettlementDecision> decisions =
                Map.ofEntries(
                        Map.entry(252L, SettlementDecision.W),
                        Map.entry(2199L, SettlementDecision.W),
                        Map.entry(2422L, SettlementDecision.W),
                        Map.entry(2568L, SettlementDecision.W),
                        Map.entry(7749L, SettlementDecision.W),
                        Map.entry(8421L, SettlementDecision.W),
                        Map.entry(11780L, SettlementDecision.W),
                        Map.entry(12098L, SettlementDecision.W),
                        Map.entry(14862L, SettlementDecision.W),
                        Map.entry(15104L, SettlementDecision.W),
                        Map.entry(16038L, SettlementDecision.W),
                        Map.entry(17307L, SettlementDecision.W),
                        Map.entry(20097L, SettlementDecision.W),
                        Map.entry(1792L, SettlementDecision.L),
                        Map.entry(2452L, SettlementDecision.L),
                        Map.entry(10767L, SettlementDecision.L),
                        Map.entry(11238L, SettlementDecision.L),
                        Map.entry(11948L, SettlementDecision.L),
                        Map.entry(12162L, SettlementDecision.L),
                        Map.entry(14503L, SettlementDecision.L)
                );

        List<FootballSettlementRepository.SettlementUpdate> updates =
                decisions.entrySet()
                        .stream()
                        .map(entry -> new FootballSettlementRepository.SettlementUpdate(
                                entry.getKey(),
                                entry.getKey() + 1000,
                                entry.getValue()
                        ))
                        .toList();

        return snapshotWith(
                decisions,
                Set.of(
                        117L, 280L, 857L, 876L, 1269L, 1277L,
                        2170L, 2367L, 9214L, 12225L, 13707L, 20082L
                ),
                updates
        );
    }

    private static SettleFootballMinimumTotalDryRunMain.AuditSnapshot snapshotWith(
            Map<Long, SettlementDecision> decisions,
            Set<Long> rejectedLegIds,
            List<FootballSettlementRepository.SettlementUpdate> updates
    ) {
        return new SettleFootballMinimumTotalDryRunMain.AuditSnapshot(
                32,
                20,
                13,
                7,
                12,
                0,
                0,
                decisions,
                rejectedLegIds,
                updates
        );
    }
}
