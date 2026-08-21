package pl.zagranietyper;

import com.fasterxml.jackson.databind.ObjectMapper;
import pl.zagranietyper.config.ApiFootballConfig;
import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.fixture.ApiFootballClient;
import pl.zagranietyper.model.FootballFixtureStatisticsSnapshot;
import pl.zagranietyper.repository.Database;
import pl.zagranietyper.repository.FootballFixtureStatisticsBackfillTargetSelector;
import pl.zagranietyper.repository.FootballFixtureStatisticsRepository;
import pl.zagranietyper.service.FootballFixtureStatisticsBackfillRunner;
import pl.zagranietyper.service.FootballFixtureStatisticsIngestionService;
import pl.zagranietyper.service.FootballFixtureStatisticsNormalizer;

public final class FootballFixtureStatisticsBackfillMain {
    private static final int PREVIEW_SIZE = 20;

    private FootballFixtureStatisticsBackfillMain() {}

    public static void main(String[] args) {
        CliOptions options = parseArgs(args);
        ObjectMapper mapper = new ObjectMapper();
        Database database = new Database(AppConfig.fromEnvironment());
        var selector = new FootballFixtureStatisticsBackfillTargetSelector(database);
        var repository = new FootballFixtureStatisticsRepository(database, mapper);

        FootballFixtureStatisticsBackfillRunner.FixtureIngestor ingestor;
        if (options.run()) {
            var service = new FootballFixtureStatisticsIngestionService(
                    new ApiFootballClient(ApiFootballConfig.fromEnvironment(), mapper),
                    repository, new FootballFixtureStatisticsNormalizer());
            ingestor = service::ingest;
        } else {
            ingestor = fixtureId -> {
                throw new IllegalStateException("DRY_RUN must not ingest fixture " + fixtureId);
            };
        }

        var runner = new FootballFixtureStatisticsBackfillRunner(
                selector::findTargets, repository::loadStatus, ingestor, System.out);
        var audit = runner.audit();
        printAudit(audit, options);
        if (!options.run()) return;

        var summary = runner.run(new FootballFixtureStatisticsBackfillRunner.RunOptions(
                options.effectiveLimit(), options.retryIncomplete()));
        printSummary(summary);
    }

    static CliOptions parseArgs(String[] args) {
        boolean run = false;
        boolean all = false;
        boolean retry = false;
        Integer limit = null;
        for (String arg : args == null ? new String[0] : args) {
            if ("--run".equals(arg)) run = true;
            else if ("--all".equals(arg)) all = true;
            else if ("--retry-incomplete".equals(arg)) retry = true;
            else if (arg.startsWith("--limit=")) {
                if (limit != null) throw new IllegalArgumentException("--limit may be supplied once");
                try {
                    limit = Integer.valueOf(arg.substring("--limit=".length()));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid --limit: " + arg, e);
                }
                if (limit <= 0) throw new IllegalArgumentException("--limit must be positive");
            } else throw new IllegalArgumentException("Unknown argument: " + arg);
        }
        if (all && limit != null) throw new IllegalArgumentException("Use either --all or --limit, not both");
        if (run && !all && limit == null) {
            throw new IllegalArgumentException("--run requires --limit=N or the explicit --all flag");
        }
        if (!run && (all || limit != null || retry)) {
            throw new IllegalArgumentException("--all, --limit and --retry-incomplete require --run");
        }
        return new CliOptions(run, all, retry, limit);
    }

    static void printAudit(FootballFixtureStatisticsBackfillRunner.Audit audit, CliOptions options) {
        System.out.println("Zagranie Typer — FOOTBALL FIXTURE STATISTICS BACKFILL");
        System.out.println("MODE=" + (options.run() ? "RUN" : "DRY_RUN"));
        if (!options.run()) {
            System.out.println("NO API REQUESTS");
            System.out.println("NO DATABASE WRITES");
        }
        System.out.println("totalTargets=" + audit.totalTargets());
        System.out.println("alreadyComplete=" + audit.alreadyComplete());
        System.out.println("needsFetch=" + audit.needsFetch());
        System.out.println("untouched=" + audit.untouched());
        for (FootballFixtureStatisticsSnapshot.FetchStatus status
                : FootballFixtureStatisticsSnapshot.FetchStatus.values()) {
            System.out.println("existing." + status + "=" + audit.statusCounts().get(status));
        }
        System.out.println("firstTargetFixtureIds=" + audit.fixtureIds().stream()
                .limit(PREVIEW_SIZE).toList());
        System.out.println("estimatedRequestCount=" + audit.needsFetch());
    }

    static void printSummary(FootballFixtureStatisticsBackfillRunner.Summary s) {
        System.out.println("FINAL SUMMARY");
        System.out.println("totalTargets=" + s.totalTargets());
        System.out.println("alreadyComplete=" + s.alreadyComplete());
        System.out.println("attempted=" + s.attempted());
        System.out.println("complete=" + s.complete());
        System.out.println("partial=" + s.partial());
        System.out.println("unsupported=" + s.unsupported());
        System.out.println("unavailable=" + s.unavailable());
        System.out.println("fetchFailed=" + s.fetchFailed());
        System.out.println("apiError=" + s.apiError());
        System.out.println("parseError=" + s.parseError());
        System.out.println("skippedComplete=" + s.skippedComplete());
        System.out.println("skippedIncomplete=" + s.skippedIncomplete());
        System.out.println("remainingUntouched=" + s.remainingUntouched());
        System.out.println("requestsAttempted=" + s.requestsAttempted());
        System.out.println("elapsedMillis=" + s.elapsedMillis());
        System.out.println("unknownApiLabels=" + s.unknownLabels());
    }

    record CliOptions(boolean run, boolean all, boolean retryIncomplete, Integer limit) {
        int effectiveLimit() { return all ? Integer.MAX_VALUE : limit; }
    }
}
