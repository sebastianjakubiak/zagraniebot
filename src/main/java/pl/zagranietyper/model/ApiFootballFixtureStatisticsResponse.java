package pl.zagranietyper.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

/** Raw, non-normalized result of exactly one /fixtures/statistics request. */
public record ApiFootballFixtureStatisticsResponse(
        long fixtureId,
        Status status,
        Integer httpStatus,
        String errorMessage,
        String rawJson,
        Instant fetchedAt,
        List<TeamStatistics> teams
) {
    public ApiFootballFixtureStatisticsResponse {
        teams = List.copyOf(teams);
    }

    public enum Status { SUCCESS, EMPTY, API_ERROR, FETCH_FAILED, PARSE_ERROR }

    public record TeamStatistics(long teamId, String teamName, List<RawStatistic> statistics) {
        public TeamStatistics { statistics = List.copyOf(statistics); }
    }

    public record RawStatistic(String label, JsonNode value) {}
}
