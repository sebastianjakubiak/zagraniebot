package pl.zagranietyper.service;

import pl.zagranietyper.http.ZagranieClient;
import pl.zagranietyper.model.AuthorIdentity;
import pl.zagranietyper.model.DiscoveredAuthor;
import pl.zagranietyper.parser.AuthorIdentityParser;
import pl.zagranietyper.parser.EditorialTipDetector;
import pl.zagranietyper.repository.AuthorRepository;
import pl.zagranietyper.wp.WpPost;
import pl.zagranietyper.wp.WpPostPage;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class AuthorDiscoveryService {

    private static final Logger LOG =
            Logger.getLogger(
                    AuthorDiscoveryService.class.getName()
            );

    private static final Duration WINDOW_SIZE =
            Duration.ofDays(
                    30
            );

    private static final Duration WINDOW_OVERLAP =
            Duration.ofSeconds(
                    2
            );

    private final ZagranieClient client;
    private final EditorialTipDetector tipDetector;
    private final AuthorIdentityParser identityParser;
    private final AuthorRepository authorRepository;

    public AuthorDiscoveryService(
            ZagranieClient client,
            EditorialTipDetector tipDetector,
            AuthorIdentityParser identityParser,
            AuthorRepository authorRepository
    ) {
        this.client =
                client;

        this.tipDetector =
                tipDetector;

        this.identityParser =
                identityParser;

        this.authorRepository =
                authorRepository;
    }

    public DiscoveryResult discover(
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

        Map<Long, MutableCandidate> candidates =
                new LinkedHashMap<>();

        Set<Long> seenPostIds =
                new HashSet<>();

        int pagesFetched =
                0;

        int postsScanned =
                0;

        int editorialPosts =
                0;

        int editorialLegs =
                0;

        int windowNumber =
                0;

        Instant windowFrom =
                fromInclusive;

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
                    "DISCOVERY WINDOW "
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
                        "DISCOVERY WINDOW "
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

                    String html =
                            fetchPostHtml(
                                    wpPost
                            );

                    int tips =
                            tipDetector
                                    .countEditorialTips(
                                            html
                                    );

                    if (
                            tips <= 0
                    ) {
                        continue;
                    }

                    editorialPosts++;

                    editorialLegs +=
                            tips;

                    MutableCandidate candidate =
                            candidates.computeIfAbsent(
                                    wpPost.author(),
                                    ignored ->
                                            new MutableCandidate(
                                                    wpPost.author(),
                                                    wpPost.id(),
                                                    wpPost.link()
                                            )
                            );

                    candidate.editorialPosts++;

                    candidate.editorialLegs +=
                            tips;

                    LOG.info(
                            "EDITORIAL POST"
                                    + " | wp="
                                    + wpPost.id()
                                    + " | author="
                                    + wpPost.author()
                                    + " | tips="
                                    + tips
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
                    "DISCOVERY WINDOW "
                            + windowNumber
                            + " DONE"
                            + " | uniquePosts="
                            + postsScanned
                            + " | editorialPosts="
                            + editorialPosts
                            + " | editorialLegs="
                            + editorialLegs
                            + " | candidateAuthors="
                            + candidates.size()
            );

            windowFrom =
                    windowTo;
        }

        List<DiscoveredAuthor> discovered =
                new ArrayList<>();

        for (
                MutableCandidate candidate :
                candidates.values()
        ) {
            AuthorIdentity identity =
                    resolveIdentity(
                            candidate
                    );

            String displayName =
                    identity.displayName();

            if (
                    displayName == null
                            || displayName.isBlank()
            ) {
                displayName =
                        "author-"
                                + candidate.authorId;
            }

            DiscoveredAuthor author =
                    new DiscoveredAuthor(
                            candidate.authorId,
                            displayName,
                            identity.slug(),
                            candidate.samplePostId,
                            candidate.samplePostUrl,
                            candidate.editorialPosts,
                            candidate.editorialLegs
                    );

            authorRepository
                    .upsertDiscoveredAuthor(
                            author,
                            fromInclusive,
                            toExclusive
                    );

            discovered.add(
                    author
            );

            LOG.info(
                    "TIPSTER CANDIDATE"
                            + " | id="
                            + author.authorId()
                            + " | name="
                            + author.displayName()
                            + " | slug="
                            + author.slug()
                            + " | posts="
                            + author.editorialPosts()
                            + " | legs="
                            + author.editorialLegs()
            );
        }

        LOG.info(
                "========================================"
        );

        LOG.info(
                "DISCOVERY FINISHED"
                        + " | windows="
                        + windowNumber
                        + " | pages="
                        + pagesFetched
                        + " | postsScanned="
                        + postsScanned
                        + " | editorialPosts="
                        + editorialPosts
                        + " | editorialLegs="
                        + editorialLegs
                        + " | authors="
                        + discovered.size()
        );

        return new DiscoveryResult(
                pagesFetched,
                postsScanned,
                editorialPosts,
                editorialLegs,
                List.copyOf(
                        discovered
                )
        );
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

    private AuthorIdentity resolveIdentity(
            MutableCandidate candidate
    ) {
        try {
            String articleHtml =
                    client.fetchArticleHtml(
                            candidate.samplePostUrl
                    );

            return identityParser.parse(
                    articleHtml,
                    candidate.samplePostUrl
            );

        } catch (
                RuntimeException e
        ) {
            LOG.warning(
                    "Nie udało się ustalić "
                            + "nazwy autora "
                            + candidate.authorId
                            + ": "
                            + e.getMessage()
            );

            return new AuthorIdentity(
                    null,
                    null
            );
        }
    }

    public record DiscoveryResult(
            int pagesFetched,
            int postsScanned,
            int editorialPosts,
            int editorialLegs,
            List<DiscoveredAuthor> authors
    ) {
    }

    private static final class MutableCandidate {

        private final long authorId;
        private final long samplePostId;
        private final String samplePostUrl;

        private int editorialPosts;
        private int editorialLegs;

        private MutableCandidate(
                long authorId,
                long samplePostId,
                String samplePostUrl
        ) {
            this.authorId =
                    authorId;

            this.samplePostId =
                    samplePostId;

            this.samplePostUrl =
                    samplePostUrl;
        }
    }
}