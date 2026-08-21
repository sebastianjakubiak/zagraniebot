package pl.zagranietyper;

import pl.zagranietyper.model.SettlementDecision;

import java.util.*;

/** Immutable reviewed boundary for the approved half-markets settlement. */
public final class FootballHalfMarketsReviewedSnapshot {
    public static final int EXPECTED_COUNT=19,EXPECTED_W=11,EXPECTED_L=8,EXPECTED_V=0;
    public static final String EXPECTED_SHA256="d4a78d4720433b62cb64b6f1444a1b7fdb6d1baf723f6e09eafc3ed9d92a7182";
    public static final Set<Long> APPROVED_IDS=Set.of(870L,1242L,1406L,2258L,2259L,2260L,2323L,3187L,3208L,8390L,8744L,9162L,10084L,10797L,11544L,11969L,14729L,15855L,17692L);
    private FootballHalfMarketsReviewedSnapshot(){}
    public static Gate verify(List<FootballHalfMarketsCoverageDryRunMain.Row> rows)throws Exception{Set<Long>ids=new HashSet<>();boolean unique=rows.stream().allMatch(r->ids.add(r.legId()));Set<Long>missing=new TreeSet<>(APPROVED_IDS);missing.removeAll(ids);Set<Long>unexpected=new TreeSet<>(ids);unexpected.removeAll(APPROVED_IDS);int w=count(rows,SettlementDecision.W),l=count(rows,SettlementDecision.L),v=count(rows,SettlementDecision.V);String hash=FootballHalfMarketsCoverageDryRunMain.sha256(rows.stream().sorted(Comparator.comparingLong(FootballHalfMarketsCoverageDryRunMain.Row::legId)).toList());boolean count=rows.size()==EXPECTED_COUNT,hashGate=EXPECTED_SHA256.equals(hash),legSet=unique&&ids.equals(APPROVED_IDS),safety=unique&&w==EXPECTED_W&&l==EXPECTED_L&&v==EXPECTED_V;return new Gate(rows.size(),w,l,v,hash,Set.copyOf(ids),Set.copyOf(missing),Set.copyOf(unexpected),count,hashGate,legSet,safety);}
    private static int count(List<FootballHalfMarketsCoverageDryRunMain.Row> rows,SettlementDecision d){return(int)rows.stream().filter(r->r.decision()==d).count();}
    public record Gate(int count,int w,int l,int v,String sha256,Set<Long>ids,Set<Long>missing,Set<Long>unexpected,boolean countGate,boolean hashGate,boolean legSetGate,boolean safetyGate){public boolean ready(){return countGate&&hashGate&&legSetGate&&safetyGate;}}
}
