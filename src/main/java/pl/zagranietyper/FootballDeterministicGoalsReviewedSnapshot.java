package pl.zagranietyper;

import pl.zagranietyper.model.SettlementDecision;
import java.util.*;

/** Immutable reviewed boundary for the approved extended full-time goal settlement. */
public final class FootballDeterministicGoalsReviewedSnapshot {
    public static final int EXPECTED_COUNT=58,EXPECTED_W=38,EXPECTED_L=20,EXPECTED_V=0;
    public static final String EXPECTED_SHA256="00586bfc1e4083a9181d4753d0de70770ff3260d44f07ed7c561799930ceb90b";
    public static final Set<Long> APPROVED_IDS=Set.of(140L,229L,317L,508L,520L,810L,818L,824L,960L,997L,1031L,1081L,1122L,1188L,1402L,1450L,1490L,1545L,1560L,1767L,1815L,1843L,1876L,1962L,2045L,2157L,2172L,2239L,2308L,2355L,2362L,2563L,2609L,2636L,3104L,3184L,3225L,3226L,3263L,3370L,3438L,3441L,3581L,7553L,7666L,7777L,7886L,7923L,7931L,8208L,8327L,8446L,8643L,8694L,8930L,9425L,10551L,14501L);
    private FootballDeterministicGoalsReviewedSnapshot(){}
    public static Gate verify(List<FootballDeterministicGoalsCoverageDryRunMain.Row>rows,boolean noFalsePositives)throws Exception{Set<Long>ids=new HashSet<>();boolean unique=rows.stream().allMatch(r->ids.add(r.legId()));Set<Long>missing=new TreeSet<>(APPROVED_IDS);missing.removeAll(ids);Set<Long>unexpected=new TreeSet<>(ids);unexpected.removeAll(APPROVED_IDS);int w=count(rows,SettlementDecision.W),l=count(rows,SettlementDecision.L),v=count(rows,SettlementDecision.V);String hash=FootballDeterministicGoalsCoverageDryRunMain.sha(rows.stream().sorted(Comparator.comparingLong(FootballDeterministicGoalsCoverageDryRunMain.Row::legId)).toList());boolean count=rows.size()==EXPECTED_COUNT,hashGate=EXPECTED_SHA256.equals(hash),legSet=unique&&ids.equals(APPROVED_IDS),safety=noFalsePositives&&unique&&w==EXPECTED_W&&l==EXPECTED_L&&v==EXPECTED_V;return new Gate(rows.size(),w,l,v,hash,Set.copyOf(missing),Set.copyOf(unexpected),count,hashGate,legSet,safety);}
    private static int count(List<FootballDeterministicGoalsCoverageDryRunMain.Row>rows,SettlementDecision d){return(int)rows.stream().filter(r->r.decision()==d).count();}
    public record Gate(int count,int w,int l,int v,String sha256,Set<Long>missing,Set<Long>unexpected,boolean countGate,boolean hashGate,boolean legSetGate,boolean safetyGate){public boolean ready(){return countGate&&hashGate&&legSetGate&&safetyGate;}}
}
