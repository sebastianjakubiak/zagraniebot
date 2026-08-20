package pl.zagranietyper.service;

import pl.zagranietyper.http.ZagranieClient;
import pl.zagranietyper.model.ParsedPost;
import pl.zagranietyper.parser.ZagraniePostParser;
import pl.zagranietyper.repository.ImportRepository;
import pl.zagranietyper.wp.WpPost;
import pl.zagranietyper.wp.WpPostPage;

import java.time.Instant;
import java.util.logging.Logger;

public final class HistoricalBackfillService {

    private static final Logger LOG =
            Logger.getLogger(
                    HistoricalBackfillService.class.getName()
            );

    private final ZagranieClient client;
    private final ZagraniePostParser parser;
    private final ImportRepository repository;

    public HistoricalBackfillService(
            ZagranieClient client,
            ZagraniePostParser parser,
            ImportRepository repository
    ) {
        this.client = client;
        this.parser = parser;
        this.repository = repository;
    }

    public BackfillResult run(
            long authorId,
            String authorName,
            Instant fromInclusive,
            Instant toExclusive
    ) {
        repository.upsertAuthor(
                authorId,
                authorName
        );

        long runId =
                repository.startImportRun(
                        authorId,
                        fromInclusive,
                        toExclusive
                );

        int page = 1;
        int pagesFetched = 0;
        int postsSeen = 0;
        int postsSaved = 0;
        int betsSaved = 0;
        int legsSaved = 0;

        try {
            while (true) {
                WpPostPage wpPage =
                        client.fetchPostsPage(
                                fromInclusive,
                                toExclusive,
                                page
                        );

                pagesFetched++;

                if (wpPage.posts().isEmpty()) {
                    LOG.info(
                            "Strona "
                                    + page
                                    + " jest pusta. Koniec paginacji."
                    );

                    break;
                }

                LOG.info(
                        "Strona "
                                + page
                                + "/"
                                + wpPage.totalPages()
                                + ", wszystkich postów="
                                + wpPage.posts().size()
                                + (
                                wpPage.totalPosts() >= 0
                                        ? ", łącznie w zakresie="
                                        + wpPage.totalPosts()
                                        : ""
                        )
                );

                int authorPostsOnPage = 0;

                for (WpPost wpPost : wpPage.posts()) {
                    if (
                            wpPost.author()
                                    != authorId
                    ) {
                        continue;
                    }

                    authorPostsOnPage++;
                    postsSeen++;

                    String html =
                            wpPost.renderedContent();

                    if (
                            html == null
                                    || html.isBlank()
                    ) {
                        LOG.warning(
                                "WP post "
                                        + wpPost.id()
                                        + " ma pusty content.rendered. "
                                        + "Pobieram HTML artykułu."
                        );

                        html =
                                client.fetchArticleHtml(
                                        wpPost.link()
                                );
                    }

                    ParsedPost parsedPost =
                            parser.parse(
                                    wpPost,
                                    html
                            );

                    ImportRepository.SaveResult saved =
                            repository.savePostWithBets(
                                    parsedPost
                            );

                    postsSaved++;
                    betsSaved +=
                            saved.betsSaved();

                    legsSaved +=
                            saved.legsSaved();

                    LOG.info(
                            "WP post "
                                    + wpPost.id()
                                    + " | author="
                                    + wpPost.author()
                                    + " | bets="
                                    + saved.betsSaved()
                                    + " | legs="
                                    + saved.legsSaved()
                                    + " | "
                                    + parsedPost.title()
                    );
                }

                LOG.info(
                        "Na stronie "
                                + page
                                + " znaleziono postów autora "
                                + authorId
                                + ": "
                                + authorPostsOnPage
                );

                repository.updateImportRun(
                        runId,
                        pagesFetched,
                        postsSeen,
                        postsSaved,
                        betsSaved,
                        legsSaved
                );

                if (
                        page >= wpPage.totalPages()
                ) {
                    break;
                }

                page++;
            }

            repository.finishImportRunSuccess(
                    runId,
                    pagesFetched,
                    postsSeen,
                    postsSaved,
                    betsSaved,
                    legsSaved
            );

            return new BackfillResult(
                    runId,
                    pagesFetched,
                    postsSeen,
                    postsSaved,
                    betsSaved,
                    legsSaved
            );

        } catch (RuntimeException e) {
            repository.finishImportRunFailed(
                    runId,
                    e.toString()
            );

            throw e;
        }
    }

    public record BackfillResult(
            long runId,
            int pagesFetched,
            int postsSeen,
            int postsSaved,
            int betsSaved,
            int legsSaved
    ) {
    }
}