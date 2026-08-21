package pl.zagranietyper;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.*;
import pl.zagranietyper.parser.FootballFoulsSyntaxAdapter;
import pl.zagranietyper.repository.FootballSettlementRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class FootballFoulsReviewedSnapshotTest {
    @Test void exactSnapshotPassesAllGates() {
        var values=sample();
        assertTrue(verify(values,ids(values),3,FootballFoulsReviewedSnapshot.hashCandidates(values)).applyReady());
    }

    @Test void wrongCountAndWrongHashFailIndependently() {
        var values=sample();
        assertFalse(verify(values,ids(values),4,FootballFoulsReviewedSnapshot.hashCandidates(values)).countGate());
        assertFalse(verify(values,ids(values),3,"0".repeat(64)).hashGate());
    }

    @Test void missingUnexpectedAndSameCountDifferentSetFail() {
        var values=sample();
        var missing=verify(values.subList(0,2),ids(values),3,FootballFoulsReviewedSnapshot.hashCandidates(values));
        assertFalse(missing.legSetGate()); assertEquals(Set.of(3L),missing.missingApprovedIds());
        var unexpected=verify(values,Set.of(1L,2L,4L),3,FootballFoulsReviewedSnapshot.hashCandidates(values));
        assertFalse(unexpected.legSetGate()); assertEquals(Set.of(3L),unexpected.unexpectedIds());
        assertEquals(Set.of(4L),unexpected.missingApprovedIds());
    }

    @Test void sameIdsChangedConditionOrDecisionFailsHash() {
        var values=sample(); String hash=FootballFoulsReviewedSnapshot.hashCandidates(values);
        var changed=List.of(values.get(0),values.get(1),candidate(3,SettlementDecision.W,
                FootballFixtureStatisticCondition.Subject.AWAY,"4.5",true));
        var gate=verify(changed,ids(values),3,hash);
        assertTrue(gate.legSetGate()); assertFalse(gate.hashGate());
    }

    @Test void defaultsToDryRunAndRequiresExactApplyFlag() {
        assertFalse(FootballFoulsCoverageDryRunMain.parseApplyFlag(new String[0]));
        assertTrue(FootballFoulsCoverageDryRunMain.parseApplyFlag(new String[]{"--apply"}));
        assertThrows(IllegalArgumentException.class,()->FootballFoulsCoverageDryRunMain.parseApplyFlag(new String[]{"apply"}));
    }

    @Test void dryRunAndFailedGateMakeZeroApplyExactCalls() {
        AtomicInteger calls=new AtomicInteger();
        assertNull(FootballFoulsCoverageDryRunMain.applyIfRequested(false,gate(true),sample(),updates->{calls.incrementAndGet();return success();}));
        assertThrows(IllegalStateException.class,()->FootballFoulsCoverageDryRunMain.applyIfRequested(true,gate(false),sample(),updates->{calls.incrementAndGet();return success();}));
        assertEquals(0,calls.get());
    }

    @Test void successfulGateDelegatesExactlyOnce() {
        AtomicInteger calls=new AtomicInteger();
        var result=FootballFoulsCoverageDryRunMain.applyIfRequested(true,gate(true),sample(),updates->{calls.incrementAndGet();assertEquals(3,updates.size());return success();});
        assertEquals(1,calls.get()); assertEquals(19,result.updatedLegs());
    }

    @Test void unresolvedAndRejectedMarketsCannotEnterApprovedSnapshot() {
        assertFalse(FootballFoulsReviewedSnapshot.APPROVED_LEG_IDS.contains(15870L));
        assertTrue(java.util.Collections.disjoint(FootballFoulsReviewedSnapshot.APPROVED_LEG_IDS,
                FootballFoulsReviewedSnapshot.REJECTED_LEG_IDS));
        var adapter=new FootballFoulsSyntaxAdapter();
        assertEquals(FootballFoulsSyntaxAdapter.Status.AMBIGUOUS_PARTICIPANT,
                adapter.parse("Anglia powyżej 9.5 fauli","Albania","England").status());
        assertEquals(FootballFoulsSyntaxAdapter.Status.UNSUPPORTED_PLAYER,
                adapter.parse("J. Palhinha +1.5 fauli","Portugal","Croatia").status());
        assertEquals(FootballFoulsSyntaxAdapter.Status.UNSUPPORTED_COMPOSITE,
                adapter.parse("Bournemouth +12.5 fauli i +1.5 kartek","Bournemouth","Manchester City").status());
        assertEquals(FootballFoulsSyntaxAdapter.Status.UNSUPPORTED_HANDICAP_OR_COMPARISON,
                adapter.parse("Liczba fauli - 2.drużyna — powyżej 10.5","Newcastle","Arsenal").status());
    }

    private static FootballFoulsReviewedSnapshot.GateResult verify(List<FootballFoulsReviewedSnapshot.Candidate> c,Set<Long> ids,int count,String hash) {
        return FootballFoulsReviewedSnapshot.verify(c,new FootballFoulsReviewedSnapshot.Boundary(count,hash,ids,1,1,1));
    }
    private static List<FootballFoulsReviewedSnapshot.Candidate> sample(){return List.of(
            candidate(1,SettlementDecision.W,FootballFixtureStatisticCondition.Subject.MATCH,"8.5",true),
            candidate(2,SettlementDecision.L,FootballFixtureStatisticCondition.Subject.HOME,"4.5",true),
            candidate(3,SettlementDecision.V,FootballFixtureStatisticCondition.Subject.AWAY,"5",true));}
    private static FootballFoulsReviewedSnapshot.Candidate candidate(long id,SettlementDecision d,FootballFixtureStatisticCondition.Subject s,String threshold,boolean safe) {
        return new FootballFoulsReviewedSnapshot.Candidate(id,id+100,FootballFixtureStatisticCondition.threshold(
                FootballFixtureStatisticType.FOULS,s,FootballFixtureStatisticCondition.Comparison.OVER,new BigDecimal(threshold)),d,safe);
    }
    private static Set<Long> ids(List<FootballFoulsReviewedSnapshot.Candidate> v){return v.stream().map(FootballFoulsReviewedSnapshot.Candidate::legId).collect(java.util.stream.Collectors.toSet());}
    private static FootballFoulsReviewedSnapshot.GateResult gate(boolean pass){return new FootballFoulsReviewedSnapshot.GateResult(19,12,7,0,"hash",Set.of(),Set.of(),Set.of(),pass,pass,pass,pass);}
    private static FootballSettlementRepository.ApplyResult success(){return new FootballSettlementRepository.ApplyResult(19,0,12,7,0,18,10,8,0,0,0);}
}
