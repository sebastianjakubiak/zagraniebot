package pl.zagranietyper.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record FootballFixtureStatisticsSnapshot(
        long fixtureId,
        FetchStatus status,
        String source,
        Integer httpStatus,
        int returnedTeamCount,
        String errorMessage,
        Set<String> unknownLabels,
        String rawJson,
        Instant fetchedAt,
        int parserVersion,
        List<StatisticValue> values
) {
    public FootballFixtureStatisticsSnapshot {
        unknownLabels = Set.copyOf(unknownLabels);
        values = List.copyOf(values);
    }

    public Optional<StatisticValue> value(TeamSide side, FootballFixtureStatisticType type) {
        return values.stream().filter(v -> v.side() == side && v.type() == type).findFirst();
    }

    public boolean safelyUsable(TeamSide side, FootballFixtureStatisticType type) {
        return status == FetchStatus.COMPLETE
                && value(side, type).map(v -> v.status() == ValueStatus.KNOWN).orElse(false);
    }

    public enum FetchStatus {
        COMPLETE, PARTIAL, UNAVAILABLE, UNSUPPORTED, FETCH_FAILED, API_ERROR, PARSE_ERROR
    }

    public enum ValueStatus { KNOWN, ABSENT, INVALID }

    public enum TeamSide { HOME, AWAY }

    public record StatisticValue(
            long teamId,
            TeamSide side,
            FootballFixtureStatisticType type,
            BigDecimal value,
            ValueStatus status,
            String sourceLabel
    ) {
        public StatisticValue {
            if (status == ValueStatus.KNOWN && (value == null || value.signum() < 0)) {
                throw new IllegalArgumentException("KNOWN statistic requires a non-negative value");
            }
            if (status != ValueStatus.KNOWN && value != null) {
                throw new IllegalArgumentException("Unknown statistic value must remain null");
            }
        }
    }
}
