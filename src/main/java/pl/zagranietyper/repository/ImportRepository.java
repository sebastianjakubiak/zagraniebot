package pl.zagranietyper.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.zagranietyper.model.EventMetadata;
import pl.zagranietyper.model.ParsedBet;
import pl.zagranietyper.model.ParsedLeg;
import pl.zagranietyper.model.ParsedPost;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

public final class ImportRepository {

    private final Database database;
    private final ObjectMapper objectMapper;

    public ImportRepository(
            Database database,
            ObjectMapper objectMapper
    ) {
        this.database =
                database;

        this.objectMapper =
                objectMapper;
    }

    public Set<Long> findExistingPostIds() {
        String sql =
                """
                SELECT wp_post_id
                FROM posts
                """;

        Set<Long> result =
                new HashSet<>();

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
                result.add(
                        rs.getLong(
                                "wp_post_id"
                        )
                );
            }

            return result;

        } catch (
                SQLException e
        ) {
            throw new IllegalStateException(
                    "Nie udało się pobrać istniejących wp_post_id",
                    e
            );
        }
    }

    public void upsertAuthor(
            long wpAuthorId,
            String displayName
    ) {
        String sql = """
                INSERT INTO authors (
                    wp_author_id,
                    display_name
                )
                VALUES (?, ?)

                ON CONFLICT (wp_author_id)
                DO UPDATE SET
                    display_name = EXCLUDED.display_name,
                    last_seen_at = NOW()
                """;

        try (
                Connection connection =
                        database.openConnection();

                PreparedStatement ps =
                        connection.prepareStatement(sql)
        ) {
            ps.setLong(
                    1,
                    wpAuthorId
            );

            ps.setString(
                    2,
                    displayName
            );

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Nie udało się zapisać autora "
                            + wpAuthorId,
                    e
            );
        }
    }

    public SaveResult savePostWithBets(
            ParsedPost post
    ) {
        try (
                Connection connection =
                        database.openConnection()
        ) {
            connection.setAutoCommit(false);

            try {
                upsertPost(
                        connection,
                        post
                );

                deactivateExistingBetsAndLegs(
                        connection,
                        post.wpPostId()
                );

                int betsSaved = 0;
                int legsSaved = 0;

                for (
                        ParsedBet bet :
                        post.bets()
                ) {
                    long betId =
                            upsertBet(
                                    connection,
                                    post.wpPostId(),
                                    bet
                            );

                    betsSaved++;

                    for (
                            ParsedLeg leg :
                            bet.legs()
                    ) {
                        upsertLeg(
                                connection,
                                betId,
                                leg
                        );

                        legsSaved++;
                    }
                }

                connection.commit();

                return new SaveResult(
                        betsSaved,
                        legsSaved
                );

            } catch (
                    SQLException
                    | RuntimeException e
            ) {
                connection.rollback();
                throw e;
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Nie udało się zapisać posta "
                            + post.wpPostId(),
                    e
            );
        }
    }

    public long startImportRun(
            long authorId,
            Instant from,
            Instant to
    ) {
        String sql = """
                INSERT INTO import_runs (
                    wp_author_id,
                    date_from,
                    date_to
                )
                VALUES (?, ?, ?)
                RETURNING id
                """;

        try (
                Connection connection =
                        database.openConnection();

                PreparedStatement ps =
                        connection.prepareStatement(sql)
        ) {
            ps.setLong(
                    1,
                    authorId
            );

            ps.setTimestamp(
                    2,
                    Timestamp.from(from)
            );

            ps.setTimestamp(
                    3,
                    Timestamp.from(to)
            );

            try (
                    ResultSet rs =
                            ps.executeQuery()
            ) {
                rs.next();
                return rs.getLong(1);
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Nie udało się rozpocząć import_run",
                    e
            );
        }
    }

    public void updateImportRun(
            long runId,
            int pages,
            int postsSeen,
            int postsSaved,
            int betsSaved,
            int legsSaved
    ) {
        String sql = """
                UPDATE import_runs
                SET pages_fetched = ?,
                    posts_seen = ?,
                    posts_saved = ?,
                    bets_saved = ?,
                    legs_saved = ?
                WHERE id = ?
                """;

        try (
                Connection connection =
                        database.openConnection();

                PreparedStatement ps =
                        connection.prepareStatement(sql)
        ) {
            ps.setInt(
                    1,
                    pages
            );

            ps.setInt(
                    2,
                    postsSeen
            );

            ps.setInt(
                    3,
                    postsSaved
            );

            ps.setInt(
                    4,
                    betsSaved
            );

            ps.setInt(
                    5,
                    legsSaved
            );

            ps.setLong(
                    6,
                    runId
            );

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Nie udało się zaktualizować import_run="
                            + runId,
                    e
            );
        }
    }

    public void finishImportRunSuccess(
            long runId,
            int pages,
            int postsSeen,
            int postsSaved,
            int betsSaved,
            int legsSaved
    ) {
        String sql = """
                UPDATE import_runs
                SET status = 'SUCCESS',
                    finished_at = NOW(),
                    pages_fetched = ?,
                    posts_seen = ?,
                    posts_saved = ?,
                    bets_saved = ?,
                    legs_saved = ?
                WHERE id = ?
                """;

        try (
                Connection connection =
                        database.openConnection();

                PreparedStatement ps =
                        connection.prepareStatement(sql)
        ) {
            ps.setInt(
                    1,
                    pages
            );

            ps.setInt(
                    2,
                    postsSeen
            );

            ps.setInt(
                    3,
                    postsSaved
            );

            ps.setInt(
                    4,
                    betsSaved
            );

            ps.setInt(
                    5,
                    legsSaved
            );

            ps.setLong(
                    6,
                    runId
            );

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Nie udało się zakończyć import_run="
                            + runId,
                    e
            );
        }
    }

    public void finishImportRunFailed(
            long runId,
            String errorMessage
    ) {
        String sql = """
                UPDATE import_runs
                SET status = 'FAILED',
                    finished_at = NOW(),
                    error_message = ?
                WHERE id = ?
                """;

        try (
                Connection connection =
                        database.openConnection();

                PreparedStatement ps =
                        connection.prepareStatement(sql)
        ) {
            ps.setString(
                    1,
                    abbreviate(
                            errorMessage,
                            10_000
                    )
            );

            ps.setLong(
                    2,
                    runId
            );

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Nie udało się oznaczyć import_run="
                            + runId
                            + " jako FAILED",
                    e
            );
        }
    }

    private void upsertPost(
            Connection connection,
            ParsedPost post
    ) throws SQLException {
        String sql = """
                INSERT INTO posts (
                    wp_post_id,
                    wp_author_id,
                    slug,
                    title,
                    url,
                    published_at,
                    modified_at,
                    raw_html,
                    raw_metadata_json,
                    content_hash
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)

                ON CONFLICT (wp_post_id)
                DO UPDATE SET
                    wp_author_id = EXCLUDED.wp_author_id,
                    slug = EXCLUDED.slug,
                    title = EXCLUDED.title,
                    url = EXCLUDED.url,
                    published_at = EXCLUDED.published_at,
                    modified_at = EXCLUDED.modified_at,
                    raw_html = EXCLUDED.raw_html,
                    raw_metadata_json = EXCLUDED.raw_metadata_json,
                    content_hash = EXCLUDED.content_hash,
                    updated_at = NOW()
                """;

        try (
                PreparedStatement ps =
                        connection.prepareStatement(sql)
        ) {
            ps.setLong(
                    1,
                    post.wpPostId()
            );

            ps.setLong(
                    2,
                    post.wpAuthorId()
            );

            ps.setString(
                    3,
                    post.slug()
            );

            ps.setString(
                    4,
                    post.title()
            );

            ps.setString(
                    5,
                    post.url()
            );

            ps.setTimestamp(
                    6,
                    Timestamp.from(
                            post.publishedAt()
                    )
            );

            setTimestampNullable(
                    ps,
                    7,
                    post.modifiedAt()
            );

            ps.setString(
                    8,
                    post.rawHtml()
            );

            ps.setString(
                    9,
                    json(
                            post.rawMetadataBlocks()
                    )
            );

            ps.setString(
                    10,
                    post.contentHash()
            );

            ps.executeUpdate();
        }
    }

    private void deactivateExistingBetsAndLegs(
            Connection connection,
            long wpPostId
    ) throws SQLException {
        try (
                PreparedStatement ps =
                        connection.prepareStatement("""
                                UPDATE bet_legs bl
                                SET active = FALSE,
                                    updated_at = NOW()
                                FROM bets b
                                WHERE bl.bet_id = b.id
                                  AND b.wp_post_id = ?
                                """)
        ) {
            ps.setLong(
                    1,
                    wpPostId
            );

            ps.executeUpdate();
        }

        try (
                PreparedStatement ps =
                        connection.prepareStatement("""
                                UPDATE bets
                                SET active = FALSE,
                                    updated_at = NOW()
                                WHERE wp_post_id = ?
                                """)
        ) {
            ps.setLong(
                    1,
                    wpPostId
            );

            ps.executeUpdate();
        }
    }

    private long upsertBet(
            Connection connection,
            long wpPostId,
            ParsedBet bet
    ) throws SQLException {
        String sql = """
                INSERT INTO bets (
                    wp_post_id,
                    ordinal,
                    source_fingerprint,
                    active,
                    bet_type,
                    displayed_odds,
                    calculated_odds,
                    odds_source,
                    odds_verified,
                    odds_consistency
                )
                VALUES (
                    ?, ?, ?, TRUE,
                    ?, ?, ?, ?, ?, ?
                )

                ON CONFLICT (
                    wp_post_id,
                    source_fingerprint
                )
                DO UPDATE SET
                    ordinal = EXCLUDED.ordinal,
                    active = TRUE,
                    bet_type = EXCLUDED.bet_type,
                    displayed_odds = EXCLUDED.displayed_odds,
                    calculated_odds = EXCLUDED.calculated_odds,
                    odds_source = EXCLUDED.odds_source,
                    odds_verified = EXCLUDED.odds_verified,
                    odds_consistency = EXCLUDED.odds_consistency,
                    updated_at = NOW()

                RETURNING id
                """;

        try (
                PreparedStatement ps =
                        connection.prepareStatement(sql)
        ) {
            ps.setLong(
                    1,
                    wpPostId
            );

            ps.setInt(
                    2,
                    bet.ordinal()
            );

            ps.setString(
                    3,
                    bet.sourceFingerprint()
            );

            ps.setString(
                    4,
                    bet.type().name()
            );

            setBigDecimalNullable(
                    ps,
                    5,
                    bet.displayedOdds()
            );

            setBigDecimalNullable(
                    ps,
                    6,
                    bet.calculatedOdds()
            );

            ps.setString(
                    7,
                    bet.oddsSource().name()
            );

            ps.setBoolean(
                    8,
                    bet.oddsVerified()
            );

            ps.setString(
                    9,
                    bet.oddsConsistency().name()
            );

            try (
                    ResultSet rs =
                            ps.executeQuery()
            ) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private void upsertLeg(
            Connection connection,
            long betId,
            ParsedLeg leg
    ) throws SQLException {
        String sql = """
                INSERT INTO bet_legs (
                    bet_id,
                    ordinal,
                    source_fingerprint,
                    active,
                    operator,
                    tip_title,
                    tip_odds,
                    event_external_id,
                    event_home,
                    event_away,
                    event_competition,
                    event_start_at,
                    event_start_raw,
                    source_attributes,
                    event_attributes
                )
                VALUES (
                    ?, ?, ?, TRUE,
                    ?, ?, ?,
                    ?, ?, ?, ?,
                    ?, ?,
                    ?::jsonb,
                    ?::jsonb
                )

                ON CONFLICT (
                    bet_id,
                    source_fingerprint
                )
                DO UPDATE SET
                    ordinal = EXCLUDED.ordinal,
                    active = TRUE,
                    operator = EXCLUDED.operator,
                    tip_title = EXCLUDED.tip_title,
                    tip_odds = EXCLUDED.tip_odds,
                    event_external_id = EXCLUDED.event_external_id,
                    event_home = EXCLUDED.event_home,
                    event_away = EXCLUDED.event_away,
                    event_competition = EXCLUDED.event_competition,
                    event_start_at = EXCLUDED.event_start_at,
                    event_start_raw = EXCLUDED.event_start_raw,
                    source_attributes = EXCLUDED.source_attributes,
                    event_attributes = EXCLUDED.event_attributes,
                    updated_at = NOW()
                """;

        EventMetadata event =
                leg.event();

        try (
                PreparedStatement ps =
                        connection.prepareStatement(sql)
        ) {
            ps.setLong(
                    1,
                    betId
            );

            ps.setInt(
                    2,
                    leg.ordinal()
            );

            ps.setString(
                    3,
                    leg.sourceFingerprint()
            );

            ps.setString(
                    4,
                    leg.operator()
            );

            ps.setString(
                    5,
                    leg.tipTitle()
            );

            setBigDecimalNullable(
                    ps,
                    6,
                    leg.tipOdds()
            );

            ps.setString(
                    7,
                    event.externalId()
            );

            ps.setString(
                    8,
                    event.home()
            );

            ps.setString(
                    9,
                    event.away()
            );

            ps.setString(
                    10,
                    event.competition()
            );

            setTimestampNullable(
                    ps,
                    11,
                    event.startAt()
            );

            ps.setString(
                    12,
                    event.startRaw()
            );

            ps.setString(
                    13,
                    json(
                            leg.sourceAttributes()
                    )
            );

            ps.setString(
                    14,
                    json(
                            event.attributes()
                    )
            );

            ps.executeUpdate();
        }
    }

    private String json(
            Object value
    ) {
        try {
            return objectMapper.writeValueAsString(
                    value
            );

        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Nie udało się zserializować JSON",
                    e
            );
        }
    }

    private static void setTimestampNullable(
            PreparedStatement ps,
            int index,
            Instant instant
    ) throws SQLException {
        if (
                instant == null
        ) {
            ps.setNull(
                    index,
                    Types.TIMESTAMP_WITH_TIMEZONE
            );

        } else {
            ps.setTimestamp(
                    index,
                    Timestamp.from(
                            instant
                    )
            );
        }
    }

    private static void setBigDecimalNullable(
            PreparedStatement ps,
            int index,
            java.math.BigDecimal value
    ) throws SQLException {
        if (
                value == null
        ) {
            ps.setNull(
                    index,
                    Types.NUMERIC
            );

        } else {
            ps.setBigDecimal(
                    index,
                    value
            );
        }
    }

    private static String abbreviate(
            String value,
            int maxLength
    ) {
        if (
                value == null
                        || value.length() <= maxLength
        ) {
            return value;
        }

        return value.substring(
                0,
                maxLength
        );
    }

    public record SaveResult(
            int betsSaved,
            int legsSaved
    ) {
    }
}