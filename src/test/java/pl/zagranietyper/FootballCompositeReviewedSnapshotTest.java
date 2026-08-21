package pl.zagranietyper;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.*;
import pl.zagranietyper.repository.FootballSettlementRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class FootballCompositeReviewedSnapshotTest {
    @Test void defaultAndFailedGateNeverWrite(){AtomicInteger calls=new AtomicInteger();assertFalse(FootballCompositeCoverageDryRunMain.parseApply(new String[0]));assertNull(FootballCompositeCoverageDryRunMain.applyIfRequested(false,gate(true),List.of(),u->{calls.incrementAndGet();return success();}));assertThrows(IllegalStateException.class,()->FootballCompositeCoverageDryRunMain.applyIfRequested(true,gate(false),List.of(),u->{calls.incrementAndGet();return success();}));assertEquals(0,calls.get());}
    @Test void successfulGateDelegatesToExistingExactApply(){AtomicInteger calls=new AtomicInteger();var result=FootballCompositeCoverageDryRunMain.applyIfRequested(true,gate(true),List.of(),u->{calls.incrementAndGet();return success();});assertEquals(1,calls.get());assertEquals(29,result.updatedLegs());}
    @Test void immutableBoundaryContainsExactApprovedSet(){assertEquals(29,FootballCompositeReviewedSnapshot.APPROVED_IDS.size());assertEquals(Set.of(259L,793L,874L,884L,1761L,1787L,1939L,2348L,2645L,2863L,3374L,7790L,7990L,8900L,9918L,10226L,10749L,11904L,13796L,14281L,14435L,14819L,14910L,15446L,16259L,16261L,18015L,18129L,19730L),FootballCompositeReviewedSnapshot.APPROVED_IDS);assertFalse(FootballCompositeReviewedSnapshot.APPROVED_IDS.contains(2838L));assertFalse(FootballCompositeReviewedSnapshot.APPROVED_IDS.contains(10143L));assertFalse(FootballCompositeReviewedSnapshot.APPROVED_IDS.contains(2949L));}
    @Test void changedConditionCannotPreserveHash() throws Exception {var row=new FootballCompositeCoverageDryRunMain.SnapshotRow(259,1,List.of("x","y"),new FootballCompositeCondition(List.of(score("1.5"),score("2.5"))),List.of(SettlementDecision.W,SettlementDecision.W),SettlementDecision.W);assertNotEquals(FootballCompositeReviewedSnapshot.EXPECTED_SHA256,FootballCompositeCoverageDryRunMain.sha256(List.of(row)));}
    private static FootballCompositeCondition.ScoreBranch score(String line){return new FootballCompositeCondition.ScoreBranch(new UnifiedFootballMarket(List.of(new UnifiedFootballMarket.TotalGoals(UnifiedFootballMarket.GoalSubject.MATCH,FootballScorePeriod.FULL_TIME,UnifiedFootballMarket.TotalDirection.OVER,new BigDecimal(line)))));}
    private static FootballCompositeReviewedSnapshot.Gate gate(boolean pass){return new FootballCompositeReviewedSnapshot.Gate(29,13,16,0,"hash",Set.of(),Set.of(),Set.of(),pass,pass,pass,pass);}
    private static FootballSettlementRepository.ApplyResult success(){return new FootballSettlementRepository.ApplyResult(29,0,13,16,0,25,10,15,0,0,0);}
}
