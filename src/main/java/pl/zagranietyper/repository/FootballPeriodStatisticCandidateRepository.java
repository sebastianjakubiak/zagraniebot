package pl.zagranietyper.repository;

import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class FootballPeriodStatisticCandidateRepository {
    private final Database db; public FootballPeriodStatisticCandidateRepository(Database db){this.db=db;}
    public List<Candidate> find(){String q="SELECT bl.id,bl.bet_id,bl.tip_title,f.fixture_id,f.kickoff_at,f.home_team_name,f.away_team_name,f.goals_home,f.goals_away FROM bet_legs bl JOIN bets b ON b.id=bl.bet_id AND b.active JOIN api_football_fixtures f ON f.fixture_id=bl.resolved_external_event_id::bigint WHERE bl.active AND bl.resolved_provider='API_FOOTBALL' AND bl.settlement_status='PENDING' AND bl.settlement_source='NONE' AND f.status_short IN('FT','AET','PEN') AND lower(bl.tip_title)~'(połow|polow)' AND lower(bl.tip_title)~'(roż|rozn|strza|faul|kart|spalon|interwenc|blok)' ORDER BY bl.id";try(Connection c=db.openConnection()){c.setReadOnly(true);try(var p=c.prepareStatement(q);var r=p.executeQuery()){List<Candidate>o=new ArrayList<>();while(r.next())o.add(new Candidate(r.getLong(1),r.getLong(2),r.getString(3),r.getLong(4),r.getTimestamp(5).toInstant(),r.getString(6),r.getString(7),nullable(r,8),nullable(r,9)));return List.copyOf(o);}}catch(SQLException e){throw new IllegalStateException(e);}}
    private static Integer nullable(ResultSet r,int i)throws SQLException{int n=r.getInt(i);return r.wasNull()?null:n;}
    public record Candidate(long legId,long betId,String title,long fixtureId,Instant kickoff,String home,String away,Integer homeScore,Integer awayScore){}
}
