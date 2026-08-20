package pl.zagranietyper.service;

import pl.zagranietyper.config.ApiFootballConfig;
import pl.zagranietyper.fixture.ApiFootballClient;
import pl.zagranietyper.fixture.ApiFootballMatcher;
import pl.zagranietyper.model.ApiFootballFixture;
import pl.zagranietyper.model.ApiFootballMatch;
import pl.zagranietyper.model.ApiFootballResolutionCandidate;
import pl.zagranietyper.repository.ApiFootballRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public final class ApiFootballResolutionService {

    private static final ZoneId WARSAW =
            ZoneId.of(
                    "Europe/Warsaw"
            );

    private final ApiFootballConfig config;
    private final ApiFootballClient client;
    private final ApiFootballRepository repository;
    private final ApiFootballMatcher matcher;

    public ApiFootballResolutionService(
            ApiFootballConfig config,
            ApiFootballClient client,
            ApiFootballRepository repository,
            ApiFootballMatcher matcher
    ) {
        this.config = config;
        this.client = client;
        this.repository = repository;
        this.matcher = matcher;
    }

    public Result run() {
        return runCandidates(
                repository.findUnresolvedCandidates()
        );
    }

    public Result run(
            long legId
    ) {
        List<ApiFootballResolutionCandidate> candidates =
                repository.findUnresolvedCandidates()
                        .stream()
                        .filter(
                                candidate ->
                                        candidate.legId()
                                                == legId
                        )
                        .toList();

        return runCandidates(
                candidates
        );
    }

    private Result runCandidates(
            List<ApiFootballResolutionCandidate> candidates
    ) {
        if (
                candidates.isEmpty()
        ) {
            return new Result(
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    null,
                    null
            );
        }

        LocalDate min =
                null;

        LocalDate max =
                null;

        for (
                ApiFootballResolutionCandidate candidate :
                candidates
        ) {
            if (
                    candidate.publishedAt() == null
            ) {
                continue;
            }

            LocalDate publication =
                    candidate.publishedAt()
                            .atZone(
                                    WARSAW
                            )
                            .toLocalDate();

            LocalDate candidateMin =
                    publication.minusDays(
                            config.daysBeforePublication()
                    );

            LocalDate candidateMax =
                    publication.plusDays(
                            config.daysAfterPublication()
                    );

            if (
                    min == null
                            || candidateMin.isBefore(
                            min
                    )
            ) {
                min =
                        candidateMin;
            }

            if (
                    max == null
                            || candidateMax.isAfter(
                            max
                    )
            ) {
                max =
                        candidateMax;
            }
        }

        if (
                min == null
                        || max == null
        ) {
            return new Result(
                    candidates.size(),
                    0,
                    candidates.size(),
                    candidates.size(),
                    0,
                    0,
                    null,
                    null
            );
        }

        int fetchedDays =
                0;

        int cachedDays =
                0;

        Integer dailyRemaining =
                null;

        Integer minuteRemaining =
                null;

        LocalDate date =
                min;

        while (
                !date.isAfter(
                        max
                )
        ) {
            if (
                    repository.isDateComplete(
                            date
                    )
            ) {
                cachedDays++;

                date =
                        date.plusDays(
                                1
                        );

                continue;
            }

            ApiFootballClient.FetchResult fetched =
                    client.fetchDate(
                            date
                    );

            repository.saveFetchedDate(
                    date,
                    fetched.fixtures()
            );

            fetchedDays++;

            dailyRemaining =
                    fetched.dailyRemaining();

            minuteRemaining =
                    fetched.minuteRemaining();

            System.out.println(
                    "FETCH "
                            + date
                            + " fixtures="
                            + fetched.fixtures().size()
                            + " daily="
                            + value(
                            fetched.dailyRemaining()
                    )
                            + "/"
                            + value(
                            fetched.dailyLimit()
                    )
                            + " minute="
                            + value(
                            fetched.minuteRemaining()
                    )
                            + "/"
                            + value(
                            fetched.minuteLimit()
                    )
            );

            date =
                    date.plusDays(
                            1
                    );
        }

        int matched =
                0;

        int unmatched =
                0;

        int incompleteWindow =
                0;

        for (
                ApiFootballResolutionCandidate candidate :
                candidates
        ) {
            if (
                    candidate.publishedAt() == null
            ) {
                unmatched++;
                continue;
            }

            LocalDate publication =
                    candidate.publishedAt()
                            .atZone(
                                    WARSAW
                            )
                            .toLocalDate();

            LocalDate from =
                    publication.minusDays(
                            config.daysBeforePublication()
                    );

            LocalDate to =
                    publication.plusDays(
                            config.daysAfterPublication()
                    );

            if (
                    !repository.isWindowComplete(
                            from,
                            to
                    )
            ) {
                incompleteWindow++;
                continue;
            }

            List<ApiFootballFixture> fixtures =
                    repository.findBetween(
                            from,
                            to
                    );

            ApiFootballMatch match =
                    matcher.match(
                            candidate,
                            fixtures
                    );

            if (
                    match == null
            ) {
                unmatched++;
                continue;
            }

            repository.saveMatch(
                    candidate.legId(),
                    match
            );

            matched++;
        }

        return new Result(
                candidates.size(),
                matched,
                unmatched,
                incompleteWindow,
                fetchedDays,
                cachedDays,
                dailyRemaining,
                minuteRemaining
        );
    }

    private static String value(
            Integer value
    ) {
        return value == null
                ? "?"
                : value.toString();
    }

    public record Result(
            int candidates,

            int matched,
            int unmatched,
            int incompleteWindow,

            int fetchedDays,
            int cachedDays,

            Integer dailyRemaining,
            Integer minuteRemaining
    ) {
    }
}