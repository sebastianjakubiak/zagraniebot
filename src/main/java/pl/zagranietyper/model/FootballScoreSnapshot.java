package pl.zagranietyper.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable, period-aware score input for football settlement. */
public final class FootballScoreSnapshot {

    private final Map<FootballScorePeriod, FootballScore> scores;

    private FootballScoreSnapshot(
            Map<FootballScorePeriod, FootballScore> scores
    ) {
        this.scores = Map.copyOf(scores);
    }

    public static FootballScoreSnapshot fullTime(
            FootballScore fullTime
    ) {
        Objects.requireNonNull(fullTime, "fullTime");

        return new FootballScoreSnapshot(
                Map.of(FootballScorePeriod.FULL_TIME, fullTime)
        );
    }

    public static FootballScoreSnapshot fullTimeAndFirstHalf(
            FootballScore fullTime,
            FootballScore firstHalf
    ) {
        Objects.requireNonNull(fullTime, "fullTime");
        Objects.requireNonNull(firstHalf, "firstHalf");

        EnumMap<FootballScorePeriod, FootballScore> result =
                new EnumMap<>(FootballScorePeriod.class);

        result.put(FootballScorePeriod.FULL_TIME, fullTime);
        result.put(FootballScorePeriod.FIRST_HALF, firstHalf);

        int secondHalfHome = fullTime.home() - firstHalf.home();
        int secondHalfAway = fullTime.away() - firstHalf.away();

        if (secondHalfHome >= 0 && secondHalfAway >= 0) {
            result.put(
                    FootballScorePeriod.SECOND_HALF,
                    new FootballScore(secondHalfHome, secondHalfAway)
            );
        }

        return new FootballScoreSnapshot(result);
    }

    public Optional<FootballScore> score(
            FootballScorePeriod period
    ) {
        Objects.requireNonNull(period, "period");

        return Optional.ofNullable(scores.get(period));
    }

    public boolean hasScore(FootballScorePeriod period) {
        return score(period).isPresent();
    }
}
