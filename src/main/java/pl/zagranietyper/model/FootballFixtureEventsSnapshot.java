package pl.zagranietyper.model;

import java.time.Instant;import java.util.Comparator;import java.util.List;

/** Canonical fixture-event snapshot; sourceIndex is the stable tie-breaker for chronology. */
public record FootballFixtureEventsSnapshot(long fixtureId,FetchStatus status,String source,Integer httpStatus,String errorMessage,String rawJson,Instant fetchedAt,int parserVersion,List<Event>events){
 public FootballFixtureEventsSnapshot{events=events.stream().sorted(Comparator.comparingInt(Event::sourceIndex)).toList();}
 public enum FetchStatus{COMPLETE,UNSUPPORTED_OR_EMPTY,FETCH_FAILED,API_ERROR,PARSE_ERROR}
 public record Event(int sourceIndex,Long teamId,String teamName,Long playerId,String playerName,Long assistPlayerId,String assistName,String type,String detail,Integer minute,Integer extraMinute,String comments){}
}
