package pl.zagranietyper;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.SettlementDecision;
import pl.zagranietyper.repository.FootballSettlementRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FootballTeamGoalsCoverageDryRunMainTest {
    private static final long[] W = {20, 117, 1269, 1277, 2156, 2170, 2367, 8737, 9214,
            9733, 9941, 10048, 10171, 11770, 14248, 19221, 20082};
    private static final long[] L = {142, 280, 857, 872, 876, 7602, 11486, 14018, 18350};

    @Test
    void defaultsToDryRunAndRequiresExactApplyFlag() {
        assertFalse(FootballTeamGoalsCoverageDryRunMain.parseApplyFlag(new String[0]));
        assertTrue(FootballTeamGoalsCoverageDryRunMain.parseApplyFlag(new String[]{"--apply"}));
        assertThrows(IllegalArgumentException.class,
                () -> FootballTeamGoalsCoverageDryRunMain.parseApplyFlag(new String[]{"apply"}));
    }

    @Test
    void acceptsOnlyExactAuditedSnapshot() {
        assertDoesNotThrow(() -> FootballTeamGoalsCoverageDryRunMain.validateApplySafety(approved()));
        var approved = approved();
        assertThrows(IllegalStateException.class,
                () -> FootballTeamGoalsCoverageDryRunMain.validateApplySafety(newSnapshot(
                        1169, approved.decisions(), approved.updates())));
        Map<Long, SettlementDecision> changed = new LinkedHashMap<>(approved.decisions());
        changed.put(20L, SettlementDecision.L);
        assertThrows(IllegalStateException.class,
                () -> FootballTeamGoalsCoverageDryRunMain.validateApplySafety(newSnapshot(
                        1170, changed, approved.updates())));
    }

    @Test
    void refusesUnsupportedOrChangedUpdateSet() {
        var approved = approved();
        List<FootballSettlementRepository.SettlementUpdate> unsupported = new ArrayList<>(approved.updates());
        unsupported.set(0, new FootballSettlementRepository.SettlementUpdate(
                unsupported.getFirst().legId(), unsupported.getFirst().betId(), SettlementDecision.UNSUPPORTED));
        assertThrows(IllegalStateException.class,
                () -> FootballTeamGoalsCoverageDryRunMain.validateApplySafety(newSnapshot(
                        1170, approved.decisions(), unsupported)));

        List<FootballSettlementRepository.SettlementUpdate> missing = new ArrayList<>(approved.updates());
        missing.removeLast();
        assertThrows(IllegalStateException.class,
                () -> FootballTeamGoalsCoverageDryRunMain.validateApplySafety(newSnapshot(
                        1170, approved.decisions(), missing)));
    }

    private static FootballTeamGoalsCoverageDryRunMain.AuditSnapshot approved() {
        Map<Long, SettlementDecision> decisions = new LinkedHashMap<>();
        List<FootballSettlementRepository.SettlementUpdate> updates = new ArrayList<>();
        for (long id : W) add(decisions, updates, id, SettlementDecision.W);
        for (long id : L) add(decisions, updates, id, SettlementDecision.L);
        return newSnapshot(1170, decisions, updates);
    }

    private static void add(Map<Long, SettlementDecision> decisions,
                            List<FootballSettlementRepository.SettlementUpdate> updates,
                            long id, SettlementDecision decision) {
        decisions.put(id, decision);
        updates.add(new FootballSettlementRepository.SettlementUpdate(id, id + 100_000, decision));
    }

    private static FootballTeamGoalsCoverageDryRunMain.AuditSnapshot newSnapshot(
            int eligible, Map<Long, SettlementDecision> decisions,
            List<FootballSettlementRepository.SettlementUpdate> updates) {
        return new FootballTeamGoalsCoverageDryRunMain.AuditSnapshot(
                eligible, 319, 26, 17, 9, 0, 293, 0, 50, 78, 0, decisions, updates);
    }
}
