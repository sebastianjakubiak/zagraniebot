package pl.zagranietyper.model;

import java.time.LocalDate;

public record ResolvedEvent(
        ResolvedSport sport,

        String eventName,
        String participantA,
        String participantB,

        LocalDate eventDate,

        ResolutionSource source,
        ResolutionConfidence confidence,

        String evidence
) {

    public boolean resolved() {
        return source != ResolutionSource.NONE;
    }

    public static ResolvedEvent unresolved(
            ResolvedSport sport
    ) {
        return new ResolvedEvent(
                sport,
                null,
                null,
                null,
                null,
                ResolutionSource.NONE,
                null,
                null
        );
    }
}