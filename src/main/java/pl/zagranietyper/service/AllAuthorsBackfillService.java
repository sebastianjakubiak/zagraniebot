package pl.zagranietyper.service;

import pl.zagranietyper.http.ZagranieClient;
import pl.zagranietyper.model.ParsedPost;
import pl.zagranietyper.parser.ZagraniePostParser;
import pl.zagranietyper.repository.AuthorRepository;
import pl.zagranietyper.repository.ImportRepository;
import pl.zagranietyper.wp.WpPost;
import pl.zagranietyper.wp.WpPostPage;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class AllAuthorsBackfillService {

    private static final Logger LOG =
            Logger.getLogger(
                    AllAuthorsBackfillService.class.getName()
            );

    private static final Duration WINDOW_SIZE =
            Duration.ofDays(
                    30
            );

    private static final Duration WINDOW_OVERLAP =
            Duration.ofSeconds(
                    2
            );

    private static final int RESUME_LOG_INTERVAL =
            250;

    private final ZagranieClient client;
    private final ZagraniePostParser parser;
    private final ImportRepository importRepository;
    private final AuthorRepository authorRepository;

    public AllAuthorsBackfillService(
            ZagranieClient client,
            ZagraniePostParser parser,
            ImportRepository importRepository,
            AuthorRepository authorRepository
    ) {
        this.client =
                client;

        this.parser =
                parser;

        this.importRepository =
                importRepository;

        this.authorRepository =
                authorRepository;
    }

    public BackfillAllResult run(
            Instant fromInclusive,
            Instant toExclusive
    ) {
        if (
                !fromInclusive.isBefore(
                        toExclusive
                )
        ) {
            throw new IllegalArgumentException(
                    "fromInclusive musi być przed toExclusive"
            );
        }

        List<AuthorRepository.TipsterCandidate> candidates =
                authorRepository.findTipsterCandidates();

        if (
                candidates.isEmpty()
        ) {
            throw new IllegalStateException(
                    "Brak discovered authors. "
                            + "Najpierw uruchom discover-authors."
            );
        }

        Map<Long, AuthorState> states =
                new LinkedHashMap<>();

        for (
                AuthorRepository.TipsterCandidate candidate :
                candidates
        ) {
            long runId =
                    importRepository.startImportRun(
                            candidate.authorId(),
                            fromInclusive,
                            toExclusive
                    );

            states.put(
                    candidate.authorId(),
                    new AuthorState(
                            candidate,
                            runId
                    )
            );
        }

        /*
         * RESUME:
         *
         * Wczytujemy raz wszystkie posty, które już zostały
         * poprawnie zapisane w poprzednim przebiegu.
         *
         * Dzięki temu podczas ponownego backfillu nadal
         * przechodzimy po lekkim indeksie WordPressa,
         * ale NIE wykonujemy ponownie ciężkiego:
         *
         * /wp-json/wp/v2/posts/{id}?_fields=id,content
         *
         * dla postów, które już mamy.
         *
         * Post, na którym wcześniejszy parser wybuchł,
         * nie został zapisany, więc nie znajdzie się w tym secie
         * i zostanie normalnie pobrany oraz przetworzony.
         */
        Set<Long> existingPostIds =
                new HashSet<>(
                        importRepository.findExistingPostIds()
                );

        LOG.info(
                "BACKFILL ALL RESUME"
                        + " | existingPosts="
                        + existingPostIds.size()
                        + " | existing content GETs będą pomijane"
        );

        Set<Long> seenPostIds =
                new HashSet<>();

        int pagesFetched =
                0;

        int postsScanned =
                0;

        int postsSkippedExisting =
                0;

        int windowNumber =
                0;

        Instant windowFrom =
                fromInclusive;

        try {
            while (
                    windowFrom.isBefore(
                            toExclusive
                    )
            ) {
                windowNumber++;

                Instant windowTo =
                        windowFrom.plus(
                                WINDOW_SIZE
                        );

                if (
                        windowTo.isAfter(
                                toExclusive
                        )
                ) {
                    windowTo =
                            toExclusive;
                }

                Instant requestFrom =
                        windowFrom.equals(
                                fromInclusive
                        )
                                ? windowFrom
                                : windowFrom.minus(
                                WINDOW_OVERLAP
                        );

                Instant requestTo =
                        windowTo.equals(
                                toExclusive
                        )
                                ? windowTo
                                : windowTo.plus(
                                WINDOW_OVERLAP
                        );

                LOG.info(
                        "========================================"
                );

                LOG.info(
                        "BACKFILL ALL WINDOW "
                                + windowNumber
                                + " | logical="
                                + windowFrom
                                + " -> "
                                + windowTo
                                + " | request="
                                + requestFrom
                                + " -> "
                                + requestTo
                );

                int page =
                        1;

                while (
                        true
                ) {
                    WpPostPage wpPage =
                            client.fetchPostsPage(
                                    requestFrom,
                                    requestTo,
                                    page
                            );

                    pagesFetched++;

                    if (
                            wpPage.posts()
                                    .isEmpty()
                    ) {
                        break;
                    }

                    LOG.info(
                            "BACKFILL ALL WINDOW "
                                    + windowNumber
                                    + " | page="
                                    + page
                                    + "/"
                                    + wpPage.totalPages()
                                    + " | indexPosts="
                                    + wpPage.posts()
                                    .size()
                    );

                    for (
                            WpPost wpPost :
                            wpPage.posts()
                    ) {
                        if (
                                !seenPostIds.add(
                                        wpPost.id()
                                )
                        ) {
                            continue;
                        }

                        postsScanned++;

                        AuthorState state =
                                states.get(
                                        wpPost.author()
                                );

                        /*
                         * Dla autora spoza listy tipsterów
                         * nie pobieramy ciężkiego contentu.
                         */
                        if (
                                state == null
                        ) {
                            continue;
                        }

                        state.postsSeen++;

                        /*
                         * RESUME FAST-PATH.
                         *
                         * Jeżeli post już istnieje w bazie,
                         * oznacza to, że poprzedni run:
                         *
                         * 1. pobrał content,
                         * 2. poprawnie go sparsował,
                         * 3. zapisał post + bety + legi
                         *    w jednej transakcji.
                         *
                         * Nie ma powodu ponownie pobierać
                         * ciężkiego HTML-a.
                         */
                        if (
                                existingPostIds.contains(
                                        wpPost.id()
                                )
                        ) {
                            state.postsSkippedExisting++;
                            postsSkippedExisting++;

                            if (
                                    postsSkippedExisting
                                            % RESUME_LOG_INTERVAL
                                            == 0
                            ) {
                                LOG.info(
                                        "BACKFILL ALL RESUME PROGRESS"
                                                + " | skippedExisting="
                                                + postsSkippedExisting
                                                + " | indexPostsScanned="
                                                + postsScanned
                                                + " | currentWpPost="
                                                + wpPost.id()
                                );
                            }

                            continue;
                        }

                        String html =
                                fetchPostHtml(
                                        wpPost
                                );

                        ParsedPost parsedPost =
                                parser.parse(
                                        wpPost,
                                        html
                                );

                        if (
                                parsedPost.bets()
                                        .isEmpty()
                        ) {
                            continue;
                        }

                        ImportRepository.SaveResult saved =
                                importRepository.savePostWithBets(
                                        parsedPost
                                );

                        /*
                         * Od tego momentu również podczas tego samego runa
                         * traktujemy post jako istniejący.
                         */
                        existingPostIds.add(
                                wpPost.id()
                        );

                        state.postsSaved++;

                        state.betsSaved +=
                                saved.betsSaved();

                        state.legsSaved +=
                                saved.legsSaved();

                        LOG.info(
                                "WP "
                                        + wpPost.id()
                                        + " | author="
                                        + state.candidate.displayName()
                                        + " ["
                                        + state.candidate.authorId()
                                        + "]"
                                        + " | bets="
                                        + saved.betsSaved()
                                        + " | legs="
                                        + saved.legsSaved()
                        );
                    }

                    for (
                            AuthorState state :
                            states.values()
                    ) {
                        importRepository.updateImportRun(
                                state.runId,
                                pagesFetched,
                                state.postsSeen,
                                state.postsSaved,
                                state.betsSaved,
                                state.legsSaved
                        );
                    }

                    if (
                            page >=
                                    wpPage.totalPages()
                    ) {
                        break;
                    }

                    page++;
                }

                LOG.info(
                        "BACKFILL ALL WINDOW "
                                + windowNumber
                                + " DONE"
                                + " | pagesTotal="
                                + pagesFetched
                                + " | uniquePostsScanned="
                                + postsScanned
                                + " | skippedExisting="
                                + postsSkippedExisting
                );

                windowFrom =
                        windowTo;
            }

            for (
                    AuthorState state :
                    states.values()
            ) {
                importRepository.finishImportRunSuccess(
                        state.runId,
                        pagesFetched,
                        state.postsSeen,
                        state.postsSaved,
                        state.betsSaved,
                        state.legsSaved
                );
            }

            List<AuthorBackfillResult> authors =
                    states.values()
                            .stream()
                            .map(
                                    state ->
                                            new AuthorBackfillResult(
                                                    state.candidate.authorId(),
                                                    state.candidate.displayName(),
                                                    state.runId,
                                                    state.postsSeen,
                                                    state.postsSaved,
                                                    state.betsSaved,
                                                    state.legsSaved
                                            )
                            )
                            .toList();

            LOG.info(
                    "========================================"
            );

            LOG.info(
                    "BACKFILL ALL FINISHED"
                            + " | windows="
                            + windowNumber
                            + " | pages="
                            + pagesFetched
                            + " | uniquePostsScanned="
                            + postsScanned
                            + " | skippedExisting="
                            + postsSkippedExisting
                            + " | postsSaved="
                            + authors.stream()
                            .mapToInt(
                                    AuthorBackfillResult::postsSaved
                            )
                            .sum()
                            + " | betsSaved="
                            + authors.stream()
                            .mapToInt(
                                    AuthorBackfillResult::betsSaved
                            )
                            .sum()
                            + " | legsSaved="
                            + authors.stream()
                            .mapToInt(
                                    AuthorBackfillResult::legsSaved
                            )
                            .sum()
            );

            return new BackfillAllResult(
                    pagesFetched,
                    postsScanned,
                    authors
            );

        } catch (
                RuntimeException e
        ) {
            for (
                    AuthorState state :
                    states.values()
            ) {
                try {
                    importRepository.finishImportRunFailed(
                            state.runId,
                            e.toString()
                    );

                } catch (
                        RuntimeException ignored
                ) {
                }
            }

            throw e;
        }
    }

    private String fetchPostHtml(
            WpPost wpPost
    ) {
        String html =
                client.fetchPostRenderedContent(
                        wpPost.id()
                );

        if (
                html != null
                        && !html.isBlank()
        ) {
            return html;
        }

        LOG.warning(
                "WP content.rendered pusty dla "
                        + wpPost.id()
                        + ". Pobieram frontend HTML."
        );

        return client.fetchArticleHtml(
                wpPost.link()
        );
    }

    public record BackfillAllResult(
            int pagesFetched,
            int postsScanned,
            List<AuthorBackfillResult> authors
    ) {

        public int totalPostsSaved() {
            return authors.stream()
                    .mapToInt(
                            AuthorBackfillResult::postsSaved
                    )
                    .sum();
        }

        public int totalBetsSaved() {
            return authors.stream()
                    .mapToInt(
                            AuthorBackfillResult::betsSaved
                    )
                    .sum();
        }

        public int totalLegsSaved() {
            return authors.stream()
                    .mapToInt(
                            AuthorBackfillResult::legsSaved
                    )
                    .sum();
        }
    }

    public record AuthorBackfillResult(
            long authorId,
            String displayName,
            long runId,
            int postsSeen,
            int postsSaved,
            int betsSaved,
            int legsSaved
    ) {
    }

    private static final class AuthorState {

        private final AuthorRepository.TipsterCandidate candidate;
        private final long runId;

        private int postsSeen;
        private int postsSaved;
        private int betsSaved;
        private int legsSaved;
        private int postsSkippedExisting;

        private AuthorState(
                AuthorRepository.TipsterCandidate candidate,
                long runId
        ) {
            this.candidate =
                    candidate;

            this.runId =
                    runId;
        }
    }
}