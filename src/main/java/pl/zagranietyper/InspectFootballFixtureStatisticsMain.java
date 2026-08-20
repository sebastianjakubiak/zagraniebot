package pl.zagranietyper;

import com.fasterxml.jackson.databind.ObjectMapper;
import pl.zagranietyper.config.ApiFootballConfig;
import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.fixture.ApiFootballClient;
import pl.zagranietyper.repository.Database;
import pl.zagranietyper.repository.FootballFixtureStatisticsRepository;
import pl.zagranietyper.service.FootballFixtureStatisticsIngestionService;
import pl.zagranietyper.service.FootballFixtureStatisticsNormalizer;

/** Read-only inspection of one fixture. This Main has no persistence option. */
public final class InspectFootballFixtureStatisticsMain {
    private InspectFootballFixtureStatisticsMain() {}

    public static void main(String[] args) {
        if (args == null || args.length != 1) {
            throw new IllegalArgumentException("Usage: InspectFootballFixtureStatisticsMain <fixtureId>");
        }
        long fixtureId = Long.parseLong(args[0]);
        if (fixtureId <= 0) throw new IllegalArgumentException("fixtureId must be positive");

        ObjectMapper mapper = new ObjectMapper();
        var service = new FootballFixtureStatisticsIngestionService(
                new ApiFootballClient(ApiFootballConfig.fromEnvironment(), mapper),
                new FootballFixtureStatisticsRepository(
                        new Database(AppConfig.fromEnvironment()), mapper),
                new FootballFixtureStatisticsNormalizer());
        var snapshot = service.inspect(fixtureId);

        System.out.println("Zagranie Typer — ONE FIXTURE STATISTICS INSPECTION");
        System.out.println("MODE=DRY_RUN");
        System.out.println("NO DATABASE WRITES");
        System.out.println("fixtureId=" + snapshot.fixtureId());
        System.out.println("status=" + snapshot.status());
        System.out.println("returnedTeamCount=" + snapshot.returnedTeamCount());
        System.out.println("error=" + snapshot.errorMessage());
        System.out.println("unknownLabels=" + snapshot.unknownLabels());
        System.out.println("CANONICAL STATISTICS");
        snapshot.values().forEach(value -> System.out.println(
                value.side() + " | teamId=" + value.teamId()
                        + " | " + value.type()
                        + " | status=" + value.status()
                        + " | value=" + value.value()
                        + " | sourceLabel=" + value.sourceLabel()));
    }
}
