package pl.zagranietyper.service;

import pl.zagranietyper.model.FootballScore;
import pl.zagranietyper.model.FootballScoreSnapshot;
import pl.zagranietyper.model.SettlementDecision;
import pl.zagranietyper.model.UnifiedFootballMarket;

import java.math.BigDecimal;
import java.util.Optional;

/** Settles unified semantic conditions without parsing or data access. */
public final class UnifiedFootballSettlementEngine {

    public SettlementDecision settle(
            UnifiedFootballMarket market,
            FootballScoreSnapshot snapshot
    ) {
        if (market == null || snapshot == null) {
            return SettlementDecision.UNSUPPORTED;
        }

        boolean hasWin = false;
        boolean hasVoid = false;

        for (UnifiedFootballMarket.Condition condition : market.conditions()) {
            if (!snapshot.hasScore(condition.period())) {
                return SettlementDecision.UNSUPPORTED;
            }
        }

        for (UnifiedFootballMarket.Condition condition : market.conditions()) {
            Optional<FootballScore> score = snapshot.score(condition.period());

            SettlementDecision decision =
                    settleCondition(condition, score.orElseThrow());

            if (decision == SettlementDecision.UNSUPPORTED) {
                return SettlementDecision.UNSUPPORTED;
            }

            if (decision == SettlementDecision.L) {
                return SettlementDecision.L;
            }

            if (decision == SettlementDecision.W) {
                hasWin = true;
            }

            if (decision == SettlementDecision.V) {
                hasVoid = true;
            }
        }

        if (hasVoid && !hasWin) {
            return SettlementDecision.V;
        }

        if (hasWin) {
            return SettlementDecision.W;
        }

        return SettlementDecision.UNSUPPORTED;
    }

    private static SettlementDecision settleCondition(
            UnifiedFootballMarket.Condition condition,
            FootballScore score
    ) {
        if (condition instanceof UnifiedFootballMarket.Result result) {
            return settleResult(result, score);
        }

        if (condition instanceof UnifiedFootballMarket.TotalGoals total) {
            return settleTotal(total, score);
        }

        if (condition instanceof UnifiedFootballMarket.MinimumGoals minimum) {
            return goals(minimum.subject(), score) >= minimum.minimum()
                    ? SettlementDecision.W
                    : SettlementDecision.L;
        }

        if (condition instanceof UnifiedFootballMarket.GoalRange range) {
            int actual = goals(range.subject(), score);

            return actual >= range.minimum() && actual <= range.maximum()
                    ? SettlementDecision.W
                    : SettlementDecision.L;
        }

        if (condition instanceof UnifiedFootballMarket.TeamToScore toScore) {
            boolean scored = goals(toScore.subject(), score) > 0;

            return scored == toScore.expected()
                    ? SettlementDecision.W
                    : SettlementDecision.L;
        }

        if (condition instanceof UnifiedFootballMarket.BothTeamsToScore btts) {
            return score.bothTeamsScored() == btts.expected()
                    ? SettlementDecision.W
                    : SettlementDecision.L;
        }

        return SettlementDecision.UNSUPPORTED;
    }

    private static SettlementDecision settleResult(
            UnifiedFootballMarket.Result condition,
            FootballScore score
    ) {
        boolean won = switch (condition.selection()) {
            case HOME -> score.homeWin();
            case DRAW -> score.draw();
            case AWAY -> score.awayWin();
            case HOME_OR_DRAW -> score.home() >= score.away();
            case AWAY_OR_DRAW -> score.away() >= score.home();
            case HOME_OR_AWAY -> !score.draw();
        };

        return won ? SettlementDecision.W : SettlementDecision.L;
    }

    private static SettlementDecision settleTotal(
            UnifiedFootballMarket.TotalGoals condition,
            FootballScore score
    ) {
        BigDecimal actual = BigDecimal.valueOf(
                goals(condition.subject(), score)
        );
        int comparison = actual.compareTo(condition.line());

        if (comparison == 0) {
            return SettlementDecision.V;
        }

        boolean won = switch (condition.direction()) {
            case OVER -> comparison > 0;
            case UNDER -> comparison < 0;
        };

        return won ? SettlementDecision.W : SettlementDecision.L;
    }

    private static int goals(
            UnifiedFootballMarket.GoalSubject subject,
            FootballScore score
    ) {
        return switch (subject) {
            case MATCH -> score.totalGoals();
            case HOME -> score.home();
            case AWAY -> score.away();
        };
    }
}
