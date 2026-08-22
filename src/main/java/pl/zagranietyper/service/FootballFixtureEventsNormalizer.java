package pl.zagranietyper.service;
import pl.zagranietyper.model.*;import java.util.List;

public final class FootballFixtureEventsNormalizer {
 public static final int PARSER_VERSION=1;
 public FootballFixtureEventsSnapshot normalize(ApiFootballFixtureEventsResponse raw){var status=switch(raw.status()){case SUCCESS->FootballFixtureEventsSnapshot.FetchStatus.COMPLETE;case EMPTY->FootballFixtureEventsSnapshot.FetchStatus.UNSUPPORTED_OR_EMPTY;case FETCH_FAILED->FootballFixtureEventsSnapshot.FetchStatus.FETCH_FAILED;case API_ERROR->FootballFixtureEventsSnapshot.FetchStatus.API_ERROR;case PARSE_ERROR->FootballFixtureEventsSnapshot.FetchStatus.PARSE_ERROR;};List<FootballFixtureEventsSnapshot.Event>events=raw.events().stream().map(e->new FootballFixtureEventsSnapshot.Event(e.sourceIndex(),e.teamId(),e.teamName(),e.playerId(),e.playerName(),e.assistPlayerId(),e.assistName(),e.type(),e.detail(),e.minute(),e.extraMinute(),e.comments())).toList();return new FootballFixtureEventsSnapshot(raw.fixtureId(),status,"API_FOOTBALL",raw.httpStatus(),raw.errorMessage(),raw.rawJson(),raw.fetchedAt(),PARSER_VERSION,events);}
}
