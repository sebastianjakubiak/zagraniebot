package pl.zagranietyper;

import pl.zagranietyper.model.SettlementDecision;

import java.util.*;

/** Immutable reviewed boundary for the approved generic-composite settlement. */
public final class FootballCompositeReviewedSnapshot {
    public static final int EXPECTED_COUNT=29, EXPECTED_W=13, EXPECTED_L=16, EXPECTED_V=0;
    public static final String EXPECTED_SHA256="482fd76e6b4a542a28477f9c3af98f80f6c4f34768cd3c6fc6acc6cc981e15ab";
    public static final Set<Long> APPROVED_IDS=Set.of(259L,793L,874L,884L,1761L,1787L,1939L,2348L,2645L,2863L,3374L,7790L,7990L,8900L,9918L,10226L,10749L,11904L,13796L,14281L,14435L,14819L,14910L,15446L,16259L,16261L,18015L,18129L,19730L);
    private FootballCompositeReviewedSnapshot(){}

    public static Gate verify(List<FootballCompositeCoverageDryRunMain.SnapshotRow> rows) throws Exception {
        Set<Long> ids=new HashSet<>();boolean unique=rows.stream().allMatch(r->ids.add(r.legId()));
        Set<Long> missing=new TreeSet<>(APPROVED_IDS);missing.removeAll(ids);Set<Long> unexpected=new TreeSet<>(ids);unexpected.removeAll(APPROVED_IDS);
        int w=count(rows,SettlementDecision.W),l=count(rows,SettlementDecision.L),v=count(rows,SettlementDecision.V);
        String hash=FootballCompositeCoverageDryRunMain.sha256(rows.stream().sorted(Comparator.comparingLong(FootballCompositeCoverageDryRunMain.SnapshotRow::legId)).toList());
        boolean count=rows.size()==EXPECTED_COUNT,hashGate=EXPECTED_SHA256.equals(hash),legSet=unique&&ids.equals(APPROVED_IDS);
        boolean safety=unique&&w==EXPECTED_W&&l==EXPECTED_L&&v==EXPECTED_V&&Collections.disjoint(ids,Set.of(2838L,10143L,2949L));
        return new Gate(rows.size(),w,l,v,hash,Set.copyOf(ids),Set.copyOf(missing),Set.copyOf(unexpected),count,hashGate,legSet,safety);
    }
    private static int count(List<FootballCompositeCoverageDryRunMain.SnapshotRow> rows,SettlementDecision d){return(int)rows.stream().filter(r->r.decision()==d).count();}
    public record Gate(int count,int w,int l,int v,String sha256,Set<Long> ids,Set<Long> missing,Set<Long> unexpected,boolean countGate,boolean hashGate,boolean legSetGate,boolean safetyGate){public boolean ready(){return countGate&&hashGate&&legSetGate&&safetyGate;}}
}
