package pl.zagranietyper;

import com.fasterxml.jackson.databind.ObjectMapper;
import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.model.FootballFixtureStatisticCondition;
import pl.zagranietyper.model.FootballFixtureStatisticType;
import pl.zagranietyper.model.FootballFixtureStatisticsSnapshot;
import pl.zagranietyper.model.SettlementDecision;
import pl.zagranietyper.parser.FootballCornersSyntaxAdapter;
import pl.zagranietyper.repository.Database;
import pl.zagranietyper.repository.FootballFixtureStatisticsRepository;
import pl.zagranietyper.repository.FootballSettlementRepository;
import pl.zagranietyper.service.FootballFixtureStatisticSettlementEngine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Read-only global coverage report for full-time corners markets. */
public final class FootballCornersCoverageDryRunMain {
    private static final int EXAMPLES_PER_REASON = 3;

    private FootballCornersCoverageDryRunMain() {}

    public static void main(String[] args) {
        boolean apply = parseApplyFlag(args);
        Database database = new Database(AppConfig.fromEnvironment());
        var settlementRepository = new FootballSettlementRepository(database);
        var candidates = settlementRepository.findPendingApiFootballCandidates();
        var statistics = new FootballFixtureStatisticsRepository(database, new ObjectMapper());
        var adapter = new FootballCornersSyntaxAdapter();
        var engine = new FootballFixtureStatisticSettlementEngine();

        int totalEligible = 0;
        int cornersLike = 0;
        int fullTimeCornersLike = 0;
        int rawDataAvailableFullTime = 0;
        int wins = 0, losses = 0, voids = 0;
        EnumMap<FootballCornersSyntaxAdapter.Category, Integer> categories =
                new EnumMap<>(FootballCornersSyntaxAdapter.Category.class);
        EnumMap<FootballCornersSyntaxAdapter.SyntaxFamily, SignedCounters> signed =
                new EnumMap<>(FootballCornersSyntaxAdapter.SyntaxFamily.class);
        for (var family : FootballCornersSyntaxAdapter.SyntaxFamily.values()) {
            if (family.signed()) signed.put(family, new SignedCounters());
        }
        List<String> signedDecisionRecords = new ArrayList<>();
        List<FootballCornersReviewedSnapshot.Candidate> reviewedCandidates = new ArrayList<>();
        LinkedHashMap<Reason, Integer> reasons = new LinkedHashMap<>();
        LinkedHashMap<Reason, List<String>> examples = new LinkedHashMap<>();
        for (Reason reason : Reason.values()) {
            reasons.put(reason, 0);
            examples.put(reason, new ArrayList<>());
        }

        System.out.println("Zagranie Typer — GLOBAL CORNERS COVERAGE");
        System.out.println("MODE=" + (apply ? "APPLY" : "DRY_RUN"));
        if (!apply) System.out.println("NO SETTLEMENT WRITES");
        System.out.println("PARSED RECORDS");

        for (FootballSettlementRepository.Candidate candidate : candidates) {
            if (!eligible(candidate.statusShort())) continue;
            totalEligible++;
            var parsed = adapter.parse(candidate.tipTitle(), candidate.homeTeam(), candidate.awayTeam());
            if (parsed.status() == FootballCornersSyntaxAdapter.Status.NOT_CORNERS_LIKE) continue;
            cornersLike++;
            SignedCounters signedCounters = signed.get(parsed.syntaxFamily());
            if (signedCounters != null) signedCounters.syntaxMatches++;
            Optional<FootballFixtureStatisticsSnapshot> snapshot = Optional.empty();
            if (parsed.status() != FootballCornersSyntaxAdapter.Status.UNSUPPORTED_PERIOD) {
                fullTimeCornersLike++;
                snapshot = statistics.load(candidate.fixtureId());
                if (snapshot.isPresent()
                        && snapshot.get().status() == FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE
                        && known(snapshot.get(), FootballFixtureStatisticsSnapshot.TeamSide.HOME)
                        && known(snapshot.get(), FootballFixtureStatisticsSnapshot.TeamSide.AWAY)) {
                    rawDataAvailableFullTime++;
                }
            }

            if (!parsed.parsed()) {
                if (signedCounters != null
                        && parsed.status() == FootballCornersSyntaxAdapter.Status.AMBIGUOUS_PARTICIPANT) {
                    signedCounters.ambiguousParticipant++;
                }
                reject(reason(parsed.status()), candidate, reasons, examples);
                continue;
            }
            if (snapshot.isEmpty()) {
                reject(Reason.MISSING_SNAPSHOT, candidate, reasons, examples);
                continue;
            }
            if (snapshot.get().status() == FootballFixtureStatisticsSnapshot.FetchStatus.UNSUPPORTED) {
                if (signedCounters != null) signedCounters.unsupportedFixture++;
                reject(Reason.UNSUPPORTED_FIXTURE, candidate, reasons, examples);
                continue;
            }
            if (snapshot.get().status() != FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE) {
                reject(Reason.MISSING_SNAPSHOT, candidate, reasons, examples);
                continue;
            }
            if (!requiredKnown(parsed.condition(), snapshot.get())) {
                if (signedCounters != null) signedCounters.absentStatistic++;
                reject(Reason.ABSENT_STATISTIC, candidate, reasons, examples);
                continue;
            }

            SettlementDecision decision = engine.settle(parsed.condition(), snapshot.get());
            if (decision == SettlementDecision.UNSUPPORTED) {
                if (signedCounters != null) signedCounters.absentStatistic++;
                reject(Reason.ABSENT_STATISTIC, candidate, reasons, examples);
                continue;
            }
            categories.merge(parsed.category(), 1, Integer::sum);
            if (decision == SettlementDecision.W) wins++;
            else if (decision == SettlementDecision.L) losses++;
            else if (decision == SettlementDecision.V) voids++;
            if (signedCounters != null) signedCounters.decision(decision);
            reviewedCandidates.add(new FootballCornersReviewedSnapshot.Candidate(
                    candidate.legId(), candidate.betId(), parsed.condition(), decision, true));
            String record = String.format("leg_id=%d fixture=%d %s vs %s corners_HOME=%s corners_AWAY=%s "
                            + "tip_title=%s resolvedSubject=%s parsedCondition=%s syntaxFamily=%s decision=%s",
                    candidate.legId(), candidate.fixtureId(), candidate.homeTeam(), candidate.awayTeam(),
                    corner(snapshot.get(), FootballFixtureStatisticsSnapshot.TeamSide.HOME),
                    corner(snapshot.get(), FootballFixtureStatisticsSnapshot.TeamSide.AWAY),
                    candidate.tipTitle(), parsed.condition().subject(), parsed.condition(),
                    parsed.syntaxFamily(), decision);
            System.out.println(record);
            if (signedCounters != null) signedDecisionRecords.add(record);
        }

        int parsedCount = wins + losses + voids;
        int rejected = reasons.values().stream().mapToInt(Integer::intValue).sum();
        System.out.println("SUMMARY");
        System.out.println("totalEligible=" + totalEligible);
        System.out.println("cornersLike=" + cornersLike);
        System.out.println("fullTimeCornersLike=" + fullTimeCornersLike);
        System.out.println("rawDataAvailableFullTime=" + rawDataAvailableFullTime);
        System.out.println("parsed=" + parsedCount);
        System.out.println("W=" + wins);
        System.out.println("L=" + losses);
        System.out.println("V=" + voids);
        System.out.println("rejected=" + rejected);
        for (Reason reason : Reason.values()) {
            System.out.println(reason.label + "=" + reasons.get(reason));
        }
        for (FootballCornersSyntaxAdapter.Category category : FootballCornersSyntaxAdapter.Category.values()) {
            System.out.println("parsed." + category + "=" + categories.getOrDefault(category, 0));
        }
        System.out.println("SIGNED FAMILY BREAKDOWN");
        for (var entry : signed.entrySet()) {
            SignedCounters value = entry.getValue();
            System.out.println(entry.getKey()
                    + " syntaxMatches=" + value.syntaxMatches
                    + " parsedDecisions=" + value.parsedDecisions
                    + " W=" + value.wins
                    + " L=" + value.losses
                    + " V=" + value.voids
                    + " unsupportedFixture=" + value.unsupportedFixture
                    + " absentStatistic=" + value.absentStatistic
                    + " ambiguousParticipant=" + value.ambiguousParticipant);
        }
        System.out.println("SIGNED DECISION RECORDS");
        signedDecisionRecords.forEach(System.out::println);
        System.out.println("REJECTION EXAMPLES");
        for (Reason reason : Reason.values()) {
            System.out.println(reason.label + ".examples=" + examples.get(reason));
        }
        if (cornersLike != parsedCount + rejected) {
            throw new IllegalStateException("Corners coverage counts do not reconcile");
        }

        FootballCornersReviewedSnapshot.GateResult gate =
                FootballCornersReviewedSnapshot.verify(reviewedCandidates);
        printApplyGate(gate);
        applyIfRequested(apply, gate, reviewedCandidates, updates ->
                settlementRepository.applyExact(updates));
    }

