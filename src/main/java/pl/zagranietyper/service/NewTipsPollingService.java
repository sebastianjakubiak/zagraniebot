package pl.zagranietyper.service;

import pl.zagranietyper.http.ZagranieClient;
import pl.zagranietyper.model.ParsedBet;
import pl.zagranietyper.model.ParsedPost;
import pl.zagranietyper.parser.ZagraniePostParser;
import pl.zagranietyper.repository.ImportRepository;
import pl.zagranietyper.wp.WpPost;
import pl.zagranietyper.wp.WpPostPage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

public final class NewTipsPollingService {

    private static final Logger LOG =
            Logger.getLogger(
                    NewTipsPollingService.class.getName()
            );

    private final ZagranieClient client;
    private final ZagraniePostParser parser;
    private final ImportRepository repository;
    private final AllowedAuthors allowedAuthors;
    private final Duration bootstrapLookback;
    private final Duration recentScanLookback;
    private final Duration overlap;
    private final Clock clock;

    public NewTipsPollingService(
            ZagranieClient client,
            ZagraniePostParser parser,
            ImportRepository repository,
            AllowedAuthors allowedAuthors,
            Duration bootstrapLookback,
            Duration recentScanLookback,
            Duration overlap
    ) {
        this(
                client,
                parser,
                repository,
                allowedAuthors,
                bootstrapLookback,
                recentScanLookback,
                overlap,
                Clock.systemUTC()
        );
    }

    NewTipsPollingService(
            ZagranieClient client,
            ZagraniePostParser parser,
            ImportRepository repository,
            AllowedAuthors allowedAuthors,
            Duration bootstrapLookback,
            Duration recentScanLookback,
            Duration overlap,
            Clock clock
    ) {
        this.client = client;
        this.parser = parser;
        this.repository = repository;
        this.allowedAuthors = allowedAuthors;
        this.bootstrapLookback = bootstrapLookback;
        this.recentScanLookback = recentScanLookback;
        this.overlap = overlap;
        this.clock = clock;

        if (
                allowedAuthors == null
                        || allowedAuthors.ids().isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "Brak dozwolonych autorów"
            );
        }

        if (
                bootstrapLookback == null
                        || bootstrapLookback.isNegative()
                        || bootstrapLookback.isZero()
        ) {
            throw new IllegalArgumentException(
                    "bootstrapLookback musi być > 0"
            );
        }

        if (
                recentScanLookback == null
                        || recentScanLookback.isNegative()
                        || recentScanLookback.isZero()
        ) {
            throw new IllegalArgumentException(
                    "recentScanLookback musi być > 0"
            );
        }

