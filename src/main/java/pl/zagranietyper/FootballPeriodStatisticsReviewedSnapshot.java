package pl.zagranietyper;

import pl.zagranietyper.model.SettlementDecision;
import java.util.*;

public final class FootballPeriodStatisticsReviewedSnapshot {
    public static final int EXPECTED_COUNT=2,EXPECTED_W=1,EXPECTED_L=1,EXPECTED_V=0;
    public static final String EXPECTED_SHA256="3a87edce7a29e883f9cd4da7b50240f717c939c8b48967de8b68bd23022b597b";
    public static final Set<Long> APPROVED_IDS=Set.of(1862L,10717L);
    private FootballPeriodStatisticsReviewedSnapshot(){}
    public static Gate verify(List<FootballPeriodStatisticsCoverageDryRunMain.Row> rows,boolean noFalsePositives)throws Exception{
        Set<Long>ids=new HashSet<>();boolean unique=rows.stream().allMatch(r->ids.add(r.legId()));Set<Long>missing=new TreeSet<>(APPROVED_IDS);missing.removeAll(ids);Set<Long>unexpected=new TreeSet<>(ids);unexpected.removeAll(APPROVED_IDS);int w=count(rows,SettlementDecision.W),l=count(rows,SettlementDecision.L),v=count(rows,SettlementDecision.V);String hash=FootballPeriodStatisticsCoverageDryRunMain.sha(rows);return new Gate(rows.size(),w,l,v,hash,Set.copyOf(missing),Set.copyOf(unexpected),rows.size()==EXPECTED_COUNT,EXPECTED_SHA256.equals(hash),unique&&ids.equals(APPROVED_IDS),noFalsePositives&&unique&&w==EXPECTED_W&&l==EXPECTED_L&&v==EXPECTED_V);
    }
    private static int count(List<FootballPeriodStatisticsCoverageDryRunMain.Row> rows,SettlementDecision d){return(int)rows.stream().filter(r->r.decision()==d).count();}
    public record Gate(int count,int w,int l,int v,String sha256,Set<Long>missing,Set<Long>unexpected,boolean countGate,boolean hashGate,boolean legSetGate,boolean safetyGate){public boolean ready(){return countGate&&hashGate&&legSetGate&&safetyGate;}}
}
