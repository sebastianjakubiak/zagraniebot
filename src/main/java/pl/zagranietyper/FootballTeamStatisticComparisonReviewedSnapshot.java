package pl.zagranietyper;

import pl.zagranietyper.model.SettlementDecision;
import java.util.*;

public final class FootballTeamStatisticComparisonReviewedSnapshot {
    public static final int EXPECTED_COUNT = 3, EXPECTED_W = 3, EXPECTED_L = 0, EXPECTED_V = 0;
    public static final String EXPECTED_SHA256 = "6b657e5d8410f1c3b7427cf7cb6a47a6eda7330af20aa09dadc50c03e2a2af3e";
    public static final Set<Long> APPROVED_IDS = Set.of(841L, 14182L, 15239L);
    private FootballTeamStatisticComparisonReviewedSnapshot() {}

    public static Gate verify(List<FootballTeamStatisticComparisonCoverageDryRunMain.Row> rows,
                              boolean noFalsePositives) throws Exception {
        Set<Long> ids = new HashSet<>();
        boolean unique = rows.stream().allMatch(row -> ids.add(row.legId()));
        Set<Long> missing = new TreeSet<>(APPROVED_IDS); missing.removeAll(ids);
        Set<Long> unexpected = new TreeSet<>(ids); unexpected.removeAll(APPROVED_IDS);
        int w = count(rows, SettlementDecision.W), l = count(rows, SettlementDecision.L),
                v = count(rows, SettlementDecision.V);
        String hash = FootballTeamStatisticComparisonCoverageDryRunMain.sha(rows);
        return new Gate(rows.size(), w, l, v, hash, Set.copyOf(missing), Set.copyOf(unexpected),
                rows.size() == EXPECTED_COUNT, EXPECTED_SHA256.equals(hash), unique && ids.equals(APPROVED_IDS),
                noFalsePositives && unique && w == EXPECTED_W && l == EXPECTED_L && v == EXPECTED_V);
    }

    private static int count(List<FootballTeamStatisticComparisonCoverageDryRunMain.Row> rows,
                             SettlementDecision decision) {
        return (int) rows.stream().filter(row -> row.decision() == decision).count();
    }

    public record Gate(int count, int w, int l, int v, String sha256, Set<Long> missing,
                       Set<Long> unexpected, boolean countGate, boolean hashGate,
                       boolean legSetGate, boolean safetyGate) {
        public boolean ready() { return countGate && hashGate && legSetGate && safetyGate; }
    }
}
