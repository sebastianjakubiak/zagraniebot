package pl.zagranietyper.service;

import pl.zagranietyper.model.FootballMarket;
import pl.zagranietyper.model.FootballScore;
import pl.zagranietyper.model.SettlementDecision;

import java.math.BigDecimal;

public final class FootballMarketSettlementEngine {

    public SettlementDecision settle(
            FootballMarket market,
            FootballScore score
    ) {
        if (
                market == null
                        || score == null
        ) {
            return SettlementDecision.UNSUPPORTED;
        }

        boolean hasWin =
                false;

        boolean hasVoid =
                false;

        for (
                FootballMarket.Condition condition :
                market.conditions()
        ) {
            SettlementDecision decision =
                    settleCondition(
                            condition,
                            score
                    );

            if (
                    decision == SettlementDecision.UNSUPPORTED
            ) {
                return SettlementDecision.UNSUPPORTED;
            }

            /*
             * Przy warunkach połączonych AND jedna porażka
             * zabija cały selection.
             */
            if (
                    decision == SettlementDecision.L
            ) {
                return SettlementDecision.L;
            }

            if (
                    decision == SettlementDecision.W
            ) {
                hasWin =
                        true;
            }

            if (
                    decision == SettlementDecision.V
            ) {
                hasVoid =
                        true;
            }
        }

        /*
         * Wszystkie warunki void.
         */
        if (
                hasVoid
                        && !hasWin
        ) {
            return SettlementDecision.V;
        }

        /*
         * W + ewentualny V = W.
         *
         * Jest to zachowanie jak dla składników kuponu:
         * void nie powoduje przegranej pozostałych warunków.
         */
        if (
                hasWin
        ) {
            return SettlementDecision.W;
        }

        return SettlementDecision.UNSUPPORTED;
    }

    private SettlementDecision settleCondition(
            FootballMarket.Condition condition,
            FootballScore score
    ) {
        if (
                condition instanceof FootballMarket.MatchResult result
        ) {
            return settleMatchResult(
                    result,
                    score
            );
        }

        if (
                condition instanceof FootballMarket.DoubleChance doubleChance
        ) {
            return settleDoubleChance(
                    doubleChance,
                    score
            );
        }

        if (
                condition instanceof FootballMarket.TotalGoals totalGoals
        ) {
            return settleTotalGoals(
                    totalGoals,
                    score
            );
        }

        if (
                condition instanceof FootballMarket.MinimumTotalGoals minimumTotalGoals
        ) {
            return score.totalGoals()
                    >= minimumTotalGoals.minimum()
                    ? SettlementDecision.W
                    : SettlementDecision.L;
        }

        if (
                condition instanceof FootballMarket.MatchGoalRange range
        ) {
            int total = score.totalGoals();

            return total >= range.minimum()
                    && total <= range.maximum()
                    ? SettlementDecision.W
                    : SettlementDecision.L;
        }

        if (
                condition instanceof FootballMarket.BothTeamsToScore btts
        ) {
            return settleBtts(
                    btts,
                    score
            );
        }

        return SettlementDecision.UNSUPPORTED;
    }

    private SettlementDecision settleMatchResult(
            FootballMarket.MatchResult market,
            FootballScore score
    ) {
        boolean won =
                switch (
                        market.selection()
                        ) {
                    case HOME ->
                            score.homeWin();

                    case DRAW ->
                            score.draw();

                    case AWAY ->
                            score.awayWin();
                };

        return won
                ? SettlementDecision.W
                : SettlementDecision.L;
    }

    private SettlementDecision settleDoubleChance(
            FootballMarket.DoubleChance market,
            FootballScore score
    ) {
        boolean won =
                switch (
                        market.selection()
                        ) {
                    case HOME_OR_DRAW ->
                            score.home() >= score.away();

                    case AWAY_OR_DRAW ->
                            score.away() >= score.home();

                    case HOME_OR_AWAY ->
                            !score.draw();
                };

        return won
                ? SettlementDecision.W
                : SettlementDecision.L;
    }

    private SettlementDecision settleTotalGoals(
            FootballMarket.TotalGoals market,
            FootballScore score
    ) {
        BigDecimal actual =
                BigDecimal.valueOf(
                        score.totalGoals()
                );

        int comparison =
                actual.compareTo(
                        market.line()
                );

        if (
                comparison == 0
        ) {
            return SettlementDecision.V;
        }

        boolean won =
                switch (
                        market.direction()
                        ) {
                    case OVER ->
                            comparison > 0;

                    case UNDER ->
                            comparison < 0;
                };

        return won
                ? SettlementDecision.W
                : SettlementDecision.L;
    }

    private SettlementDecision settleBtts(
            FootballMarket.BothTeamsToScore market,
            FootballScore score
    ) {
        boolean occurred =
                score.bothTeamsScored();

        return occurred == market.yes()
                ? SettlementDecision.W
                : SettlementDecision.L;
    }
}
