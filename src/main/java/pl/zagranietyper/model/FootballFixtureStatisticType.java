package pl.zagranietyper.model;

import java.util.Map;
import java.util.Optional;

/** Canonical API-Football team statistic labels verified for fixture statistics. */
public enum FootballFixtureStatisticType {
    CORNERS("Corner Kicks"),
    FOULS("Fouls"),
    SHOTS_TOTAL("Total Shots"),
    SHOTS_ON_TARGET("Shots on Goal"),
    SHOTS_OFF_TARGET("Shots off Goal"),
    BLOCKED_SHOTS("Blocked Shots"),
    OFFSIDES("Offsides"),
    SAVES("Goalkeeper Saves"),
    YELLOW_CARDS("Yellow Cards"),
    RED_CARDS("Red Cards");

    private static final Map<String, FootballFixtureStatisticType> BY_LABEL =
            java.util.Arrays.stream(values()).collect(java.util.stream.Collectors.toUnmodifiableMap(
                    FootballFixtureStatisticType::apiLabel, value -> value));

    private final String apiLabel;

    FootballFixtureStatisticType(String apiLabel) {
        this.apiLabel = apiLabel;
    }

    public String apiLabel() {
        return apiLabel;
    }

    public static Optional<FootballFixtureStatisticType> fromApiLabel(String label) {
        return Optional.ofNullable(BY_LABEL.get(label));
    }
}