    static boolean parseApplyFlag(String[] args) {
        if (args == null || args.length == 0) return false;
        if (args.length == 1 && "--apply".equals(args[0])) return true;
        throw new IllegalArgumentException("Usage: FootballCornersCoverageDryRunMain [--apply]");
    }

    static FootballSettlementRepository.ApplyResult applyIfRequested(
            boolean apply,
            FootballCornersReviewedSnapshot.GateResult gate,
            List<FootballCornersReviewedSnapshot.Candidate> candidates,
            ApplyExecutor executor
    ) {
        if (!apply) return null;
        if (!gate.applyReady()) {
            throw new IllegalStateException("REFUSING APPLY: exact reviewed CORNERS snapshot gate failed");
        }
        List<FootballSettlementRepository.SettlementUpdate> updates = candidates.stream()
                .map(candidate -> new FootballSettlementRepository.SettlementUpdate(
                        candidate.legId(), candidate.betId(), candidate.decision()))
                .toList();
        FootballSettlementRepository.ApplyResult result = executor.apply(updates);
        if (result.updatedLegs() != FootballCornersReviewedSnapshot.EXPECTED_COUNT
                || result.skippedLegs() != 0
                || result.winLegs() != FootballCornersReviewedSnapshot.EXPECTED_W
                || result.lossLegs() != FootballCornersReviewedSnapshot.EXPECTED_L
                || result.voidLegs() != FootballCornersReviewedSnapshot.EXPECTED_V) {
            throw new IllegalStateException("CORNERS exact apply result mismatch: " + result);
        }
        printApplyResult(result);
        return result;
    }

