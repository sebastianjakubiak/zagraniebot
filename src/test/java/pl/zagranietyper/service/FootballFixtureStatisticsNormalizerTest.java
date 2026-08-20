package pl.zagranietyper.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.ApiFootballFixtureStatisticsResponse;
import pl.zagranietyper.model.FootballFixtureStatisticType;
import pl.zagranietyper.model.FootballFixtureStatisticsSnapshot;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FootballFixtureStatisticsNormalizerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final FootballFixtureStatisticsNormalizer.FixtureIdentity FIXTURE =
            new FootballFixtureStatisticsNormalizer.FixtureIdentity(99, 10, "Home", 20, "Away");
    private final FootballFixtureStatisticsNormalizer normalizer = new FootballFixtureStatisticsNormalizer();

    @Test
    void mapsOnlyVerifiedCanonicalLabels() {
        assertEquals(FootballFixtureStatisticType.CORNERS,
                FootballFixtureStatisticType.fromApiLabel("Corner Kicks").orElseThrow());
        assertEquals(FootballFixtureStatisticType.SAVES,
                FootballFixtureStatisticType.fromApiLabel("Goalkeeper Saves").orElseThrow());
        assertTrue(FootballFixtureStatisticType.fromApiLabel("Dangerous Attacks").isEmpty());
    }

    @Test
    void reconcilesTeamsByIdRatherThanArrayPositionAndPreservesKnownZero() {
        var snapshot = normalizer.normalize(FIXTURE, success(
                team(20, stat("Corner Kicks", number(4))),
                team(10, stat("Corner Kicks", number(0)))));
        assertEquals(FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE, snapshot.status());
        var home = snapshot.value(FootballFixtureStatisticsSnapshot.TeamSide.HOME,
                FootballFixtureStatisticType.CORNERS).orElseThrow();
        assertEquals(FootballFixtureStatisticsSnapshot.ValueStatus.KNOWN, home.status());
        assertEquals(0, home.value().intValueExact());
        assertTrue(snapshot.safelyUsable(FootballFixtureStatisticsSnapshot.TeamSide.HOME,
                FootballFixtureStatisticType.CORNERS));
    }

    @Test
    void absentIsNotZeroAndUnknownLabelsRemainObservable() {
        var snapshot = normalizer.normalize(FIXTURE, success(
                team(10, stat("Corner Kicks", null), stat("Dangerous Attacks", number(8))),
                team(20, stat("Corner Kicks", number(2)))));
        var home = snapshot.value(FootballFixtureStatisticsSnapshot.TeamSide.HOME,
                FootballFixtureStatisticType.CORNERS).orElseThrow();
        assertEquals(FootballFixtureStatisticsSnapshot.ValueStatus.ABSENT, home.status());
        assertNull(home.value());
        assertFalse(snapshot.safelyUsable(FootballFixtureStatisticsSnapshot.TeamSide.HOME,
                FootballFixtureStatisticType.CORNERS));
        assertEquals(java.util.Set.of("Dangerous Attacks"), snapshot.unknownLabels());
    }

    @Test
    void malformedNumericMakesSnapshotPartial() {
        var snapshot = normalizer.normalize(FIXTURE, success(
                team(10, stat("Fouls", text("twelve"))),
                team(20, stat("Fouls", number(9)))));
        assertEquals(FootballFixtureStatisticsSnapshot.FetchStatus.PARTIAL, snapshot.status());
        assertEquals(FootballFixtureStatisticsSnapshot.ValueStatus.INVALID,
                snapshot.value(FootballFixtureStatisticsSnapshot.TeamSide.HOME,
                        FootballFixtureStatisticType.FOULS).orElseThrow().status());
    }

    @Test
    void missingOrForeignTeamCannotAppearComplete() {
        assertEquals(FootballFixtureStatisticsSnapshot.FetchStatus.PARTIAL,
                normalizer.normalize(FIXTURE, success(team(10, stat("Fouls", number(1))))).status());
        assertEquals(FootballFixtureStatisticsSnapshot.FetchStatus.PARTIAL,
                normalizer.normalize(FIXTURE, success(
                        team(10, stat("Fouls", number(1))),
                        team(30, stat("Fouls", number(2))))).status());
    }

    @Test
    void failedAndUnsupportedResponsesHaveNoKnownZero() {
        var failed = response(ApiFootballFixtureStatisticsResponse.Status.FETCH_FAILED, List.of());
        var empty = response(ApiFootballFixtureStatisticsResponse.Status.EMPTY, List.of());
        var failedSnapshot = normalizer.normalize(FIXTURE, failed);
        var unsupportedSnapshot = normalizer.normalize(FIXTURE, empty);
        assertEquals(FootballFixtureStatisticsSnapshot.FetchStatus.FETCH_FAILED, failedSnapshot.status());
        assertEquals(FootballFixtureStatisticsSnapshot.FetchStatus.UNSUPPORTED, unsupportedSnapshot.status());
        assertTrue(failedSnapshot.values().isEmpty());
        assertTrue(unsupportedSnapshot.values().isEmpty());
    }

    private static ApiFootballFixtureStatisticsResponse success(
            ApiFootballFixtureStatisticsResponse.TeamStatistics... teams) {
        return response(ApiFootballFixtureStatisticsResponse.Status.SUCCESS, List.of(teams));
    }

    private static ApiFootballFixtureStatisticsResponse response(
            ApiFootballFixtureStatisticsResponse.Status status,
            List<ApiFootballFixtureStatisticsResponse.TeamStatistics> teams) {
        return new ApiFootballFixtureStatisticsResponse(
                99, status, 200, status == ApiFootballFixtureStatisticsResponse.Status.FETCH_FAILED ? "failed" : null,
                "{}", Instant.EPOCH, teams);
    }

    private static ApiFootballFixtureStatisticsResponse.TeamStatistics team(
            long id, ApiFootballFixtureStatisticsResponse.RawStatistic... values) {
        return new ApiFootballFixtureStatisticsResponse.TeamStatistics(id, "Team " + id, List.of(values));
    }

    private static ApiFootballFixtureStatisticsResponse.RawStatistic stat(String label, JsonNode value) {
        return new ApiFootballFixtureStatisticsResponse.RawStatistic(label, value);
    }

    private static JsonNode number(int value) { return JSON.valueToTree(value); }
    private static JsonNode text(String value) { return JSON.valueToTree(value); }
}
