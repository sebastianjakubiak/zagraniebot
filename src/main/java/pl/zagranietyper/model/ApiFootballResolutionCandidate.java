package pl.zagranietyper.model;

import java.time.Instant;
import java.time.LocalDate;

public record ApiFootballResolutionCandidate(
        long legId,
        long wpPostId,

        BetType betType,
        int betLegCount,

        Instant publishedAt,

        ResolvedSport sport,

        String postTitle,
        String tipTitle,
        String heading,
        String previousText,

        ResolutionSource currentResolutionSource,
        ResolutionConfidence currentResolutionConfidence,

        String resolvedEventName,
        String resolvedParticipantA,
        String resolvedParticipantB,
        LocalDate resolvedEventDate,

        String currentResolutionEvidence
) {

    /*
     * Konstruktor kompatybilny ze starymi testami matchera.
     *
     * Kandydat bez lokalnego resolution zachowuje się dokładnie
     * tak jak wcześniej.
     */
    public ApiFootballResolutionCandidate(
            long legId,
            long wpPostId,
            BetType betType,
            int betLegCount,
            Instant publishedAt,
            ResolvedSport sport,
            String postTitle,
            String tipTitle,
            String heading,
            String previousText
    ) {
        this(
                legId,
                wpPostId,
                betType,
                betLegCount,
                publishedAt,
                sport,
                postTitle,
                tipTitle,
                heading,
                previousText,
                ResolutionSource.NONE,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public boolean hasLocalResolution() {
        return currentResolutionSource != null
                && currentResolutionSource != ResolutionSource.NONE
                && currentResolutionSource != ResolutionSource.API_FOOTBALL;
    }

    public boolean hasLocalMatchup() {
        return hasLocalResolution()
                && notBlank(resolvedParticipantA)
                && notBlank(resolvedParticipantB);
    }

    private static boolean notBlank(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }
}