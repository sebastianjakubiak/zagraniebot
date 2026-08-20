package pl.zagranietyper.model;

import java.time.Instant;

public record EventResolutionCandidate(
        long legId,
        long betId,
        BetType betType,
        int betLegCount,

        long wpPostId,
        Instant publishedAt,

        String postTitle,
        String tipTitle,

        String heading,
        String previousText,

        String sourceEventExternalId,
        String sourceHome,
        String sourceAway,
        String sourceCompetition,
        Instant sourceStartAt
) {
}