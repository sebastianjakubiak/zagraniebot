package pl.zagranietyper.service;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.FootballFixtureStatisticCondition;
import pl.zagranietyper.model.FootballFixtureStatisticType;
import pl.zagranietyper.model.FootballFixtureStatisticsSnapshot;
import pl.zagranietyper.model.SettlementDecision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FootballFixtureStatisticSettlementEngineTest {
    private final FootballFixtureStatisticSettlementEngine engine =
            new FootballFixtureStatisticSettlementEngine();

    @Test void matchOverUnderAndEquality() {
        assertEquals(SettlementDecision.W, settle(match(Comparison.OVER, "8.5"), known(5, 4)));
        assertEquals(SettlementDecision.L, settle(match(Comparison.OVER, "9.5"), known(5, 4)));
        assertEquals(SettlementDecision.W, settle(match(Comparison.UNDER, "9.5"), known(5, 4)));
        assertEquals(SettlementDecision.L, settle(match(Comparison.UNDER, "8.5"), known(5, 4)));
        assertEquals(SettlementDecision.V, settle(match(Comparison.OVER, "9"), known(5, 4)));
        assertEquals(SettlementDecision.V, settle(match(Comparison.UNDER, "9"), known(5, 4)));
    }

    @Test void matchMinimumAndRangeAreInclusive() {
        assertEquals(SettlementDecision.W, settle(match(Comparison.MINIMUM, "9"), known(5, 4)));
        assertEquals(SettlementDecision.L, settle(match(Comparison.MINIMUM, "10"), known(5, 4)));
        assertEquals(SettlementDecision.W, settle(range(9, 11), known(5, 4)));
        assertEquals(SettlementDecision.W, settle(range(8, 9), known(5, 4)));
        assertEquals(SettlementDecision.L, settle(range(10, 12), known(5, 4)));
    }

    @Test void matchKnownZeroIsDataButAbsentOrInvalidIsUnsupported() {
        assertEquals(SettlementDecision.W, settle(match(Comparison.UNDER, "5"), known(0, 4)));
        assertEquals(SettlementDecision.UNSUPPORTED, settle(match(Comparison.OVER, "2"),
                snapshot(value(TeamSide.HOME, ValueStatus.ABSENT, null), knownValue(TeamSide.AWAY, 4))));
        assertEquals(SettlementDecision.UNSUPPORTED, settle(match(Comparison.OVER, "2"),
                snapshot(value(TeamSide.HOME, ValueStatus.INVALID, null), knownValue(TeamSide.AWAY, 4))));
    }

    @Test void teamUsesOnlyResolvedSideAndPreservesKnownZero() {
        var snapshot = known(0, 7);
        assertEquals(SettlementDecision.L, settle(team(Subject.HOME, Comparison.OVER, "0.5"), snapshot));
        assertEquals(SettlementDecision.W, settle(team(Subject.AWAY, Comparison.OVER, "6.5"), snapshot));
        assertEquals(SettlementDecision.W, settle(team(Subject.HOME, Comparison.MINIMUM, "0"), snapshot));
        var homeAbsent = snapshot(value(TeamSide.HOME, ValueStatus.ABSENT, null), knownValue(TeamSide.AWAY, 7));
        assertEquals(SettlementDecision.UNSUPPORTED,
                settle(team(Subject.HOME, Comparison.OVER, "0.5"), homeAbsent));
        assertEquals(SettlementDecision.W,
                settle(team(Subject.AWAY, Comparison.OVER, "6.5"), homeAbsent));
    }

    @Test void incompleteSnapshotAndMissingConditionAreUnsupported() {
        var complete = known(2, 3);
        assertEquals(SettlementDecision.UNSUPPORTED, engine.settle(null, complete));
        var partial = new FootballFixtureStatisticsSnapshot(1,
                FootballFixtureStatisticsSnapshot.FetchStatus.PARTIAL, "test", 200, 2, null,
                Set.of(), "{}", Instant.EPOCH, 1, complete.values());
        assertEquals(SettlementDecision.UNSUPPORTED, settle(match(Comparison.OVER, "1"), partial));
    }

    private SettlementDecision settle(FootballFixtureStatisticCondition condition,
                                      FootballFixtureStatisticsSnapshot snapshot) {
        return engine.settle(condition, snapshot);
    }
    private static FootballFixtureStatisticCondition match(Comparison comparison, String threshold) {
        return team(Subject.MATCH, comparison, threshold);
    }
    private static FootballFixtureStatisticCondition team(Subject subject, Comparison comparison, String threshold) {
        return FootballFixtureStatisticCondition.threshold(FootballFixtureStatisticType.CORNERS,
                subject.value, comparison.value, new BigDecimal(threshold));
    }
    private static FootballFixtureStatisticCondition range(int minimum, int maximum) {
        return FootballFixtureStatisticCondition.range(FootballFixtureStatisticType.CORNERS,
                FootballFixtureStatisticCondition.Subject.MATCH,
                BigDecimal.valueOf(minimum), BigDecimal.valueOf(maximum));
    }
    private static FootballFixtureStatisticsSnapshot known(int home, int away) {
        return snapshot(knownValue(TeamSide.HOME, home), knownValue(TeamSide.AWAY, away));
    }
    private static FootballFixtureStatisticsSnapshot snapshot(
            FootballFixtureStatisticsSnapshot.StatisticValue... values) {
        return new FootballFixtureStatisticsSnapshot(1,
                FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE, "test", 200, 2, null,
                Set.of(), "{}", Instant.EPOCH, 1, List.of(values));
    }
    private static FootballFixtureStatisticsSnapshot.StatisticValue knownValue(TeamSide side, int value) {
        return value(side, ValueStatus.KNOWN, BigDecimal.valueOf(value));
    }
    private static FootballFixtureStatisticsSnapshot.StatisticValue value(
            TeamSide side, ValueStatus status, BigDecimal value) {
        return new FootballFixtureStatisticsSnapshot.StatisticValue(
                side == TeamSide.HOME ? 10 : 20, side.value,
                FootballFixtureStatisticType.CORNERS, value, status.value, "Corner Kicks");
    }
    private enum Subject {
        MATCH(FootballFixtureStatisticCondition.Subject.MATCH),
        HOME(FootballFixtureStatisticCondition.Subject.HOME),
        AWAY(FootballFixtureStatisticCondition.Subject.AWAY);
        private final FootballFixtureStatisticCondition.Subject value;
        Subject(FootballFixtureStatisticCondition.Subject value) { this.value = value; }
    }
    private enum Comparison {
        OVER(FootballFixtureStatisticCondition.Comparison.OVER),
        UNDER(FootballFixtureStatisticCondition.Comparison.UNDER),
        MINIMUM(FootballFixtureStatisticCondition.Comparison.MINIMUM);
        private final FootballFixtureStatisticCondition.Comparison value;
        Comparison(FootballFixtureStatisticCondition.Comparison value) { this.value = value; }
    }
    private enum TeamSide {
        HOME(FootballFixtureStatisticsSnapshot.TeamSide.HOME),
        AWAY(FootballFixtureStatisticsSnapshot.TeamSide.AWAY);
        private final FootballFixtureStatisticsSnapshot.TeamSide value;
        TeamSide(FootballFixtureStatisticsSnapshot.TeamSide value) { this.value = value; }
    }
    private enum ValueStatus {
        KNOWN(FootballFixtureStatisticsSnapshot.ValueStatus.KNOWN),
        ABSENT(FootballFixtureStatisticsSnapshot.ValueStatus.ABSENT),
        INVALID(FootballFixtureStatisticsSnapshot.ValueStatus.INVALID);
        private final FootballFixtureStatisticsSnapshot.ValueStatus value;
        ValueStatus(FootballFixtureStatisticsSnapshot.ValueStatus value) { this.value = value; }
    }
}
