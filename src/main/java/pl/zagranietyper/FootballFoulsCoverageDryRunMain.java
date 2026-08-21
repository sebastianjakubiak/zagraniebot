package pl.zagranietyper;

import com.fasterxml.jackson.databind.ObjectMapper;
import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.model.*;
import pl.zagranietyper.parser.FootballFoulsSyntaxAdapter;
import pl.zagranietyper.repository.Database;
import pl.zagranietyper.repository.FootballFixtureStatisticsRepository;
import pl.zagranietyper.repository.FootballSettlementRepository;
import pl.zagranietyper.service.FootballFixtureStatisticSettlementEngine;

import java.math.BigDecimal;
import java.util.*;

/** Read-only global coverage and exact-snapshot report for full-time fouls markets. */
public final class FootballFoulsCoverageDryRunMain {
    private FootballFoulsCoverageDryRunMain() {}

    public static void main(String[] args) {
        boolean apply = parseApplyFlag(args);
        var database = new Database(AppConfig.fromEnvironment());
        var candidates = new FootballSettlementRepository(database).findPendingApiFootballCandidates();
        var statistics = new FootballFixtureStatisticsRepository(database, new ObjectMapper());
        var adapter = new FootballFoulsSyntaxAdapter();
        var engine = new FootballFixtureStatisticSettlementEngine();
        EnumMap<FootballFoulsSyntaxAdapter.Category, Integer> categories =
                new EnumMap<>(FootballFoulsSyntaxAdapter.Category.class);
        EnumMap<Reason, Integer> reasons = new EnumMap<>(Reason.class);
        for (Reason reason : Reason.values()) reasons.put(reason, 0);
        List<FootballFoulsReviewedSnapshot.Candidate> reviewedCandidates = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int totalEligible=0, foulsLike=0, fullTimeFoulsLike=0, rawDataAvailableFullTime=0;
        int wins=0, losses=0, voids=0;

        System.out.println("Zagranie Typer — GLOBAL FOULS COVERAGE");
        System.out.println("MODE="+(apply?"APPLY":"DRY_RUN"));
        if (!apply) System.out.println("NO SETTLEMENT WRITES");
        System.out.println("PARSED RECORDS");
        for (var candidate : candidates) {
            if (!eligible(candidate.statusShort())) continue;
            totalEligible++;
            var parsed = adapter.parse(candidate.tipTitle(), candidate.homeTeam(), candidate.awayTeam());
            if (parsed.status() == FootballFoulsSyntaxAdapter.Status.NOT_FOULS_LIKE) continue;
            foulsLike++;
            Optional<FootballFixtureStatisticsSnapshot> snapshot = Optional.empty();
            if (parsed.status() != FootballFoulsSyntaxAdapter.Status.UNSUPPORTED_PERIOD) {
                fullTimeFoulsLike++;
                snapshot = statistics.load(candidate.fixtureId());
                if (snapshot.isPresent() && snapshot.get().status() == FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE
                        && known(snapshot.get(), FootballFixtureStatisticsSnapshot.TeamSide.HOME)
                        && known(snapshot.get(), FootballFixtureStatisticsSnapshot.TeamSide.AWAY)) {
                    rawDataAvailableFullTime++;
                }
            }
            if (!parsed.parsed()) {
                reject(reason(parsed.status()), candidate);
                reasons.merge(reason(parsed.status()), 1, Integer::sum);
                continue;
            }
            if (snapshot.isEmpty()) {
                reject(Reason.MISSING_SNAPSHOT, candidate); reasons.merge(Reason.MISSING_SNAPSHOT, 1, Integer::sum); continue;
            }
            if (snapshot.get().status() == FootballFixtureStatisticsSnapshot.FetchStatus.UNSUPPORTED) {
                reject(Reason.UNSUPPORTED_FIXTURE, candidate); reasons.merge(Reason.UNSUPPORTED_FIXTURE, 1, Integer::sum); continue;
            }
            if (snapshot.get().status() != FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE) {
                reject(Reason.MISSING_SNAPSHOT, candidate); reasons.merge(Reason.MISSING_SNAPSHOT, 1, Integer::sum); continue;
            }
            if (!requiredKnown(parsed.condition(), snapshot.get())) {
                reject(Reason.ABSENT_STATISTIC, candidate); reasons.merge(Reason.ABSENT_STATISTIC, 1, Integer::sum); continue;
            }
            SettlementDecision decision = engine.settle(parsed.condition(), snapshot.get());
            if (decision == SettlementDecision.UNSUPPORTED) {
                reject(Reason.ABSENT_STATISTIC, candidate); reasons.merge(Reason.ABSENT_STATISTIC, 1, Integer::sum); continue;
            }
            if (!seen.add(candidate.legId())) throw new IllegalStateException("Duplicate parsed leg: " + candidate.legId());
            categories.merge(parsed.category(), 1, Integer::sum);
            if (decision == SettlementDecision.W) wins++; else if (decision == SettlementDecision.L) losses++; else voids++;
            reviewedCandidates.add(new FootballFoulsReviewedSnapshot.Candidate(
                    candidate.legId(), candidate.betId(), parsed.condition(), decision, true));
            System.out.printf("leg_id=%d fixture=%d FOULS_HOME=%s FOULS_AWAY=%s tip_title=%s resolvedSubject=%s parsedCondition=%s decision=%s%n",
                    candidate.legId(), candidate.fixtureId(), value(snapshot.get(), FootballFixtureStatisticsSnapshot.TeamSide.HOME),
                    value(snapshot.get(), FootballFixtureStatisticsSnapshot.TeamSide.AWAY), candidate.tipTitle(),
                    parsed.condition().subject(), parsed.condition(), decision);
        }
        int parsed = wins+losses+voids;
        int rejected = reasons.values().stream().mapToInt(Integer::intValue).sum();
        System.out.println("SUMMARY");
        System.out.println("totalEligible="+totalEligible);
        System.out.println("foulsLike="+foulsLike);
        System.out.println("fullTimeFoulsLike="+fullTimeFoulsLike);
        System.out.println("rawDataAvailableFullTime="+rawDataAvailableFullTime);
        System.out.println("parsed="+parsed);
        System.out.println("W="+wins); System.out.println("L="+losses); System.out.println("V="+voids);
        System.out.println("rejected="+rejected);
        for (Reason reason : Reason.values()) System.out.println(reason.label+"="+reasons.get(reason));
        for (var category : FootballFoulsSyntaxAdapter.Category.values()) {
            System.out.println("parsed."+category+"="+categories.getOrDefault(category,0));
        }
        System.out.println("SNAPSHOT");
        reviewedCandidates.stream().sorted(Comparator.comparingLong(FootballFoulsReviewedSnapshot.Candidate::legId))
                .map(FootballFoulsReviewedSnapshot::canonicalLine).forEach(System.out::println);
        System.out.println("snapshotCount="+reviewedCandidates.size());
        System.out.println("snapshotSHA256="+FootballFoulsReviewedSnapshot.hashCandidates(reviewedCandidates));
        System.out.println("duplicateLegs="+(reviewedCandidates.size()-seen.size()));
        if (foulsLike != parsed+rejected) throw new IllegalStateException("Fouls coverage counts do not reconcile");
        var gate=FootballFoulsReviewedSnapshot.verify(reviewedCandidates);
        printApplyGate(gate);
        applyIfRequested(apply,gate,reviewedCandidates,updates->
                new FootballSettlementRepository(database).applyExact(updates));
    }

