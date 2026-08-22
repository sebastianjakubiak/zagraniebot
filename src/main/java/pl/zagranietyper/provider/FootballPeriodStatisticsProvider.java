package pl.zagranietyper.provider;

import pl.zagranietyper.model.FootballPeriodStatisticsSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface FootballPeriodStatisticsProvider {
    String name();
    List<Event> events(LocalDate date) throws Exception;
    FootballPeriodStatisticsSnapshot statistics(long fixtureId, String eventId) throws Exception;
    record Event(String id, Instant kickoff, String home, String away, Integer homeScore,
                 Integer awayScore, boolean completed) {}
}