        if (
                overlap == null
                        || overlap.isNegative()
        ) {
            throw new IllegalArgumentException(
                    "overlap nie może być ujemny"
            );
        }
    }

    public SyncResult run() {
        return run(
                false
        );
    }

    public SyncResult run(
            boolean dryRun
    ) {
        Instant now =
                clock.instant();

        Instant toExclusive =
                now.plusSeconds(
                        1
                );

        int authorsScanned = 0;
        int pagesFetched = 0;
        int postsSeen = 0;
        int postsProcessed = 0;
        int postsSkippedUnchanged = 0;
        int betsSaved = 0;
        int legsSaved = 0;

        List<DetectedBet> newBets =
                new ArrayList<>();

        for (
                long authorId :
                allowedAuthors.ids()
        ) {
            authorsScanned++;

            Optional<Instant> latestStoredModifiedAt =
                    repository.findLatestModifiedAt(
                            authorId
                    );

            boolean bootstrapRun =
                    latestStoredModifiedAt.isEmpty();

            Instant fromInclusive =
                    computePublishedScanFrom(
                            latestStoredModifiedAt.orElse(
                                    null
                            ),
                            now,
                            bootstrapLookback,
                            recentScanLookback,
                            overlap
                    );

            LOG.info(
                    "LIVE SYNC AUTHOR"
                            + " | author="
                            + authorId
                            + " | from="
                            + fromInclusive
                            + " | to="
                            + toExclusive
            );

            Set<Long> seenPostIds =
                    new HashSet<>();

            int page = 1;

            while (
                    true
            ) {
                WpPostPage wpPage =
                        client.fetchPostsPage(
                                fromInclusive,
                                toExclusive,
                                page
                        );

                pagesFetched++;

                if (
                        wpPage.posts().isEmpty()
                ) {
                    break;
                }

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

                    postsSeen++;

                    if (
                            wpPost.author()
                                    != authorId
                                    || !allowedAuthors.isAllowed(
                                    wpPost.author()
                            )
                    ) {
                        continue;
                    }

                    Instant remoteModifiedAt =
                            WpPostTimes.modifiedAt(
                                    wpPost
                            );

                    Optional<ImportRepository.PostVersion> existingVersion =
                            repository.findPostVersion(
                                    wpPost.id()
                            );

                    if (
                            !shouldProcess(
                                    existingVersion.orElse(
                                            null
                                    ),
                                    remoteModifiedAt
                            )
                    ) {
                        postsSkippedUnchanged++;
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
                            !dryRun
                    ) {
                        repository.ensureAuthorExists(
                                wpPost.author()
                        );
                    }

                    if (
                            parsedPost.bets().isEmpty()
                    ) {
                        if (
                                !dryRun
                        ) {
                            repository.savePostMetadata(
                                    parsedPost
                            );
                        }

                        postsProcessed++;
                        continue;
                    }

                    ImportRepository.LiveSaveResult saved;

                    if (
                            dryRun
                    ) {
                        saved =
                                new ImportRepository.LiveSaveResult(
                                        0,
                                        0,
                                        repository.findNewBetOrdinals(
                                                parsedPost
                                        )
                                );

                    } else {
                        saved =
                                repository.savePostWithBetsPreservingIdentity(
                                        parsedPost
                                );
                    }

                    postsProcessed++;
                    betsSaved +=
                            saved.betsSaved();
                    legsSaved +=
                            saved.legsSaved();

                    Set<Integer> newOrdinals =
                            Set.copyOf(
                                    saved.newBetOrdinals()
                            );

                    for (
                            ParsedBet bet :
                            parsedPost.bets()
                    ) {
                        if (
                                newOrdinals.contains(
                                        bet.ordinal()
                                )
                        ) {
                            newBets.add(
                                    new DetectedBet(
                                            parsedPost.wpPostId(),
                                            parsedPost.url(),
                                            parsedPost.title(),
                                            bet,
                                            bootstrapRun
                                    )
                            );
                        }
                    }
                }

                if (
                        page >=
                                wpPage.totalPages()
                ) {
                    break;
                }

                page++;
            }
        }

        return new SyncResult(
                authorsScanned,
                pagesFetched,
                postsSeen,
                postsProcessed,
                postsSkippedUnchanged,
                betsSaved,
                legsSaved,
                List.copyOf(
                        newBets
                )
        );
    }

    static Instant computePublishedScanFrom(
            Instant latestStoredModifiedAt,
            Instant now,
            Duration bootstrapLookback,
            Duration recentScanLookback,
            Duration overlap
    ) {
        Instant recentWindowFrom =
                now.minus(
                        recentScanLookback
                );

        if (
                latestStoredModifiedAt == null
        ) {
            return now.minus(
                    bootstrapLookback
            );
        }

        Instant catchupFrom =
                latestStoredModifiedAt.minus(
                        overlap
                );

        return catchupFrom.isBefore(
                recentWindowFrom
        )
                ? catchupFrom
                : recentWindowFrom;
    }

    static boolean shouldProcess(
            ImportRepository.PostVersion existing,
            Instant remoteModifiedAt
    ) {
        if (
                existing == null
        ) {
            return true;
        }

        if (
                remoteModifiedAt == null
                        || existing.modifiedAt() == null
        ) {
            return true;
        }

        return remoteModifiedAt.isAfter(
                existing.modifiedAt()
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

    public record DetectedBet(
            long wpPostId,
            String articleUrl,
            String articleTitle,
            ParsedBet bet,
            boolean bootstrap
    ) {
    }

    public record SyncResult(
            int authorsScanned,
            int pagesFetched,
            int postsSeen,
            int postsProcessed,
            int postsSkippedUnchanged,
            int betsSaved,
            int legsSaved,
            List<DetectedBet> newBets
    ) {
        public SyncResult {
            newBets = newBets == null
                    ? List.of()
                    : List.copyOf(
                            newBets
                    );
        }
    }
}
