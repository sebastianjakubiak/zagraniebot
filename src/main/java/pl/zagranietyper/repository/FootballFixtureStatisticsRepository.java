package pl.zagranietyper.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.zagranietyper.model.FootballFixtureStatisticType;
import pl.zagranietyper.model.FootballFixtureStatisticsSnapshot;
import pl.zagranietyper.service.FootballFixtureStatisticsNormalizer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class FootballFixtureStatisticsRepository {
    private final Database database;
    private final ObjectMapper objectMapper;

    public FootballFixtureStatisticsRepository(Database database, ObjectMapper objectMapper) {
        this.database = database;
        this.objectMapper = objectMapper;
    }

    public Optional<FootballFixtureStatisticsNormalizer.FixtureIdentity> findFixtureIdentity(long fixtureId) {
        String sql = """
                SELECT fixture_id, home_team_id, home_team_name, away_team_id, away_team_name
                FROM api_football_fixtures WHERE fixture_id = ?
                """;
        try (Connection connection = database.openConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, fixtureId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Long homeId = nullableLong(rs, "home_team_id");
                Long awayId = nullableLong(rs, "away_team_id");
                if (homeId == null || awayId == null) return Optional.empty();
                return Optional.of(new FootballFixtureStatisticsNormalizer.FixtureIdentity(
                        fixtureId, homeId, rs.getString("home_team_name"),
                        awayId, rs.getString("away_team_name")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load API-Football fixture identity", e);
        }
    }

    /** Returns false when a known-good COMPLETE snapshot is deliberately preserved. */
    public boolean store(FootballFixtureStatisticsSnapshot snapshot) {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                var existing = lockStatus(connection, snapshot.fixtureId());
                if (!shouldReplace(existing.orElse(null), snapshot.status())) {
                    connection.rollback();
                    return false;
                }
                if (!upsertFetch(connection, snapshot)) {
                    connection.rollback();
                    return false;
                }
                if (snapshot.status() == FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE) {
                    deleteValues(connection, snapshot.fixtureId());
                }
                upsertValues(connection, snapshot);
                connection.commit();
                return true;
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not store fixture statistics snapshot", e);
        }
    }

    public Optional<FootballFixtureStatisticsSnapshot> load(long fixtureId) {
        String fetchSql = """
                SELECT status, source, http_status, returned_team_count, error_message,
                       unknown_labels::text, raw_json::text, fetched_at, parser_version
                FROM api_football_fixture_statistics_fetches WHERE fixture_id = ?
                """;
        try (Connection connection = database.openConnection();
             PreparedStatement ps = connection.prepareStatement(fetchSql)) {
            ps.setLong(1, fixtureId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new FootballFixtureStatisticsSnapshot(
                        fixtureId,
                        FootballFixtureStatisticsSnapshot.FetchStatus.valueOf(rs.getString("status")),
                        rs.getString("source"), nullableInteger(rs, "http_status"),
                        rs.getInt("returned_team_count"), rs.getString("error_message"),
                        readLabels(rs.getString("unknown_labels")), rs.getString("raw_json"),
                        rs.getTimestamp("fetched_at").toInstant(), rs.getInt("parser_version"),
                        loadValues(connection, fixtureId)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load fixture statistics snapshot", e);
        }
    }

    public Optional<FootballFixtureStatisticsSnapshot.StatisticValue> loadValue(
            long fixtureId, FootballFixtureStatisticsSnapshot.TeamSide side,
            FootballFixtureStatisticType type) {
        return load(fixtureId).flatMap(snapshot -> snapshot.value(side, type));
    }

    public Optional<FootballFixtureStatisticsSnapshot.FetchStatus> loadStatus(long fixtureId) {
        return load(fixtureId).map(FootballFixtureStatisticsSnapshot::status);
    }

    public static boolean shouldReplace(
            FootballFixtureStatisticsSnapshot.FetchStatus existing,
            FootballFixtureStatisticsSnapshot.FetchStatus incoming) {
        return existing != FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE
                || incoming == FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE;
    }

    private Optional<FootballFixtureStatisticsSnapshot.FetchStatus> lockStatus(
            Connection connection, long fixtureId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT status FROM api_football_fixture_statistics_fetches WHERE fixture_id=? FOR UPDATE")) {
            ps.setLong(1, fixtureId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(FootballFixtureStatisticsSnapshot.FetchStatus.valueOf(
                        rs.getString(1))) : Optional.empty();
            }
        }
    }

    private boolean upsertFetch(Connection connection, FootballFixtureStatisticsSnapshot s)
            throws SQLException, JsonProcessingException {
        String sql = """
                INSERT INTO api_football_fixture_statistics_fetches
                  (fixture_id,status,source,http_status,returned_team_count,error_message,
                   unknown_labels,raw_json,fetched_at,parser_version)
                VALUES (?,?,?,?,?,?,?::jsonb,?::jsonb,?,?)
                ON CONFLICT (fixture_id) DO UPDATE SET
                  status=EXCLUDED.status, source=EXCLUDED.source, http_status=EXCLUDED.http_status,
                  returned_team_count=EXCLUDED.returned_team_count,
                  error_message=EXCLUDED.error_message, unknown_labels=EXCLUDED.unknown_labels,
                  raw_json=EXCLUDED.raw_json, fetched_at=EXCLUDED.fetched_at,
                  parser_version=EXCLUDED.parser_version
                WHERE api_football_fixture_statistics_fetches.status <> 'COMPLETE'
                   OR EXCLUDED.status = 'COMPLETE'
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, s.fixtureId());
            ps.setString(2, s.status().name());
            ps.setString(3, s.source());
            if (s.httpStatus() == null) ps.setNull(4, java.sql.Types.INTEGER); else ps.setInt(4, s.httpStatus());
            ps.setInt(5, s.returnedTeamCount());
            ps.setString(6, s.errorMessage());
            ps.setString(7, objectMapper.writeValueAsString(s.unknownLabels()));
            ps.setString(8, jsonDocument(s.rawJson()));
            ps.setTimestamp(9, java.sql.Timestamp.from(s.fetchedAt()));
            ps.setInt(10, s.parserVersion());
            return ps.executeUpdate() == 1;
        }
    }

    private static void deleteValues(Connection connection, long fixtureId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM api_football_fixture_statistics WHERE fixture_id=?")) {
            ps.setLong(1, fixtureId);
            ps.executeUpdate();
        }
    }

    private static void upsertValues(Connection connection, FootballFixtureStatisticsSnapshot s)
            throws SQLException {
        String sql = """
                INSERT INTO api_football_fixture_statistics
                  (fixture_id,team_id,team_side,statistic_type,value_numeric,value_status,
                   source_label,source,fetched_at,parser_version)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (fixture_id,team_side,statistic_type) DO UPDATE SET
                  team_id=EXCLUDED.team_id, value_numeric=EXCLUDED.value_numeric,
                  value_status=EXCLUDED.value_status, source_label=EXCLUDED.source_label,
                  source=EXCLUDED.source, fetched_at=EXCLUDED.fetched_at,
                  parser_version=EXCLUDED.parser_version
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (var value : s.values()) {
                ps.setLong(1, s.fixtureId());
                ps.setLong(2, value.teamId());
                ps.setString(3, value.side().name());
                ps.setString(4, value.type().name());
                if (value.value() == null) ps.setNull(5, java.sql.Types.NUMERIC);
                else ps.setBigDecimal(5, value.value());
                ps.setString(6, value.status().name());
                ps.setString(7, value.sourceLabel());
                ps.setString(8, s.source());
                ps.setTimestamp(9, java.sql.Timestamp.from(s.fetchedAt()));
                ps.setInt(10, s.parserVersion());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static List<FootballFixtureStatisticsSnapshot.StatisticValue> loadValues(
            Connection connection, long fixtureId) throws SQLException {
        String sql = """
                SELECT team_id,team_side,statistic_type,value_numeric,value_status,source_label
                FROM api_football_fixture_statistics WHERE fixture_id=?
                ORDER BY team_side,statistic_type
                """;
        List<FootballFixtureStatisticsSnapshot.StatisticValue> values = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, fixtureId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal value = rs.getBigDecimal("value_numeric");
                    values.add(new FootballFixtureStatisticsSnapshot.StatisticValue(
                            rs.getLong("team_id"),
                            FootballFixtureStatisticsSnapshot.TeamSide.valueOf(rs.getString("team_side")),
                            FootballFixtureStatisticType.valueOf(rs.getString("statistic_type")),
                            value,
                            FootballFixtureStatisticsSnapshot.ValueStatus.valueOf(rs.getString("value_status")),
                            rs.getString("source_label")));
                }
            }
        }
        return List.copyOf(values);
    }

    private Set<String> readLabels(String json) {
        try {
            return new LinkedHashSet<>(List.of(objectMapper.readValue(json, String[].class)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid stored unknown-label JSON", e);
        }
    }

    private String jsonDocument(String raw) throws JsonProcessingException {
        if (raw == null) return null;
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(raw));
        } catch (JsonProcessingException ignored) {
            // Non-JSON HTTP error bodies are retained as a JSON string, never discarded.
            return objectMapper.writeValueAsString(raw);
        }
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
