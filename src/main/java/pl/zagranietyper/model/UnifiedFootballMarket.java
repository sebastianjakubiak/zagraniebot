package pl.zagranietyper.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Subject-aware semantic model for score-based football settlement. */
public record UnifiedFootballMarket(
        List<Condition> conditions
) {

    public UnifiedFootballMarket {
        Objects.requireNonNull(conditions, "conditions");

        if (conditions.isEmpty()) {
            throw new IllegalArgumentException(
                    "UnifiedFootballMarket requires at least one condition"
            );
        }

        conditions = List.copyOf(conditions);
    }

    public sealed interface Condition
            permits Result,
            TotalGoals,
            MinimumGoals,
            GoalRange,
            TeamToScore,
            BothTeamsToScore {

        FootballScorePeriod period();
    }

    public enum GoalSubject {
        MATCH,
        HOME,
        AWAY
    }

    public enum ResultSelection {
        HOME,
        DRAW,
        AWAY,
        HOME_OR_DRAW,
        AWAY_OR_DRAW,
        HOME_OR_AWAY
    }

    public enum TotalDirection {
        OVER,
        UNDER
    }

    public record Result(
            ResultSelection selection,
            FootballScorePeriod period
    ) implements Condition {

        public Result {
            Objects.requireNonNull(selection, "selection");
            Objects.requireNonNull(period, "period");
        }
    }

    /** OVER/UNDER line where equality settles as VOID. */
    public record TotalGoals(
            GoalSubject subject,
            FootballScorePeriod period,
            TotalDirection direction,
            BigDecimal line
    ) implements Condition {

        public TotalGoals {
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(period, "period");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(line, "line");

            if (line.signum() < 0) {
                throw new IllegalArgumentException("line cannot be negative");
            }
        }
    }

    /** Integer minimum where equality settles as WIN. */
    public record MinimumGoals(
            GoalSubject subject,
            FootballScorePeriod period,
            int minimum
    ) implements Condition {

        public MinimumGoals {
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(period, "period");

            if (minimum < 0) {
                throw new IllegalArgumentException("minimum cannot be negative");
            }
        }
    }

    /** Inclusive integer goal range. */
    public record GoalRange(
            GoalSubject subject,
            FootballScorePeriod period,
            int minimum,
            int maximum
    ) implements Condition {

        public GoalRange {
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(period, "period");

            if (minimum < 0 || maximum < minimum) {
                throw new IllegalArgumentException("invalid inclusive range");
            }
        }
    }

    /** Named legacy semantic kept distinct from a generic minimum. */
    public record TeamToScore(
            GoalSubject subject,
            FootballScorePeriod period,
            boolean expected
    ) implements Condition {

        public TeamToScore {
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(period, "period");

            if (subject == GoalSubject.MATCH) {
                throw new IllegalArgumentException(
                        "TeamToScore requires HOME or AWAY subject"
                );
            }
        }
    }

    public record BothTeamsToScore(
            FootballScorePeriod period,
            boolean expected
    ) implements Condition {

        public BothTeamsToScore {
            Objects.requireNonNull(period, "period");
        }
    }
}
