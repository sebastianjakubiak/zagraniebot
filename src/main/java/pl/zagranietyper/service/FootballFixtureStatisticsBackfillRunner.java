package pl.zagranietyper.service;

import pl.zagranietyper.model.FootballFixtureStatisticsSnapshot;

import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class FootballFixtureStatisticsBackfillRunner {
    private final TargetSource targets;
    private final StatusSource statuses;
    private final FixtureIngestor ingestor;
    private final PrintStream out;

    public FootballFixtureStatisticsBackfillRunner(
            TargetSource targets, StatusSource statuses,
            FixtureIngestor ingestor, PrintStream out) {
        this.targets = targets;
        this.statuses = statuses;
        this.ingestor = ingestor;
        this.out = out;
    }

    public Audit audit() {
        List<Long> fixtureIds = targets.findTargets();
        EnumMap<FootballFixtureStatisticsSnapshot.FetchStatus, Integer> counts =
                emptyStatusCounts();
        int untouched = 0;
        for (long fixtureId : fixtureIds) {
            Optional<FootballFixtureStatisticsSnapshot.FetchStatus> status = statuses.status(fixtureId);
            if (status.isPresent()) counts.merge(status.get(), 1, Integer::sum);
            else untouched++;
        }
        int complete = counts.get(FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE);
        return new Audit(fixtureIds, counts, untouched, complete, fixtureIds.size() - complete);
    }

    public Summary run(RunOptions options) {
        Audit audit = audit();
        Instant started = Instant.now();
        MutableSummary result = new MutableSummary(audit.totalTargets(), audit.alreadyComplete());
        int eligibleAttempts = 0;

        for (long fixtureId : audit.fixtureIds()) {
            Optional<FootballFixtureStatisticsSnapshot.FetchStatus> existing = statuses.status(fixtureId);
            if (existing.orElse(null) == FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE) {
                result.skippedComplete++;
                continue;
            }
            if (existing.isPresent() && !options.retryIncomplete()) {
                result.skippedIncomplete++;
                continue;
            }
            if (eligibleAttempts >= options.limit()) {
                result.remainingUntouched++;
                continue;
            }

            eligibleAttempts++;
            result.attempted++;
            result.requestsAttempted++;
            FootballFixtureStatisticsSnapshot snapshot = ingestor.ingest(fixtureId);
            result.increment(snapshot.status());
            snapshot.unknownLabels().forEach(label ->
                    result.unknownLabels.merge(label, 1, Integer::sum));
            progress(result.attempted, options.limit(), fixtureId, snapshot.status(), started);
        }
        result.elapsedMillis = Duration.between(started, Instant.now()).toMillis();
        return result.freeze();
    }

    private void progress(int attempted, int limit, long fixtureId,
                          FootballFixtureStatisticsSnapshot.FetchStatus status, Instant started) {
        out.println("[" + attempted + "/" + limit + "] fixture=" + fixtureId
                + " status=" + status + " elapsed="
                + Duration.between(started, Instant.now()).toMillis() + "ms");
    }

    private static EnumMap<FootballFixtureStatisticsSnapshot.FetchStatus, Integer> emptyStatusCounts() {
        EnumMap<FootballFixtureStatisticsSnapshot.FetchStatus, Integer> counts =
                new EnumMap<>(FootballFixtureStatisticsSnapshot.FetchStatus.class);
        for (var status : FootballFixtureStatisticsSnapshot.FetchStatus.values()) counts.put(status, 0);
        return counts;
    }

    @FunctionalInterface public interface TargetSource { List<Long> findTargets(); }
    @FunctionalInterface public interface StatusSource {
        Optional<FootballFixtureStatisticsSnapshot.FetchStatus> status(long fixtureId);
    }
    @FunctionalInterface public interface FixtureIngestor {
        FootballFixtureStatisticsSnapshot ingest(long fixtureId);
    }

    public record RunOptions(int limit, boolean retryIncomplete) {
        public RunOptions {
            if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        }
    }

    public record Audit(
            List<Long> fixtureIds,
            Map<FootballFixtureStatisticsSnapshot.FetchStatus, Integer> statusCounts,
            int untouched,
            int alreadyComplete,
            int needsFetch) {
        public Audit {
            fixtureIds = List.copyOf(fixtureIds);
            statusCounts = Map.copyOf(statusCounts);
        }
        public int totalTargets() { return fixtureIds.size(); }
    }

    public record Summary(
            int totalTargets, int alreadyComplete, int attempted, int complete, int partial,
            int unsupported, int unavailable, int fetchFailed, int apiError, int parseError,
            int skippedComplete, int skippedIncomplete, int remainingUntouched,
            int requestsAttempted, long elapsedMillis, Map<String, Integer> unknownLabels) {}

    private static final class MutableSummary {
        private final int totalTargets;
        private final int alreadyComplete;
        private int attempted, complete, partial, unsupported, unavailable, fetchFailed,
                apiError, parseError, skippedComplete, skippedIncomplete, remainingUntouched,
                requestsAttempted;
        private long elapsedMillis;
        private final Map<String, Integer> unknownLabels = new TreeMap<>();

        private MutableSummary(int totalTargets, int alreadyComplete) {
            this.totalTargets = totalTargets;
            this.alreadyComplete = alreadyComplete;
        }

        private void increment(FootballFixtureStatisticsSnapshot.FetchStatus status) {
            switch (status) {
                case COMPLETE -> complete++;
                case PARTIAL -> partial++;
                case UNSUPPORTED -> unsupported++;
                case UNAVAILABLE -> unavailable++;
                case FETCH_FAILED -> fetchFailed++;
                case API_ERROR -> apiError++;
                case PARSE_ERROR -> parseError++;
            }
        }

        private Summary freeze() {
            return new Summary(totalTargets, alreadyComplete, attempted, complete, partial,
                    unsupported, unavailable, fetchFailed, apiError, parseError,
                    skippedComplete, skippedIncomplete, remainingUntouched,
                    requestsAttempted, elapsedMillis,
                    Map.copyOf(new LinkedHashMap<>(unknownLabels)));
        }
    }
}
