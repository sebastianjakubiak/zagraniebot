package pl.zagranietyper.model;

import java.time.Instant;import java.util.List;

/** Raw lossless fields needed from one /fixtures/events response. */
public record ApiFootballFixtureEventsResponse(long fixtureId,Status status,Integer httpStatus,String errorMessage,String rawJson,Instant fetchedAt,List<Event>events){
 public ApiFootballFixtureEventsResponse{events=List.copyOf(events);}
 public enum Status{SUCCESS,EMPTY,FETCH_FAILED,API_ERROR,PARSE_ERROR}
 public record Event(int sourceIndex,Long teamId,String teamName,Long playerId,String playerName,Long assistPlayerId,String assistName,String type,String detail,Integer minute,Integer extraMinute,String comments){}
}
