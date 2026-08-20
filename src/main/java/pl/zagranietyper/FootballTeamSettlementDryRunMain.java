package pl.zagranietyper;

import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.model.FootballScore;
import pl.zagranietyper.model.FootballTeamMarket;
import pl.zagranietyper.model.SettlementDecision;
import pl.zagranietyper.parser.FootballTeamMarketParser;
import pl.zagranietyper.repository.Database;
import pl.zagranietyper.repository.FootballSettlementRepository;
import pl.zagranietyper.service.FootballTeamMarketSettlementEngine;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FootballTeamSettlementDryRunMain {

    private static final int EXAMPLE_LIMIT =
            25;

    private FootballTeamSettlementDryRunMain() {
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

        FootballSettlementRepository repository =
                new FootballSettlementRepository(
                        database
                );

        FootballTeamMarketParser parser =
                new FootballTeamMarketParser();

        FootballTeamMarketSettlementEngine engine =
                new FootballTeamMarketSettlementEngine();

        System.out.println(
                "Zagranie Typer — Football TEAM Market DRY RUN"
        );

        System.out.println(
                "PENDING/NONE ONLY"
        );

        System.out.println(
                "NO DATABASE WRITES"
        );

        System.out.println();

        List<FootballSettlementRepository.Candidate> candidates =
                repository.findPendingApiFootballCandidates();

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

        int teamTotals =
                0;

        int teamToScore =
                0;

        Map<SettlementDecision, Integer> decisions =
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
            decisions.put(
                    decision,
                    0
            );

            examples.put(
                    decision,
                    new ArrayList<>()
            );
        }

        List<String> skippedExamples =
                new ArrayList<>();

        for (
                FootballSettlementRepository.Candidate candidate :
                candidates
        ) {
            String description =
                    describe(
                            candidate
                    );

            if (
                    !eligibleStatus(
                            candidate.statusShort()
                    )
            ) {
                skippedFixture++;

                addExample(
                        skippedExamples,
                        description
                );

                continue;
            }

            eligibleFixture++;

            if (
                    candidate.fulltimeHome() == null
                            || candidate.fulltimeAway() == null
            ) {
                missingFulltime++;

                continue;
            }

            Optional<FootballTeamMarket> market =
                    parser.parse(
                            candidate.tipTitle(),
                            candidate.homeTeam(),
                            candidate.awayTeam()
                    );

            if (
                    market.isEmpty()
            ) {
                unsupported++;

                continue;
            }

            parsed++;

            if (
                    market.get()
                            instanceof FootballTeamMarket.TeamTotalGoals
            ) {
                teamTotals++;
            }

            if (
                    market.get()
                            instanceof FootballTeamMarket.TeamToScore
            ) {
                teamToScore++;
            }

            SettlementDecision decision =
                    engine.settle(
                            market.get(),
                            new FootballScore(
                                    candidate.fulltimeHome(),
                                    candidate.fulltimeAway()
                            )
                    );

            decisions.compute(
                    decision,
                    (
                            ignored,
                            previous
                    ) ->
                            previous == null
                                    ? 1
                                    : previous + 1
            );

            addExample(
                    examples.get(
                            decision
                    ),
                    description
                            + System.lineSeparator()
                            + "  parsed="
                            + market.get()
            );
        }

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
                        + candidates.size()
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
                "missingFulltime="
                        + missingFulltime
        );

        System.out.println(
                "parsed="
                        + parsed
        );

        System.out.println(
                "unsupported="
                        + unsupported
        );

        System.out.println(
                "teamTotals="
                        + teamTotals
        );

        System.out.println(
                "teamToScore="
                        + teamToScore
        );

        System.out.println(
                "W="
                        + decisions.getOrDefault(
                        SettlementDecision.W,
                        0
                )
        );

        System.out.println(
                "L="
                        + decisions.getOrDefault(
                        SettlementDecision.L,
                        0
                )
        );

        System.out.println(
                "V="
                        + decisions.getOrDefault(
                        SettlementDecision.V,
                        0
                )
        );

        System.out.println();

        printExamples(
                "W",
                examples.get(
                        SettlementDecision.W
                )
        );

        printExamples(
                "L",
                examples.get(
                        SettlementDecision.L
                )
        );

        printExamples(
                "V",
                examples.get(
                        SettlementDecision.V
                )
        );

        printExamples(
                "SKIPPED FIXTURE",
                skippedExamples
        );
    }

    private static boolean eligibleStatus(
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

    private static void addExample(
            List<String> target,
            String value
    ) {
        if (
                target.size()
                        < EXAMPLE_LIMIT
        ) {
            target.add(
                    value
            );
        }
    }

    private static String describe(
            FootballSettlementRepository.Candidate candidate
    ) {
        String score =
                candidate.fulltimeHome() == null
                        || candidate.fulltimeAway() == null
                        ? "?-?"
                        : candidate.fulltimeHome()
                        + "-"
                        + candidate.fulltimeAway();

        return "leg="
                + candidate.legId()
                + " | bet="
                + candidate.betId()
                + " | wp="
                + candidate.wpPostId()
                + " | "
                + candidate.homeTeam()
                + " "
                + score
                + " "
                + candidate.awayTeam()
                + " | tip="
                + candidate.tipTitle();
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
}