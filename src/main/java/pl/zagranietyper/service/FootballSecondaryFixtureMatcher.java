package pl.zagranietyper.service;

import pl.zagranietyper.provider.FootballPeriodStatisticsProvider;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public final class FootballSecondaryFixtureMatcher {
    public Result match(Fixture fixture,List<FootballPeriodStatisticsProvider.Event> events){
        var matches=events.stream().filter(FootballPeriodStatisticsProvider.Event::completed)
                .filter(e->Duration.between(fixture.kickoff(),e.kickoff()).abs().compareTo(Duration.ofMinutes(10))<=0)
                .filter(e->norm(fixture.home()).equals(norm(e.home()))&&norm(fixture.away()).equals(norm(e.away())))
                .filter(e->Objects.equals(fixture.homeScore(),e.homeScore())&&Objects.equals(fixture.awayScore(),e.awayScore())).toList();
        return matches.size()==1?new Result(Status.MAPPED,matches.getFirst()):
                new Result(matches.isEmpty()?Status.UNRESOLVED:Status.AMBIGUOUS,null);
    }
    static String norm(String s){return Normalizer.normalize(s.replace('ł','l').replace('Ł','L'),Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+","").trim();}
    public enum Status{MAPPED,UNRESOLVED,AMBIGUOUS}
    public record Fixture(long id,Instant kickoff,String home,String away,Integer homeScore,Integer awayScore){}
    public record Result(Status status,FootballPeriodStatisticsProvider.Event event){public boolean mapped(){return status==Status.MAPPED;}}
}
