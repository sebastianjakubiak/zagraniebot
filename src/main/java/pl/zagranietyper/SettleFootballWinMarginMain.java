package pl.zagranietyper;

import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.model.SettlementDecision;
import pl.zagranietyper.parser.FootballWinMarginParser;
import pl.zagranietyper.repository.Database;
import pl.zagranietyper.repository.FootballSettlementRepository;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SettleFootballWinMarginMain {

    private static final int REJECTED_EXAMPLE_LIMIT =
            30;

    private static final int SKIPPED_EXAMPLE_LIMIT =
            20;

    private static final Set<String> ELIGIBLE_FIXTURE_STATUSES =
            Set.of(
                    "FT",
                    "AET",
                    "PEN"
            );

    private SettleFootballWinMarginMain() {
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

        FootballWinMarginParser parser =
                new FootballWinMarginParser();

        List<FootballSettlementRepository.Candidate>
                candidates =
                repository.findPendingApiFootballCandidates();

        Map<FootballWinMarginParser.Status, Integer>
                parserStatuses =
                new EnumMap<>(
                        FootballWinMarginParser.Status.class
                );

        for (
                FootballWinMarginParser.Status status :
                FootballWinMarginParser.Status.values()
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

            FootballWinMarginParser.ParseResult result =
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
                        parser.looksLikeWinMargin(
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
                    candidate.fulltimeHome() == null
                            || candidate.fulltimeAway() == null
            ) {
                missingFulltime++;

                continue;
            }

            SettlementDecision decision =
                    settle(
                            result,
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
                "Usage: SettleFootballWinMarginMain [--apply]"
        );
    }

    /*
     * =========================================================
     * SETTLEMENT
     * =========================================================
     */

    private static SettlementDecision settle(
            FootballWinMarginParser.ParseResult result,
            int homeGoals,
            int awayGoals
    ) {
        int selectedGoals =
                switch (
                        result.selection()
                        ) {
                    case HOME ->
                            homeGoals;

                    case AWAY ->
                            awayGoals;
                };

        int opponentGoals =
                switch (
                        result.selection()
                        ) {
                    case HOME ->
                            awayGoals;

                    case AWAY ->
                            homeGoals;
                };

        int winningMargin =
                selectedGoals
                        - opponentGoals;

        return winningMargin
                >= result.minimumMargin()
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
     * SUBJECT SAFETY
     * =========================================================
     */

    private static boolean isSubjectIssue(
            FootballWinMarginParser.Status status
    ) {
        return status
                == FootballWinMarginParser.Status.SUBJECT_NOT_FOUND
                || status
                == FootballWinMarginParser.Status.SUBJECT_MISMATCH
                || status
                == FootballWinMarginParser.Status.SUBJECT_AMBIGUOUS;
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
                "Zagranie Typer — Football WIN MARGIN Settlement"
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
            Map<FootballWinMarginParser.Status, Integer>
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
                FootballWinMarginParser.Status status :
                FootballWinMarginParser.Status.values()
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

            FootballWinMarginParser.ParseResult p =
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
                            + " "
                            + c.fulltimeHome()
                            + "-"
                            + c.fulltimeAway()
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
                            + " | minimumMargin="
                            + p.minimumMargin()
                            + " | actualMargin="
                            + actualMargin(
                            p,
                            c.fulltimeHome(),
                            c.fulltimeAway()
                    )
                            + " | decision="
                            + row.decision()
            );
        }
    }

    private static int actualMargin(
            FootballWinMarginParser.ParseResult result,
            int homeGoals,
            int awayGoals
    ) {
        return switch (
                result.selection()
                ) {
            case HOME ->
                    homeGoals
                            - awayGoals;

            case AWAY ->
                    awayGoals
                            - homeGoals;
        };
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
                "WIN-MARGIN-LIKE REJECTED="
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
        FootballSettlementRepository.Candidate c =
                row.candidate();

        FootballWinMarginParser.ParseResult p =
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
                        + " | minimumMargin="
                        + value(
                        p.minimumMargin()
                )
        );
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
                "  pl.zagranietyper.SettleFootballWinMarginMain --apply"
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
     * HELPERS
     * =========================================================
     */

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

    private record Parsed(
            FootballSettlementRepository.Candidate candidate,
            FootballWinMarginParser.ParseResult parseResult,
            SettlementDecision decision
    ) {
    }

    private record Rejected(
            FootballSettlementRepository.Candidate candidate,
            FootballWinMarginParser.ParseResult parseResult
    ) {
    }
}