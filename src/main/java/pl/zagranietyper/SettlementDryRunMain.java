package pl.zagranietyper;

import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.model.FootballMarket;
import pl.zagranietyper.model.FootballScore;
import pl.zagranietyper.model.SettlementDecision;
import pl.zagranietyper.parser.FootballMarketParser;
import pl.zagranietyper.repository.Database;
import pl.zagranietyper.service.FootballMarketSettlementEngine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SettlementDryRunMain {

    private static final int EXAMPLE_LIMIT =
            20;

    private SettlementDryRunMain() {
    }

    public static void main(
            String[] args
    ) {
        AppConfig appConfig =
                AppConfig.fromEnvironment();

        Database database =
                new Database(
                        appConfig
                );

        FootballMarketParser parser =
                new FootballMarketParser();

        FootballMarketSettlementEngine engine =
                new FootballMarketSettlementEngine();

        System.out.println(
                "Zagranie Typer — Football Settlement DRY RUN"
        );

        System.out.println(
                "NO DATABASE WRITES"
        );

        System.out.println();

        Result result =
                run(
                        database,
                        parser,
                        engine
                );

        printSummary(
                result
        );

        printExamples(
                "W",
                result.examples()
                        .get(
                                SettlementDecision.W
                        )
        );

        printExamples(
                "L",
                result.examples()
                        .get(
                                SettlementDecision.L
                        )
        );

        printExamples(
                "V",
                result.examples()
                        .get(
                                SettlementDecision.V
                        )
        );

        printExamples(
                "UNSUPPORTED",
                result.examples()
                        .get(
                                SettlementDecision.UNSUPPORTED
                        )
        );

        printExamples(
                "SKIPPED FIXTURE",
                result.skippedFixtures()
        );

        printExamples(
                "MISSING FULLTIME",
                result.missingFulltimeList()
        );
    }

    private static Result run(
            Database database,
            FootballMarketParser parser,
            FootballMarketSettlementEngine engine
    ) {
        String sql =
                """
                SELECT
                    bl.id AS leg_id,
                    b.wp_post_id,
                    bl.tip_title,

                    f.fixture_id,
                    f.fixture_date,
                    f.status_short,

                    f.home_team_name,
                    f.away_team_name,

                    NULLIF(
                        f.raw_json #>> '{score,fulltime,home}',
                        ''
                    )::integer AS ft_home,

                    NULLIF(
                        f.raw_json #>> '{score,fulltime,away}',
                        ''
                    )::integer AS ft_away

                FROM bet_legs bl

                JOIN bets b
                  ON b.id = bl.bet_id
                 AND b.active = TRUE

                JOIN api_football_fixtures f
                  ON f.fixture_id =
                     bl.resolved_external_event_id::bigint

                WHERE bl.active = TRUE
                  AND bl.resolved_provider = 'API_FOOTBALL'

                ORDER BY bl.id
                """;

        int total =
                0;

        int eligibleFixture =
                0;

        int skippedFixture =
                0;

        int missingFulltime =
                0;

        int parsed =
                0;

        int unsupported =
                0;

        Map<SettlementDecision, Integer> counts =
                new EnumMap<>(
                        SettlementDecision.class
                );

        Map<SettlementDecision, List<String>> examples =
                new EnumMap<>(
                        SettlementDecision.class
                );

        for (
                SettlementDecision decision :
                SettlementDecision.values()
        ) {
            counts.put(
                    decision,
                    0
            );

            examples.put(
                    decision,
                    new ArrayList<>()
            );
        }

        List<String> skippedFixtures =
                new ArrayList<>();

        List<String> missingFulltimeExamples =
                new ArrayList<>();

        try (
                Connection connection =
                        database.openConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        );

                ResultSet rs =
                        statement.executeQuery()
        ) {
            while (
                    rs.next()
            ) {
                total++;

                long legId =
                        rs.getLong(
                                "leg_id"
                        );

                long wpPostId =
                        rs.getLong(
                                "wp_post_id"
                        );

                long fixtureId =
                        rs.getLong(
                                "fixture_id"
                        );

                String tipTitle =
                        rs.getString(
                                "tip_title"
                        );

                String status =
                        rs.getString(
                                "status_short"
                        );

                String homeTeam =
                        rs.getString(
                                "home_team_name"
                        );

                String awayTeam =
                        rs.getString(
                                "away_team_name"
                        );

                Integer home =
                        integer(
                                rs,
                                "ft_home"
                        );

                Integer away =
                        integer(
                                rs,
                                "ft_away"
                        );

                String base =
                        baseDescription(
                                legId,
                                wpPostId,
                                fixtureId,
                                status,
                                homeTeam,
                                awayTeam,
                                home,
                                away,
                                tipTitle
                        );

                if (
                        !isSettlementEligibleStatus(
                                status
                        )
                ) {
                    skippedFixture++;

                    addExample(
                            skippedFixtures,
                            base
                    );

                    continue;
                }

                eligibleFixture++;

                if (
                        home == null
                                || away == null
                ) {
                    missingFulltime++;

                    addExample(
                            missingFulltimeExamples,
                            base
                    );

                    continue;
                }

                Optional<FootballMarket> market =
                        parser.parse(
                                tipTitle,
                                homeTeam,
                                awayTeam
                        );

                if (
                        market.isEmpty()
                ) {
                    unsupported++;

                    increment(
                            counts,
                            SettlementDecision.UNSUPPORTED
                    );

                    addExample(
                            examples.get(
                                    SettlementDecision.UNSUPPORTED
                            ),
                            base
                    );

                    continue;
                }

                parsed++;

                FootballScore score =
                        new FootballScore(
                                home,
                                away
                        );

                SettlementDecision decision =
                        engine.settle(
                                market.get(),
                                score
                        );

                increment(
                        counts,
                        decision
                );

                if (
                        decision == SettlementDecision.UNSUPPORTED
                ) {
                    unsupported++;
                }

                addExample(
                        examples.get(
                                decision
                        ),
                        base
                                + System.lineSeparator()
                                + "  parsed="
                                + market.get()
                );
            }

        } catch (
                SQLException exception
        ) {
            throw new IllegalStateException(
                    "Nie udało się wykonać settlement dry-run",
                    exception
            );
        }

        return new Result(
                total,
                eligibleFixture,
                skippedFixture,
                missingFulltime,
                parsed,
                unsupported,
                Map.copyOf(
                        counts
                ),
                immutableExamples(
                        examples
                ),
                List.copyOf(
                        skippedFixtures
                ),
                List.copyOf(
                        missingFulltimeExamples
                )
        );
    }

    private static boolean isSettlementEligibleStatus(
            String status
    ) {
        if (
                status == null
        ) {
            return false;
        }

        return switch (
                status
                ) {
            case "FT",
                 "AET",
                 "PEN" ->
                    true;

            default ->
                    false;
        };
    }

    private static Integer integer(
            ResultSet rs,
            String column
    ) throws SQLException {
        int value =
                rs.getInt(
                        column
                );

        if (
                rs.wasNull()
        ) {
            return null;
        }

        return value;
    }

    private static void increment(
            Map<SettlementDecision, Integer> counts,
            SettlementDecision decision
    ) {
        counts.compute(
                decision,
                (
                        ignored,
                        value
                ) ->
                        value == null
                                ? 1
                                : value + 1
        );
    }

    private static void addExample(
            List<String> examples,
            String example
    ) {
        if (
                examples.size()
                        >= EXAMPLE_LIMIT
        ) {
            return;
        }

        examples.add(
                example
        );
    }

    private static String baseDescription(
            long legId,
            long wpPostId,
            long fixtureId,
            String status,
            String homeTeam,
            String awayTeam,
            Integer home,
            Integer away,
            String tipTitle
    ) {
        String score =
                home == null
                        || away == null
                        ? "?-?"
                        : home + "-" + away;

        return "leg="
                + legId
                + " | wp="
                + wpPostId
                + " | fixture="
                + fixtureId
                + " | status="
                + status
                + " | "
                + homeTeam
                + " "
                + score
                + " "
                + awayTeam
                + " | tip="
                + tipTitle;
    }

    private static Map<SettlementDecision, List<String>>
    immutableExamples(
            Map<SettlementDecision, List<String>> source
    ) {
        Map<SettlementDecision, List<String>> result =
                new EnumMap<>(
                        SettlementDecision.class
                );

        for (
                Map.Entry<SettlementDecision, List<String>> entry :
                source.entrySet()
        ) {
            result.put(
                    entry.getKey(),
                    List.copyOf(
                            entry.getValue()
                    )
            );
        }

        return Map.copyOf(
                result
        );
    }

    private static void printSummary(
            Result result
    ) {
        int win =
                result.counts()
                        .getOrDefault(
                                SettlementDecision.W,
                                0
                        );

        int loss =
                result.counts()
                        .getOrDefault(
                                SettlementDecision.L,
                                0
                        );

        int voidCount =
                result.counts()
                        .getOrDefault(
                                SettlementDecision.V,
                                0
                        );

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
                "apiLinked="
                        + result.total()
        );

        System.out.println(
                "eligibleFixture="
                        + result.eligibleFixture()
        );

        System.out.println(
                "skippedFixture="
                        + result.skippedFixture()
        );

        System.out.println(
                "missingFulltime="
                        + result.missingFulltime()
        );

        System.out.println(
                "parsed="
                        + result.parsed()
        );

        System.out.println(
                "unsupported="
                        + result.unsupported()
        );

        System.out.println(
                "W="
                        + win
        );

        System.out.println(
                "L="
                        + loss
        );

        System.out.println(
                "V="
                        + voidCount
        );

        System.out.println(
                "autoSettleable="
                        + (
                        win
                                + loss
                                + voidCount
                )
        );

        System.out.println();
    }

    private static void printExamples(
            String title,
            List<String> examples
    ) {
        if (
                examples == null
                        || examples.isEmpty()
        ) {
            return;
        }

        System.out.println(
                "========================================"
        );

        System.out.println(
                title
                        + " EXAMPLES ("
                        + examples.size()
                        + ")"
        );

        System.out.println(
                "========================================"
        );

        int ordinal =
                1;

        for (
                String example :
                examples
        ) {
            System.out.println(
                    "["
                            + ordinal
                            + "] "
                            + example
            );

            ordinal++;
        }

        System.out.println();
    }

    private record Result(
            int total,
            int eligibleFixture,
            int skippedFixture,
            int missingFulltime,
            int parsed,
            int unsupported,
            Map<SettlementDecision, Integer> counts,
            Map<SettlementDecision, List<String>> examples,
            List<String> skippedFixtures,
            List<String> missingFulltimeList
    ) {
    }
}