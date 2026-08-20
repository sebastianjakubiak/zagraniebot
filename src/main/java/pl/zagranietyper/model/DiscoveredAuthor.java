package pl.zagranietyper.model;

public record DiscoveredAuthor(
        long authorId,
        String displayName,
        String slug,
        long samplePostId,
        String samplePostUrl,
        int editorialPosts,
        int editorialLegs
) {
}