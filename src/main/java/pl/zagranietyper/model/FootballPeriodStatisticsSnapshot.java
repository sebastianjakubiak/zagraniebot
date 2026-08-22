package pl.zagranietyper.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record FootballPeriodStatisticsSnapshot(long fixtureId, String provider, String providerEventId,
        FetchStatus status, String rawJson, Instant fetchedAt, List<Value> values) {
    public FootballPeriodStatisticsSnapshot { values = List.copyOf(values); }
    public Optional<Value> value(Period period, FootballFixtureStatisticsSnapshot.TeamSide side,
                                 FootballFixtureStatisticType type) {
        return values.stream().filter(v -> v.period == period && v.side == side && v.type == type).findFirst();
    }
    public enum Period { FIRST_HALF, SECOND_HALF, FULL_MATCH }
    public enum FetchStatus { COMPLETE, PARTIAL, UNAVAILABLE, UNSUPPORTED, FETCH_FAILED, API_ERROR, PARSE_ERROR }
    public record Value(Period period, FootballFixtureStatisticType type,
                        FootballFixtureStatisticsSnapshot.TeamSide side, BigDecimal value,
                        FootballFixtureStatisticsSnapshot.ValueStatus status, String rawKey) {
        public Value {
            if (status == FootballFixtureStatisticsSnapshot.ValueStatus.KNOWN
                    && (value == null || value.signum() < 0)) throw new IllegalArgumentException("KNOWN requires value");
            if (status != FootballFixtureStatisticsSnapshot.ValueStatus.KNOWN && value != null)
                throw new IllegalArgumentException("unknown value must remain null");
        }
    }
}
