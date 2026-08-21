package pl.zagranietyper.service;

import pl.zagranietyper.model.FootballFixtureStatisticCondition;
import pl.zagranietyper.model.FootballFixtureStatisticsSnapshot;
import pl.zagranietyper.model.SettlementDecision;

import java.math.BigDecimal;
import java.util.Optional;

/** Pure settlement logic for canonical fixture-statistic conditions. */
public final class FootballFixtureStatisticSettlementEngine {
    public SettlementDecision settle(
            FootballFixtureStatisticCondition condition,
            FootballFixtureStatisticsSnapshot snapshot) {
        if (condition == null || snapshot == null
                || snapshot.status() != FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE) {
            return SettlementDecision.UNSUPPORTED;
        }
        Optional<BigDecimal> actual = actual(condition, snapshot);
        if (actual.isEmpty()) return SettlementDecision.UNSUPPORTED;

        int lower = actual.get().compareTo(condition.threshold());
        return switch (condition.comparison()) {
            case OVER -> lower == 0 ? SettlementDecision.V
                    : decision(lower > 0);
            case UNDER -> lower == 0 ? SettlementDecision.V
                    : decision(lower < 0);
            case MINIMUM -> decision(lower >= 0);
            case INCLUSIVE_RANGE -> decision(lower >= 0
                    && actual.get().compareTo(condition.rangeMaximum()) <= 0);
        };
    }

    private static Optional<BigDecimal> actual(
            FootballFixtureStatisticCondition condition,
            FootballFixtureStatisticsSnapshot snapshot) {
        return switch (condition.subject()) {
            case HOME -> known(snapshot, FootballFixtureStatisticsSnapshot.TeamSide.HOME, condition);
            case AWAY -> known(snapshot, FootballFixtureStatisticsSnapshot.TeamSide.AWAY, condition);
            case MATCH -> {
                Optional<BigDecimal> home = known(
                        snapshot, FootballFixtureStatisticsSnapshot.TeamSide.HOME, condition);
                Optional<BigDecimal> away = known(
                        snapshot, FootballFixtureStatisticsSnapshot.TeamSide.AWAY, condition);
                yield home.isPresent() && away.isPresent()
                        ? Optional.of(home.get().add(away.get())) : Optional.empty();
            }
        };
    }

    private static Optional<BigDecimal> known(
            FootballFixtureStatisticsSnapshot snapshot,
            FootballFixtureStatisticsSnapshot.TeamSide side,
            FootballFixtureStatisticCondition condition) {
        return snapshot.value(side, condition.type())
                .filter(value -> value.status() == FootballFixtureStatisticsSnapshot.ValueStatus.KNOWN)
                .map(FootballFixtureStatisticsSnapshot.StatisticValue::value);
    }

    private static SettlementDecision decision(boolean won) {
        return won ? SettlementDecision.W : SettlementDecision.L;
    }
}
