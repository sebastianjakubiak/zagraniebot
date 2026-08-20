package pl.zagranietyper.service;

import pl.zagranietyper.model.FootballScore;
import pl.zagranietyper.model.FootballTeamMarket;
import pl.zagranietyper.model.SettlementDecision;

import java.math.BigDecimal;

public final class FootballTeamMarketSettlementEngine {

    public SettlementDecision settle(
            FootballTeamMarket market,
            FootballScore score
    ) {
        if (
                market == null
                        || score == null
        ) {
            return SettlementDecision.UNSUPPORTED;
        }

        if (
                market instanceof FootballTeamMarket.TeamTotalGoals total
        ) {
            return settleTeamTotal(
                    total,
                    score
            );
        }

        if (
                market instanceof FootballTeamMarket.TeamToScore toScore
        ) {
            return settleTeamToScore(
                    toScore,
                    score
            );
        }

        return SettlementDecision.UNSUPPORTED;
    }

    private static SettlementDecision settleTeamTotal(
            FootballTeamMarket.TeamTotalGoals market,
            FootballScore score
    ) {
        int goals =
                goalsForSide(
                        market.side(),
                        score
                );

        BigDecimal actual =
                BigDecimal.valueOf(
                        goals
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

        return switch (
                market.direction()
                ) {
            case OVER ->
                    comparison > 0
                            ? SettlementDecision.W
                            : SettlementDecision.L;

            case UNDER ->
                    comparison < 0
                            ? SettlementDecision.W
                            : SettlementDecision.L;
        };
    }

    private static SettlementDecision settleTeamToScore(
            FootballTeamMarket.TeamToScore market,
            FootballScore score
    ) {
        int goals =
                goalsForSide(
                        market.side(),
                        score
                );

        boolean scored =
                goals > 0;

        return scored == market.yes()
                ? SettlementDecision.W
                : SettlementDecision.L;
    }

    private static int goalsForSide(
            FootballTeamMarket.TeamSide side,
            FootballScore score
    ) {
        return switch (
                side
                ) {
            case HOME ->
                    score.home();

            case AWAY ->
                    score.away();
        };
    }
}