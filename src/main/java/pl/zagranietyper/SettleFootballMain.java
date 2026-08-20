package pl.zagranietyper;

import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.model.SettlementDecision;
import pl.zagranietyper.parser.FootballMarketParser;
import pl.zagranietyper.repository.Database;
import pl.zagranietyper.repository.FootballSettlementRepository;
import pl.zagranietyper.service.FootballMarketSettlementEngine;
import pl.zagranietyper.service.FootballSettlementService;

import java.util.Arrays;
import java.util.List;

public final class SettleFootballMain {

    private SettleFootballMain() {
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

        AppConfig appConfig =
                AppConfig.fromEnvironment();

        Database database =
                new Database(
                        appConfig
                );

        FootballSettlementRepository repository =
                new FootballSettlementRepository(
                        database
                );

        FootballMarketParser parser =
                new FootballMarketParser();

        FootballMarketSettlementEngine engine =
                new FootballMarketSettlementEngine();

        FootballSettlementService service =
                new FootballSettlementService(
                        repository,
                        parser,
                        engine
                );

        System.out.println(
                "Zagranie Typer — Football Settlement"
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

        FootballSettlementService.Result result =
                service.run(
                        apply
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
                result.skippedExamples()
        );

        printExamples(
                "MISSING FULLTIME",
                result.missingExamples()
        );

        if (
                apply
        ) {
            printApplySummary(
                    result.applyResult()
            );
        } else {
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
                    "To apply these results run:"
            );

            System.out.println();

            System.out.println(
                    "java -cp target/zagranie-typer-0.1.0-SNAPSHOT.jar \\"
            );

            System.out.println(
                    "  pl.zagranietyper.SettleFootballMain --apply"
            );

            System.out.println();
        }
    }

    private static void printSummary(
            FootballSettlementService.Result result
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
                "candidates="
                        + result.candidates()
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
                        + result.wins()
        );

        System.out.println(
                "L="
                        + result.losses()
        );

        System.out.println(
                "V="
                        + result.voids()
        );

        System.out.println(
                "autoSettleable="
                        + result.autoSettleable()
        );

        System.out.println();
    }

    private static void printApplySummary(
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