    private static void printApplyGate(FootballCornersReviewedSnapshot.GateResult gate) {
        System.out.println("PRE-APPLY SNAPSHOT GATES");
        System.out.println("candidateCount=" + gate.actualCount());
        System.out.println("candidateW=" + gate.wins());
        System.out.println("candidateL=" + gate.losses());
        System.out.println("candidateV=" + gate.voids());
        System.out.println("expectedCount=" + FootballCornersReviewedSnapshot.EXPECTED_COUNT);
        System.out.println("actualCount=" + gate.actualCount());
        System.out.println("COUNT_GATE=" + pass(gate.countGate()));
        System.out.println("expectedSHA256=" + FootballCornersReviewedSnapshot.EXPECTED_SHA256);
        System.out.println("actualSHA256=" + gate.actualSha256());
        System.out.println("HASH_GATE=" + pass(gate.hashGate()));
        System.out.println("approvedLegIdsCount=" + FootballCornersReviewedSnapshot.APPROVED_LEG_IDS.size());
        System.out.println("actualLegIdsCount=" + gate.actualLegIds().size());
        System.out.println("LEG_SET_GATE=" + pass(gate.legSetGate()));
        System.out.println("missingApprovedIds=" + new java.util.TreeSet<>(gate.missingApprovedIds()));
        System.out.println("unexpectedIds=" + new java.util.TreeSet<>(gate.unexpectedIds()));
        System.out.println("secondarySafetyGate=" + pass(gate.secondarySafetyGate()));
        System.out.println("APPLY_READY=" + gate.applyReady());
    }

    private static String pass(boolean value) { return value ? "PASS" : "FAIL"; }

    private static void printApplyResult(FootballSettlementRepository.ApplyResult result) {
        System.out.println("APPLY RESULT");
        System.out.println("updatedLegs=" + result.updatedLegs());
        System.out.println("skippedLegs=" + result.skippedLegs());
        System.out.println("W=" + result.winLegs());
        System.out.println("L=" + result.lossLegs());
        System.out.println("V=" + result.voidLegs());
        System.out.println("updatedBets=" + result.updatedBets());
        System.out.println("betW=" + result.winBets());
        System.out.println("betL=" + result.lossBets());
        System.out.println("betV=" + result.voidBets());
        System.out.println("pendingBets=" + result.pendingBets());
        System.out.println("multiUnverifiedBets=" + result.multiUnverifiedBets());
    }

