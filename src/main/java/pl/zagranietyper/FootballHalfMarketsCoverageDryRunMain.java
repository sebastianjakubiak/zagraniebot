package pl.zagranietyper;

import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.model.*;
import pl.zagranietyper.parser.FootballHalfMarketSyntaxAdapter;
import pl.zagranietyper.repository.*;
import pl.zagranietyper.service.UnifiedFootballSettlementEngine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Read-only coverage for deterministic first/second-half score markets. */
public final class FootballHalfMarketsCoverageDryRunMain {
    private FootballHalfMarketsCoverageDryRunMain(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=0)throw new IllegalArgumentException("DRY_RUN only");
        var repository=new FootballSettlementRepository(new Database(AppConfig.fromEnvironment()));var parser=new FootballHalfMarketSyntaxAdapter();var engine=new UnifiedFootballSettlementEngine();
        int eligible=0,like=0,raw=0,parsed=0,w=0,l=0,v=0;EnumMap<FootballHalfMarketSyntaxAdapter.Status,Integer> rejected=new EnumMap<>(FootballHalfMarketSyntaxAdapter.Status.class);EnumMap<FootballHalfMarketSyntaxAdapter.Family,Integer> families=new EnumMap<>(FootballHalfMarketSyntaxAdapter.Family.class);Map<FootballHalfMarketSyntaxAdapter.Family,List<String>> examples=new EnumMap<>(FootballHalfMarketSyntaxAdapter.Family.class);List<Row> rows=new ArrayList<>();
        for(var c:repository.findPendingApiFootballCandidates()){
            if(!Set.of("FT","AET","PEN").contains(c.statusShort()))continue;eligible++;var p=parser.parse(c.tipTitle(),c.homeTeam(),c.awayTeam());if(p.status()==FootballHalfMarketSyntaxAdapter.Status.NOT_HALF_LIKE)continue;like++;
            boolean valid=c.fulltimeHome()!=null&&c.fulltimeAway()!=null&&c.halftimeHome()!=null&&c.halftimeAway()!=null&&c.fulltimeHome()>=c.halftimeHome()&&c.fulltimeAway()>=c.halftimeAway();if(valid)raw++;
            if(!p.parsed()){rejected.merge(p.status(),1,Integer::sum);continue;}if(!valid){rejected.merge(FootballHalfMarketSyntaxAdapter.Status.UNSUPPORTED_GRAMMAR,1,Integer::sum);continue;}
            var snapshot=FootballScoreSnapshot.fullTimeAndFirstHalf(new FootballScore(c.fulltimeHome(),c.fulltimeAway()),new FootballScore(c.halftimeHome(),c.halftimeAway()));var decision=engine.settle(p.market(),snapshot);if(decision==SettlementDecision.UNSUPPORTED){rejected.merge(FootballHalfMarketSyntaxAdapter.Status.UNSUPPORTED_GRAMMAR,1,Integer::sum);continue;}
            parsed++;if(decision==SettlementDecision.W)w++;else if(decision==SettlementDecision.L)l++;else v++;families.merge(p.family(),1,Integer::sum);examples.computeIfAbsent(p.family(),x->new ArrayList<>());if(examples.get(p.family()).size()<2)examples.get(p.family()).add(c.tipTitle());var condition=p.market().conditions().getFirst();rows.add(new Row(c.legId(),p.normalized(),condition.period(),condition,decision));System.out.println("PARSED leg_id="+c.legId()+" title="+c.tipTitle()+" condition="+condition+" period="+condition.period()+" decision="+decision);
        }
        int rejectedCount=rejected.values().stream().mapToInt(Integer::intValue).sum();System.out.println("AUDIT");families.forEach((f,n)->System.out.println(f+"="+n+" examples="+examples.get(f)));System.out.println("totalEligible="+eligible);System.out.println("halfLike="+like);System.out.println("rawScoreDataAvailable="+raw);System.out.println("parsed="+parsed);System.out.println("W="+w);System.out.println("L="+l);System.out.println("V="+v);System.out.println("rejected="+rejectedCount);rejected.forEach((s,n)->System.out.println("rejected."+s+"="+n));if(like!=parsed+rejectedCount)throw new IllegalStateException("Counts do not reconcile");var sorted=rows.stream().sorted(Comparator.comparingLong(Row::legId)).toList();System.out.println("sortedLegIds="+sorted.stream().map(Row::legId).toList());System.out.println("snapshotSHA256="+sha256(sorted));
    }
    static String canonical(Row r){return r.legId()+"|period="+r.period()+"|condition="+r.condition()+"|decision="+r.decision();}
    static String sha256(List<Row> rows)throws Exception{String payload=rows.stream().map(FootballHalfMarketsCoverageDryRunMain::canonical).reduce("",(a,b)->a+b+"\n");return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));}
    record Row(long legId,String normalized,FootballScorePeriod period,UnifiedFootballMarket.Condition condition,SettlementDecision decision){}
}