    static boolean parseApplyFlag(String[] args) {
        if (args==null||args.length==0) return false;
        if (args.length==1&&"--apply".equals(args[0])) return true;
        throw new IllegalArgumentException("Usage: FootballFoulsCoverageDryRunMain [--apply]");
    }

    static FootballSettlementRepository.ApplyResult applyIfRequested(
            boolean apply, FootballFoulsReviewedSnapshot.GateResult gate,
            List<FootballFoulsReviewedSnapshot.Candidate> candidates, ApplyExecutor executor) {
        if (!apply) return null;
        if (!gate.applyReady()) throw new IllegalStateException("REFUSING APPLY: exact reviewed FOULS snapshot gate failed");
        var updates=candidates.stream().map(candidate->new FootballSettlementRepository.SettlementUpdate(
                candidate.legId(),candidate.betId(),candidate.decision())).toList();
        var result=executor.apply(updates);
        if (result.updatedLegs()!=FootballFoulsReviewedSnapshot.EXPECTED_COUNT||result.skippedLegs()!=0
                ||result.winLegs()!=FootballFoulsReviewedSnapshot.EXPECTED_W
                ||result.lossLegs()!=FootballFoulsReviewedSnapshot.EXPECTED_L
                ||result.voidLegs()!=FootballFoulsReviewedSnapshot.EXPECTED_V) {
            throw new IllegalStateException("FOULS exact apply result mismatch: "+result);
        }
        printApplyResult(result);
        return result;
    }

