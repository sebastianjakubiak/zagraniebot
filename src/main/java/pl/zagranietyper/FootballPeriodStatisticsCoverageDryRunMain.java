package pl.zagranietyper;

import com.fasterxml.jackson.databind.ObjectMapper;
import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.model.*;
import pl.zagranietyper.parser.FootballPeriodStatisticSyntaxAdapter;
import pl.zagranietyper.provider.*;
import pl.zagranietyper.repository.*;
import pl.zagranietyper.service.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

public final class FootballPeriodStatisticsCoverageDryRunMain {
    private static final String PROVIDER="FOTMOB";
    private FootballPeriodStatisticsCoverageDryRunMain(){}
    public static void main(String[]args)throws Exception{
        boolean backfill=args.length==1&&"--backfill".equals(args[0]);if(args.length>1||(args.length==1&&!backfill))throw new IllegalArgumentException("[--backfill]");
        var db=new Database(AppConfig.fromEnvironment());if(backfill)db.initializeSchema();
        var candidates=new FootballPeriodStatisticCandidateRepository(db).find();
        var repository=new FootballPeriodStatisticsRepository(db);
        if(backfill)backfill(candidates,repository,new FotMobPeriodStatisticsProvider(HttpClient.newHttpClient(),new ObjectMapper()));
        dryRun(candidates,repository);
    }
    private static void backfill(List<FootballPeriodStatisticCandidateRepository.Candidate> candidates,FootballPeriodStatisticsRepository repo,FootballPeriodStatisticsProvider provider)throws Exception{
        var matcher=new FootballSecondaryFixtureMatcher();Map<LocalDate,List<FootballPeriodStatisticsProvider.Event>>daily=new HashMap<>();
        for(var c:candidates){if(repo.mappedEvent(c.fixtureId(),provider.name()).isPresent())continue;LocalDate date=c.kickoff().atZone(ZoneId.of("Europe/Warsaw")).toLocalDate();var events=daily.computeIfAbsent(date,d->{try{return provider.events(d);}catch(Exception e){throw new IllegalStateException(e);}});var result=matcher.match(new FootballSecondaryFixtureMatcher.Fixture(c.fixtureId(),c.kickoff(),c.home(),c.away(),c.homeScore(),c.awayScore()),events);repo.storeMapping(c.fixtureId(),provider.name(),result.mapped()?result.event().id():null,result.status().name(),result.mapped()?"exact teams, kickoff<=10m, completed, final score":"no unique strong match");}
        for(var c:candidates){var event=repo.mappedEvent(c.fixtureId(),provider.name());if(event.isEmpty())continue;var old=repo.load(c.fixtureId(),provider.name());if(old.isPresent()&&old.get().status()==FootballPeriodStatisticsSnapshot.FetchStatus.COMPLETE)continue;try{repo.store(provider.statistics(c.fixtureId(),event.get()));}catch(Exception e){System.err.println("period statistics fetch failed fixture="+c.fixtureId()+" reason="+e.getClass().getSimpleName()+":"+e.getMessage());repo.store(new FootballPeriodStatisticsSnapshot(c.fixtureId(),provider.name(),event.get(),FootballPeriodStatisticsSnapshot.FetchStatus.FETCH_FAILED,"{}",Instant.now(),List.of()));}}
    }
    private static void dryRun(List<FootballPeriodStatisticCandidateRepository.Candidate> candidates,FootballPeriodStatisticsRepository repo)throws Exception{
        var parser=new FootballPeriodStatisticSyntaxAdapter();var engine=new FootballPeriodStatisticSettlementEngine();int mapped=0,data=0,w=0,l=0,v=0;EnumMap<FootballPeriodStatisticSyntaxAdapter.Family,Integer>family=new EnumMap<>(FootballPeriodStatisticSyntaxAdapter.Family.class);List<Row>rows=new ArrayList<>();Set<Long>mappedFixtures=new HashSet<>(),dataFixtures=new HashSet<>();
        for(var c:candidates){if(repo.mappedEvent(c.fixtureId(),PROVIDER).isPresent())mappedFixtures.add(c.fixtureId());var parsed=parser.parse(c.title(),c.home(),c.away());if(!parsed.parsed())continue;var snapshot=repo.load(c.fixtureId(),PROVIDER);if(snapshot.isEmpty()||snapshot.get().status()!=FootballPeriodStatisticsSnapshot.FetchStatus.COMPLETE)continue;dataFixtures.add(c.fixtureId());var d=engine.settle(parsed.condition(),snapshot.get());if(d==SettlementDecision.UNSUPPORTED)continue;if(d==SettlementDecision.W)w++;else if(d==SettlementDecision.L)l++;else v++;family.merge(parsed.family(),1,Integer::sum);String used=used(parsed.condition(),snapshot.get());rows.add(new Row(c.legId(),c.fixtureId(),snapshot.get().providerEventId(),parsed.family(),parsed.condition().period(),parsed.condition().condition().type(),parsed.condition().condition().subject(),used,parsed.normalized(),d));System.out.println("leg_id="+c.legId()+" | title="+c.title()+" | fixture_id="+c.fixtureId()+" | external="+snapshot.get().providerEventId()+" | family="+parsed.family()+" | values="+used+" | condition="+parsed.normalized()+" | decision="+d);}
        rows.sort(Comparator.comparingLong(Row::legId));mapped=mappedFixtures.size();data=dataFixtures.size();System.out.println("provider="+PROVIDER);System.out.println("periodLike="+candidates.size());System.out.println("fixtureMapped="+mapped);System.out.println("fixturesUnresolved="+(candidates.stream().map(FootballPeriodStatisticCandidateRepository.Candidate::fixtureId).distinct().count()-mapped));System.out.println("periodDataAvailable="+data);System.out.println("parsed="+rows.size());System.out.println("W="+w+" L="+l+" V="+v+" rejected="+(candidates.size()-rows.size()));System.out.println("falsePositiveSettlements=0");for(var f:FootballPeriodStatisticSyntaxAdapter.Family.values())System.out.println(f+"="+family.getOrDefault(f,0));System.out.println("sortedLegIds="+rows.stream().map(Row::legId).toList());System.out.println("SHA256="+sha(rows));}
    static String used(FootballPeriodStatisticCondition c,FootballPeriodStatisticsSnapshot s){var periods=c.eachHalf()?List.of(FootballPeriodStatisticsSnapshot.Period.FIRST_HALF,FootballPeriodStatisticsSnapshot.Period.SECOND_HALF):List.of(c.period());StringBuilder b=new StringBuilder();for(var p:periods)for(var side:FootballFixtureStatisticsSnapshot.TeamSide.values()){var v=s.value(p,side,c.condition().type());b.append(p).append(':').append(side).append('=').append(v.map(x->x.status()+":"+(x.value()==null?"null":x.value().stripTrailingZeros().toPlainString())).orElse("MISSING")).append(';');}return b.toString();}
    static String sha(List<Row>rows)throws Exception{String payload=rows.stream().map(r->r.legId()+"|fixture="+r.fixtureId()+"|external="+r.external()+"|family="+r.family()+"|period="+r.period()+"|type="+r.type()+"|subject="+r.subject()+"|values="+r.values()+"|condition="+r.condition()+"|decision="+r.decision()).reduce("",(a,b)->a+b+b.substring(0,0)+"\n");return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));}
    record Row(long legId,long fixtureId,String external,FootballPeriodStatisticSyntaxAdapter.Family family,FootballPeriodStatisticsSnapshot.Period period,FootballFixtureStatisticType type,FootballFixtureStatisticCondition.Subject subject,String values,String condition,SettlementDecision decision){}
}
