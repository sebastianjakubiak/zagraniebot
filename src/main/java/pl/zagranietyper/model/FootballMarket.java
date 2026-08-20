package pl.zagranietyper.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record FootballMarket(
        List<Condition> conditions
) {

    public FootballMarket {
        Objects.requireNonNull(
                conditions,
                "conditions"
        );

        if (
                conditions.isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "FootballMarket musi zawierać co najmniej jeden warunek"
            );
        }

        conditions =
                List.copyOf(
                        conditions
                );
    }

    public sealed interface Condition
            permits
            MatchResult,
            DoubleChance,
            TotalGoals,
            MatchGoalRange,
            MinimumTotalGoals,
            BothTeamsToScore {
    }

    public enum MatchResultSelection {
        HOME,
        DRAW,
        AWAY
    }

    public record MatchResult(
            MatchResultSelection selection
    ) implements Condition {

        public MatchResult {
            Objects.requireNonNull(
                    selection,
                    "selection"
            );
        }
    }

    public enum DoubleChanceSelection {
        HOME_OR_DRAW,
        AWAY_OR_DRAW,
        HOME_OR_AWAY
    }

    public record DoubleChance(
            DoubleChanceSelection selection
    ) implements Condition {

        public DoubleChance {
            Objects.requireNonNull(
                    selection,
                    "selection"
            );
        }
    }

    public enum TotalDirection {
        OVER,
        UNDER
    }

    public record TotalGoals(
            TotalDirection direction,
            BigDecimal line
    ) implements Condition {

        public TotalGoals {
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
                        "Linia goli nie może być ujemna"
                );
            }
        }
    }

    public record MatchGoalRange(
            int minimum,
            int maximum
    ) implements Condition {

        public MatchGoalRange {
            if (minimum < 0) {
                throw new IllegalArgumentException(
                        "Dolna granica goli nie może być ujemna"
                );
            }

            if (maximum < minimum) {
                throw new IllegalArgumentException(
                        "Górna granica goli nie może być niższa od dolnej"
                );
            }
        }
    }

    public record MinimumTotalGoals(
            int minimum
    ) implements Condition {

        public MinimumTotalGoals {
            if (
                    minimum < 0
            ) {
                throw new IllegalArgumentException(
                        "Minimalna liczba goli nie może być ujemna"
                );
            }
        }
    }

    public record BothTeamsToScore(
            boolean yes
    ) implements Condition {
    }
}
