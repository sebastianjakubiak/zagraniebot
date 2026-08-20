package pl.zagranietyper.service;

import pl.zagranietyper.model.FootballScorePeriod;
import pl.zagranietyper.model.FootballTeamMarket;
import pl.zagranietyper.model.UnifiedFootballMarket;

import java.util.List;
import java.util.Objects;

/** Lossless semantic adapter from the legacy full-time team model. */
public final class FootballTeamMarketUnifiedAdapter {

    public UnifiedFootballMarket adapt(FootballTeamMarket legacy) {
        Objects.requireNonNull(legacy, "legacy");

        if (legacy instanceof FootballTeamMarket.TeamTotalGoals total) {
            return new UnifiedFootballMarket(
                    List.of(
                            new UnifiedFootballMarket.TotalGoals(
                                    subject(total.side()),
                                    FootballScorePeriod.FULL_TIME,
                                    switch (total.direction()) {
                                        case OVER ->
                                                UnifiedFootballMarket.TotalDirection.OVER;
                                        case UNDER ->
                                                UnifiedFootballMarket.TotalDirection.UNDER;
                                    },
                                    total.line()
                            )
                    )
            );
        }

        if (legacy instanceof FootballTeamMarket.TeamToScore toScore) {
            return new UnifiedFootballMarket(
                    List.of(
                            new UnifiedFootballMarket.TeamToScore(
                                    subject(toScore.side()),
                                    FootballScorePeriod.FULL_TIME,
                                    toScore.yes()
                            )
                    )
            );
        }

        throw new IllegalArgumentException(
                "Unsupported legacy FootballTeamMarket condition: "
                        + legacy.getClass().getName()
        );
    }

    private static UnifiedFootballMarket.GoalSubject subject(
            FootballTeamMarket.TeamSide side
    ) {
        return switch (side) {
            case HOME -> UnifiedFootballMarket.GoalSubject.HOME;
            case AWAY -> UnifiedFootballMarket.GoalSubject.AWAY;
        };
    }
}
