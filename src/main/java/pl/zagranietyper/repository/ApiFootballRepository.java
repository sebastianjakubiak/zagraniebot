package pl.zagranietyper.repository;

import pl.zagranietyper.model.ApiFootballFixture;
import pl.zagranietyper.model.ApiFootballMatch;
import pl.zagranietyper.model.ApiFootballResolutionCandidate;
import pl.zagranietyper.model.BetType;
import pl.zagranietyper.model.ResolutionConfidence;
import pl.zagranietyper.model.ResolutionSource;
import pl.zagranietyper.model.ResolvedSport;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class ApiFootballRepository {

    private final Database database;

    public ApiFootballRepository(
            Database database
    ) {
        this.database = database;
    }

    public List<ApiFootballResolutionCandidate>
    findUnresolvedCandidates() {

        /*
         * Nazwa metody została zachowana dla kompatybilności,
         * ale od teraz zwraca dwa typy kandydatów:
         *
         * 1. stare unresolved:
         *    resolution_source = NONE
         *
         * 2. eventy już rozwiązane lokalnie, ale jeszcze
         *    bez powiązania z fixture API-Football.
         *
         * Drugi przypadek jest potrzebny do settlementu.
         * Sam fakt, że lokalny resolver zna np.
         * "Rio Ave – FC Porto", nie daje nam jeszcze fixture_id.
         *
         * Do API dopuszczamy wyłącznie FOOTBALL/UNKNOWN.
         * Rozpoznane inne sporty są wycinane już na SQL-u.
         */
        String sql = """
                SELECT
                    l.id AS leg_id,
                    p.wp_post_id,

                    b.bet_type,

                    (
                        SELECT COUNT(*)
                        FROM bet_legs l2
                        WHERE l2.bet_id = b.id
                          AND l2.active = TRUE
                    ) AS bet_leg_count,

                    p.published_at,
                    l.resolved_sport,

                    p.title AS post_title,
                    l.tip_title,

                    l.source_attributes ->> 'context_heading'
                        AS context_heading,

                    l.source_attributes ->> 'context_previous_text'
                        AS context_previous_text,

                    l.resolution_source,
                    l.resolution_confidence,

                    l.resolved_event_name,
                    l.resolved_participant_a,
                    l.resolved_participant_b,
                    l.resolved_event_date,

                    l.resolution_evidence

                FROM bet_legs l

                JOIN bets b
                    ON b.id = l.bet_id
                   AND b.active = TRUE

                JOIN posts p
                    ON p.wp_post_id = b.wp_post_id

                WHERE l.active = TRUE

                  AND (
                        l.resolved_sport = 'FOOTBALL'
                        OR l.resolved_sport = 'UNKNOWN'
                  )

                  AND (
                        l.resolved_provider IS NULL
                        OR l.resolved_provider <> 'API_FOOTBALL'
                  )

                  AND (
                        l.resolved_external_event_id IS NULL
                        OR BTRIM(l.resolved_external_event_id) = ''
                  )

                  AND l.resolution_source <> 'API_FOOTBALL'

                  AND (
                        l.resolution_source = 'NONE'

                        OR (
                            l.resolution_source <> 'NONE'
                            AND l.resolved_participant_a IS NOT NULL
                            AND BTRIM(l.resolved_participant_a) <> ''
                            AND l.resolved_participant_b IS NOT NULL
                            AND BTRIM(l.resolved_participant_b) <> ''
                        )
                  )

                ORDER BY
                    p.published_at,
                    p.wp_post_id,
                    b.ordinal,
                    l.ordinal
                """;

        List<ApiFootballResolutionCandidate> result =
                new ArrayList<>();

        try (
                Connection connection =
                        database.openConnection();

                PreparedStatement ps =
                        connection.prepareStatement(
                                sql
                        );

                ResultSet rs =
                        ps.executeQuery()
        ) {
            while (
                    rs.next()
            ) {
                Timestamp published =
                        rs.getTimestamp(
                                "published_at"
                        );

                Date resolvedDate =
                        rs.getDate(
                                "resolved_event_date"
                        );

                ResolutionSource currentSource =
                        parseResolutionSource(
                                rs.getString(
                                        "resolution_source"
                                )
                        );

                ResolutionConfidence currentConfidence =
                        parseResolutionConfidence(
                                rs.getString(
                                        "resolution_confidence"
                                )
                        );

                String resolvedEventName =
                        rs.getString(
                                "resolved_event_name"
                        );

                String resolvedParticipantA =
                        rs.getString(
                                "resolved_participant_a"
                        );

                String resolvedParticipantB =
                        rs.getString(
                                "resolved_participant_b"
                        );

                String originalTipTitle =
                        rs.getString(
                                "tip_title"
                        );

                String originalHeading =
                        rs.getString(
                                "context_heading"
                        );

                String originalPreviousText =
                        rs.getString(
                                "context_previous_text"
                        );

                String currentResolutionEvidence =
                        rs.getString(
                                "resolution_evidence"
                        );

                boolean localResolution =
                        currentSource != ResolutionSource.NONE
                                && currentSource != ResolutionSource.API_FOOTBALL;

                /*
                 * Stary matcher jest już ręcznie audytowany i zahartowany.
                 *
                 * Nie zmieniamy go.
                 *
                 * Dla lokalnie rozwiązanych eventów podajemy mu
                 * leg-specific matchup jako heading, a tip_title
                 * celowo zerujemy.
                 *
                 * Dzięki temu:
                 * - BOTH_TEAMS może potwierdzić lokalne A – B,
                 * - UNIQUE_SINGLE_TEAM nie może zmatchować tylko
                 *   jednej drużyny i zrobić false-positive.
                 */
                String matcherTipTitle =
                        localResolution
                                ? null
                                : originalTipTitle;

                String matcherHeading =
                        localResolution
                                ? localMatchup(
                                resolvedParticipantA,
                                resolvedParticipantB,
                                resolvedEventName
                        )
                                : originalHeading;

                String matcherPreviousText =
                        localResolution
                                ? joinEvidence(
                                originalHeading,
                                originalPreviousText,
                                currentResolutionEvidence
                        )
                                : originalPreviousText;

                result.add(
                        new ApiFootballResolutionCandidate(
                                rs.getLong(
                                        "leg_id"
                                ),

                                rs.getLong(
                                        "wp_post_id"
                                ),

                                BetType.valueOf(
                                        rs.getString(
                                                "bet_type"
                                        )
                                ),

                                rs.getInt(
                                        "bet_leg_count"
                                ),

                                published == null
                                        ? null
                                        : published.toInstant(),

                                parseSport(
                                        rs.getString(
                                                "resolved_sport"
                                        )
                                ),

                                rs.getString(
                                        "post_title"
                                ),

                                matcherTipTitle,
                                matcherHeading,
                                matcherPreviousText,

                                currentSource,
                                currentConfidence,

                                resolvedEventName,
                                resolvedParticipantA,
                                resolvedParticipantB,

                                resolvedDate == null
                                        ? null
                                        : resolvedDate.toLocalDate(),

                                currentResolutionEvidence
                        )
                );
            }

            return List.copyOf(
                    result
            );

        } catch (
                SQLException e
        ) {
            throw new IllegalStateException(
                    "Nie udało się pobrać kandydatów API-Football",
                    e
            );
        }
    }

    public boolean isDateComplete(
            LocalDate date
    ) {
        String sql = """
                SELECT 1
                FROM api_football_fetch_days
                WHERE fixture_date = ?
                """;

        try (
                Connection connection =
                        database.openConnection();

                PreparedStatement ps =
                        connection.prepareStatement(
                                sql
                        )
        ) {
            ps.setDate(
                    1,
                    Date.valueOf(
                            date
                    )
            );

            try (
                    ResultSet rs =
                            ps.executeQuery()
            ) {
                return rs.next();
            }

        } catch (
                SQLException e
        ) {
            throw new IllegalStateException(
                    "Nie udało się sprawdzić cache API-Football",
                    e
            );
        }
    }

    public boolean isWindowComplete(
            LocalDate from,
            LocalDate to
    ) {
        String sql = """
                SELECT COUNT(*) AS days
                FROM api_football_fetch_days
                WHERE fixture_date >= ?
                  AND fixture_date <= ?
                """;

        long expected =
                to.toEpochDay()
                        - from.toEpochDay()
                        + 1;

        try (
                Connection connection =
                        database.openConnection();

                PreparedStatement ps =
                        connection.prepareStatement(
                                sql
                        )
        ) {
            ps.setDate(
                    1,
                    Date.valueOf(
                            from
                    )
            );

            ps.setDate(
                    2,
                    Date.valueOf(
                            to
                    )
            );

            try (
                    ResultSet rs =
                            ps.executeQuery()
            ) {
                rs.next();

                return rs.getLong(
                        "days"
                ) == expected;
            }

        } catch (
                SQLException e
        ) {
            throw new IllegalStateException(
                    "Nie udało się sprawdzić kompletności okna",
                    e
            );
        }
    }

    public void saveFetchedDate(
            LocalDate date,
            List<ApiFootballFixture> fixtures
    ) {
        try (
                Connection connection =
                        database.openConnection()
        ) {
            connection.setAutoCommit(
                    false
            );

            try {
                for (
                        ApiFootballFixture fixture :
                        fixtures
                ) {
                    upsertFixture(
                            connection,
                            fixture
                    );
                }

                try (
                        PreparedStatement ps =
                                connection.prepareStatement(
                                        """
                                        INSERT INTO api_football_fetch_days (
                                            fixture_date,
                                            fixture_count,
                                            fetched_at
                                        )
                                        VALUES (?, ?, NOW())

                                        ON CONFLICT (fixture_date)
                                        DO UPDATE SET
                                            fixture_count = EXCLUDED.fixture_count,
                                            fetched_at = NOW()
                                        """
                                )
                ) {
                    ps.setDate(
                            1,
                            Date.valueOf(
                                    date
                            )
                    );

                    ps.setInt(
                            2,
                            fixtures.size()
                    );

                    ps.executeUpdate();
                }

                connection.commit();

            } catch (
                    Exception e
            ) {
                connection.rollback();
                throw e;
            }

        } catch (
                SQLException e
        ) {
            throw new IllegalStateException(
                    "Nie udało się zapisać fixtures dla "
                            + date,
                    e
            );
        }
    }

    public List<ApiFootballFixture> findBetween(
            LocalDate from,
            LocalDate to
    ) {
        String sql = """
                SELECT
                    fixture_id,

                    kickoff_at,
                    fixture_date,

                    league_id,
                    league_name,
                    league_country,

                    season,
                    round,

                    home_team_id,
                    home_team_name,

                    away_team_id,
                    away_team_name,

                    goals_home,
                    goals_away,

                    status_short,
                    status_long,

                    raw_json::text AS raw_json

                FROM api_football_fixtures

                WHERE fixture_date >= ?
                  AND fixture_date <= ?

                ORDER BY
                    kickoff_at,
                    fixture_id
                """;

        List<ApiFootballFixture> result =
                new ArrayList<>();

        try (
                Connection connection =
                        database.openConnection();

                PreparedStatement ps =
                        connection.prepareStatement(
                                sql
                        )
        ) {
            ps.setDate(
                    1,
                    Date.valueOf(
                            from
                    )
            );

            ps.setDate(
                    2,
                    Date.valueOf(
                            to
                    )
            );

            try (
                    ResultSet rs =
                            ps.executeQuery()
            ) {
                while (
                        rs.next()
                ) {
                    result.add(
                            new ApiFootballFixture(
                                    rs.getLong(
                                            "fixture_id"
                                    ),

                                    rs.getTimestamp(
                                            "kickoff_at"
                                    ).toInstant(),

                                    rs.getDate(
                                            "fixture_date"
                                    ).toLocalDate(),

                                    nullableLong(
                                            rs,
                                            "league_id"
                                    ),

                                    rs.getString(
                                            "league_name"
                                    ),

                                    rs.getString(
                                            "league_country"
                                    ),

                                    nullableInteger(
                                            rs,
                                            "season"
                                    ),

                                    rs.getString(
                                            "round"
                                    ),

                                    nullableLong(
                                            rs,
                                            "home_team_id"
                                    ),

                                    rs.getString(
                                            "home_team_name"
                                    ),

                                    nullableLong(
                                            rs,
                                            "away_team_id"
                                    ),

                                    rs.getString(
                                            "away_team_name"
                                    ),

                                    nullableInteger(
                                            rs,
                                            "goals_home"
                                    ),

                                    nullableInteger(
                                            rs,
                                            "goals_away"
                                    ),

                                    rs.getString(
                                            "status_short"
                                    ),

                                    rs.getString(
                                            "status_long"
                                    ),

                                    rs.getString(
                                            "raw_json"
                                    )
                            )
                    );
                }
            }

            return List.copyOf(
                    result
            );

        } catch (
                SQLException e
        ) {
            throw new IllegalStateException(
                    "Nie udało się pobrać fixtures z cache",
                    e
            );
        }
    }

    public void saveMatch(
            ApiFootballResolutionCandidate candidate,
            ApiFootballMatch match
    ) {
        if (
                candidate.hasLocalResolution()
        ) {
            enrichLocalResolution(
                    candidate,
                    match
            );

            return;
        }

        saveApiResolution(
                candidate.legId(),
                match
        );
    }

    /*
     * Zachowujemy stary overload, żeby nie rozwalić
     * ewentualnych testów/innych wywołań.
     */
    public void saveMatch(
            long legId,
            ApiFootballMatch match
    ) {
        saveApiResolution(
                legId,
                match
        );
    }

    private void enrichLocalResolution(
            ApiFootballResolutionCandidate candidate,
            ApiFootballMatch match
    ) {
        ApiFootballFixture fixture =
                match.fixture();

        /*
         * Lokalny resolver już ustalił event i jego źródło.
         *
         * Nie nadpisujemy:
         * - resolution_source,
         * - resolution_confidence,
         * - resolved_event_name,
         * - resolved_participant_a/b.
         *
         * API-Football pełni tu rolę providera/enrichment:
         * potwierdza FOOTBALL i dostarcza fixture_id potrzebne
         * później do settlementu.
         */
        String sql = """
                UPDATE bet_legs
                SET
                    resolved_sport = 'FOOTBALL',

                    resolved_event_date =
                        COALESCE(
                            resolved_event_date,
                            ?
                        ),

                    resolution_evidence =
                        CASE
                            WHEN resolution_evidence IS NULL
                                 OR BTRIM(resolution_evidence) = ''
                            THEN ?
                            ELSE resolution_evidence
                                 || ' | API_FOOTBALL: '
                                 || ?
                        END,

                    resolved_provider = 'API_FOOTBALL',
                    resolved_external_event_id = ?,

                    resolved_at =
                        COALESCE(
                            resolved_at,
                            NOW()
                        ),

                    updated_at = NOW()

                WHERE id = ?
                """;

        try (
                Connection connection =
                        database.openConnection();

                PreparedStatement ps =
                        connection.prepareStatement(
                                sql
                        )
        ) {
            ps.setDate(
                    1,
                    Date.valueOf(
                            fixture.fixtureDate()
                    )
            );

            ps.setString(
                    2,
                    "API_FOOTBALL: "
                            + match.evidence()
            );

            ps.setString(
                    3,
                    match.evidence()
            );

            ps.setString(
                    4,
                    Long.toString(
                            fixture.fixtureId()
                    )
            );

            ps.setLong(
                    5,
                    candidate.legId()
            );

            ps.executeUpdate();

        } catch (
                SQLException e
        ) {
            throw new IllegalStateException(
                    "Nie udało się wzbogacić lokalnego resolution "
                            + "API-Football dla leg="
                            + candidate.legId(),
                    e
            );
        }
    }

    private void saveApiResolution(
            long legId,
            ApiFootballMatch match
    ) {
        ApiFootballFixture fixture =
                match.fixture();

        String sql = """
                UPDATE bet_legs
                SET
                    resolved_sport = 'FOOTBALL',

                    resolved_event_name = ?,
                    resolved_participant_a = ?,
                    resolved_participant_b = ?,

                    resolved_event_date = ?,

                    resolution_source = 'API_FOOTBALL',
                    resolution_confidence = ?,
                    resolution_evidence = ?,

                    resolved_provider = 'API_FOOTBALL',
                    resolved_external_event_id = ?,

                    resolved_at = NOW(),
                    updated_at = NOW()

                WHERE id = ?
                """;

        try (
                Connection connection =
                        database.openConnection();

                PreparedStatement ps =
                        connection.prepareStatement(
                                sql
                        )
        ) {
            ps.setString(
                    1,
                    fixture.homeTeamName()
                            + " – "
                            + fixture.awayTeamName()
            );

            ps.setString(
                    2,
                    fixture.homeTeamName()
            );

            ps.setString(
                    3,
                    fixture.awayTeamName()
            );

            ps.setDate(
                    4,
                    Date.valueOf(
                            fixture.fixtureDate()
                    )
            );

            ps.setString(
                    5,
                    match.confidence()
                            .name()
            );

            ps.setString(
                    6,
                    match.evidence()
            );

            ps.setString(
                    7,
                    Long.toString(
                            fixture.fixtureId()
                    )
            );

            ps.setLong(
                    8,
                    legId
            );

            ps.executeUpdate();

        } catch (
                SQLException e
        ) {
            throw new IllegalStateException(
                    "Nie udało się zapisać matcha API-Football"
                            + " dla leg="
                            + legId,
                    e
            );
        }
    }

    private static void upsertFixture(
            Connection connection,
            ApiFootballFixture fixture
    ) throws SQLException {

        String sql = """
                INSERT INTO api_football_fixtures (
                    fixture_id,

                    kickoff_at,
                    fixture_date,

                    league_id,
                    league_name,
                    league_country,

                    season,
                    round,

                    home_team_id,
                    home_team_name,

                    away_team_id,
                    away_team_name,

                    goals_home,
                    goals_away,

                    status_short,
                    status_long,

                    raw_json,

                    fetched_at
                )
                VALUES (
                    ?,
                    ?, ?,
                    ?, ?, ?,
                    ?, ?,
                    ?, ?,
                    ?, ?,
                    ?, ?,
                    ?, ?,
                    ?::jsonb,
                    NOW()
                )

                ON CONFLICT (fixture_id)
                DO UPDATE SET
                    kickoff_at = EXCLUDED.kickoff_at,
                    fixture_date = EXCLUDED.fixture_date,

                    league_id = EXCLUDED.league_id,
                    league_name = EXCLUDED.league_name,
                    league_country = EXCLUDED.league_country,

                    season = EXCLUDED.season,
                    round = EXCLUDED.round,

                    home_team_id = EXCLUDED.home_team_id,
                    home_team_name = EXCLUDED.home_team_name,

                    away_team_id = EXCLUDED.away_team_id,
                    away_team_name = EXCLUDED.away_team_name,

                    goals_home = EXCLUDED.goals_home,
                    goals_away = EXCLUDED.goals_away,

                    status_short = EXCLUDED.status_short,
                    status_long = EXCLUDED.status_long,

                    raw_json = EXCLUDED.raw_json,

                    fetched_at = NOW()
                """;

        try (
                PreparedStatement ps =
                        connection.prepareStatement(
                                sql
                        )
        ) {
            ps.setLong(
                    1,
                    fixture.fixtureId()
            );

            ps.setTimestamp(
                    2,
                    Timestamp.from(
                            fixture.kickoffAt()
                    )
            );

            ps.setDate(
                    3,
                    Date.valueOf(
                            fixture.fixtureDate()
                    )
            );

            setLong(
                    ps,
                    4,
                    fixture.leagueId()
            );

            ps.setString(
                    5,
                    fixture.leagueName()
            );

            ps.setString(
                    6,
                    fixture.leagueCountry()
            );

            setInteger(
                    ps,
                    7,
                    fixture.season()
            );

            ps.setString(
                    8,
                    fixture.round()
            );

            setLong(
                    ps,
                    9,
                    fixture.homeTeamId()
            );

            ps.setString(
                    10,
                    fixture.homeTeamName()
            );

            setLong(
                    ps,
                    11,
                    fixture.awayTeamId()
            );

            ps.setString(
                    12,
                    fixture.awayTeamName()
            );

            setInteger(
                    ps,
                    13,
                    fixture.goalsHome()
            );

            setInteger(
                    ps,
                    14,
                    fixture.goalsAway()
            );

            ps.setString(
                    15,
                    fixture.statusShort()
            );

            ps.setString(
                    16,
                    fixture.statusLong()
            );

            ps.setString(
                    17,
                    fixture.rawJson()
            );

            ps.executeUpdate();
        }
    }

    private static ResolvedSport parseSport(
            String value
    ) {
        if (
                value == null
                        || value.isBlank()
        ) {
            return ResolvedSport.UNKNOWN;
        }

        try {
            return ResolvedSport.valueOf(
                    value
            );

        } catch (
                IllegalArgumentException e
        ) {
            return ResolvedSport.UNKNOWN;
        }
    }

    private static ResolutionSource parseResolutionSource(
            String value
    ) {
        if (
                value == null
                        || value.isBlank()
        ) {
            return ResolutionSource.NONE;
        }

        try {
            return ResolutionSource.valueOf(
                    value
            );

        } catch (
                IllegalArgumentException e
        ) {
            return ResolutionSource.NONE;
        }
    }

    private static ResolutionConfidence parseResolutionConfidence(
            String value
    ) {
        if (
                value == null
                        || value.isBlank()
        ) {
            return null;
        }

        try {
            return ResolutionConfidence.valueOf(
                    value
            );

        } catch (
                IllegalArgumentException e
        ) {
            return null;
        }
    }

    private static String localMatchup(
            String participantA,
            String participantB,
            String eventName
    ) {
        if (
                participantA != null
                        && !participantA.isBlank()
                        && participantB != null
                        && !participantB.isBlank()
        ) {
            return participantA.trim()
                    + " – "
                    + participantB.trim();
        }

        return eventName;
    }

    private static String joinEvidence(
            String... values
    ) {
        StringBuilder result =
                new StringBuilder();

        for (
                String value :
                values
        ) {
            if (
                    value == null
                            || value.isBlank()
            ) {
                continue;
            }

            if (
                    !result.isEmpty()
            ) {
                result.append(
                        " | "
                );
            }

            result.append(
                    value.trim()
            );
        }

        return result.isEmpty()
                ? null
                : result.toString();
    }

    private static Integer nullableInteger(
            ResultSet rs,
            String column
    ) throws SQLException {

        int value =
                rs.getInt(
                        column
                );

        return rs.wasNull()
                ? null
                : value;
    }

    private static Long nullableLong(
            ResultSet rs,
            String column
    ) throws SQLException {

        long value =
                rs.getLong(
                        column
                );

        return rs.wasNull()
                ? null
                : value;
    }

    private static void setInteger(
            PreparedStatement ps,
            int index,
            Integer value
    ) throws SQLException {

        if (
                value == null
        ) {
            ps.setNull(
                    index,
                    Types.INTEGER
            );

        } else {
            ps.setInt(
                    index,
                    value
            );
        }
    }

    private static void setLong(
            PreparedStatement ps,
            int index,
            Long value
    ) throws SQLException {

        if (
                value == null
        ) {
            ps.setNull(
                    index,
                    Types.BIGINT
            );

        } else {
            ps.setLong(
                    index,
                    value
            );
        }
    }
}