package pl.zagranietyper;

import com.fasterxml.jackson.databind.ObjectMapper;
import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.model.*;
import pl.zagranietyper.parser.FootballShotsSyntaxAdapter;
import pl.zagranietyper.repository.*;
import pl.zagranietyper.service.FootballFixtureStatisticSettlementEngine;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Read-only global coverage report for total shots and shots on target. */
public final class FootballShotsCoverageDryRunMain {
    private FootballShotsCoverageDryRunMain() {}
    public static void main(String[] args){
        if(args!=null&&args.length!=0)throw new IllegalArgumentException("Usage: FootballShotsCoverageDryRunMain");
        var db=new Database(AppConfig.fromEnvironment());
        var candidates=new FootballSettlementRepository(db).findPendingApiFootballCandidates();
        var statistics=new FootballFixtureStatisticsRepository(db,new ObjectMapper());
        var adapter=new FootballShotsSyntaxAdapter(); var engine=new FootballFixtureStatisticSettlementEngine();
        var total=new Stats(FootballFixtureStatisticType.SHOTS_TOTAL); var target=new Stats(FootballFixtureStatisticType.SHOTS_ON_TARGET);
        int totalEligible=0; Set<Long> uniqueShotLegs=new HashSet<>();
        System.out.println("Zagranie Typer — GLOBAL SHOTS COVERAGE"); System.out.println("MODE=DRY_RUN"); System.out.println("NO SETTLEMENT WRITES");
        for(var candidate:candidates){
            if(!eligible(candidate.statusShort()))continue; totalEligible++;
            var parsed=adapter.parse(candidate.tipTitle(),candidate.homeTeam(),candidate.awayTeam());
            if(parsed.status()==FootballShotsSyntaxAdapter.Status.NOT_SHOTS_LIKE)continue;
            uniqueShotLegs.add(candidate.legId()); Stats out=parsed.statisticType()==FootballFixtureStatisticType.SHOTS_TOTAL?total:target; out.like++;
            Optional<FootballFixtureStatisticsSnapshot> snapshot=Optional.empty();
            if(parsed.status()!=FootballShotsSyntaxAdapter.Status.UNSUPPORTED_PERIOD){out.fullTimeLike++;snapshot=statistics.load(candidate.fixtureId());if(snapshot.isPresent()&&usableBoth(snapshot.get(),out.type))out.rawAvailable++;}
            out.families.merge(parsed.marketFamily(),1,Integer::sum);
            if(snapshot.isPresent()&&usableBoth(snapshot.get(),out.type))out.familyRaw.merge(parsed.marketFamily(),1,Integer::sum);
            if(!parsed.parsed()){out.reject(reason(parsed.status()),candidate);continue;}
            if(snapshot.isEmpty()){out.reject(Reason.MISSING_SNAPSHOT,candidate);continue;}
            if(snapshot.get().status()==FootballFixtureStatisticsSnapshot.FetchStatus.UNSUPPORTED){out.reject(Reason.UNSUPPORTED_FIXTURE,candidate);continue;}
            if(snapshot.get().status()!=FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE){out.reject(Reason.MISSING_SNAPSHOT,candidate);continue;}
            if(!requiredKnown(parsed.condition(),snapshot.get())){out.reject(Reason.ABSENT_STATISTIC,candidate);continue;}
            SettlementDecision decision=engine.settle(parsed.condition(),snapshot.get());
            if(decision==SettlementDecision.UNSUPPORTED){out.reject(Reason.ABSENT_STATISTIC,candidate);continue;}
            if(!out.seen.add(candidate.legId()))throw new IllegalStateException("Duplicate parsed leg "+candidate.legId());
            out.categories.merge(parsed.category(),1,Integer::sum);out.decision(decision);
            out.snapshot.add(new SnapshotLine(candidate.legId(),parsed.condition(),decision));
            System.out.printf("PARSED type=%s leg_id=%d fixture=%d HOME=%s AWAY=%s tip_title=%s resolvedSubject=%s condition=%s decision=%s%n",
                    out.type,candidate.legId(),candidate.fixtureId(),value(snapshot.get(),out.type,FootballFixtureStatisticsSnapshot.TeamSide.HOME),
                    value(snapshot.get(),out.type,FootballFixtureStatisticsSnapshot.TeamSide.AWAY),candidate.tipTitle(),parsed.condition().subject(),parsed.condition(),decision);
            System.out.printf("SAFETY leg_id=%d suspiciousTokens=%s explanation=%s%n",candidate.legId(),suspicious(candidate.tipTitle()),
                    parsed.syntaxFamily()==FootballShotsSyntaxAdapter.SyntaxFamily.SIGNED_TEAM?"audited signed team threshold; whole title consumed":"canonical statistic wording; whole title consumed");
        }
        System.out.println("totalEligible="+totalEligible);System.out.println("combinedUniqueShotLike="+uniqueShotLegs.size());
        total.print();target.print();
    }
    private static final class Stats{
        final FootballFixtureStatisticType type;int like,fullTimeLike,rawAvailable,w,l,v;Set<Long> seen=new HashSet<>();List<SnapshotLine> snapshot=new ArrayList<>();
        EnumMap<Reason,Integer> reasons=new EnumMap<>(Reason.class);EnumMap<FootballShotsSyntaxAdapter.MarketFamily,Integer> families=new EnumMap<>(FootballShotsSyntaxAdapter.MarketFamily.class);EnumMap<FootballShotsSyntaxAdapter.MarketFamily,Integer> familyRaw=new EnumMap<>(FootballShotsSyntaxAdapter.MarketFamily.class);EnumMap<FootballShotsSyntaxAdapter.Category,Integer> categories=new EnumMap<>(FootballShotsSyntaxAdapter.Category.class);
        Stats(FootballFixtureStatisticType type){this.type=type;for(var r:Reason.values())reasons.put(r,0);}
        void reject(Reason reason,FootballSettlementRepository.Candidate c){reasons.merge(reason,1,Integer::sum);System.out.printf("REJECTED type=%s reason=%s leg_id=%d fixture=%d tip_title=%s%n",type,reason.label,c.legId(),c.fixtureId(),c.tipTitle());}
        void decision(SettlementDecision d){if(d==SettlementDecision.W)w++;else if(d==SettlementDecision.L)l++;else v++;}
        void print(){int parsed=w+l+v,rejected=reasons.values().stream().mapToInt(Integer::intValue).sum();boolean total=type==FootballFixtureStatisticType.SHOTS_TOTAL;System.out.println("SUMMARY "+type);System.out.println((total?"shotsTotalLike":"shotsOnTargetLike")+"="+like);System.out.println((total?"fullTimeShotsTotalLike":"fullTimeShotsOnTargetLike")+"="+fullTimeLike);System.out.println((total?"rawDataAvailableShotsTotal":"rawDataAvailableShotsOnTarget")+"="+rawAvailable);System.out.println("parsed="+parsed);System.out.println("W="+w);System.out.println("L="+l);System.out.println("V="+v);System.out.println("rejected="+rejected);for(var r:Reason.values())System.out.println(r.label+"="+reasons.get(r));for(var f:FootballShotsSyntaxAdapter.MarketFamily.values())System.out.println("family."+f+"="+families.getOrDefault(f,0)+" usableRaw="+familyRaw.getOrDefault(f,0));for(var c:FootballShotsSyntaxAdapter.Category.values())System.out.println("parsed."+c+"="+categories.getOrDefault(c,0));System.out.println("SNAPSHOT "+type);snapshot.stream().sorted(Comparator.comparingLong(SnapshotLine::legId)).map(FootballShotsCoverageDryRunMain::canonical).forEach(System.out::println);System.out.println("snapshotCount="+snapshot.size());System.out.println("snapshotW="+w);System.out.println("snapshotL="+l);System.out.println("snapshotV="+v);System.out.println("sortedLegIds="+snapshot.stream().map(SnapshotLine::legId).sorted().toList());System.out.println("snapshotSHA256="+hash(snapshot));System.out.println("duplicateLegs="+(snapshot.size()-seen.size()));System.out.println("secondarySafetyGate="+(like==parsed+rejected&&snapshot.size()==seen.size()?"PASS":"FAIL"));if(like!=parsed+rejected)throw new IllegalStateException(type+" counts do not reconcile");}
    }
    private static boolean eligible(String s){return "FT".equals(s)||"AET".equals(s)||"PEN".equals(s);}
    private static boolean known(FootballFixtureStatisticsSnapshot s,FootballFixtureStatisticType type,FootballFixtureStatisticsSnapshot.TeamSide side){return s.value(side,type).map(v->v.status()==FootballFixtureStatisticsSnapshot.ValueStatus.KNOWN).orElse(false);}
    private static boolean usableBoth(FootballFixtureStatisticsSnapshot s,FootballFixtureStatisticType type){return s.status()==FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE&&known(s,type,FootballFixtureStatisticsSnapshot.TeamSide.HOME)&&known(s,type,FootballFixtureStatisticsSnapshot.TeamSide.AWAY);}
    private static boolean requiredKnown(FootballFixtureStatisticCondition c,FootballFixtureStatisticsSnapshot s){return switch(c.subject()){case HOME->known(s,c.type(),FootballFixtureStatisticsSnapshot.TeamSide.HOME);case AWAY->known(s,c.type(),FootballFixtureStatisticsSnapshot.TeamSide.AWAY);case MATCH->known(s,c.type(),FootballFixtureStatisticsSnapshot.TeamSide.HOME)&&known(s,c.type(),FootballFixtureStatisticsSnapshot.TeamSide.AWAY);};}
    private static String value(FootballFixtureStatisticsSnapshot s,FootballFixtureStatisticType type,FootballFixtureStatisticsSnapshot.TeamSide side){return s.value(side,type).map(v->v.status()+(v.value()==null?"":"("+plain(v.value())+")")).orElse("MISSING");}
    private static Reason reason(FootballShotsSyntaxAdapter.Status s){return switch(s){case AMBIGUOUS_PARTICIPANT->Reason.AMBIGUOUS_PARTICIPANT;case UNSUPPORTED_PERIOD->Reason.UNSUPPORTED_PERIOD;case UNSUPPORTED_PLAYER->Reason.UNSUPPORTED_PLAYER;case UNSUPPORTED_HANDICAP_OR_COMPARISON->Reason.UNSUPPORTED_HANDICAP_OR_COMPARISON;case UNSUPPORTED_COMPOSITE->Reason.UNSUPPORTED_COMPOSITE;case UNSUPPORTED_SIGNED_NOTATION->Reason.UNSUPPORTED_SIGNED_NOTATION;case UNSUPPORTED_GRAMMAR->Reason.UNSUPPORTED_GRAMMAR;case PARSED,NOT_SHOTS_LIKE->throw new IllegalArgumentException();};}
    private static List<String>suspicious(String t){String n=t.toLowerCase(Locale.ROOT);List<String>x=new ArrayList<>();for(String token:List.of("celn","strza","+"," i ","oraz","gol","wygra","kart","roż","1. poł","2. poł","każdej poł","handicap"))if(n.contains(token))x.add(token.trim());if(n.matches(".*wiecej.*niz.*"))x.add("więcej...niż");return x;}
    private static String canonical(SnapshotLine line){var c=line.condition();return line.legId()+"|type="+c.type()+"|subject="+c.subject()+"|comparison="+c.comparison()+"|threshold="+plain(c.threshold())+"|rangeMaximum="+plain(c.rangeMaximum())+"|decision="+line.decision();}
    private static String hash(List<SnapshotLine> lines){try{var d=MessageDigest.getInstance("SHA-256");String p=lines.stream().sorted(Comparator.comparingLong(SnapshotLine::legId)).map(FootballShotsCoverageDryRunMain::canonical).reduce("",(a,b)->a+b+"\n");return HexFormat.of().formatHex(d.digest(p.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String plain(BigDecimal v){return v==null?"null":v.stripTrailingZeros().toPlainString();}
    private enum Reason{MISSING_SNAPSHOT("missingSnapshot"),UNSUPPORTED_FIXTURE("unsupportedFixture"),ABSENT_STATISTIC("absentStatistic"),AMBIGUOUS_PARTICIPANT("ambiguousParticipant"),UNSUPPORTED_PERIOD("unsupportedPeriod"),UNSUPPORTED_PLAYER("unsupportedPlayer"),UNSUPPORTED_HANDICAP_OR_COMPARISON("unsupportedHandicapOrComparison"),UNSUPPORTED_COMPOSITE("unsupportedComposite"),UNSUPPORTED_SIGNED_NOTATION("unsupportedSignedNotation"),UNSUPPORTED_GRAMMAR("unsupportedGrammar");final String label;Reason(String l){label=l;}}
    private record SnapshotLine(long legId,FootballFixtureStatisticCondition condition,SettlementDecision decision){}
}
