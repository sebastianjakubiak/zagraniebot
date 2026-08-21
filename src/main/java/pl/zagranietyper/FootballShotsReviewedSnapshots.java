package pl.zagranietyper;

import pl.zagranietyper.model.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Two independent immutable reviewed snapshots for the shots settlement phase. */
public final class FootballShotsReviewedSnapshots {
    public static final Boundary SHOTS_TOTAL = new Boundary(
            FootballFixtureStatisticType.SHOTS_TOTAL,23,11,12,0,
            "b565e360b923ac6461df379b18f355e9275005df95663ac10035c01b8013233e",
            Set.of(218L,535L,753L,1910L,2307L,2425L,3484L,7411L,7527L,7577L,7829L,8163L,8530L,8586L,9299L,9590L,10392L,10674L,11063L,11425L,15192L,15615L,16051L));
    public static final Boundary SHOTS_ON_TARGET = new Boundary(
            FootballFixtureStatisticType.SHOTS_ON_TARGET,16,9,7,0,
            "101b71a9c0949a40d7830e307467a64905ece6310c796d84e8dd3a688fcf091b",
            Set.of(899L,1670L,2548L,3566L,8663L,8780L,9094L,9774L,10238L,10617L,10915L,11788L,16655L,16764L,20098L,20113L));
    private FootballShotsReviewedSnapshots() {}

    public static GateResult verify(List<Candidate> candidates,Boundary boundary){
        List<Candidate> sorted=candidates.stream().sorted(Comparator.comparingLong(Candidate::legId)).toList();
        Set<Long> ids=new HashSet<>();boolean unique=sorted.stream().allMatch(c->ids.add(c.legId()));
        Set<Long> missing=new TreeSet<>(boundary.ids());missing.removeAll(ids);Set<Long> unexpected=new TreeSet<>(ids);unexpected.removeAll(boundary.ids());
        int w=count(sorted,SettlementDecision.W),l=count(sorted,SettlementDecision.L),v=count(sorted,SettlementDecision.V);String hash=hash(sorted);
        boolean count=sorted.size()==boundary.count(),hashGate=hash.equals(boundary.sha256()),legSet=unique&&ids.equals(boundary.ids());
        boolean secondary=unique&&w==boundary.w()&&l==boundary.l()&&v==boundary.v()&&sorted.stream().allMatch(c->c.secondarySafe()&&c.condition().type()==boundary.type()&&c.decision()!=SettlementDecision.UNSUPPORTED);
        return new GateResult(sorted.size(),w,l,v,hash,Set.copyOf(ids),Set.copyOf(missing),Set.copyOf(unexpected),count,hashGate,legSet,secondary);
    }
    static String hash(List<Candidate> values){try{var d=MessageDigest.getInstance("SHA-256");String p=values.stream().sorted(Comparator.comparingLong(Candidate::legId)).map(FootballShotsReviewedSnapshots::canonical).reduce("",(a,b)->a+b+"\n");return HexFormat.of().formatHex(d.digest(p.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public static String canonical(Candidate x){var c=x.condition();return x.legId()+"|type="+c.type()+"|subject="+c.subject()+"|comparison="+c.comparison()+"|threshold="+plain(c.threshold())+"|rangeMaximum="+plain(c.rangeMaximum())+"|decision="+x.decision();}
    private static int count(List<Candidate>x,SettlementDecision d){return(int)x.stream().filter(v->v.decision()==d).count();}
    private static String plain(java.math.BigDecimal v){return v==null?"null":v.stripTrailingZeros().toPlainString();}
    public record Boundary(FootballFixtureStatisticType type,int count,int w,int l,int v,String sha256,Set<Long> ids){public Boundary{ids=Set.copyOf(ids);}}
    public record Candidate(long legId,long betId,FootballFixtureStatisticCondition condition,SettlementDecision decision,boolean secondarySafe){}
    public record GateResult(int actualCount,int w,int l,int v,String actualSha256,Set<Long> actualIds,Set<Long> missing,Set<Long> unexpected,boolean countGate,boolean hashGate,boolean legSetGate,boolean secondarySafetyGate){public boolean applyReady(){return countGate&&hashGate&&legSetGate&&secondarySafetyGate;}}
}
