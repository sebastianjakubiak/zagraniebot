package pl.zagranietyper;

import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.fixture.ApiFootballMatcher;
import pl.zagranietyper.fixture.ApiFootballSafeMatcher;
import pl.zagranietyper.model.ApiFootballFixture;
import pl.zagranietyper.model.ApiFootballMatch;
import pl.zagranietyper.model.ApiFootballResolutionCandidate;
import pl.zagranietyper.model.BetType;
import pl.zagranietyper.model.ResolvedSport;
import pl.zagranietyper.repository.ApiFootballRepository;
import pl.zagranietyper.repository.Database;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Full dry-run revalidation of every active leg currently resolved
 * by API-Football.
 *
 * IMPORTANT:
 *
 * - no API calls
 * - no database writes
 * - does not load fixture raw_json
 * - keeps only one publication-date fixture window in memory
 *
 * It compares the fixture already stored on the leg with the fixture selected by:
 *
 * ApiFootballSafeMatcher -> current ApiFootballMatcher.
 */
public final class RevalidateApiFootballMain {

    private static final ZoneId WARSAW =
            ZoneId.of(
                    "Europe/Warsaw"
            );

    private static final int DAYS_BEFORE_PUBLICATION =
            1;

    private static final int DAYS_AFTER_PUBLICATION =
            3;

    private RevalidateApiFootballMain() {
    }

