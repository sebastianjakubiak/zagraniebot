package pl.zagranietyper;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.repository.FootballSettlementRepository;
import java.util.*;import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

final class FootballHalfMarketsReviewedSnapshotTest {
 @Test void immutableBoundaryIsExact(){assertEquals(19,FootballHalfMarketsReviewedSnapshot.APPROVED_IDS.size());assertEquals(Set.of(870L,1242L,1406L,2258L,2259L,2260L,2323L,3187L,3208L,8390L,8744L,9162L,10084L,10797L,11544L,11969L,14729L,15855L,17692L),FootballHalfMarketsReviewedSnapshot.APPROVED_IDS);}
 @Test void dryRunAndFailedGateNeverWrite(){AtomicInteger calls=new AtomicInteger();assertFalse(FootballHalfMarketsCoverageDryRunMain.parseApply(new String[0]));assertNull(FootballHalfMarketsCoverageDryRunMain.applyIfRequested(false,gate(true),List.of(),u->{calls.incrementAndGet();return success();}));assertThrows(IllegalStateException.class,()->FootballHalfMarketsCoverageDryRunMain.applyIfRequested(true,gate(false),List.of(),u->{calls.incrementAndGet();return success();}));assertEquals(0,calls.get());}
 @Test void successfulGateDelegatesToExactApply(){AtomicInteger calls=new AtomicInteger();var result=FootballHalfMarketsCoverageDryRunMain.applyIfRequested(true,gate(true),List.of(),u->{calls.incrementAndGet();return success();});assertEquals(1,calls.get());assertEquals(19,result.updatedLegs());}
 private static FootballHalfMarketsReviewedSnapshot.Gate gate(boolean p){return new FootballHalfMarketsReviewedSnapshot.Gate(19,11,8,0,"hash",Set.of(),Set.of(),Set.of(),p,p,p,p);}
 private static FootballSettlementRepository.ApplyResult success(){return new FootballSettlementRepository.ApplyResult(19,0,11,8,0,18,10,8,0,0,0);}
}
