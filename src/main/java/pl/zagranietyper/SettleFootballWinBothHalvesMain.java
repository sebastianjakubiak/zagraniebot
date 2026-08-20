package pl.zagranietyper;

import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.model.SettlementDecision;
import pl.zagranietyper.parser.FootballWinBothHalvesParser;
import pl.zagranietyper.repository.Database;
import pl.zagranietyper.repository.FootballSettlementRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SettleFootballWinBothHalvesMain {

    private static final int REJECTED_EXAMPLE_LIMIT =
            100;

    private static final int SKIPPED_EXAMPLE_LIMIT =
            20;

    private static final Set<String> ELIGIBLE_FIXTURE_STATUSES =
            Set.of(
                    "FT",
                    "AET",
                    "PEN"
            );

    private static final String SQL =
            "SELECT "
                    + "bl.id AS leg_id, "
                    + "b.id AS bet_id, "
                    + "b.wp_post_id, "
                    + "f.fixture_id, "
                    + "f.status_short, "
                    + "f.home_team_name, "
                    + "f.raw_json #>> '{score,halftime,home}' AS ht_home_goals, "
                    + "f.raw_json #>> '{score,halftime,away}' AS ht_away_goals, "
                    + "f.raw_json #>> '{score,fulltime,home}' AS ft_home_goals, "
                    + "f.raw_json #>> '{score,fulltime,away}' AS ft_away_goals, "
                    + "f.away_team_name, "
                    + "bl.tip_title "
                    + "FROM bet_legs bl "
                    + "JOIN bets b "
                    + "  ON b.id = bl.bet_id "
                    + " AND b.active = TRUE "
                    + "JOIN api_football_fixtures f "
                    + "  ON f.fixture_id = bl.resolved_external_event_id::bigint "
                    + "WHERE bl.active = TRUE "
                    + "  AND bl.resolved_provider = 'API_FOOTBALL' "
                    + "  AND bl.settlement_status = 'PENDING' "
                    + "  AND bl.settlement_source = 'NONE' "
                    + "ORDER BY bl.id";

    private SettleFootballWinBothHalvesMain() {
    }

    public static void main(
            String[] args
    ) throws Exception {
        boolean apply =
                parseApplyFlag(
                        args
                );

        AppConfig config =
                AppConfig.fromEnvironment();

        Database database =
                new Database(
                        config
                );

        FootballSettlementRepository repository =
                new FootballSettlementRepository(
                        database
                );

        FootballWinBothHalvesParser parser =
                new FootballWinBothHalvesParser();

        List<Candidate> candidates =
                loadCandidates(
                        database
                );

        Map<FootballWinBothHalvesParser.Status, Integer>
                parserStatuses =
                new EnumMap<>(
                        FootballWinBothHalvesParser.Status.class
                );

        for (
                FootballWinBothHalvesParser.Status status :
                FootballWinBothHalvesParser.Status.values()
        ) {
            parserStatuses.put(
                    status,
                    0
            );
        }

        List<Parsed> parsed =
                new ArrayList<>();

        List<Rejected> rejected =
                new ArrayList<>();

        List<Rejected> subjectIssues =
                new ArrayList<>();

        List<Candidate> skippedFixtures =
                new ArrayList<>();

        List<FootballSettlementRepository.SettlementUpdate>
                updates =
                new ArrayList<>();

        int eligibleFixture =
                0;

        int missingHalftime =
                0;

        int missingFulltime =
                0;

        int yesMarkets =
                0;

        int noMarkets =
                0;

        int wins =
                0;

        int losses =
                0;

        for (
                Candidate candidate :
                candidates
        ) {
            if (
                    !isEligibleFixture(
                            candidate
                    )
            ) {
                skippedFixtures.add(
                        candidate
                );

                continue;
            }

            eligibleFixture++;

            FootballWinBothHalvesParser.ParseResult result =
                    parser.parse(
                            candidate.tipTitle(),
                            candidate.homeTeam(),
                            candidate.awayTeam()
                    );

            parserStatuses.compute(
                    result.status(),
                    (
                            key,
                            value
                    ) -> value == null
                            ? 1
                            : value + 1
            );

            if (
                    !result.parsed()
            ) {
                if (
                        parser.looksLikeWinBothHalves(
                                candidate.tipTitle()
                        )
                ) {
                    Rejected row =
                            new Rejected(
                                    candidate,
                                    result
                            );

                    rejected.add(
                            row
                    );

                    if (
                            isSubjectIssue(
                                    result.status()
                            )
                    ) {
                        subjectIssues.add(
                                row
                        );
                    }
                }

                continue;
            }

            if (
                    candidate.htHomeGoals() == null
                            || candidate.htAwayGoals() == null
            ) {
                missingHalftime++;

                continue;
            }

            if (
                    candidate.ftHomeGoals() == null
                            || candidate.ftAwayGoals() == null
            ) {
                missingFulltime++;

                continue;
            }

            if (
                    result.expectedYes()
            ) {
                yesMarkets++;
            } else {
                noMarkets++;
            }

            SettlementDecision decision =
                    settle(
                            result,
                            candidate
                    );

            if (
                    decision == SettlementDecision.W
            ) {
                wins++;
            } else {
                losses++;
            }

            parsed.add(
                    new Parsed(
                            candidate,
                            result,
                            decision
                    )
            );

            updates.add(
                    new FootballSettlementRepository.SettlementUpdate(
                            candidate.legId(),
                            candidate.betId(),
                            decision
                    )
            );
        }

        printHeader(
                apply
        );

        printSummary(
                candidates.size(),
                eligibleFixture,
                skippedFixtures.size(),
                parsed.size(),
                missingHalftime,
                missingFulltime,
                yesMarkets,
                noMarkets,
                wins,
                losses,
                updates.size(),
                parserStatuses
        );

        printParsed(
                parsed
        );

        printSubjectIssues(
                subjectIssues
        );

        printRejected(
                rejected
        );

        printSkippedFixtures(
                skippedFixtures
        );

        if (
                !apply
        ) {
            printDryRunFooter();

            return;
        }

        /*
         * =====================================================
         * SAFETY GATES
         * =====================================================
         */

        if (
                !subjectIssues.isEmpty()
        ) {
            throw new IllegalStateException(
                    "REFUSING APPLY: subjectIssues="
                            + subjectIssues.size()
            );
        }

        if (
                missingHalftime != 0
        ) {
            throw new IllegalStateException(
                    "REFUSING APPLY: missingHalftime="
                            + missingHalftime
            );
        }

        if (
                missingFulltime != 0
        ) {
            throw new IllegalStateException(
                    "REFUSING APPLY: missingFulltime="
                            + missingFulltime
            );
        }

        if (
                parsed.size()
                        != updates.size()
        ) {
            throw new IllegalStateException(
                    "REFUSING APPLY: parsed="
                            + parsed.size()
                            + ", updates="
                            + updates.size()
            );
        }

        FootballSettlementRepository.ApplyResult applyResult =
                repository.apply(
                        List.copyOf(
                                updates
                        )
                );

        printApplyResult(
                applyResult
        );
    }

    /*
     * =========================================================
     * ARGUMENTS
     * =========================================================
     */

    private static boolean parseApplyFlag(
            String[] args
    ) {
        if (
                args == null
                        || args.length == 0
        ) {
            return false;
        }

        if (
                args.length == 1
                        && "--apply".equals(
                        args[0]
                )
        ) {
            return true;
        }

        throw new IllegalArgumentException(
                "Usage: SettleFootballWinBothHalvesMain [--apply]"
        );
    }

    /*
     * =========================================================
     * SETTLEMENT
     * =========================================================
     */

    private static SettlementDecision settle(
            FootballWinBothHalvesParser.ParseResult result,
            Candidate candidate
    ) {
        int h2Home =
                candidate.ftHomeGoals()
                        - candidate.htHomeGoals();

        int h2Away =
                candidate.ftAwayGoals()
                        - candidate.htAwayGoals();

        boolean wonFirstHalf =
                switch (
                        result.selection()
                        ) {
                    case HOME ->
                            candidate.htHomeGoals()
                                    > candidate.htAwayGoals();

                    case AWAY ->
                            candidate.htAwayGoals()
                                    > candidate.htHomeGoals();
                };

        boolean wonSecondHalf =
                switch (
                        result.selection()
                        ) {
                    case HOME ->
                            h2Home > h2Away;

                    case AWAY ->
                            h2Away > h2Home;
                };

        boolean condition =
                wonFirstHalf
                        && wonSecondHalf;

        boolean won =
                result.expectedYes()
                        ? condition
                        : !condition;

        return won
                ? SettlementDecision.W
                : SettlementDecision.L;
    }

    /*
     * =========================================================
     * FIXTURE ELIGIBILITY
     * =========================================================
     */

    private static boolean isEligibleFixture(
            Candidate candidate
    ) {
        return candidate.statusShort() != null
                && ELIGIBLE_FIXTURE_STATUSES.contains(
                candidate.statusShort()
        );
    }

    /*
     * =========================================================
     * SUBJECT SAFETY
     * =========================================================
     */

    private static boolean isSubjectIssue(
            FootballWinBothHalvesParser.Status status
    ) {
        return status
                == FootballWinBothHalvesParser.Status.SUBJECT_NOT_FOUND
                || status
                == FootballWinBothHalvesParser.Status.SUBJECT_MISMATCH
                || status
                == FootballWinBothHalvesParser.Status.SUBJECT_AMBIGUOUS;
    }

    /*
     * =========================================================
     * DATABASE READ
     * =========================================================
     */

    private static List<Candidate> loadCandidates(
            Database database
    ) throws SQLException {
        List<Candidate> result =
                new ArrayList<>();

        try (
                Connection connection =
                        database.openConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                SQL
                        );

                ResultSet rs =
                        statement.executeQuery()
        ) {
            while (
                    rs.next()
            ) {
                result.add(
                        new Candidate(
                                rs.getLong("leg_id"),
                                rs.getLong("bet_id"),
                                rs.getLong("wp_post_id"),
                                rs.getLong("fixture_id"),
                                rs.getString("status_short"),
                                rs.getString("home_team_name"),
                                nullableInt(
                                        rs,
                                        "ht_home_goals"
                                ),
                                nullableInt(
                                        rs,
                                        "ht_away_goals"
                                ),
                                nullableInt(
                                        rs,
                                        "ft_home_goals"
                                ),
                                nullableInt(
                                        rs,
                                        "ft_away_goals"
                                ),
                                rs.getString("away_team_name"),
                                rs.getString("tip_title")
                        )
                );
            }
        }

        return result;
    }

    private static Integer nullableInt(
            ResultSet rs,
            String column
    ) throws SQLException {
        String value =
                rs.getString(
                        column
                );

        if (
                value == null
                        || value.isBlank()
        ) {
            return null;
        }

        return Integer.valueOf(
                value
        );
    }

    /*
     * =========================================================
     * OUTPUT
     * =========================================================
     */

    private static void printHeader(
            boolean apply
    ) {
        System.out.println(
                "Zagranie Typer — Football WIN BOTH HALVES Settlement"
        );

        System.out.println(
                "MODE="
                        + (
                        apply
                                ? "APPLY"
                                : "DRY_RUN"
                )
        );

        if (
                !apply
        ) {
            System.out.println(
                    "NO DATABASE WRITES"
            );
        }

        System.out.println();
    }

    private static void printSummary(
            int pendingApiCandidates,
            int eligibleFixture,
            int skippedFixture,
            int parsed,
            int missingHalftime,
            int missingFulltime,
            int yesMarkets,
            int noMarkets,
            int wins,
            int losses,
            int autoSettleable,
            Map<FootballWinBothHalvesParser.Status, Integer>
                    parserStatuses
    ) {
        System.out.println(
                "========================================"
        );

        System.out.println(
                "SUMMARY"
        );

        System.out.println(
                "========================================"
        );

        System.out.println(
                "pendingApiCandidates="
                        + pendingApiCandidates
        );

        System.out.println(
                "eligibleFixture="
                        + eligibleFixture
        );

        System.out.println(
                "skippedFixture="
                        + skippedFixture
        );

        System.out.println(
                "parsed="
                        + parsed
        );

        System.out.println(
                "missingHalftime="
                        + missingHalftime
        );

        System.out.println(
                "missingFulltime="
                        + missingFulltime
        );

        System.out.println(
                "YES="
                        + yesMarkets
        );

        System.out.println(
                "NO="
                        + noMarkets
        );

        System.out.println(
                "W="
                        + wins
        );

        System.out.println(
                "L="
                        + losses
        );

        System.out.println(
                "autoSettleable="
                        + autoSettleable
        );

        System.out.println();

        System.out.println(
                "PARSER STATUS"
        );

        for (
                FootballWinBothHalvesParser.Status status :
                FootballWinBothHalvesParser.Status.values()
        ) {
            System.out.println(
                    status.name()
                            + "="
                            + parserStatuses.get(
                            status
                    )
            );
        }
    }

    private static void printParsed(
            List<Parsed> parsed
    ) {
        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "ALL PARSED RECORDS ("
                        + parsed.size()
                        + ")"
        );

        System.out.println(
                "========================================"
        );

        int ordinal =
                0;

        for (
                Parsed row :
                parsed
        ) {
            ordinal++;

            Candidate c =
                    row.candidate();

            FootballWinBothHalvesParser.ParseResult p =
                    row.parseResult();

            int h2Home =
                    c.ftHomeGoals()
                            - c.htHomeGoals();

            int h2Away =
                    c.ftAwayGoals()
                            - c.htAwayGoals();

            System.out.println(
                    "["
                            + ordinal
                            + "] leg="
                            + c.legId()
                            + " | bet="
                            + c.betId()
                            + " | wp="
                            + c.wpPostId()
                            + " | fixture="
                            + c.fixtureId()
                            + " | "
                            + c.homeTeam()
                            + " HT "
                            + c.htHomeGoals()
                            + "-"
                            + c.htAwayGoals()
                            + " / H2 "
                            + h2Home
                            + "-"
                            + h2Away
                            + " / FT "
                            + c.ftHomeGoals()
                            + "-"
                            + c.ftAwayGoals()
                            + " "
                            + c.awayTeam()
                            + " | tip="
                            + c.tipTitle()
            );

            System.out.println(
                    "    subject="
                            + p.subject()
                            + " | selection="
                            + p.selection()
                            + " | expected="
                            + (
                            p.expectedYes()
                                    ? "YES"
                                    : "NO"
                    )
                            + " | decision="
                            + row.decision()
            );
        }
    }

    private static void printSubjectIssues(
            List<Rejected> rejected
    ) {
        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "SUBJECT ISSUES ("
                        + rejected.size()
                        + ")"
        );

        System.out.println(
                "========================================"
        );

        if (
                rejected.isEmpty()
        ) {
            System.out.println(
                    "NONE"
            );

            return;
        }

        int ordinal =
                0;

        for (
                Rejected row :
                rejected
        ) {
            ordinal++;

            printRejectedRow(
                    ordinal,
                    row
            );
        }
    }

    private static void printRejected(
            List<Rejected> rejected
    ) {
        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "WIN-BOTH-HALVES-LIKE REJECTED="
                        + rejected.size()
        );

        System.out.println(
                "EXAMPLES (max "
                        + REJECTED_EXAMPLE_LIMIT
                        + ")"
        );

        System.out.println(
                "========================================"
        );

        int limit =
                Math.min(
                        REJECTED_EXAMPLE_LIMIT,
                        rejected.size()
                );

        for (
                int i = 0;
                i < limit;
                i++
        ) {
            printRejectedRow(
                    i + 1,
                    rejected.get(
                            i
                    )
            );
        }
    }

    private static void printRejectedRow(
            int ordinal,
            Rejected row
    ) {
        Candidate c =
                row.candidate();

        FootballWinBothHalvesParser.ParseResult p =
                row.parseResult();

        System.out.println(
                "["
                        + ordinal
                        + "] leg="
                        + c.legId()
                        + " | bet="
                        + c.betId()
                        + " | wp="
                        + c.wpPostId()
                        + " | fixture="
                        + c.fixtureId()
                        + " | "
                        + c.homeTeam()
                        + " – "
                        + c.awayTeam()
                        + " | tip="
                        + c.tipTitle()
        );

        System.out.println(
                "    status="
                        + p.status()
                        + " | subject="
                        + value(
                        p.subject()
                )
                        + " | selection="
                        + value(
                        p.selection()
                )
                        + " | expected="
                        + (
                        p.expectedYes()
                                ? "YES"
                                : "NO"
                )
        );
    }

    private static void printSkippedFixtures(
            List<Candidate> skipped
    ) {
        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "SKIPPED FIXTURES ("
                        + skipped.size()
                        + ")"
        );

        System.out.println(
                "========================================"
        );

        int limit =
                Math.min(
                        SKIPPED_EXAMPLE_LIMIT,
                        skipped.size()
                );

        for (
                int i = 0;
                i < limit;
                i++
        ) {
            Candidate c =
                    skipped.get(
                            i
                    );

            System.out.println(
                    "["
                            + (i + 1)
                            + "] leg="
                            + c.legId()
                            + " | fixture="
                            + c.fixtureId()
                            + " | status="
                            + c.statusShort()
                            + " | "
                            + c.homeTeam()
                            + " – "
                            + c.awayTeam()
                            + " | tip="
                            + c.tipTitle()
            );
        }
    }

    private static void printDryRunFooter() {
        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "DRY RUN ONLY — DATABASE NOT MODIFIED"
        );

        System.out.println(
                "========================================"
        );

        System.out.println();

        System.out.println(
                "To apply exactly the currently detected records:"
        );

        System.out.println();

        System.out.println(
                "java -cp target/zagranie-typer-0.1.0-SNAPSHOT.jar \\"
        );

        System.out.println(
                "  pl.zagranietyper.SettleFootballWinBothHalvesMain --apply"
        );
    }

    private static void printApplyResult(
            FootballSettlementRepository.ApplyResult result
    ) {
        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "DATABASE APPLY"
        );

        System.out.println(
                "========================================"
        );

        System.out.println(
                "updatedLegs="
                        + result.updatedLegs()
        );

        System.out.println(
                "skippedLegs="
                        + result.skippedLegs()
        );

        System.out.println(
                "legW="
                        + result.winLegs()
        );

        System.out.println(
                "legL="
                        + result.lossLegs()
        );

        System.out.println(
                "legV="
                        + result.voidLegs()
        );

        System.out.println();

        System.out.println(
                "updatedBets="
                        + result.updatedBets()
        );

        System.out.println(
                "betW="
                        + result.winBets()
        );

        System.out.println(
                "betL="
                        + result.lossBets()
        );

        System.out.println(
                "betV="
                        + result.voidBets()
        );

        System.out.println(
                "affectedStillPending="
                        + result.pendingBets()
        );

        System.out.println(
                "multiUnverifiedBets="
                        + result.multiUnverifiedBets()
        );

        System.out.println();

        System.out.println(
                "COMMIT OK"
        );
    }

    private static String value(
            Object value
    ) {
        return value == null
                ? "?"
                : value.toString();
    }

    /*
     * =========================================================
     * TYPES
     * =========================================================
     */

    private record Candidate(
            long legId,
            long betId,
            long wpPostId,
            long fixtureId,
            String statusShort,
            String homeTeam,
            Integer htHomeGoals,
            Integer htAwayGoals,
            Integer ftHomeGoals,
            Integer ftAwayGoals,
            String awayTeam,
            String tipTitle
    ) {
    }

    private record Parsed(
            Candidate candidate,
            FootballWinBothHalvesParser.ParseResult parseResult,
            SettlementDecision decision
    ) {
    }

    private record Rejected(
            Candidate candidate,
            FootballWinBothHalvesParser.ParseResult parseResult
    ) {
    }
}