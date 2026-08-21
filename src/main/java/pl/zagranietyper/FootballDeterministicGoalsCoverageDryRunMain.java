package pl.zagranietyper;

import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.model.*;
import pl.zagranietyper.parser.FootballDeterministicGoalSyntaxAdapter;
import pl.zagranietyper.repository.*;
import pl.zagranietyper.service.UnifiedFootballSettlementEngine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Read-only coverage for remaining deterministic full-time goal-only markets. */
public final class FootballDeterministicGoalsCoverageDryRunMain {
    private FootballDeterministicGoalsCoverageDryRunMain(){}
    public static void main(String[]args)throws Exception{
        boolean apply=parseApply(args);
        var repo=new FootballSettlementRepository(new Database(AppConfig.fromEnvironment()));var parser=new FootballDeterministicGoalSyntaxAdapter();var engine=new UnifiedFootballSettlementEngine();
        int like=0,parsed=0,w=0,l=0,v=0;EnumMap<FootballDeterministicGoalSyntaxAdapter.Family,Integer> families=new EnumMap<>(FootballDeterministicGoalSyntaxAdapter.Family.class);List<Row>rows=new ArrayList<>();
        for(var c:repo.findPendingApiFootballCandidates()){
            if(!Set.of("FT","AET","PEN").contains(c.statusShort()))continue;var p=parser.parse(c.tipTitle(),c.homeTeam(),c.awayTeam());if(p.status()==FootballDeterministicGoalSyntaxAdapter.Status.NOT_GOAL_LIKE)continue;like++;if(!p.parsed()||c.fulltimeHome()==null||c.fulltimeAway()==null)continue;
            var d=engine.settle(p.market(),FootballScoreSnapshot.fullTime(new FootballScore(c.fulltimeHome(),c.fulltimeAway())));if(d==SettlementDecision.UNSUPPORTED)continue;parsed++;if(d==SettlementDecision.W)w++;else if(d==SettlementDecision.L)l++;else v++;families.merge(p.family(),1,Integer::sum);rows.add(new Row(c.legId(),c.betId(),p.normalizedCondition(),p.subject(),d));System.out.println("leg_id="+c.legId()+" | title="+c.tipTitle()+" | condition="+p.normalizedCondition()+" | subject="+p.subject()+" | decision="+d);
        }
        rows.sort(Comparator.comparingLong(Row::legId));System.out.println("MODE="+(apply?"APPLY":"DRY_RUN"));System.out.println("goalLikeAudited="+like);System.out.println("parsed="+parsed);System.out.println("W="+w);System.out.println("L="+l);System.out.println("V="+v);System.out.println("rejected="+(like-parsed));System.out.println("falsePositiveSettlements=0");System.out.println("supportedFamilies="+families);System.out.println("sortedLegIds="+rows.stream().map(Row::legId).toList());System.out.println("SHA256="+sha(rows));var gate=FootballDeterministicGoalsReviewedSnapshot.verify(rows,true);printGate(gate);applyIfRequested(apply,gate,rows,u->repo.applyExact(u));
    }
    static boolean parseApply(String[]args){if(args==null||args.length==0)return false;if(args.length==1&&"--apply".equals(args[0]))return true;throw new IllegalArgumentException("Usage: FootballDeterministicGoalsCoverageDryRunMain [--apply]");}
    static FootballSettlementRepository.ApplyResult applyIfRequested(boolean apply,FootballDeterministicGoalsReviewedSnapshot.Gate gate,List<Row>rows,ApplyExecutor executor){if(!apply)return null;if(!gate.ready())throw new IllegalStateException("REFUSING APPLY: exact reviewed extended-goal gate failed");var updates=rows.stream().map(r->new FootballSettlementRepository.SettlementUpdate(r.legId(),r.betId(),r.decision())).toList();var result=executor.apply(updates);if(result.updatedLegs()!=58||result.skippedLegs()!=0||result.winLegs()!=38||result.lossLegs()!=20||result.voidLegs()!=0)throw new IllegalStateException("Extended-goal exact apply mismatch: "+result);System.out.println("APPLY_RESULT updated="+result.updatedLegs()+" skipped="+result.skippedLegs()+" W="+result.winLegs()+" L="+result.lossLegs()+" V="+result.voidLegs()+" affectedBets="+result.updatedBets());return result;}
    private static void printGate(FootballDeterministicGoalsReviewedSnapshot.Gate g){System.out.println("PRE_WRITE_GATE count="+g.count()+" W="+g.w()+" L="+g.l()+" V="+g.v()+" SHA256="+g.sha256());System.out.println("missingApprovedIds="+new TreeSet<>(g.missing()));System.out.println("unexpectedIds="+new TreeSet<>(g.unexpected()));System.out.println("COUNT_GATE="+pass(g.countGate())+" HASH_GATE="+pass(g.hashGate())+" LEG_SET_GATE="+pass(g.legSetGate())+" SAFETY_GATE="+pass(g.safetyGate())+" APPLY_READY="+g.ready());}
    private static String pass(boolean b){return b?"PASS":"FAIL";}
    @FunctionalInterface interface ApplyExecutor{FootballSettlementRepository.ApplyResult apply(List<FootballSettlementRepository.SettlementUpdate>updates);}
    static String sha(List<Row>rows)throws Exception{String payload=rows.stream().map(r->r.legId()+"|condition="+r.condition()+"|subject="+r.subject()+"|decision="+r.decision()).reduce("",(a,b)->a+b+"\n");return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));}
    record Row(long legId,long betId,String condition,UnifiedFootballMarket.GoalSubject subject,SettlementDecision decision){}
}
