package pl.zagranietyper.repository;
import java.sql.*;import java.util.*;import java.util.regex.Pattern;

/** Current pending fixtures whose markets can require event chronology or event participants. */
public final class FootballFixtureEventsTargetSelector {
 private static final Pattern EVENT_MARKET=Pattern.compile("pierwszy gol|pierwsza bram|1[.]? gol|1[.]? bram|wynik po [0-9]+ minut|strzeli.{0,35}(gola|bramk)|gol [\\p{L}]|asyst|otrzyma.{0,20}(kart|upomn)|zobaczy.{0,20}(kart|upomn)|ukarany|kartka dla");
 private final Database database;public FootballFixtureEventsTargetSelector(Database database){this.database=database;}
 public List<Long>findTargets(){String sql="SELECT f.fixture_id,lower(bl.tip_title) title FROM bet_legs bl JOIN bets b ON b.id=bl.bet_id AND b.active JOIN api_football_fixtures f ON f.fixture_id=bl.resolved_external_event_id::bigint WHERE bl.active AND bl.resolved_provider='API_FOOTBALL'AND bl.settlement_status='PENDING'AND bl.settlement_source='NONE'AND f.status_short IN('FT','AET','PEN') ORDER BY f.fixture_id";try(Connection c=database.openConnection()){c.setReadOnly(true);try(var ps=c.prepareStatement(sql);var rs=ps.executeQuery()){TreeSet<Long>ids=new TreeSet<>();while(rs.next())if(EVENT_MARKET.matcher(rs.getString("title")).find())ids.add(rs.getLong("fixture_id"));return List.copyOf(ids);}}catch(SQLException e){throw new IllegalStateException("Could not select fixture-event targets",e);}}
}