    @FunctionalInterface
    interface ApplyExecutor {
        FootballSettlementRepository.ApplyResult apply(
                List<FootballSettlementRepository.SettlementUpdate> updates);
    }

    private static boolean eligible(String status) {
        return "FT".equals(status) || "AET".equals(status) || "PEN".equals(status);
    }

    private static boolean requiredKnown(FootballFixtureStatisticCondition condition,
                                         FootballFixtureStatisticsSnapshot snapshot) {
        return switch (condition.subject()) {
            case HOME -> known(snapshot, FootballFixtureStatisticsSnapshot.TeamSide.HOME);
            case AWAY -> known(snapshot, FootballFixtureStatisticsSnapshot.TeamSide.AWAY);
            case MATCH -> known(snapshot, FootballFixtureStatisticsSnapshot.TeamSide.HOME)
                    && known(snapshot, FootballFixtureStatisticsSnapshot.TeamSide.AWAY);
        };
    }

    private static boolean known(FootballFixtureStatisticsSnapshot snapshot,
                                 FootballFixtureStatisticsSnapshot.TeamSide side) {
        return snapshot.value(side, FootballFixtureStatisticType.CORNERS)
                .map(v -> v.status() == FootballFixtureStatisticsSnapshot.ValueStatus.KNOWN)
                .orElse(false);
    }

    private static String corner(FootballFixtureStatisticsSnapshot snapshot,
                                 FootballFixtureStatisticsSnapshot.TeamSide side) {
        return snapshot.value(side, FootballFixtureStatisticType.CORNERS)
                .map(v -> v.status() + (v.value() == null ? "" : "(" + plain(v.value()) + ")"))
                .orElse("MISSING");
    }

    private static String plain(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    private static Reason reason(FootballCornersSyntaxAdapter.Status status) {
        return switch (status) {
            case AMBIGUOUS_PARTICIPANT -> Reason.AMBIGUOUS_PARTICIPANT;
            case UNSUPPORTED_PERIOD -> Reason.UNSUPPORTED_PERIOD;
            case UNSUPPORTED_HANDICAP -> Reason.UNSUPPORTED_HANDICAP;
            case UNSUPPORTED_COMPOSITE -> Reason.UNSUPPORTED_COMPOSITE;
            case UNSUPPORTED_GRAMMAR -> Reason.UNSUPPORTED_GRAMMAR;
            case PARSED, NOT_CORNERS_LIKE -> throw new IllegalArgumentException("Not a rejection: " + status);
        };
    }

    private static void reject(Reason reason, FootballSettlementRepository.Candidate candidate,
                               Map<Reason, Integer> counts, Map<Reason, List<String>> examples) {
        counts.merge(reason, 1, Integer::sum);
        if (examples.get(reason).size() < EXAMPLES_PER_REASON) {
            examples.get(reason).add("leg_id=" + candidate.legId() + " fixture=" + candidate.fixtureId()
                    + " tip_title=" + candidate.tipTitle());
        }
    }

    private enum Reason {
        MISSING_SNAPSHOT("missingSnapshot"), UNSUPPORTED_FIXTURE("unsupportedFixture"),
        ABSENT_STATISTIC("absentStatistic"), AMBIGUOUS_PARTICIPANT("ambiguousParticipant"),
        UNSUPPORTED_PERIOD("unsupportedPeriod"), UNSUPPORTED_HANDICAP("unsupportedHandicap"),
        UNSUPPORTED_COMPOSITE("unsupportedComposite"), UNSUPPORTED_GRAMMAR("unsupportedGrammar");
        private final String label;
        Reason(String label) { this.label = label; }
    }

    private static final class SignedCounters {
        private int syntaxMatches;
        private int parsedDecisions;
        private int wins;
        private int losses;
        private int voids;
        private int unsupportedFixture;
        private int absentStatistic;
        private int ambiguousParticipant;

        private void decision(SettlementDecision decision) {
            parsedDecisions++;
            if (decision == SettlementDecision.W) wins++;
            else if (decision == SettlementDecision.L) losses++;
            else if (decision == SettlementDecision.V) voids++;
        }
    }
}
