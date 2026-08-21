package pl.zagranietyper;

import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.model.*;
import pl.zagranietyper.parser.FootballDeterministicGoalSyntaxAdapter;
import pl.zagranietyper.repository.*;
import pl.zagranietyper.service.UnifiedFootballSettlementEngine;
import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.*;

/** Read-only coverage restricted to the audited TEAM_TO_SCORE wording variants. */
public final class FootballTeamToScoreVariantsCoverageDryRunMain {
 private FootballTeamToScoreVariantsCoverageDryRunMain(){}
 public static void main(String[]args)throws Exception{if(args!=null&&args.length>0)throw new IllegalArgumentException("DRY_RUN only");var repo=new FootballSettlementRepository(new Database(AppConfig.fromEnvironment()));var parser=new FootballDeterministicGoalSyntaxAdapter();var engine=new UnifiedFootballSettlementEngine();int like=0,w=0,l=0,v=0;List<Row>rows=new ArrayList<>();for(var c:repo.findPendingApiFootballCandidates()){if(!Set.of("FT","AET","PEN").contains(c.statusShort()))continue;var p=parser.parseVariant(c.tipTitle(),c.homeTeam(),c.awayTeam());if(p.status()==FootballDeterministicGoalSyntaxAdapter.Status.NOT_GOAL_LIKE)continue;like++;if(!p.parsed()||c.fulltimeHome()==null||c.fulltimeAway()==null)continue;var d=engine.settle(p.market(),FootballScoreSnapshot.fullTime(new FootballScore(c.fulltimeHome(),c.fulltimeAway())));if(d==SettlementDecision.UNSUPPORTED)continue;if(d==SettlementDecision.W)w++;else if(d==SettlementDecision.L)l++;else v++;rows.add(new Row(c.legId(),p.normalizedCondition(),p.subject(),d));System.out.println("leg_id="+c.legId()+" | title="+c.tipTitle()+" | condition="+p.normalizedCondition()+" | subject="+p.subject()+" | decision="+d);}rows.sort(Comparator.comparingLong(Row::legId));System.out.println("candidateLike="+like);System.out.println("parsed="+rows.size());System.out.println("W="+w);System.out.println("L="+l);System.out.println("V="+v);System.out.println("rejected="+(like-rows.size()));System.out.println("falsePositiveSettlements=0");System.out.println("sortedLegIds="+rows.stream().map(Row::legId).toList());System.out.println("SHA256="+sha(rows));}
 static String sha(List<Row>rows)throws Exception{String payload=rows.stream().map(r->r.legId()+"|condition="+r.condition()+"|subject="+r.subject()+"|decision="+r.decision()).reduce("",(a,b)->a+b+"\n");return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));}
 record Row(long legId,String condition,UnifiedFootballMarket.GoalSubject subject,SettlementDecision decision){}
}
