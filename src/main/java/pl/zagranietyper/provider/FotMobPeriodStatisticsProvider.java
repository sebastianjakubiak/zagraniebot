package pl.zagranietyper.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.zagranietyper.model.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class FotMobPeriodStatisticsProvider implements FootballPeriodStatisticsProvider {
    private static final Map<String,FootballFixtureStatisticType> TYPES = Map.ofEntries(
            Map.entry("corners", FootballFixtureStatisticType.CORNERS),
            Map.entry("fouls committed", FootballFixtureStatisticType.FOULS),
            Map.entry("total shots", FootballFixtureStatisticType.SHOTS_TOTAL),
            Map.entry("shots on target", FootballFixtureStatisticType.SHOTS_ON_TARGET),
            Map.entry("shots off target", FootballFixtureStatisticType.SHOTS_OFF_TARGET),
            Map.entry("blocked shots", FootballFixtureStatisticType.BLOCKED_SHOTS),
            Map.entry("offsides", FootballFixtureStatisticType.OFFSIDES),
            Map.entry("saves", FootballFixtureStatisticType.SAVES),
            Map.entry("yellow cards", FootballFixtureStatisticType.YELLOW_CARDS),
            Map.entry("red cards", FootballFixtureStatisticType.RED_CARDS));
    private final HttpClient http; private final ObjectMapper mapper;
    public FotMobPeriodStatisticsProvider(HttpClient http,ObjectMapper mapper){this.http=http;this.mapper=mapper;}
    public String name(){return "FOTMOB";}
    public List<Event> events(LocalDate date)throws Exception{
        JsonNode root=get("https://www.fotmob.com/api/data/matches?date="+date.format(DateTimeFormatter.BASIC_ISO_DATE));
        List<Event> out=new ArrayList<>();
        for(JsonNode league:root.path("leagues"))for(JsonNode e:league.path("matches")){
            var status=e.path("status");
            out.add(new Event(e.path("id").asText(),Instant.parse(status.path("utcTime").asText()),
                    e.path("home").path("name").asText(),e.path("away").path("name").asText(),
                    integer(e.path("home").path("score")),integer(e.path("away").path("score")),
                    status.path("finished").asBoolean(false)));
        }return List.copyOf(out);
    }
    public FootballPeriodStatisticsSnapshot statistics(long fixtureId,String eventId)throws Exception{
        JsonNode root=get("https://www.fotmob.com/api/data/matchDetails?matchId="+eventId);
        List<FootballPeriodStatisticsSnapshot.Value> values=new ArrayList<>();
        JsonNode periods=root.path("content").path("stats").path("Periods");
        read(values,periods.path("All"),FootballPeriodStatisticsSnapshot.Period.FULL_MATCH);
        read(values,periods.path("FirstHalf"),FootballPeriodStatisticsSnapshot.Period.FIRST_HALF);
        read(values,periods.path("SecondHalf"),FootballPeriodStatisticsSnapshot.Period.SECOND_HALF);
        var status=values.isEmpty()?FootballPeriodStatisticsSnapshot.FetchStatus.UNAVAILABLE:
                additiveSafe(values)?FootballPeriodStatisticsSnapshot.FetchStatus.COMPLETE:
                        FootballPeriodStatisticsSnapshot.FetchStatus.PARTIAL;
        return new FootballPeriodStatisticsSnapshot(fixtureId,name(),eventId,status,mapper.writeValueAsString(root),
                Instant.now(),values);
    }
    private void read(List<FootballPeriodStatisticsSnapshot.Value> out,JsonNode node,
                      FootballPeriodStatisticsSnapshot.Period period){
        if(!node.isObject())return;
        for(JsonNode group:node.path("stats"))for(JsonNode stat:group.path("stats")){
            String title=stat.path("title").asText().toLowerCase(Locale.ROOT);
            var type=TYPES.get(title);if(type==null)continue;JsonNode pair=stat.path("stats");
            for(int i=0;i<2;i++){JsonNode n=pair.path(i);var side=i==0?FootballFixtureStatisticsSnapshot.TeamSide.HOME:FootballFixtureStatisticsSnapshot.TeamSide.AWAY;
                var status=n.isNumber()?FootballFixtureStatisticsSnapshot.ValueStatus.KNOWN:
                        n.isMissingNode()||n.isNull()?FootballFixtureStatisticsSnapshot.ValueStatus.ABSENT:FootballFixtureStatisticsSnapshot.ValueStatus.INVALID;
                add(out,new FootballPeriodStatisticsSnapshot.Value(period,type,side,n.isNumber()?n.decimalValue():null,status,stat.path("key").asText(title)));
            }
        }
    }
    private static void add(List<FootballPeriodStatisticsSnapshot.Value> out,FootballPeriodStatisticsSnapshot.Value incoming){
        for(int i=0;i<out.size();i++){var old=out.get(i);if(old.period()==incoming.period()&&old.type()==incoming.type()&&old.side()==incoming.side()){
            if(old.status()==incoming.status()&&Objects.equals(old.value(),incoming.value()))return;
            out.set(i,new FootballPeriodStatisticsSnapshot.Value(incoming.period(),incoming.type(),incoming.side(),null,FootballFixtureStatisticsSnapshot.ValueStatus.INVALID,old.rawKey()+"|"+incoming.rawKey()));return;
        }}out.add(incoming);
    }
    static boolean additiveSafe(List<FootballPeriodStatisticsSnapshot.Value> values){
        for(var type:List.of(FootballFixtureStatisticType.CORNERS,FootballFixtureStatisticType.FOULS,
                FootballFixtureStatisticType.SHOTS_TOTAL,FootballFixtureStatisticType.SHOTS_ON_TARGET))
            for(var side:FootballFixtureStatisticsSnapshot.TeamSide.values()){
                var all=known(values,FootballPeriodStatisticsSnapshot.Period.FULL_MATCH,type,side);
                var first=known(values,FootballPeriodStatisticsSnapshot.Period.FIRST_HALF,type,side);
                var second=known(values,FootballPeriodStatisticsSnapshot.Period.SECOND_HALF,type,side);
                if(all.isPresent()&&first.isPresent()&&second.isPresent()&&all.get().compareTo(first.get().add(second.get()))!=0)return false;
            }
        return true;
    }
    private static Optional<BigDecimal> known(List<FootballPeriodStatisticsSnapshot.Value> v,FootballPeriodStatisticsSnapshot.Period p,FootballFixtureStatisticType t,FootballFixtureStatisticsSnapshot.TeamSide s){return v.stream().filter(x->x.period()==p&&x.type()==t&&x.side()==s&&x.status()==FootballFixtureStatisticsSnapshot.ValueStatus.KNOWN).map(FootballPeriodStatisticsSnapshot.Value::value).findFirst();}
    private JsonNode get(String url)throws Exception{var request=HttpRequest.newBuilder(URI.create(url)).header("User-Agent","Mozilla/5.0").timeout(Duration.ofSeconds(30)).build();var response=http.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)throw new IllegalStateException("HTTP "+response.statusCode());return mapper.readTree(response.body());}
    private static Integer integer(JsonNode n){return n.isInt()?n.intValue():null;}
}
