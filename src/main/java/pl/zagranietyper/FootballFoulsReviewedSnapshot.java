package pl.zagranietyper;

import pl.zagranietyper.model.FootballFixtureStatisticCondition;
import pl.zagranietyper.model.FootballFixtureStatisticType;
import pl.zagranietyper.model.SettlementDecision;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Immutable audit artifact for the manually reviewed FOULS settlement batch. */
public final class FootballFoulsReviewedSnapshot {
    public static final int EXPECTED_COUNT = 19;
    public static final int EXPECTED_W = 12;
    public static final int EXPECTED_L = 7;
    public static final int EXPECTED_V = 0;
    public static final String EXPECTED_SHA256 =
            "65dbdad86e330fc27a6b6f0bd00acc3c44752bfd3b102c4417793260b268c76b";
    public static final Set<Long> APPROVED_LEG_IDS = Set.of(
            214L, 1375L, 2383L, 2942L, 3517L, 3567L, 3686L, 7549L, 7589L,
            7832L, 9010L, 9219L, 9701L, 10604L, 14685L, 15255L, 15579L, 16294L, 18079L);
    public static final Set<Long> REJECTED_LEG_IDS = Set.of(
            565L, 2338L, 7606L, 9465L, 10184L, 10395L, 10658L, 15870L, 16096L, 17219L);

    private FootballFoulsReviewedSnapshot() {}

    public static GateResult verify(List<Candidate> candidates) {
        return verify(candidates, new Boundary(EXPECTED_COUNT, EXPECTED_SHA256, APPROVED_LEG_IDS,
                EXPECTED_W, EXPECTED_L, EXPECTED_V));
    }

    static GateResult verify(List<Candidate> candidates, Boundary boundary) {
        List<Candidate> sorted = candidates.stream().sorted(Comparator.comparingLong(Candidate::legId)).toList();
        Set<Long> actualIds = new HashSet<>();
        boolean unique = sorted.stream().allMatch(candidate -> actualIds.add(candidate.legId()));
        Set<Long> missing = new TreeSet<>(boundary.approvedLegIds()); missing.removeAll(actualIds);
        Set<Long> unexpected = new TreeSet<>(actualIds); unexpected.removeAll(boundary.approvedLegIds());
        int wins=count(sorted, SettlementDecision.W), losses=count(sorted, SettlementDecision.L), voids=count(sorted, SettlementDecision.V);
        String hash = hashCandidates(sorted);
        boolean countGate = sorted.size()==boundary.expectedCount();
        boolean hashGate = hash.equals(boundary.expectedSha256());
        boolean legSetGate = unique && actualIds.equals(boundary.approvedLegIds());
        boolean secondary = unique && wins==boundary.expectedW() && losses==boundary.expectedL() && voids==boundary.expectedV()
                && Collections.disjoint(actualIds, REJECTED_LEG_IDS)
                && !actualIds.contains(15870L)
                && sorted.stream().allMatch(candidate -> candidate.secondarySafe()
                        && candidate.condition().type()==FootballFixtureStatisticType.FOULS
                        && candidate.decision()!=SettlementDecision.UNSUPPORTED);
        return new GateResult(sorted.size(),wins,losses,voids,hash,Set.copyOf(actualIds),Set.copyOf(missing),Set.copyOf(unexpected),
                countGate,hashGate,legSetGate,secondary);
    }

    static String hashCandidates(List<Candidate> candidates) {
        List<String> lines=candidates.stream().sorted(Comparator.comparingLong(Candidate::legId)).map(FootballFoulsReviewedSnapshot::canonicalLine).toList();
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((String.join("\n",lines)+"\n").getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable",e); }
    }

    public static String canonicalLine(Candidate candidate) {
        var c=candidate.condition();
        return candidate.legId()+"|type=FOULS|subject="+c.subject()+"|comparison="+c.comparison()
                +"|threshold="+plain(c.threshold())+"|rangeMaximum="+plain(c.rangeMaximum())+"|decision="+candidate.decision();
    }
    private static int count(List<Candidate> values, SettlementDecision decision) {
        return (int)values.stream().filter(v->v.decision()==decision).count();
    }
    private static String plain(BigDecimal value) { return value==null?"null":value.stripTrailingZeros().toPlainString(); }

    public record Candidate(long legId,long betId,FootballFixtureStatisticCondition condition,
                            SettlementDecision decision,boolean secondarySafe) {}
    record Boundary(int expectedCount,String expectedSha256,Set<Long> approvedLegIds,int expectedW,int expectedL,int expectedV) {
        Boundary { approvedLegIds=Set.copyOf(approvedLegIds); }
    }
    public record GateResult(int actualCount,int wins,int losses,int voids,String actualSha256,
                             Set<Long> actualLegIds,Set<Long> missingApprovedIds,Set<Long> unexpectedIds,
                             boolean countGate,boolean hashGate,boolean legSetGate,boolean secondarySafetyGate) {
        public boolean applyReady(){return countGate&&hashGate&&legSetGate&&secondarySafetyGate;}
    }
}