    public static void main(
            String[] args
    ) {
        AppConfig config =
                AppConfig.fromEnvironment();

        Database database =
                new Database(
                        config
                );

        ApiFootballRepository fixtureRepository =
                new ApiFootballRepository(
                        database
                );

        ApiFootballSafeMatcher matcher =
                new ApiFootballSafeMatcher(
                        new ApiFootballMatcher()
                );

        List<Row> rows =
                findRows(
                        database
                );

        List<Diff> diffs =
                new ArrayList<>();

        int same =
                0;

        int changed =
                0;

        int nowNone =
                0;

        int incompleteWindow =
                0;

        int missingPublishedAt =
                0;

        int changedAuto =
                0;

        int nowNoneAuto =
                0;

        int changedPending =
                0;

        int nowNonePending =
                0;

        /*
         * Rows są posortowane po published_at.
         *
         * Dlatego nie potrzebujemy:
         *
         * Map<LocalDate, List<ApiFootballFixture>>
         *
         * która wcześniej trzymała setki mocno pokrywających się
         * pięciodniowych okien w pamięci.
         *
         * Trzymamy wyłącznie JEDNO okno dla aktualnej daty publikacji.
         */
        LocalDate loadedPublicationDate =
                null;

        List<ApiFootballFixture> loadedFixtures =
                List.of();

        boolean loadedWindowComplete =
                false;

        LocalDate loadedFrom =
                null;

        LocalDate loadedTo =
                null;

        for (
                Row row :
                rows
        ) {
            if (
                    row.publishedAt() == null
            ) {
                missingPublishedAt++;
                nowNone++;

                if (
                        row.isAutoSettled()
                ) {
                    nowNoneAuto++;
                } else if (
                        row.isPending()
                ) {
                    nowNonePending++;
                }

                diffs.add(
                        new Diff(
                                DiffType.NOW_NONE,
                                row,
                                null,
                                "missing published_at"
                        )
                );

                continue;
            }

            LocalDate publicationDate =
                    row.publishedAt()
                            .atZone(
                                    WARSAW
                            )
                            .toLocalDate();

            /*
             * Nowe fixture window ładujemy tylko wtedy,
             * gdy zmienia się data publikacji.
             */
            if (
                    !publicationDate.equals(
                            loadedPublicationDate
                    )
            ) {
                loadedPublicationDate =
                        publicationDate;

                loadedFrom =
                        publicationDate.minusDays(
                                DAYS_BEFORE_PUBLICATION
                        );

                loadedTo =
                        publicationDate.plusDays(
                                DAYS_AFTER_PUBLICATION
                        );

                loadedWindowComplete =
                        fixtureRepository.isWindowComplete(
                                loadedFrom,
                                loadedTo
                        );

                if (
                        loadedWindowComplete
                ) {
                    loadedFixtures =
                            findLightFixturesBetween(
                                    database,
                                    loadedFrom,
                                    loadedTo
                            );
                } else {
                    loadedFixtures =
                            List.of();
                }
            }

            if (
                    !loadedWindowComplete
            ) {
                incompleteWindow++;

                diffs.add(
                        new Diff(
                                DiffType.INCOMPLETE_WINDOW,
                                row,
                                null,
                                "window="
                                        + loadedFrom
                                        + ".."
                                        + loadedTo
                        )
                );

                continue;
            }

            ApiFootballResolutionCandidate candidate =
                    new ApiFootballResolutionCandidate(
                            row.legId(),
                            row.wpPostId(),

                            row.betType(),
                            row.betLegCount(),

                            row.publishedAt(),

                            row.revalidationSport(),

                            row.postTitle(),
                            row.tipTitle(),
                            row.heading(),
                            row.previousText()
                    );

            ApiFootballMatch newMatch =
                    matcher.match(
                            candidate,
                            loadedFixtures
                    );

            if (
                    newMatch == null
            ) {
                nowNone++;

                if (
                        row.isAutoSettled()
                ) {
                    nowNoneAuto++;
                } else if (
                        row.isPending()
                ) {
                    nowNonePending++;
                }

                diffs.add(
                        new Diff(
                                DiffType.NOW_NONE,
                                row,
                                null,
                                null
                        )
                );

                continue;
            }

            long newFixtureId =
                    newMatch.fixture()
                            .fixtureId();

            if (
                    row.oldFixtureId() != null
                            && row.oldFixtureId()
                            == newFixtureId
            ) {
                same++;
                continue;
            }

            changed++;

            if (
                    row.isAutoSettled()
            ) {
                changedAuto++;
            } else if (
                    row.isPending()
            ) {
                changedPending++;
            }

            diffs.add(
                    new Diff(
                            DiffType.CHANGED,
                            row,
                            newMatch,
                            null
                    )
            );
        }

        System.out.println(
                "========================================"
        );

        System.out.println(
                "API-FOOTBALL REVALIDATION DRY RUN"
        );

        System.out.println(
                "========================================"
        );

        System.out.println(
                "rows="
                        + rows.size()
        );

        System.out.println(
                "same="
                        + same
        );

        System.out.println(
                "changed="
                        + changed
        );

        System.out.println(
                "nowNone="
                        + nowNone
        );

        System.out.println(
                "incompleteWindow="
                        + incompleteWindow
        );

        System.out.println(
                "missingPublishedAt="
                        + missingPublishedAt
        );

        System.out.println();

        System.out.println(
                "AUTO AFFECTED"
        );

        System.out.println(
                "changedAuto="
                        + changedAuto
        );

        System.out.println(
                "nowNoneAuto="
                        + nowNoneAuto
        );

        System.out.println(
                "autoAffected="
                        + (
                        changedAuto
                                + nowNoneAuto
                )
        );

        System.out.println();

        System.out.println(
                "PENDING/NONE AFFECTED"
        );

        System.out.println(
                "changedPending="
                        + changedPending
        );

        System.out.println(
                "nowNonePending="
                        + nowNonePending
        );

        System.out.println(
                "pendingAffected="
                        + (
                        changedPending
                                + nowNonePending
                )
        );

        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "DIFFS"
        );

        System.out.println(
                "========================================"
        );

        diffs.stream()
                .sorted(
                        (
                                left,
                                right
                        ) -> {
                            int auto =
                                    Boolean.compare(
                                            right.row()
                                                    .isAutoSettled(),
                                            left.row()
                                                    .isAutoSettled()
                                    );

                            if (
                                    auto != 0
                            ) {
                                return auto;
                            }

                            int type =
                                    left.type()
                                            .compareTo(
                                                    right.type()
                                            );

                            if (
                                    type != 0
                            ) {
                                return type;
                            }

                            return Long.compare(
                                    left.row()
                                            .legId(),
                                    right.row()
                                            .legId()
                            );
                        }
                )
                .forEach(
                        RevalidateApiFootballMain::printDiff
                );

