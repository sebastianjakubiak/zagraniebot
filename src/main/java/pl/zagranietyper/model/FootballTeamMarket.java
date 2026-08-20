package pl.zagranietyper.model;

import java.math.BigDecimal;
import java.util.Objects;

public sealed interface FootballTeamMarket
        permits FootballTeamMarket.TeamTotalGoals,
        FootballTeamMarket.TeamToScore {

    TeamSide side();

    enum TeamSide {
        HOME,
        AWAY
    }

    enum Direction {
        OVER,
        UNDER
    }

    record TeamTotalGoals(
            TeamSide side,
            Direction direction,
            BigDecimal line
    ) implements FootballTeamMarket {

        public TeamTotalGoals {
            Objects.requireNonNull(
                    side,
                    "side"
            );

            Objects.requireNonNull(
                    direction,
                    "direction"
            );

            Objects.requireNonNull(
                    line,
                    "line"
            );

            if (
                    line.signum() < 0
            ) {
                throw new IllegalArgumentException(
                        "line cannot be negative"
                );
            }
        }
    }

    record TeamToScore(
            TeamSide side,
            boolean yes
    ) implements FootballTeamMarket {

        public TeamToScore {
            Objects.requireNonNull(
                    side,
                    "side"
            );
        }
    }
}