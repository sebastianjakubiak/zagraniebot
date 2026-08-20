package pl.zagranietyper;

import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.model.FootballMarket;
import pl.zagranietyper.model.FootballScore;
import pl.zagranietyper.model.SettlementDecision;
import pl.zagranietyper.parser.FootballMarketParser;
import pl.zagranietyper.repository.Database;
import pl.zagranietyper.repository.FootballSettlementRepository;
import pl.zagranietyper.service.FootballMarketSettlementEngine;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Audited settlement for match-total whole-goal minimum markets. */
public final class SettleFootballMinimumTotalDryRunMain {

    private static final int EXPECTED_MINIMUM_GOAL_LIKE = 32;
    private static final int EXPECTED_PARSED = 20;
    private static final int EXPECTED_WINS = 13;
    private static final int EXPECTED_LOSSES = 7;
    private static final int EXPECTED_REJECTED = 12;

    private static final Map<Long, SettlementDecision> EXPECTED_DECISIONS =
            Map.ofEntries(
                    Map.entry(252L, SettlementDecision.W),
                    Map.entry(2199L, SettlementDecision.W),
                    Map.entry(2422L, SettlementDecision.W),
                    Map.entry(2568L, SettlementDecision.W),
                    Map.entry(7749L, SettlementDecision.W),
                    Map.entry(8421L, SettlementDecision.W),
                    Map.entry(11780L, SettlementDecision.W),
                    Map.entry(12098L, SettlementDecision.W),
                    Map.entry(14862L, SettlementDecision.W),
                    Map.entry(15104L, SettlementDecision.W),
                    Map.entry(16038L, SettlementDecision.W),
                    Map.entry(17307L, SettlementDecision.W),
                    Map.entry(20097L, SettlementDecision.W),
                    Map.entry(1792L, SettlementDecision.L),
                    Map.entry(2452L, SettlementDecision.L),
                    Map.entry(10767L, SettlementDecision.L),
                    Map.entry(11238L, SettlementDecision.L),
                    Map.entry(11948L, SettlementDecision.L),
                    Map.entry(12162L, SettlementDecision.L),
                    Map.entry(14503L, SettlementDecision.L)
            );

    private static final Set<Long> EXPECTED_REJECTED_LEG_IDS =
            Set.of(
                    117L,
                    280L,
                    857L,
                    876L,
                    1269L,
                    1277L,
                    2170L,
                    2367L,
                    9214L,
                    12225L,
                    13707L,
                    20082L
            );

    private static final Pattern MINIMUM_GOAL_LIKE =
            Pattern.compile(
                    "\\b(?:co najmniej|minimum)\\s+"
                            + "(?:[0-9]+(?:[.,][0-9]+)?|[a-z]+)"
                            + "\\s*"
                            + "(?:gol|gole|gola|goli|bramka|bramki|bramek|trafienie|trafienia|trafien)"
                            + "\\b"
            );

    private SettleFootballMinimumTotalDryRunMain() {
    }

