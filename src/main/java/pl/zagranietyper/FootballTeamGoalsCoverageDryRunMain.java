package pl.zagranietyper;

import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.model.FootballScore;
import pl.zagranietyper.model.FootballScoreSnapshot;
import pl.zagranietyper.model.SettlementDecision;
import pl.zagranietyper.parser.UnifiedFootballTeamGoalParser;
import pl.zagranietyper.repository.Database;
import pl.zagranietyper.repository.FootballSettlementRepository;
import pl.zagranietyper.service.UnifiedFootballSettlementEngine;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FootballTeamGoalsCoverageDryRunMain {
    private static final Set<String> ELIGIBLE = Set.of("FT", "AET", "PEN");
    private static final int EXPECTED_TOTAL_ELIGIBLE = 1170;
    private static final int EXPECTED_TEAM_GOAL_LIKE = 319;
    private static final int EXPECTED_PARSED = 26;
    private static final int EXPECTED_W = 17;
    private static final int EXPECTED_L = 9;
    private static final int EXPECTED_V = 0;
    private static final int EXPECTED_REJECTED = 293;
    private static final int EXPECTED_AMBIGUOUS = 0;
    private static final int EXPECTED_SIGNED = 50;
    private static final int EXPECTED_COMPOSITE = 78;
    private static final int EXPECTED_MISSING_SCORE = 0;
    private static final Map<Long, SettlementDecision> EXPECTED_DECISIONS = Map.ofEntries(
            Map.entry(20L, SettlementDecision.W),
            Map.entry(117L, SettlementDecision.W),
            Map.entry(1269L, SettlementDecision.W),
            Map.entry(1277L, SettlementDecision.W),
            Map.entry(2156L, SettlementDecision.W),
            Map.entry(2170L, SettlementDecision.W),
            Map.entry(2367L, SettlementDecision.W),
            Map.entry(8737L, SettlementDecision.W),
            Map.entry(9214L, SettlementDecision.W),
            Map.entry(9733L, SettlementDecision.W),
            Map.entry(9941L, SettlementDecision.W),
            Map.entry(10048L, SettlementDecision.W),
            Map.entry(10171L, SettlementDecision.W),
            Map.entry(11770L, SettlementDecision.W),
            Map.entry(14248L, SettlementDecision.W),
            Map.entry(19221L, SettlementDecision.W),
            Map.entry(20082L, SettlementDecision.W),
            Map.entry(142L, SettlementDecision.L),
            Map.entry(280L, SettlementDecision.L),
            Map.entry(857L, SettlementDecision.L),
            Map.entry(872L, SettlementDecision.L),
            Map.entry(876L, SettlementDecision.L),
            Map.entry(7602L, SettlementDecision.L),
            Map.entry(11486L, SettlementDecision.L),
            Map.entry(14018L, SettlementDecision.L),
            Map.entry(18350L, SettlementDecision.L)
    );

    private FootballTeamGoalsCoverageDryRunMain() {}

    public static void main(String[] args) {
        boolean apply = parseApplyFlag(args);
        var config = AppConfig.fromEnvironment();
        var repository = new FootballSettlementRepository(new Database(config));
        var parser = new UnifiedFootballTeamGoalParser();
        var engine = new UnifiedFootballSettlementEngine();

        int eligible = 0, like = 0, parsed = 0, rejected = 0, ambiguous = 0;
        int signed = 0, composite = 0, missingScore = 0;
        Map<SettlementDecision, Integer> decisions = new EnumMap<>(SettlementDecision.class);
        Map<UnifiedFootballTeamGoalParser.Category, Integer> categories =
                new EnumMap<>(UnifiedFootballTeamGoalParser.Category.class);
        List<String> parsedRows = new ArrayList<>();
        List<String> rejectedRows = new ArrayList<>();
        Map<Long, SettlementDecision> legDecisions = new LinkedHashMap<>();
        List<FootballSettlementRepository.SettlementUpdate> updates = new ArrayList<>();

        for (var candidate : repository.findPendingApiFootballCandidates()) {
            if (!ELIGIBLE.contains(candidate.statusShort())) continue;
            eligible++;
            var result = parser.parse(candidate.tipTitle(), candidate.homeTeam(), candidate.awayTeam());
            if (result.status() == UnifiedFootballTeamGoalParser.Status.NOT_TEAM_GOAL_LIKE) continue;
            like++;
            if (!result.parsed()) {
                rejected++;
                if (result.status() == UnifiedFootballTeamGoalParser.Status.AMBIGUOUS_PARTICIPANT) ambiguous++;
                if (result.status() == UnifiedFootballTeamGoalParser.Status.UNSUPPORTED_SIGNED_FORMAT) signed++;
                if (result.status() == UnifiedFootballTeamGoalParser.Status.UNSUPPORTED_COMPOSITE) composite++;
                rejectedRows.add("leg_id=" + candidate.legId() + " | tip_title=" + candidate.tipTitle()
                        + " | reason=" + result.status());
                continue;
            }
            if (candidate.fulltimeHome() == null || candidate.fulltimeAway() == null) {
                missingScore++;
                rejected++;
                rejectedRows.add("leg_id=" + candidate.legId() + " | tip_title=" + candidate.tipTitle()
                        + " | reason=MISSING_SCORE");
                continue;
            }
            FootballScore score = new FootballScore(candidate.fulltimeHome(), candidate.fulltimeAway());
            SettlementDecision decision = engine.settle(result.market(), FootballScoreSnapshot.fullTime(score));
            if (decision == SettlementDecision.UNSUPPORTED) {
                rejected++;
                rejectedRows.add("leg_id=" + candidate.legId() + " | tip_title=" + candidate.tipTitle()
                        + " | reason=UNSUPPORTED_SETTLEMENT");
                continue;
            }
            parsed++;
            decisions.merge(decision, 1, Integer::sum);
            categories.merge(result.category(), 1, Integer::sum);
            legDecisions.put(candidate.legId(), decision);
            updates.add(new FootballSettlementRepository.SettlementUpdate(
                    candidate.legId(), candidate.betId(), decision));
            parsedRows.add("leg_id=" + candidate.legId()
                    + " | fixture=" + candidate.homeTeam() + " vs " + candidate.awayTeam()
                    + " | score=" + score.home() + ":" + score.away()
                    + " | tip_title=" + candidate.tipTitle()
                    + " | participant=" + result.subject()
                    + " | condition=" + result.market().conditions()
                    + " | decision=" + decision);
        }

        System.out.println("Zagranie Typer — UNIFIED TEAM GOALS COVERAGE");
        System.out.println("MODE=" + (apply ? "APPLY" : "DRY_RUN"));
        if (!apply) System.out.println("READ ONLY — NO DATABASE WRITES");
        System.out.println();
        System.out.println("PARSED RECORDS");
        parsedRows.forEach(System.out::println);
        System.out.println();
        System.out.println("REJECTED TEAM-GOAL-LIKE RECORDS");
        rejectedRows.forEach(System.out::println);
        System.out.println();
        System.out.println("SUMMARY");
        System.out.println("eligibleCandidatesInspected=" + eligible);
        System.out.println("teamGoalLike=" + like);
        System.out.println("parsed=" + parsed);
        System.out.println("W=" + decisions.getOrDefault(SettlementDecision.W, 0));
        System.out.println("L=" + decisions.getOrDefault(SettlementDecision.L, 0));
        System.out.println("V=" + decisions.getOrDefault(SettlementDecision.V, 0));
        System.out.println("rejected=" + rejected);
        System.out.println("ambiguousParticipant=" + ambiguous);
        System.out.println("unsupportedSignedFormat=" + signed);
        System.out.println("unsupportedComposite=" + composite);
        System.out.println("missingScore=" + missingScore);
        for (var category : UnifiedFootballTeamGoalParser.Category.values()) {
            System.out.println(category + "=" + categories.getOrDefault(category, 0));
        }

        if (!apply) return;

        AuditSnapshot snapshot = new AuditSnapshot(
                eligible, like, parsed,
                decisions.getOrDefault(SettlementDecision.W, 0),
                decisions.getOrDefault(SettlementDecision.L, 0),
                decisions.getOrDefault(SettlementDecision.V, 0),
                rejected, ambiguous, signed, composite, missingScore,
                legDecisions, updates);
        validateApplySafety(snapshot);
        System.out.println();
        System.out.println("APPLY SAFETY GATES PASSED");
        printApplyResult(repository.apply(List.copyOf(updates)));
    }

    static boolean parseApplyFlag(String[] args) {
        if (args == null || args.length == 0) return false;
        if (args.length == 1 && "--apply".equals(args[0])) return true;
        throw new IllegalArgumentException("Usage: FootballTeamGoalsCoverageDryRunMain [--apply]");
    }

    static void validateApplySafety(AuditSnapshot snapshot) {
        requireExpected("totalEligible", EXPECTED_TOTAL_ELIGIBLE, snapshot.totalEligible());
        requireExpected("teamGoalLike", EXPECTED_TEAM_GOAL_LIKE, snapshot.teamGoalLike());
        requireExpected("parsed", EXPECTED_PARSED, snapshot.parsed());
        requireExpected("W", EXPECTED_W, snapshot.wins());
        requireExpected("L", EXPECTED_L, snapshot.losses());
        requireExpected("V", EXPECTED_V, snapshot.voids());
        requireExpected("rejected", EXPECTED_REJECTED, snapshot.rejected());
        requireExpected("ambiguousParticipant", EXPECTED_AMBIGUOUS, snapshot.ambiguous());
        requireExpected("unsupportedSignedFormat", EXPECTED_SIGNED, snapshot.signed());
        requireExpected("unsupportedComposite", EXPECTED_COMPOSITE, snapshot.composite());
        requireExpected("missingScore", EXPECTED_MISSING_SCORE, snapshot.missingScore());

        if (!snapshot.decisions().equals(EXPECTED_DECISIONS)) {
            throw new IllegalStateException("REFUSING APPLY: audited leg decisions changed, expected="
                    + EXPECTED_DECISIONS + ", actual=" + snapshot.decisions());
        }

        Set<Long> updateIds = new HashSet<>();
        for (var update : snapshot.updates()) {
            if (update.decision() == SettlementDecision.UNSUPPORTED) {
                throw new IllegalStateException("REFUSING APPLY: UNSUPPORTED decision for leg=" + update.legId());
            }
            if (!updateIds.add(update.legId())) {
                throw new IllegalStateException("REFUSING APPLY: duplicate update leg=" + update.legId());
            }
            if (snapshot.decisions().get(update.legId()) != update.decision()) {
                throw new IllegalStateException("REFUSING APPLY: update decision mismatch for leg=" + update.legId());
            }
        }
        if (snapshot.updates().size() != EXPECTED_PARSED
                || !updateIds.equals(EXPECTED_DECISIONS.keySet())) {
            throw new IllegalStateException("REFUSING APPLY: update leg set changed, expected="
                    + EXPECTED_DECISIONS.keySet() + ", actual=" + updateIds);
        }
    }

    private static void requireExpected(String name, int expected, int actual) {
        if (expected != actual) {
            throw new IllegalStateException("REFUSING APPLY: expected " + name + "=" + expected
                    + ", actual=" + actual);
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
            int totalEligible, int teamGoalLike, int parsed, int wins, int losses, int voids,
            int rejected, int ambiguous, int signed, int composite, int missingScore,
            Map<Long, SettlementDecision> decisions,
            List<FootballSettlementRepository.SettlementUpdate> updates
    ) {
        AuditSnapshot {
            decisions = Map.copyOf(decisions);
            updates = List.copyOf(updates);
        }
    }
}
