package pl.zagranietyper.service;

import pl.zagranietyper.model.FootballMarket;
import pl.zagranietyper.model.FootballScorePeriod;
import pl.zagranietyper.model.UnifiedFootballMarket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Lossless semantic adapter from the legacy full-time match model. */
public final class FootballMarketUnifiedAdapter {

    public UnifiedFootballMarket adapt(FootballMarket legacy) {
        Objects.requireNonNull(legacy, "legacy");

        List<UnifiedFootballMarket.Condition> conditions =
                new ArrayList<>();

        for (FootballMarket.Condition condition : legacy.conditions()) {
            conditions.add(adaptCondition(condition));
        }

        return new UnifiedFootballMarket(conditions);
    }

    private static UnifiedFootballMarket.Condition adaptCondition(
            FootballMarket.Condition condition
    ) {
        if (condition instanceof FootballMarket.MatchResult result) {
            return new UnifiedFootballMarket.Result(
                    switch (result.selection()) {
                        case HOME -> UnifiedFootballMarket.ResultSelection.HOME;
                        case DRAW -> UnifiedFootballMarket.ResultSelection.DRAW;
                        case AWAY -> UnifiedFootballMarket.ResultSelection.AWAY;
                    },
                    FootballScorePeriod.FULL_TIME
            );
        }

        if (condition instanceof FootballMarket.DoubleChance doubleChance) {
            return new UnifiedFootballMarket.Result(
                    switch (doubleChance.selection()) {
                        case HOME_OR_DRAW ->
                                UnifiedFootballMarket.ResultSelection.HOME_OR_DRAW;
                        case AWAY_OR_DRAW ->
                                UnifiedFootballMarket.ResultSelection.AWAY_OR_DRAW;
                        case HOME_OR_AWAY ->
                                UnifiedFootballMarket.ResultSelection.HOME_OR_AWAY;
                    },
                    FootballScorePeriod.FULL_TIME
            );
        }

        if (condition instanceof FootballMarket.TotalGoals total) {
            return new UnifiedFootballMarket.TotalGoals(
                    UnifiedFootballMarket.GoalSubject.MATCH,
                    FootballScorePeriod.FULL_TIME,
                    switch (total.direction()) {
                        case OVER -> UnifiedFootballMarket.TotalDirection.OVER;
                        case UNDER -> UnifiedFootballMarket.TotalDirection.UNDER;
                    },
                    total.line()
            );
        }

        if (condition instanceof FootballMarket.MinimumTotalGoals minimum) {
            return new UnifiedFootballMarket.MinimumGoals(
                    UnifiedFootballMarket.GoalSubject.MATCH,
                    FootballScorePeriod.FULL_TIME,
                    minimum.minimum()
            );
        }

        if (condition instanceof FootballMarket.MatchGoalRange range) {
            return new UnifiedFootballMarket.GoalRange(
                    UnifiedFootballMarket.GoalSubject.MATCH,
                    FootballScorePeriod.FULL_TIME,
                    range.minimum(),
                    range.maximum()
            );
        }

        if (condition instanceof FootballMarket.BothTeamsToScore btts) {
            return new UnifiedFootballMarket.BothTeamsToScore(
                    FootballScorePeriod.FULL_TIME,
                    btts.yes()
            );
        }

        throw new IllegalArgumentException(
                "Unsupported legacy FootballMarket condition: "
                        + condition.getClass().getName()
        );
    }
}
