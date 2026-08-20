package pl.zagranietyper.service;

import com.fasterxml.jackson.databind.JsonNode;
import pl.zagranietyper.model.ApiFootballFixtureStatisticsResponse;
import pl.zagranietyper.model.FootballFixtureStatisticType;
import pl.zagranietyper.model.FootballFixtureStatisticsSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FootballFixtureStatisticsNormalizer {
    public static final int PARSER_VERSION = 1;
    public static final String SOURCE = "API_FOOTBALL";

    public FootballFixtureStatisticsSnapshot normalize(
            FixtureIdentity fixture, ApiFootballFixtureStatisticsResponse response) {
        if (fixture.fixtureId() != response.fixtureId()) {
            return failed(fixture.fixtureId(), response, FootballFixtureStatisticsSnapshot.FetchStatus.PARTIAL,
                    "Response fixture ID does not match requested fixture");
        }
        if (response.status() != ApiFootballFixtureStatisticsResponse.Status.SUCCESS) {
            return failed(fixture.fixtureId(), response, mapStatus(response.status()), response.errorMessage());
        }

        Map<FootballFixtureStatisticsSnapshot.TeamSide,
                ApiFootballFixtureStatisticsResponse.TeamStatistics> reconciled = new EnumMap<>(
                FootballFixtureStatisticsSnapshot.TeamSide.class);
        for (var team : response.teams()) {
            FootballFixtureStatisticsSnapshot.TeamSide side;
            if (team.teamId() == fixture.homeTeamId()) side = FootballFixtureStatisticsSnapshot.TeamSide.HOME;
            else if (team.teamId() == fixture.awayTeamId()) side = FootballFixtureStatisticsSnapshot.TeamSide.AWAY;
            else return failed(fixture.fixtureId(), response,
                        FootballFixtureStatisticsSnapshot.FetchStatus.PARTIAL,
                        "Statistics contain non-fixture team ID " + team.teamId());
            if (reconciled.putIfAbsent(side, team) != null) {
                return failed(fixture.fixtureId(), response,
                        FootballFixtureStatisticsSnapshot.FetchStatus.PARTIAL,
                        "Duplicate statistics team " + side);
            }
        }
        if (reconciled.size() != 2) {
            return normalizeTeams(fixture, response, reconciled,
                    FootballFixtureStatisticsSnapshot.FetchStatus.PARTIAL,
                    "Statistics response does not contain both fixture teams");
        }
        return normalizeTeams(fixture, response, reconciled,
                FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE, null);
    }

    private FootballFixtureStatisticsSnapshot normalizeTeams(
            FixtureIdentity fixture,
            ApiFootballFixtureStatisticsResponse response,
            Map<FootballFixtureStatisticsSnapshot.TeamSide,
                    ApiFootballFixtureStatisticsResponse.TeamStatistics> teams,
            FootballFixtureStatisticsSnapshot.FetchStatus initialStatus,
            String initialError) {
        List<FootballFixtureStatisticsSnapshot.StatisticValue> result = new ArrayList<>();
        Set<String> unknown = new LinkedHashSet<>();
        boolean invalid = false;
        for (var teamEntry : teams.entrySet()) {
            var side = teamEntry.getKey();
            var team = teamEntry.getValue();
            Map<FootballFixtureStatisticType, ApiFootballFixtureStatisticsResponse.RawStatistic> known =
                    new EnumMap<>(FootballFixtureStatisticType.class);
            for (var raw : team.statistics()) {
                var type = FootballFixtureStatisticType.fromApiLabel(raw.label());
                if (type.isEmpty()) {
                    unknown.add(raw.label());
                    continue;
                }
                if (known.putIfAbsent(type.get(), raw) != null) invalid = true;
            }
            for (var type : FootballFixtureStatisticType.values()) {
                var raw = known.get(type);
                if (raw == null || raw.value() == null || raw.value().isNull()
                        || (raw.value().isTextual() && raw.value().asText().isBlank())) {
                    result.add(new FootballFixtureStatisticsSnapshot.StatisticValue(
                            team.teamId(), side, type, null,
                            FootballFixtureStatisticsSnapshot.ValueStatus.ABSENT,
                            raw == null ? null : raw.label()));
                    continue;
                }
                BigDecimal value = numeric(raw.value());
                if (value == null || value.signum() < 0) {
                    invalid = true;
                    result.add(new FootballFixtureStatisticsSnapshot.StatisticValue(
                            team.teamId(), side, type, null,
                            FootballFixtureStatisticsSnapshot.ValueStatus.INVALID, raw.label()));
                } else {
                    result.add(new FootballFixtureStatisticsSnapshot.StatisticValue(
                            team.teamId(), side, type, value,
                            FootballFixtureStatisticsSnapshot.ValueStatus.KNOWN, raw.label()));
                }
            }
        }
        var status = invalid ? FootballFixtureStatisticsSnapshot.FetchStatus.PARTIAL : initialStatus;
        String error = invalid ? "Malformed or duplicate canonical statistic value" : initialError;
        return snapshot(fixture.fixtureId(), response, status, error, unknown, result);
    }

    private static BigDecimal numeric(JsonNode node) {
        try {
            if (node.isNumber()) return node.decimalValue();
            if (node.isTextual() && node.asText().trim().matches("[0-9]+(?:\\.[0-9]+)?")) {
                return new BigDecimal(node.asText().trim());
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private static FootballFixtureStatisticsSnapshot failed(
            long fixtureId, ApiFootballFixtureStatisticsResponse response,
            FootballFixtureStatisticsSnapshot.FetchStatus status, String error) {
        return snapshot(fixtureId, response, status, error, Set.of(), List.of());
    }

    private static FootballFixtureStatisticsSnapshot snapshot(
            long fixtureId, ApiFootballFixtureStatisticsResponse response,
            FootballFixtureStatisticsSnapshot.FetchStatus status, String error,
            Set<String> unknown, List<FootballFixtureStatisticsSnapshot.StatisticValue> values) {
        return new FootballFixtureStatisticsSnapshot(
                fixtureId, status, SOURCE, response.httpStatus(), response.teams().size(), error,
                unknown, response.rawJson(), response.fetchedAt() == null ? Instant.now() : response.fetchedAt(),
                PARSER_VERSION, values);
    }

    private static FootballFixtureStatisticsSnapshot.FetchStatus mapStatus(
            ApiFootballFixtureStatisticsResponse.Status status) {
        return switch (status) {
            case EMPTY -> FootballFixtureStatisticsSnapshot.FetchStatus.UNSUPPORTED;
            case API_ERROR -> FootballFixtureStatisticsSnapshot.FetchStatus.API_ERROR;
            case FETCH_FAILED -> FootballFixtureStatisticsSnapshot.FetchStatus.FETCH_FAILED;
            case PARSE_ERROR -> FootballFixtureStatisticsSnapshot.FetchStatus.PARSE_ERROR;
            case SUCCESS -> throw new IllegalArgumentException("SUCCESS must be normalized");
        };
    }

    public record FixtureIdentity(
            long fixtureId, long homeTeamId, String homeTeamName,
            long awayTeamId, String awayTeamName) {}
}
