package pl.zagranietyper.wp;

import java.util.List;

public record WpPostPage(
        List<WpPost> posts,
        int page,
        int totalPages,
        int totalPosts
) {
}