        System.out.println();

        System.out.println(
                "DRY RUN ONLY — DATABASE NOT MODIFIED"
        );
    }

    /*
     * =========================================================
     * CURRENT API-FOOTBALL LEGS
     * =========================================================
     */

    private static List<Row> findRows(
            Database database
    ) {
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

                    l.resolved_external_event_id,
                    l.resolved_event_name,
                    l.resolution_evidence,

                    l.settlement_status,
                    l.settlement_source

                FROM bet_legs l

                JOIN bets b
                  ON b.id = l.bet_id
                 AND b.active = TRUE

                JOIN posts p
                  ON p.wp_post_id = b.wp_post_id

                WHERE l.active = TRUE
                  AND l.resolved_provider = 'API_FOOTBALL'
                  AND l.resolution_source = 'API_FOOTBALL'

                ORDER BY
                    p.published_at,
                    p.wp_post_id,
                    b.ordinal,
                    l.ordinal
                """;

        List<Row> result =
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

                String evidence =
                        rs.getString(
                                "resolution_evidence"
                        );

                result.add(
                        new Row(
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

                                rs.getString(
                                        "tip_title"
                                ),

                                rs.getString(
                                        "context_heading"
                                ),

                                rs.getString(
                                        "context_previous_text"
                                ),

                                parseLong(
                                        rs.getString(
                                                "resolved_external_event_id"
                                        )
                                ),

                                rs.getString(
                                        "resolved_event_name"
                                ),

                                evidence,

                                evidence != null
                                        && evidence.contains(
                                        "requiredGap=0.060"
                                ),

                                rs.getString(
                                        "settlement_status"
                                ),

                                rs.getString(
                                        "settlement_source"
                                )
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
                    "Nie udało się pobrać API_FOOTBALL do rewalidacji",
                    e
            );
        }
    }

    /*
     * =========================================================
     * LIGHTWEIGHT FIXTURE LOAD
     * =========================================================
     *
     * ApiFootballRepository.findBetween() pobiera raw_json.
     *
     * Do matchera potrzebujemy tutaj tylko:
     *
     * - fixture id
     * - daty
     * - nazw drużyn
     * - metadanych wariantu
     *
     * Nie ma żadnego sensu dekodować i przechowywać
     * dziesiątek tysięcy JSON-ów.
     */

    private static List<ApiFootballFixture>
    findLightFixturesBetween(
            Database database,
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
                    status_long

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

                                    /*
                                     * Matcher tego nie używa.
                                     *
                                     * Celowo NIE pobieramy raw_json
                                     * z PostgreSQL.
                                     */
                                    "{}"
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
                    "Nie udało się pobrać lekkiego fixture window "
                            + from
                            + ".."
                            + to,
                    e
            );
        }
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

    /*
     * =========================================================
     * OUTPUT
     * =========================================================
     */

    private static void printDiff(
            Diff diff
    ) {
        Row row =
                diff.row();

        System.out.println();

        System.out.println(
                "----------------------------------------"
        );

        System.out.println(
                diff.type()
                        + (
                        row.isAutoSettled()
                                ? " [AUTO]"
                                : ""
                )
        );

        System.out.println(
                "legId="
                        + row.legId()
                        + " wpPostId="
                        + row.wpPostId()
        );

        System.out.println(
                "settlement="
                        + safe(
                        row.settlementStatus()
                )
                        + "/"
                        + safe(
                        row.settlementSource()
                )
        );

        System.out.println(
                "hadLocalResolution="
                        + row.hadLocalResolution()
        );

        System.out.println(
                "tip="
                        + oneLine(
                        row.tipTitle()
                )
        );

        System.out.println(
                "heading="
                        + oneLine(
                        row.heading()
                )
        );

        System.out.println(
                "oldFixtureId="
                        + value(
                        row.oldFixtureId()
                )
                        + " oldEvent="
                        + oneLine(
                        row.oldEventName()
                )
        );

        if (
                diff.newMatch() == null
        ) {
            System.out.println(
                    "newFixture=NONE"
            );
        } else {
            ApiFootballMatch newMatch =
                    diff.newMatch();

            ApiFootballFixture fixture =
                    newMatch.fixture();

            System.out.println(
                    "newFixtureId="
                            + fixture.fixtureId()
                            + " newEvent="
                            + fixture.homeTeamName()
                            + " – "
                            + fixture.awayTeamName()
                            + " date="
                            + fixture.fixtureDate()
            );

            System.out.println(
                    "newEvidence="
                            + oneLine(
                            newMatch.evidence()
                    )
            );
        }

        if (
                diff.note() != null
                        && !diff.note()
                        .isBlank()
        ) {
            System.out.println(
                    "note="
                            + diff.note()
            );
        }
    }

    /*
     * =========================================================
     * PARSING / HELPERS
     * =========================================================
     */

    private static ResolvedSport parseSport(
            String raw
    ) {
        if (
                raw == null
                        || raw.isBlank()
        ) {
            return ResolvedSport.UNKNOWN;
        }

        try {
            return ResolvedSport.valueOf(
                    raw
            );
        } catch (
                IllegalArgumentException e
        ) {
            return ResolvedSport.UNKNOWN;
        }
    }

    private static Long parseLong(
            String raw
    ) {
        if (
                raw == null
                        || raw.isBlank()
        ) {
            return null;
        }

        try {
            return Long.parseLong(
                    raw.trim()
            );
        } catch (
                NumberFormatException e
        ) {
            return null;
        }
    }

    private static String value(
            Long value
    ) {
        return value == null
                ? "NULL"
                : value.toString();
    }

    private static String safe(
            String value
    ) {
        return value == null
                ? "NULL"
                : value;
    }

    private static String oneLine(
            String value
    ) {
        if (
                value == null
                        || value.isBlank()
        ) {
            return "";
        }

        String result =
                value.replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();

        return result.length() <= 400
                ? result
                : result.substring(
                0,
                397
        ) + "...";
    }

    /*
     * =========================================================
     * INTERNAL TYPES
     * =========================================================
     */

    private enum DiffType {
        CHANGED,
        NOW_NONE,
        INCOMPLETE_WINDOW
    }

    private record Row(
            long legId,
            long wpPostId,

            BetType betType,
            int betLegCount,

            Instant publishedAt,

            ResolvedSport sport,

            String postTitle,
            String tipTitle,
            String heading,
            String previousText,

            Long oldFixtureId,
            String oldEventName,
            String oldResolutionEvidence,

            boolean hadLocalResolution,

            String settlementStatus,
            String settlementSource
    ) {

        ResolvedSport revalidationSport() {
            if (
                    oldResolutionEvidence != null
                            && oldResolutionEvidence.contains(
                            "requiredGap=0.100"
                    )
            ) {
                return ResolvedSport.UNKNOWN;
            }

            if (
                    oldResolutionEvidence != null
                            && oldResolutionEvidence.contains(
                            "requiredGap=0.060"
                    )
            ) {
                return ResolvedSport.FOOTBALL;
            }

            /*
             * Najstarsze evidence nie zapisywało requiredGap.
             * Wtedy zachowujemy sport zapisany na legu.
             */
            return sport;
        }

        boolean isAutoSettled() {
            return "AUTO".equals(
                    settlementSource
            )
                    && (
                    "W".equals(
                            settlementStatus
                    )
                            || "L".equals(
                            settlementStatus
                    )
                            || "V".equals(
                            settlementStatus
                    )
            );
        }

        boolean isPending() {
            return "PENDING".equals(
                    settlementStatus
            )
                    && "NONE".equals(
                    settlementSource
            );
        }
    }

    private record Diff(
            DiffType type,
            Row row,
            ApiFootballMatch newMatch,
            String note
    ) {
    }
}