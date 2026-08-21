package pl.zagranietyper;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.repository.FootballSettlementRepository;
import java.util.*;import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

final class FootballDeterministicGoalsReviewedSnapshotTest {
 @Test void immutableBoundaryIsExact(){assertEquals(58,FootballDeterministicGoalsReviewedSnapshot.APPROVED_IDS.size());assertEquals("00586bfc1e4083a9181d4753d0de70770ff3260d44f07ed7c561799930ceb90b",FootballDeterministicGoalsReviewedSnapshot.EXPECTED_SHA256);}
 @Test void dryRunAndFailedGateNeverWrite(){AtomicInteger calls=new AtomicInteger();assertFalse(FootballDeterministicGoalsCoverageDryRunMain.parseApply(new String[0]));assertNull(FootballDeterministicGoalsCoverageDryRunMain.applyIfRequested(false,gate(true),List.of(),u->{calls.incrementAndGet();return success();}));assertThrows(IllegalStateException.class,()->FootballDeterministicGoalsCoverageDryRunMain.applyIfRequested(true,gate(false),List.of(),u->{calls.incrementAndGet();return success();}));assertEquals(0,calls.get());}
 @Test void successfulGateDelegatesToExactApply(){AtomicInteger calls=new AtomicInteger();var result=FootballDeterministicGoalsCoverageDryRunMain.applyIfRequested(true,gate(true),List.of(),u->{calls.incrementAndGet();return success();});assertEquals(1,calls.get());assertEquals(58,result.updatedLegs());}
 private static FootballDeterministicGoalsReviewedSnapshot.Gate gate(boolean p){return new FootballDeterministicGoalsReviewedSnapshot.Gate(58,38,20,0,"hash",Set.of(),Set.of(),p,p,p,p);}
 private static FootballSettlementRepository.ApplyResult success(){return new FootballSettlementRepository.ApplyResult(58,0,38,20,0,50,20,20,0,10,0);}
}
