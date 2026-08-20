package pl.zagranietyper;

import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.model.SettlementDecision;
import pl.zagranietyper.parser.FootballHandicapParser;
import pl.zagranietyper.repository.Database;
import pl.zagranietyper.repository.FootballSettlementRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SettleFootballHandicapMain {

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

    /*
     * =========================================================
     * AUDITED APPLY SNAPSHOT
     * =========================================================
     *
     * Dry-run zatwierdzony 2026-08-20:
     *
     * parsed=46
     * W=23
     * L=23
     * ambiguousPush=0
     *
     * APPLY ma rozliczyć dokładnie ten batch.
     *
     * Jeżeli pomiędzy DRY_RUN a APPLY cokolwiek zmieni się
     * w zestawie wykrytych handicapów, zapis zostanie przerwany.
     */
    private static final int EXPECTED_APPLY_COUNT =
            46;

    private static final int EXPECTED_WINS =
            23;

    private static final int EXPECTED_LOSSES =
            23;

    private static final Set<Long> EXPECTED_APPLY_LEG_IDS =
            Set.of(
                    330L,
                    460L,
                    637L,
                    751L,
                    878L,
                    981L,
                    1152L,
                    1366L,
                    1651L,
                    1908L,
                    2211L,
                    2557L,
                    3121L,
                    3326L,
                    3541L,
                    7429L,
                    7696L,
                    7783L,
                    7859L,
                    7977L,
                    8602L,
                    8866L,
                    9007L,
                    9393L,
                    9638L,
                    9650L,
                    10021L,
                    10024L,
                    10173L,
                    10237L,
                    10556L,
                    10622L,
                    10729L,
                    10750L,
                    11199L,
                    11381L,
                    11960L,
                    12090L,
                    14878L,
                    15129L,
                    15477L,
                    16748L,
                    17883L,
                    19705L,
                    20081L,
                    20111L
            );

    /*
     * Celowo pozostają PENDING:
     *
     * leg=1070
     * FC Barcelona handicap -0,5 rzutów rożnych
     *
     * Nie jest to handicap wyniku meczu.
     */
    private static final long EXPECTED_NON_SCORE_HANDICAP_LEG_ID =
            1070L;

    /*
     * Celowo pozostaje PENDING:
     *
     * leg=8078
     * Handicap 0:1
     *
     * Brak jawnego subjectu w tip_title.
     */
    private static final long EXPECTED_SUBJECTLESS_HANDICAP_LEG_ID =
            8078L;

    private SettleFootballHandicapMain() {
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

        FootballHandicapParser parser =
                new FootballHandicapParser();

        List<FootballSettlementRepository.Candidate> candidates =
                repository.findPendingApiFootballCandidates();

        Map<FootballHandicapParser.Status, Integer> parserStatuses =
                new EnumMap<>(
                        FootballHandicapParser.Status.class
                );

        for (
                FootballHandicapParser.Status status :
                FootballHandicapParser.Status.values()
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

        List<Parsed> ambiguousPush =
                new ArrayList<>();

        List<FootballSettlementRepository.Candidate> skippedFixtures =
                new ArrayList<>();

        List<FootballSettlementRepository.SettlementUpdate> updates =
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

            FootballHandicapParser.ParseResult result =
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
                        parser.looksLikeHandicap(
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

            SettlementResult settlement =
                    settle(
                            result,
                            candidate.fulltimeHome(),
                            candidate.fulltimeAway()
                    );

            Parsed row =
                    new Parsed(
                            candidate,
                            result,
                            settlement.decision(),
                            settlement.adjustedMargin()
                    );

            parsed.add(
                    row
            );

            if (
                    settlement.decision()
                            == SettlementDecision.UNSUPPORTED
            ) {
                ambiguousPush.add(
                        row
                );

                continue;
            }

            if (
                    settlement.decision()
                            == SettlementDecision.W
            ) {
                wins++;
            }

            if (
                    settlement.decision()
                            == SettlementDecision.L
            ) {
                losses++;
            }

            updates.add(
                    new FootballSettlementRepository.SettlementUpdate(
                            candidate.legId(),
                            candidate.betId(),
                            settlement.decision()
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
                ambiguousPush.size(),
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

        printAmbiguousPush(
                ambiguousPush
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

        validateApplySafety(
                parsed,
                rejected,
                subjectIssues,
                ambiguousPush,
                updates,
                missingFulltime,
                wins,
                losses
        );

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
                "Usage: SettleFootballHandicapMain [--apply]"
        );
    }

    /*
     * =========================================================
     * SETTLEMENT
     * =========================================================
     */

    private static SettlementResult settle(
            FootballHandicapParser.ParseResult result,
            int homeGoals,
            int awayGoals
    ) {
        int rawMargin =
                switch (
                        result.selection()
                        ) {
                    case HOME ->
                            homeGoals - awayGoals;

                    case AWAY ->
                            awayGoals - homeGoals;
                };

        BigDecimal adjusted =
                BigDecimal.valueOf(
                        rawMargin
                ).add(
                        result.line()
                );

        int comparison =
                adjusted.compareTo(
                        BigDecimal.ZERO
                );

        if (
                comparison > 0
        ) {
            return new SettlementResult(
                    SettlementDecision.W,
                    adjusted
            );
        }

        if (
                comparison < 0
        ) {
            return new SettlementResult(
                    SettlementDecision.L,
                    adjusted
            );
        }

        /*
         * Dokładnie zero:
         *
         * np. handicap -3 i wygrana dokładnie trzema golami.
         *
         * Z samego tip_title nie mamy pewności, czy był to:
         *
         * - handicap azjatycki -> V,
         * - handicap europejski / 3-way -> selekcja drużyny L.
         *
         * Dlatego taki rekord pozostaje PENDING.
         */
        return new SettlementResult(
                SettlementDecision.UNSUPPORTED,
                adjusted
        );
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
            FootballHandicapParser.Status status
    ) {
        return status
                == FootballHandicapParser.Status.SUBJECT_NOT_FOUND
                || status
                == FootballHandicapParser.Status.SUBJECT_MISMATCH
                || status
                == FootballHandicapParser.Status.SUBJECT_AMBIGUOUS;
    }

    /*
     * =========================================================
     * APPLY SAFETY
     * =========================================================
     */

    private static void validateApplySafety(
            List<Parsed> parsed,
            List<Rejected> rejected,
            List<Rejected> subjectIssues,
            List<Parsed> ambiguousPush,
            List<FootballSettlementRepository.SettlementUpdate> updates,
            int missingFulltime,
            int wins,
            int losses
    ) {
        validateExpectedSubjectIssue(
                subjectIssues
        );

        validateExpectedRejectedRows(
                rejected
        );

        if (
                missingFulltime != 0
        ) {
            throw new IllegalStateException(
                    "REFUSING APPLY: missingFulltime="
                            + missingFulltime
            );
        }

        if (
                !ambiguousPush.isEmpty()
        ) {
            throw new IllegalStateException(
                    "REFUSING APPLY: ambiguousPush="
                            + ambiguousPush.size()
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

        if (
                parsed.size()
                        != EXPECTED_APPLY_COUNT
        ) {
            throw new IllegalStateException(
                    "REFUSING APPLY: expected parsed="
                            + EXPECTED_APPLY_COUNT
                            + ", actual="
                            + parsed.size()
            );
        }

        if (
                updates.size()
                        != EXPECTED_APPLY_COUNT
        ) {
            throw new IllegalStateException(
                    "REFUSING APPLY: expected updates="
                            + EXPECTED_APPLY_COUNT
                            + ", actual="
                            + updates.size()
            );
        }

        if (
                wins
                        != EXPECTED_WINS
        ) {
            throw new IllegalStateException(
                    "REFUSING APPLY: expected W="
                            + EXPECTED_WINS
                            + ", actual="
                            + wins
            );
        }

        if (
                losses
                        != EXPECTED_LOSSES
        ) {
            throw new IllegalStateException(
                    "REFUSING APPLY: expected L="
                            + EXPECTED_LOSSES
                            + ", actual="
                            + losses
            );
        }

        Set<Long> actualLegIds =
                new HashSet<>();

        for (
                FootballSettlementRepository.SettlementUpdate update :
                updates
        ) {
            if (
                    !actualLegIds.add(
                            update.legId()
                    )
            ) {
                throw new IllegalStateException(
                        "REFUSING APPLY: duplicate legId="
                                + update.legId()
                );
            }
        }

        if (
                !actualLegIds.equals(
                        EXPECTED_APPLY_LEG_IDS
                )
        ) {
            Set<Long> missing =
                    new HashSet<>(
                            EXPECTED_APPLY_LEG_IDS
                    );

            missing.removeAll(
                    actualLegIds
            );

            Set<Long> unexpected =
                    new HashSet<>(
                            actualLegIds
                    );

            unexpected.removeAll(
                    EXPECTED_APPLY_LEG_IDS
            );

            throw new IllegalStateException(
                    "REFUSING APPLY: audited leg set changed"
                            + ", missing="
                            + missing
                            + ", unexpected="
                            + unexpected
            );
        }

        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "APPLY SAFETY GATES PASSED"
        );

        System.out.println(
                "========================================"
        );

        System.out.println(
                "auditedLegs="
                        + actualLegIds.size()
        );

        System.out.println(
                "W="
                        + wins
                        + " | L="
                        + losses
        );

        System.out.println(
                "knownPendingNonScoreLeg="
                        + EXPECTED_NON_SCORE_HANDICAP_LEG_ID
        );

        System.out.println(
                "knownPendingSubjectlessLeg="
                        + EXPECTED_SUBJECTLESS_HANDICAP_LEG_ID
        );
    }

    private static void validateExpectedSubjectIssue(
            List<Rejected> subjectIssues
    ) {
        if (
                subjectIssues.size()
                        != 1
        ) {
            throw new IllegalStateException(
                    "REFUSING APPLY: expected exactly 1 known subject issue"
                            + ", actual="
                            + subjectIssues.size()
            );
        }

        Rejected row =
                subjectIssues.get(
                        0
                );

        if (
                !isExpectedSubjectlessHandicap(
                        row
                )
        ) {
            throw new IllegalStateException(
                    "REFUSING APPLY: unexpected subject issue"
                            + " leg="
                            + row.candidate().legId()
                            + " status="
                            + row.parseResult().status()
                            + " format="
                            + row.parseResult().format()
            );
        }
    }

    private static void validateExpectedRejectedRows(
            List<Rejected> rejected
    ) {
        if (
                rejected.size()
                        != 2
        ) {
            throw new IllegalStateException(
                    "REFUSING APPLY: expected exactly 2 known rejected handicap-like rows"
                            + ", actual="
                            + rejected.size()
            );
        }

        boolean foundNonScore =
                false;

        boolean foundSubjectless =
                false;

        for (
                Rejected row :
                rejected
        ) {
            if (
                    isExpectedNonScoreHandicap(
                            row
                    )
            ) {
                foundNonScore =
                        true;

                continue;
            }

            if (
                    isExpectedSubjectlessHandicap(
                            row
                    )
            ) {
                foundSubjectless =
                        true;

                continue;
            }

            throw new IllegalStateException(
                    "REFUSING APPLY: unexpected rejected handicap-like row"
                            + " leg="
                            + row.candidate().legId()
                            + " status="
                            + row.parseResult().status()
                            + " tip="
                            + row.candidate().tipTitle()
            );
        }

        if (
                !foundNonScore
                        || !foundSubjectless
        ) {
            throw new IllegalStateException(
                    "REFUSING APPLY: expected rejected rows not found"
                            + ", nonScore="
                            + foundNonScore
                            + ", subjectless="
                            + foundSubjectless
            );
        }
    }

    private static boolean isExpectedNonScoreHandicap(
            Rejected row
    ) {
        return row.candidate().legId()
                == EXPECTED_NON_SCORE_HANDICAP_LEG_ID
                && row.parseResult().status()
                == FootballHandicapParser.Status.UNSUPPORTED_NON_SCORE_HANDICAP;
    }

    private static boolean isExpectedSubjectlessHandicap(
            Rejected row
    ) {
        return row.candidate().legId()
                == EXPECTED_SUBJECTLESS_HANDICAP_LEG_ID
                && row.parseResult().status()
                == FootballHandicapParser.Status.SUBJECT_NOT_FOUND
                && row.parseResult().format()
                == FootballHandicapParser.Format.COLON_0_1_SUBJECTLESS;
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
                "Zagranie Typer — Football HANDICAP Settlement"
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
            int ambiguousPush,
            int wins,
            int losses,
            int autoSettleable,
            Map<FootballHandicapParser.Status, Integer> parserStatuses
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
                "ambiguousPush="
                        + ambiguousPush
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
                FootballHandicapParser.Status status :
                FootballHandicapParser.Status.values()
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

            FootballHandicapParser.ParseResult p =
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
                    "    selection="
                            + p.selection()
                            + " | subject="
                            + p.subject()
                            + " | handicap="
                            + p.line()
                            + " | format="
                            + p.format()
                            + " | adjustedMargin="
                            + row.adjustedMargin()
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

    private static void printAmbiguousPush(
            List<Parsed> rows
    ) {
        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "AMBIGUOUS EXACT-LINE RESULTS ("
                        + rows.size()
                        + ")"
        );

        System.out.println(
                "========================================"
        );

        if (
                rows.isEmpty()
        ) {
            System.out.println(
                    "NONE"
            );

            return;
        }

        int ordinal =
                0;

        for (
                Parsed row :
                rows
        ) {
            ordinal++;

            FootballSettlementRepository.Candidate c =
                    row.candidate();

            FootballHandicapParser.ParseResult p =
                    row.parseResult();

            System.out.println(
                    "["
                            + ordinal
                            + "] leg="
                            + c.legId()
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
                    "    selection="
                            + p.selection()
                            + " | handicap="
                            + p.line()
                            + " | adjustedMargin="
                            + row.adjustedMargin()
                            + " | decision=UNSUPPORTED"
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
                "HANDICAP-LIKE REJECTED="
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

        FootballHandicapParser.ParseResult p =
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
                        + " | selection="
                        + value(
                        p.selection()
                )
                        + " | subject="
                        + value(
                        p.subject()
                )
                        + " | handicap="
                        + value(
                        p.line()
                )
                        + " | format="
                        + value(
                        p.format()
                )
        );
    }

    private static void printSkippedFixtures(
            List<FootballSettlementRepository.Candidate> skipped
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
                "To apply exactly the audited HANDICAP batch:"
        );

        System.out.println();

        System.out.println(
                "java -cp target/zagranie-typer-0.1.0-SNAPSHOT.jar \\"
        );

        System.out.println(
                "  pl.zagranietyper.SettleFootballHandicapMain --apply"
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

    private record SettlementResult(
            SettlementDecision decision,
            BigDecimal adjustedMargin
    ) {
    }

    private record Parsed(
            FootballSettlementRepository.Candidate candidate,
            FootballHandicapParser.ParseResult parseResult,
            SettlementDecision decision,
            BigDecimal adjustedMargin
    ) {
    }

    private record Rejected(
            FootballSettlementRepository.Candidate candidate,
            FootballHandicapParser.ParseResult parseResult
    ) {
    }
}