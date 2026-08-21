package pl.zagranietyper.parser;

import java.util.Map;
import java.util.Optional;

/** Exact, directional participant aliases approved by data audit. */
public final class FootballParticipantAliasRegistry {

    private static final Map<String, String> APPROVED_ALIASES = Map.of(
            "argentyna", "argentina",
            "brazylia", "brazil",
            "atletico madryt", "atletico madrid"
    );

    private FootballParticipantAliasRegistry() {}

    static Optional<String> canonicalTarget(String normalizedSubject) {
        return Optional.ofNullable(APPROVED_ALIASES.get(normalizedSubject));
    }
}
