package pl.zagranietyper.service;

import pl.zagranietyper.http.ZagranieClient;
import pl.zagranietyper.model.ParsedPost;
import pl.zagranietyper.parser.ZagraniePostParser;
import pl.zagranietyper.repository.ImportRepository;
import pl.zagranietyper.wp.WpPost;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class RepairPostsService {

    private static final Logger LOG =
            Logger.getLogger(
                    RepairPostsService.class.getName()
            );

    private final ZagranieClient client;
    private final ZagraniePostParser parser;
    private final ImportRepository importRepository;

    public RepairPostsService(
            ZagranieClient client,
            ZagraniePostParser parser,
            ImportRepository importRepository
    ) {
        this.client =
                client;

        this.parser =
                parser;

        this.importRepository =
                importRepository;
    }

    public RepairResult repair(
            List<Long> postIds
    ) {
        if (
                postIds == null
                        || postIds.isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "Brak postów do naprawy"
            );
        }

        List<RepairedPost> repairedPosts =
                new ArrayList<>();

        int totalBetsSaved =
                0;

        int totalLegsSaved =
                0;

        for (
                Long postId :
                postIds
        ) {
            if (
                    postId == null
                            || postId <= 0
            ) {
                throw new IllegalArgumentException(
                        "Niepoprawny WP post id: "
                                + postId
                );
            }

            LOG.info(
                    "REPAIR POST START"
                            + " | wpPostId="
                            + postId
            );

            WpPost wpPost =
                    client.fetchPost(
                            postId
                    );

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
                throw new IllegalStateException(
                        "Repair post "
                                + postId
                                + " nie zwrócił żadnych betów. "
                                + "Nie zapisuję pustego wyniku."
                );
            }

            ImportRepository.SaveResult saved =
                    importRepository.savePostWithBets(
                            parsedPost
                    );

            totalBetsSaved +=
                    saved.betsSaved();

            totalLegsSaved +=
                    saved.legsSaved();

            repairedPosts.add(
                    new RepairedPost(
                            postId,
                            saved.betsSaved(),
                            saved.legsSaved()
                    )
            );

            LOG.info(
                    "REPAIR POST DONE"
                            + " | wpPostId="
                            + postId
                            + " | bets="
                            + saved.betsSaved()
                            + " | legs="
                            + saved.legsSaved()
            );
        }

        return new RepairResult(
                repairedPosts.size(),
                totalBetsSaved,
                totalLegsSaved,
                List.copyOf(
                        repairedPosts
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

    public record RepairResult(
            int postsSaved,
            int betsSaved,
            int legsSaved,
            List<RepairedPost> posts
    ) {
    }

    public record RepairedPost(
            long wpPostId,
            int betsSaved,
            int legsSaved
    ) {
    }
}