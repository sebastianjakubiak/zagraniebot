package pl.zagranietyper.service;

import pl.zagranietyper.fixture.ApiFootballClient;
import pl.zagranietyper.model.FootballFixtureStatisticsSnapshot;
import pl.zagranietyper.repository.FootballFixtureStatisticsRepository;

public final class FootballFixtureStatisticsIngestionService {
    private final ApiFootballClient client;
    private final FootballFixtureStatisticsRepository repository;
    private final FootballFixtureStatisticsNormalizer normalizer;

    public FootballFixtureStatisticsIngestionService(
            ApiFootballClient client,
            FootballFixtureStatisticsRepository repository,
            FootballFixtureStatisticsNormalizer normalizer) {
        this.client = client;
        this.repository = repository;
        this.normalizer = normalizer;
    }

    /** Fetches and normalizes exactly one fixture without persistence. */
    public FootballFixtureStatisticsSnapshot inspect(long fixtureId) {
        var fixture = repository.findFixtureIdentity(fixtureId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Fixture not found or fixture team IDs are unavailable: " + fixtureId));
        return normalizer.normalize(fixture, client.fetchFixtureStatistics(fixtureId));
    }

    /** Single-fixture persistence operation. No caller in Phase A loops over fixtures. */
    public FootballFixtureStatisticsSnapshot ingest(long fixtureId) {
        FootballFixtureStatisticsSnapshot snapshot = inspect(fixtureId);
        repository.store(snapshot);
        return snapshot;
    }
}