    public static void main(
            String[] args
    ) {
        boolean apply = parseApplyFlag(args);

        FootballSettlementRepository repository =
                new FootballSettlementRepository(
                        new Database(
                                AppConfig.fromEnvironment()
                        )
                );

        FootballMarketParser parser =
                new FootballMarketParser();

        FootballMarketSettlementEngine engine =
                new FootballMarketSettlementEngine();

        List<String> parsed =
                new ArrayList<>();

        List<String> rejected =
                new ArrayList<>();

        Map<Long, SettlementDecision> decisions =
                new LinkedHashMap<>();

        Set<Long> rejectedLegIds =
                new LinkedHashSet<>();

        List<FootballSettlementRepository.SettlementUpdate> updates =
                new ArrayList<>();

        int wins = 0;
        int losses = 0;
        int skippedFixture = 0;
        int missingFulltime = 0;
        int minimumGoalLike = 0;

        for (
                FootballSettlementRepository.Candidate candidate :
                repository.findPendingApiFootballCandidates()
        ) {
            if (
                    !looksMinimumGoalLike(
                            candidate.tipTitle()
                    )
            ) {
                continue;
            }

            minimumGoalLike++;

            if (
                    !eligibleStatus(
                            candidate.statusShort()
                    )
            ) {
                skippedFixture++;
                rejected.add(
                        describe(
                                candidate
                        ) + " | reason=fixture status"
                );
                rejectedLegIds.add(candidate.legId());
                continue;
            }

            if (
                    candidate.fulltimeHome() == null
                            || candidate.fulltimeAway() == null
            ) {
                missingFulltime++;
                rejected.add(
                        describe(
                                candidate
                        ) + " | reason=missing fulltime"
                );
                rejectedLegIds.add(candidate.legId());
                continue;
            }

            Optional<FootballMarket> market =
                    parser.parse(
                            candidate.tipTitle(),
                            candidate.homeTeam(),
                            candidate.awayTeam()
                    );

            if (
                    market.isEmpty()
                            || !isOnlyMinimumMatchTotal(
                            market.get()
                    )
            ) {
                rejected.add(
                        describe(
                                candidate
                        ) + " | reason=unsupported or not pure match total minimum"
                );
                rejectedLegIds.add(candidate.legId());
                continue;
            }

            SettlementDecision decision =
                    engine.settle(
                            market.get(),
                            new FootballScore(
                                    candidate.fulltimeHome(),
                                    candidate.fulltimeAway()
                            )
                    );

            if (
                    decision == SettlementDecision.W
            ) {
                wins++;
            } else if (
                    decision == SettlementDecision.L
            ) {
                losses++;
            } else {
                rejected.add(
                        describe(
                                candidate
                        ) + " | reason=unexpected decision " + decision
                );
                rejectedLegIds.add(candidate.legId());
                continue;
            }

            decisions.put(
                    candidate.legId(),
                    decision
            );

            updates.add(
                    new FootballSettlementRepository.SettlementUpdate(
                            candidate.legId(),
                            candidate.betId(),
                            decision
                    )
            );

            FootballMarket.MinimumTotalGoals condition =
                    (FootballMarket.MinimumTotalGoals) market.get()
                            .conditions()
                            .getFirst();

            parsed.add(
                    describe(
                            candidate
                    )
                            + " | minimum="
                            + condition.minimum()
                            + " | decision="
                            + decision
            );
        }

        System.out.println("Zagranie Typer — MATCH TOTAL minimum goals");
        System.out.println("MODE=" + (apply ? "APPLY" : "DRY_RUN"));
        if (!apply) {
            System.out.println("NO DATABASE WRITES");
        }
        System.out.println();
        System.out.println("SUMMARY");
        System.out.println("minimumGoalLike=" + minimumGoalLike);
        System.out.println("parsed=" + parsed.size());
        System.out.println("W=" + wins);
        System.out.println("L=" + losses);
        System.out.println("rejected=" + rejected.size());
        System.out.println("skippedFixture=" + skippedFixture);
        System.out.println("missingFulltime=" + missingFulltime);
        System.out.println();
        print("PARSED RECORDS", parsed);
        print("REJECTED MINIMUM-GOAL-LIKE RECORDS", rejected);

        if (!apply) {
            return;
        }

        validateApplySafety(
                new AuditSnapshot(
                        minimumGoalLike,
                        parsed.size(),
                        wins,
                        losses,
                        rejected.size(),
                        skippedFixture,
                        missingFulltime,
                        decisions,
                        rejectedLegIds,
                        updates
                )
        );

        FootballSettlementRepository.ApplyResult result =
                repository.apply(
                        List.copyOf(updates)
                );

        printApplyResult(result);
    }

    static boolean parseApplyFlag(String[] args) {
        if (args == null || args.length == 0) {
            return false;
        }

        if (args.length == 1 && "--apply".equals(args[0])) {
            return true;
        }

        throw new IllegalArgumentException(
                "Usage: SettleFootballMinimumTotalDryRunMain [--apply]"
        );
    }

