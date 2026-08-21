package pl.zagranietyper.service;

import pl.zagranietyper.model.*;

import java.util.ArrayList;
import java.util.List;

/** AND settlement over already-supported score and fixture-statistic primitives. */
public final class FootballCompositeSettlementEngine {
    private final UnifiedFootballSettlementEngine scoreEngine = new UnifiedFootballSettlementEngine();
    private final FootballFixtureStatisticSettlementEngine statisticEngine =
            new FootballFixtureStatisticSettlementEngine();

    public Result settle(FootballCompositeCondition composite, FootballScoreSnapshot score,
                         FootballFixtureStatisticsSnapshot statistics) {
        List<SettlementDecision> branchDecisions = new ArrayList<>();
        for (var branch : composite.branches()) {
            SettlementDecision decision = switch (branch) {
                case FootballCompositeCondition.ScoreBranch value -> scoreEngine.settle(value.market(), score);
                case FootballCompositeCondition.StatisticBranch value ->
                        statistics == null ? SettlementDecision.UNSUPPORTED
                                : statisticEngine.settle(value.condition(), statistics);
            };
            branchDecisions.add(decision);
        }
        return new Result(List.copyOf(branchDecisions), combine(branchDecisions));
    }

    public static SettlementDecision combine(List<SettlementDecision> decisions) {
        if (decisions == null || decisions.size() < 2) return SettlementDecision.UNSUPPORTED;
        if (decisions.contains(SettlementDecision.L)) return SettlementDecision.L;
        if (decisions.contains(SettlementDecision.UNSUPPORTED)) return SettlementDecision.UNSUPPORTED;
        return decisions.stream().allMatch(d -> d == SettlementDecision.V)
                ? SettlementDecision.V : SettlementDecision.W;
    }

    public record Result(List<SettlementDecision> branchDecisions, SettlementDecision decision) {}
}
