package pl.zagranietyper;

import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.model.SettlementDecision;
import pl.zagranietyper.parser.FootballDrawParser;
import pl.zagranietyper.repository.Database;
import pl.zagranietyper.repository.FootballSettlementRepository;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SettleFootballDrawMain {

    private static final int REJECTED_EXAMPLE_LIMIT =
            120;

    private static final int SKIPPED_EXAMPLE_LIMIT =
            20;

    private static final Set<String> ELIGIBLE_FIXTURE_STATUSES =
            Set.of(
                    "FT",
                    "AET",
                    "PEN"
            );

    private SettleFootballDrawMain() {
    }

    public static void main(
            String[] args
    ) {
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

        FootballDrawParser parser =
                new FootballDrawParser();

        List<FootballSettlementRepository.Candidate>
                candidates =
                repository.findPendingApiFootballCandidates();

        Map<FootballDrawParser.Status, Integer>
                parserStatuses =
                new EnumMap<>(
                        FootballDrawParser.Status.class
                );

        for (
                FootballDrawParser.Status status :
                FootballDrawParser.Status.values()
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

        List<FootballSettlementRepository.Candidate>
                skippedFixtures =
                new ArrayList<>();

        List<FootballSettlementRepository.SettlementUpdate>
                updates =
                new ArrayList<>();

        int eligibleFixture =
                0;

        int missingFulltime =
                0;

        int wins =
                0;

        int losses =
                0;

        for (
                FootballSettlementRepository.Candidate candidate :
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

            FootballDrawParser.ParseResult result =
                    parser.parse(
                            candidate.tipTitle()
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
                        parser.looksLikeDraw(
                                candidate.tipTitle()
                        )
                ) {
                    rejected.add(
                            new Rejected(
                                    candidate,
                                    result
                            )
                    );
                }

                continue;
            }

            if (
                    candidate.fulltimeHome() == null
                            || candidate.fulltimeAway() == null
            ) {
                missingFulltime++;

                continue;
            }

            SettlementDecision decision =
                    settle(
                            candidate.fulltimeHome(),
                            candidate.fulltimeAway()
                    );

            if (
                    decision
                            == SettlementDecision.W
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
                missingFulltime,
                wins,
                losses,
                updates.size(),
                parserStatuses
        );

        printParsed(
                parsed
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

        /*
         * Jeżeli pojawił się jakiś nowy draw-like pattern,
         * którego nie sklasyfikowaliśmy jako:
         *
         * - DNB
         * - HALF
         * - DOUBLE CHANCE
         *
         * nie robimy apply bez ponownego audytu.
         */
        int unsupportedComposite =
                parserStatuses.get(
                        FootballDrawParser.Status.UNSUPPORTED_COMPOSITE
                );

        if (
                unsupportedComposite != 0
        ) {
            throw new IllegalStateException(
                    "REFUSING APPLY: unsupportedComposite="
                            + unsupportedComposite
            );
        }

        /*
         * Każdy accepted historyczny draw musi mieć
         * kompletny wynik fulltime.
         */
        if (
                missingFulltime != 0
        ) {
            throw new IllegalStateException(
                    "REFUSING APPLY: missingFulltime="
                            + missingFulltime
            );
        }

        /*
         * Jeden parsed rekord = jeden SettlementUpdate.
         */
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
                "Usage: SettleFootballDrawMain [--apply]"
        );
    }

    /*
     * =========================================================
     * SETTLEMENT
     * =========================================================
     */

    private static SettlementDecision settle(
            int homeGoals,
            int awayGoals
    ) {
        return homeGoals == awayGoals
                ? SettlementDecision.W
                : SettlementDecision.L;
    }

    /*
     * =========================================================
     * FIXTURE ELIGIBILITY
     * =========================================================
     */

    private static boolean isEligibleFixture(
            FootballSettlementRepository.Candidate candidate
    ) {
        return candidate.statusShort() != null
                && ELIGIBLE_FIXTURE_STATUSES.contains(
                candidate.statusShort()
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
                "Zagranie Typer — Football PURE DRAW Settlement"
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
            int missingFulltime,
            int wins,
            int losses,
            int autoSettleable,
            Map<FootballDrawParser.Status, Integer>
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
                "missingFulltime="
                        + missingFulltime
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
                FootballDrawParser.Status status :
                FootballDrawParser.Status.values()
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

            FootballSettlementRepository.Candidate c =
                    row.candidate();

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
                            + " | status="
                            + c.statusShort()
                            + " | "
                            + c.homeTeam()
                            + " "
                            + c.fulltimeHome()
                            + "-"
                            + c.fulltimeAway()
                            + " "
                            + c.awayTeam()
                            + " | tip="
                            + c.tipTitle()
                            + " | decision="
                            + row.decision()
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
                "DRAW-LIKE REJECTED="
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
            Rejected row =
                    rejected.get(
                            i
                    );

            FootballSettlementRepository.Candidate c =
                    row.candidate();

            FootballDrawParser.ParseResult p =
                    row.parseResult();

            System.out.println(
                    "["
                            + (i + 1)
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
                            + " | normalized="
                            + p.normalizedTitle()
            );
        }
    }

    private static void printSkippedFixtures(
            List<FootballSettlementRepository.Candidate>
                    skipped
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
            FootballSettlementRepository.Candidate c =
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
                "  pl.zagranietyper.SettleFootballDrawMain --apply"
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

    /*
     * =========================================================
     * TYPES
     * =========================================================
     */

    private record Parsed(
            FootballSettlementRepository.Candidate candidate,
            FootballDrawParser.ParseResult parseResult,
            SettlementDecision decision
    ) {
    }

    private record Rejected(
            FootballSettlementRepository.Candidate candidate,
            FootballDrawParser.ParseResult parseResult
    ) {
    }
}