    static void validateApplySafety(AuditSnapshot snapshot) {
        requireExpected("minimumGoalLike", EXPECTED_MINIMUM_GOAL_LIKE, snapshot.minimumGoalLike());
        requireExpected("parsed", EXPECTED_PARSED, snapshot.parsed());
        requireExpected("W", EXPECTED_WINS, snapshot.wins());
        requireExpected("L", EXPECTED_LOSSES, snapshot.losses());
        requireExpected("rejected", EXPECTED_REJECTED, snapshot.rejected());
        requireExpected("skippedFixture", 0, snapshot.skippedFixture());
        requireExpected("missingFulltime", 0, snapshot.missingFulltime());

        if (!snapshot.decisions().equals(EXPECTED_DECISIONS)) {
            throw new IllegalStateException(
                    "REFUSING APPLY: audited leg decisions changed"
                            + ", expected=" + EXPECTED_DECISIONS
                            + ", actual=" + snapshot.decisions()
            );
        }

        if (!snapshot.rejectedLegIds().equals(EXPECTED_REJECTED_LEG_IDS)) {
            throw new IllegalStateException(
                    "REFUSING APPLY: rejected leg set changed"
                            + ", expected=" + EXPECTED_REJECTED_LEG_IDS
                            + ", actual=" + snapshot.rejectedLegIds()
            );
        }

        Set<Long> updateLegIds = new HashSet<>();

        for (FootballSettlementRepository.SettlementUpdate update : snapshot.updates()) {
            if (update.decision() != SettlementDecision.W
                    && update.decision() != SettlementDecision.L) {
                throw new IllegalStateException(
                        "REFUSING APPLY: non-W/L decision for leg="
                                + update.legId() + ", decision=" + update.decision()
                );
            }

            if (!updateLegIds.add(update.legId())) {
                throw new IllegalStateException(
                        "REFUSING APPLY: duplicate update leg=" + update.legId()
                );
            }

            if (snapshot.decisions().get(update.legId()) != update.decision()) {
                throw new IllegalStateException(
                        "REFUSING APPLY: update decision mismatch for leg=" + update.legId()
                );
            }
        }

        if (snapshot.updates().size() != EXPECTED_PARSED
                || !updateLegIds.equals(EXPECTED_DECISIONS.keySet())) {
            throw new IllegalStateException(
                    "REFUSING APPLY: update leg set changed"
                            + ", expected=" + EXPECTED_DECISIONS.keySet()
                            + ", actual=" + updateLegIds
            );
        }
    }

    private static void requireExpected(String name, int expected, int actual) {
        if (actual != expected) {
            throw new IllegalStateException(
                    "REFUSING APPLY: expected " + name + "=" + expected + ", actual=" + actual
            );
        }
    }

    private static void printApplyResult(FootballSettlementRepository.ApplyResult result) {
        System.out.println("APPLY RESULT");
        System.out.println("updatedLegs=" + result.updatedLegs());
        System.out.println("skippedLegs=" + result.skippedLegs());
        System.out.println("W=" + result.winLegs());
        System.out.println("L=" + result.lossLegs());
        System.out.println("V=" + result.voidLegs());
        System.out.println("updatedBets=" + result.updatedBets());
        System.out.println("pendingBets=" + result.pendingBets());
        System.out.println("multiUnverifiedBets=" + result.multiUnverifiedBets());
    }

    record AuditSnapshot(
            int minimumGoalLike,
            int parsed,
            int wins,
            int losses,
            int rejected,
            int skippedFixture,
            int missingFulltime,
            Map<Long, SettlementDecision> decisions,
            Set<Long> rejectedLegIds,
            List<FootballSettlementRepository.SettlementUpdate> updates
    ) {
        AuditSnapshot {
            decisions = Map.copyOf(decisions);
            rejectedLegIds = Set.copyOf(rejectedLegIds);
            updates = List.copyOf(updates);
        }
    }

    static boolean looksMinimumGoalLike(
            String value
    ) {
        return MINIMUM_GOAL_LIKE
                .matcher(
                        normalize(
                                value
                        )
                )
                .find();
    }

    private static boolean isOnlyMinimumMatchTotal(
            FootballMarket market
    ) {
        return market.conditions()
                .size() == 1
                && market.conditions()
                .getFirst() instanceof FootballMarket.MinimumTotalGoals;
    }

    private static boolean eligibleStatus(
            String status
    ) {
        return "FT".equals(status)
                || "AET".equals(status)
                || "PEN".equals(status);
    }

    private static void print(
            String title,
            List<String> rows
    ) {
        System.out.println(title + " (" + rows.size() + ")");

        for (
                int index = 0;
                index < rows.size();
                index++
        ) {
            System.out.println("[" + (index + 1) + "] " + rows.get(index));
        }

        System.out.println();
    }

    private static String describe(
            FootballSettlementRepository.Candidate candidate
    ) {
        String score =
                candidate.fulltimeHome() == null
                        || candidate.fulltimeAway() == null
                        ? "?-?"
                        : candidate.fulltimeHome() + "-" + candidate.fulltimeAway();

        return "leg=" + candidate.legId()
                + " | bet=" + candidate.betId()
                + " | wp=" + candidate.wpPostId()
                + " | fixture=" + candidate.fixtureId()
                + " | status=" + candidate.statusShort()
                + " | " + candidate.homeTeam() + " " + score + " " + candidate.awayTeam()
                + " | tip=" + candidate.tipTitle();
    }

    private static String normalize(
            String value
    ) {
        if (
                value == null
        ) {
            return "";
        }

        String transliterated =
                value.replace('ł', 'l')
                        .replace('Ł', 'L');

        return Normalizer.normalize(
                        transliterated,
                        Normalizer.Form.NFD
                )
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9.,]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