    private static void printApplyGate(FootballFoulsReviewedSnapshot.GateResult gate) {
        System.out.println("PRE-APPLY SNAPSHOT GATES");
        System.out.println("candidateCount="+gate.actualCount());
        System.out.println("candidateW="+gate.wins()); System.out.println("candidateL="+gate.losses()); System.out.println("candidateV="+gate.voids());
        System.out.println("expectedCount="+FootballFoulsReviewedSnapshot.EXPECTED_COUNT);
        System.out.println("actualCount="+gate.actualCount()); System.out.println("COUNT_GATE="+pass(gate.countGate()));
        System.out.println("expectedSHA256="+FootballFoulsReviewedSnapshot.EXPECTED_SHA256);
        System.out.println("actualSHA256="+gate.actualSha256()); System.out.println("HASH_GATE="+pass(gate.hashGate()));
        System.out.println("approvedLegIdsCount="+FootballFoulsReviewedSnapshot.APPROVED_LEG_IDS.size());
        System.out.println("actualLegIdsCount="+gate.actualLegIds().size()); System.out.println("LEG_SET_GATE="+pass(gate.legSetGate()));
        System.out.println("missingApprovedIds="+new TreeSet<>(gate.missingApprovedIds()));
        System.out.println("unexpectedIds="+new TreeSet<>(gate.unexpectedIds()));
        System.out.println("secondarySafetyGate="+pass(gate.secondarySafetyGate()));
        System.out.println("APPLY_READY="+gate.applyReady());
    }
    private static void printApplyResult(FootballSettlementRepository.ApplyResult result) {
        System.out.println("APPLY RESULT");
        System.out.println("updatedLegs="+result.updatedLegs()); System.out.println("skippedLegs="+result.skippedLegs());
        System.out.println("W="+result.winLegs()); System.out.println("L="+result.lossLegs()); System.out.println("V="+result.voidLegs());
        System.out.println("updatedBets="+result.updatedBets()); System.out.println("betW="+result.winBets());
        System.out.println("betL="+result.lossBets()); System.out.println("betV="+result.voidBets());
        System.out.println("pendingBets="+result.pendingBets()); System.out.println("multiUnverifiedBets="+result.multiUnverifiedBets());
    }
    private static String pass(boolean value){return value?"PASS":"FAIL";}
    @FunctionalInterface interface ApplyExecutor {
        FootballSettlementRepository.ApplyResult apply(List<FootballSettlementRepository.SettlementUpdate> updates);
    }

    private static void reject(Reason reason, FootballSettlementRepository.Candidate candidate) {
        System.out.printf("REJECTED reason=%s leg_id=%d fixture=%d tip_title=%s%n",
                reason.label, candidate.legId(), candidate.fixtureId(), candidate.tipTitle());
    }
    private static boolean eligible(String status) { return "FT".equals(status)||"AET".equals(status)||"PEN".equals(status); }
    private static boolean known(FootballFixtureStatisticsSnapshot s, FootballFixtureStatisticsSnapshot.TeamSide side) {
        return s.value(side, FootballFixtureStatisticType.FOULS).map(v->v.status()==FootballFixtureStatisticsSnapshot.ValueStatus.KNOWN).orElse(false);
    }
    private static boolean requiredKnown(FootballFixtureStatisticCondition c, FootballFixtureStatisticsSnapshot s) {
        return switch(c.subject()) { case HOME -> known(s, FootballFixtureStatisticsSnapshot.TeamSide.HOME);
            case AWAY -> known(s, FootballFixtureStatisticsSnapshot.TeamSide.AWAY);
            case MATCH -> known(s, FootballFixtureStatisticsSnapshot.TeamSide.HOME)&&known(s, FootballFixtureStatisticsSnapshot.TeamSide.AWAY); };
    }
    private static String value(FootballFixtureStatisticsSnapshot s, FootballFixtureStatisticsSnapshot.TeamSide side) {
        return s.value(side, FootballFixtureStatisticType.FOULS).map(v->v.status()+(v.value()==null?"":"("+plain(v.value())+")")).orElse("MISSING");
    }
    private static Reason reason(FootballFoulsSyntaxAdapter.Status status) {
        return switch(status) {
            case AMBIGUOUS_PARTICIPANT -> Reason.AMBIGUOUS_PARTICIPANT;
            case UNSUPPORTED_PERIOD -> Reason.UNSUPPORTED_PERIOD;
            case UNSUPPORTED_PLAYER -> Reason.UNSUPPORTED_PLAYER;
            case UNSUPPORTED_HANDICAP_OR_COMPARISON -> Reason.UNSUPPORTED_HANDICAP_OR_COMPARISON;
            case UNSUPPORTED_COMPOSITE -> Reason.UNSUPPORTED_COMPOSITE;
            case UNSUPPORTED_SIGNED_NOTATION -> Reason.UNSUPPORTED_SIGNED_NOTATION;
            case UNSUPPORTED_GRAMMAR -> Reason.UNSUPPORTED_GRAMMAR;
            case PARSED, NOT_FOULS_LIKE -> throw new IllegalArgumentException("Not a rejection: "+status);
        };
    }
    private static String plain(BigDecimal value) { return value==null?"null":value.stripTrailingZeros().toPlainString(); }
    private enum Reason {
        MISSING_SNAPSHOT("missingSnapshot"), UNSUPPORTED_FIXTURE("unsupportedFixture"), ABSENT_STATISTIC("absentStatistic"),
        AMBIGUOUS_PARTICIPANT("ambiguousParticipant"), UNSUPPORTED_PERIOD("unsupportedPeriod"), UNSUPPORTED_PLAYER("unsupportedPlayer"),
        UNSUPPORTED_HANDICAP_OR_COMPARISON("unsupportedHandicapOrComparison"), UNSUPPORTED_COMPOSITE("unsupportedComposite"),
        UNSUPPORTED_SIGNED_NOTATION("unsupportedSignedNotation"), UNSUPPORTED_GRAMMAR("unsupportedGrammar");
        final String label; Reason(String label){this.label=label;}
    }
}
