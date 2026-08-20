package pl.zagranietyper.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ParsedPost(
        long wpPostId,
        long wpAuthorId,
        String slug,
        String title,
        String url,
        Instant publishedAt,
        Instant modifiedAt,
        String rawHtml,
        String contentHash,
        List<Map<String, String>> rawMetadataBlocks,
        List<ParsedBet> bets
) {
}