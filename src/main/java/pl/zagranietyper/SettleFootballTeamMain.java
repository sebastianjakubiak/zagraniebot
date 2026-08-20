package pl.zagranietyper;

import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.model.FootballScore;
import pl.zagranietyper.model.FootballTeamMarket;
import pl.zagranietyper.model.SettlementDecision;
import pl.zagranietyper.repository.Database;
import pl.zagranietyper.repository.FootballSettlementRepository;
import pl.zagranietyper.parser.FootballTeamMarketParser;
import pl.zagranietyper.service.FootballTeamMarketSettlementEngine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class SettleFootballTeamMain {

    private SettleFootballTeamMain() {
    }

    public static void main(
            String[] args
    ) {
        boolean apply =
                Arrays.asList(
                                args
                        )
                        .contains(
                                "--apply"
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

        FootballTeamMarketParser parser =
                new FootballTeamMarketParser();

        FootballTeamMarketSettlementEngine engine =
                new FootballTeamMarketSettlementEngine();

        System.out.println(
                "Zagranie Typer — Football TEAM Settlement"
        );

        System.out.println(
                apply
                        ? "MODE=APPLY"
                        : "MODE=DRY_RUN"
        );

        if (
                !apply
        ) {
            System.out.println(
                    "NO DATABASE WRITES"
            );
        }

        System.out.println();

        List<FootballSettlementRepository.Candidate> candidates =
                repository.findPendingApiFootballCandidates();

        List<FootballSettlementRepository.SettlementUpdate> updates =
                new ArrayList<>();

        List<String> parsedRecords =
                new ArrayList<>();

        List<String> skippedRecords =
                new ArrayList<>();

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

        int wins =
                0;

        int losses =
                0;

        int voids =
                0;

        for (
                FootballSettlementRepository.Candidate candidate :
                candidates
        ) {
            if (
                    !eligibleStatus(
                            candidate.statusShort()
                    )
            ) {
                skippedFixture++;

                skippedRecords.add(
                        describeBase(
                                candidate
                        )
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

            FootballScore score =
                    new FootballScore(
                            candidate.fulltimeHome(),
                            candidate.fulltimeAway()
                    );

            SettlementDecision decision =
                    engine.settle(
                            market.get(),
                            score
                    );

            if (
                    decision == SettlementDecision.UNSUPPORTED
            ) {
                unsupported++;

                continue;
            }

            switch (
                    decision
            ) {
                case W ->
                        wins++;

                case L ->
                        losses++;

                case V ->
                        voids++;

                case UNSUPPORTED -> {
                    // handled above
                }
            }

            updates.add(
                    new FootballSettlementRepository.SettlementUpdate(
                            candidate.legId(),
                            candidate.betId(),
                            decision
                    )
            );

            parsedRecords.add(
                    describeParsed(
                            candidate,
                            market.get(),
                            decision
                    )
            );
        }

        printSummary(
                candidates.size(),
                eligibleFixture,
                skippedFixture,
                missingFulltime,
                parsed,
                unsupported,
                teamTotals,
                teamToScore,
                wins,
                losses,
                voids,
                updates.size()
        );

        printParsedRecords(
                parsedRecords
        );

        printSkippedRecords(
                skippedRecords
        );

        if (
                !apply
        ) {
            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "DRY RUN ONLY"
            );

            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "No database writes."
            );

            System.out.println(
                    "To apply exactly the currently detected records:"
            );

            System.out.println();

            System.out.println(
                    "java -cp target/zagranie-typer-0.1.0-SNAPSHOT.jar \\"
            );

            System.out.println(
                    "  pl.zagranietyper.SettleFootballTeamMain --apply"
            );

            System.out.println();

            return;
        }

        FootballSettlementRepository.ApplyResult applyResult =
                repository.apply(
                        updates
                );

        printApplyResult(
                applyResult
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

    private static void printSummary(
            int candidates,
            int eligibleFixture,
            int skippedFixture,
            int missingFulltime,
            int parsed,
            int unsupported,
            int teamTotals,
            int teamToScore,
            int wins,
            int losses,
            int voids,
            int autoSettleable
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
                        + candidates
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
                        + wins
        );

        System.out.println(
                "L="
                        + losses
        );

        System.out.println(
                "V="
                        + voids
        );

        System.out.println(
                "autoSettleable="
                        + autoSettleable
        );

        System.out.println();
    }

    private static void printParsedRecords(
            List<String> records
    ) {
        System.out.println(
                "========================================"
        );

        System.out.println(
                "ALL PARSED RECORDS ("
                        + records.size()
                        + ")"
        );

        System.out.println(
                "========================================"
        );

        int ordinal =
                1;

        for (
                String record :
                records
        ) {
            System.out.println(
                    "["
                            + ordinal
                            + "] "
                            + record
            );

            ordinal++;
        }

        System.out.println();
    }

    private static void printSkippedRecords(
            List<String> records
    ) {
        if (
                records.isEmpty()
        ) {
            return;
        }

        System.out.println(
                "========================================"
        );

        System.out.println(
                "SKIPPED FIXTURES ("
                        + records.size()
                        + ")"
        );

        System.out.println(
                "========================================"
        );

        int ordinal =
                1;

        for (
                String record :
                records
        ) {
            System.out.println(
                    "["
                            + ordinal
                            + "] "
                            + record
            );

            ordinal++;
        }

        System.out.println();
    }

    private static String describeParsed(
            FootballSettlementRepository.Candidate candidate,
            FootballTeamMarket market,
            SettlementDecision decision
    ) {
        return describeBase(
                candidate
        )
                + System.lineSeparator()
                + "    market="
                + market
                + System.lineSeparator()
                + "    decision="
                + decision;
    }

    private static String describeBase(
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
                + " | fixture="
                + candidate.fixtureId()
                + " | status="
                + candidate.statusShort()
                + " | "
                + candidate.homeTeam()
                + " "
                + score
                + " "
                + candidate.awayTeam()
                + " | tip="
                + candidate.tipTitle();
    }

    private static void printApplyResult(
            FootballSettlementRepository.ApplyResult result
    ) {
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
                "multiUnverifiedNotAggregated="
                        + result.multiUnverifiedBets()
        );

        System.out.println();

        System.out.println(
                "COMMIT OK"
        );

        System.out.println();
    }
}