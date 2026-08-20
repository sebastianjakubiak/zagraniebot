package pl.zagranietyper.parser;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FootballNoDrawParser {

    private static final String PREFIX =
            "zwyciestwo ";

    private static final String SEPARATOR =
            " lub ";

    private static final Pattern WINNER_ALTERNATIVE_PATTERN =
            Pattern.compile(
                    "^(.+?) wygra(?: mecz)? lub (.+?) wygra(?: mecz)?$"
            );

    private static final Pattern DIACRITIC_MARKS =
            Pattern.compile(
                    "\\p{M}+"
            );

    private static final Pattern NON_ALPHANUMERIC =
            Pattern.compile(
                    "[^\\p{L}\\p{N}]+"
            );

    private static final Pattern MULTIPLE_SPACES =
            Pattern.compile(
                    "\\s+"
            );

    private static final Set<String> TEAM_NOISE =
            Set.of(
                    "fc",
                    "cf",
                    "fk",
                    "afc",
                    "sc",
                    "ks",
                    "mks",
                    "rks",
                    "lks",
                    "ssa",
                    "sv",
                    "cp",
                    "ac",
                    "as",
                    "club",
                    "town"
            );

    private static final Set<String> SIMPLE_INFLECTION_SUFFIXES =
            Set.of(
                    "u",
                    "a",
                    "em",
                    "ie",
                    "i",
                    "y",
                    "owi",
                    "om",
                    "ow",
                    "ia"
            );

    private static final Set<String> A_STEM_SUFFIXES =
            Set.of(
                    "a",
                    "y",
                    "i",
                    "ie",
                    "ii",
                    "e"
            );

    private static final List<String> COMPOSITE_SIGNALS =
            List.of(
                    "remis",

                    "bram",
                    "gol",

                    "powyzej",
                    "ponizej",
                    "minimum",
                    "maksymalnie",
                    "co najmniej",

                    "btts",
                    "obie druzyny",

                    "kart",
                    "rzut",
                    "corner",
                    "korner",

                    "strzal",
                    "faul",
                    "spalony",

                    "handicap",
                    "awans",

                    "polow",
                    "przerw"
            );

    private static final Map<String, List<String>> TEAM_ALIASES =
            createAliases();

    private static final FootballParticipantResolver PARTICIPANT_RESOLVER =
            new FootballParticipantResolver();

    public ParseResult parse(
            String tipTitle,
            String homeTeam,
            String awayTeam
    ) {
        String text =
                normalize(
                        tipTitle
                );

        if (!looksNoDrawLikeNormalized(text)) {
            return rejected(
                    Status.NOT_NO_DRAW
            );
        }

        if (
                tipTitle != null
                        && tipTitle.contains(
                        "+"
                )
        ) {
            return rejected(
                    Status.UNSUPPORTED_COMPOSITE
            );
        }

        if (
                containsCompositeSignal(
                        text
                )
        ) {
            return rejected(
                    Status.UNSUPPORTED_COMPOSITE
            );
        }

        Participants participants = extractParticipants(text);

        if (participants == null) {
            return rejected(Status.UNSUPPORTED_COMPOSITE);
        }

        String left = participants.left();
        String right = participants.right();

        if (
                left.isBlank()
                        || right.isBlank()
        ) {
            return rejected(
                    Status.PARTICIPANTS_NOT_FOUND
            );
        }

        FootballParticipantResolver.Resolution leftResolution =
                PARTICIPANT_RESOLVER.resolve(
                        left,
                        homeTeam,
                        awayTeam,
                        FootballParticipantResolver.MatchingPolicy.SUBJECT_TOKENS_IN_TEAM,
                        TEAM_ALIASES
                );

        FootballParticipantResolver.Resolution rightResolution =
                PARTICIPANT_RESOLVER.resolve(
                        right,
                        homeTeam,
                        awayTeam,
                        FootballParticipantResolver.MatchingPolicy.SUBJECT_TOKENS_IN_TEAM,
                        TEAM_ALIASES
                );

        boolean normalOrder =
                leftResolution == FootballParticipantResolver.Resolution.HOME
                        && rightResolution == FootballParticipantResolver.Resolution.AWAY;

        boolean reversedOrder =
                leftResolution == FootballParticipantResolver.Resolution.AWAY
                        && rightResolution == FootballParticipantResolver.Resolution.HOME;

        if (
                leftResolution == FootballParticipantResolver.Resolution.AMBIGUOUS
                        || rightResolution == FootballParticipantResolver.Resolution.AMBIGUOUS
        ) {
            return new ParseResult(
                    Status.PARTICIPANTS_AMBIGUOUS,
                    left,
                    right
            );
        }

        if (
                normalOrder
                        || reversedOrder
        ) {
            return new ParseResult(
                    Status.PARSED,
                    left,
                    right
            );
        }

        return new ParseResult(
                Status.PARTICIPANTS_MISMATCH,
                left,
                right
        );
    }

    public boolean looksNoDrawLike(
            String tipTitle
    ) {
        String text =
                normalize(
                        tipTitle
                );

        return looksNoDrawLikeNormalized(text);
    }

    public boolean looksExplicitTwoTeamWinnerAlternativeLike(
            String tipTitle
    ) {
        String text = normalize(tipTitle);

        return text.startsWith("wygrana ")
                && text.contains(SEPARATOR)
                || hasWinnerCueOnBothSides(text);
    }

    private static boolean looksNoDrawLikeNormalized(String text) {
        return text.contains("zwyciestwo")
                && text.contains(SEPARATOR)
                || text.startsWith("wygrana ")
                && text.contains(SEPARATOR)
                || hasWinnerCueOnBothSides(text);
    }

    private static boolean hasWinnerCueOnBothSides(String text) {
        int separator = text.indexOf(SEPARATOR);

        if (separator <= 0) {
            return false;
        }

        String left = text.substring(0, separator);
        String right = text.substring(separator + SEPARATOR.length());

        boolean leftHasWinnerCue =
                left.endsWith(" wygra")
                        || left.endsWith(" wygra mecz");

        return leftHasWinnerCue
                && right.contains(" wygra");
    }

    private static Participants extractParticipants(String text) {
        if (text.startsWith(PREFIX) || text.startsWith("wygrana ")) {
            String prefix = text.startsWith(PREFIX)
                    ? PREFIX
                    : "wygrana ";
            String body = text.substring(prefix.length()).trim();
            int separator = body.indexOf(SEPARATOR);

            if (separator <= 0
                    || body.indexOf(SEPARATOR, separator + SEPARATOR.length()) >= 0) {
                return null;
            }

            return new Participants(
                    body.substring(0, separator).trim(),
                    body.substring(separator + SEPARATOR.length()).trim()
            );
        }

        Matcher matcher = WINNER_ALTERNATIVE_PATTERN.matcher(text);

        if (!matcher.matches()) {
            return null;
        }

        return new Participants(matcher.group(1).trim(), matcher.group(2).trim());
    }

    /*
     * =========================================================
     * PARTICIPANT MATCHING
     * =========================================================
     */

    private static boolean participantMatchesTeam(
            String participant,
            String apiTeam
    ) {
        List<String> participantTokens =
                meaningfulTokens(
                        participant
                );

        if (
                participantTokens.isEmpty()
        ) {
            return false;
        }

        for (
                String variant :
                teamVariants(
                        apiTeam
                )
        ) {
            List<String> teamTokens =
                    meaningfulTokens(
                            variant
                    );

            if (
                    teamTokens.isEmpty()
            ) {
                continue;
            }

            if (
                    allParticipantTokensMatch(
                            participantTokens,
                            teamTokens
                    )
            ) {
                return true;
            }
        }

        return false;
    }

    private static boolean allParticipantTokensMatch(
            List<String> participantTokens,
            List<String> teamTokens
    ) {
        for (
                String participantToken :
                participantTokens
        ) {
            boolean matched =
                    false;

            for (
                    String teamToken :
                    teamTokens
            ) {
                if (
                        tokenEquivalent(
                                teamToken,
                                participantToken
                        )
                ) {
                    matched =
                            true;

                    break;
                }
            }

            if (
                    !matched
            ) {
                return false;
            }
        }

        return true;
    }

    private static boolean tokenEquivalent(
            String teamToken,
            String participantToken
    ) {
        if (
                teamToken.equals(
                        participantToken
                )
        ) {
            return true;
        }

        if (
                teamToken.length() < 4
                        || participantToken.length() < 4
        ) {
            return false;
        }

        if (
                participantToken.startsWith(
                        teamToken
                )
                        && participantToken.length()
                        > teamToken.length()
        ) {
            String suffix =
                    participantToken.substring(
                            teamToken.length()
                    );

            if (
                    SIMPLE_INFLECTION_SUFFIXES.contains(
                            suffix
                    )
            ) {
                return true;
            }
        }

        if (
                teamToken.endsWith(
                        "a"
                )
                        && teamToken.length() >= 5
        ) {
            String stem =
                    teamToken.substring(
                            0,
                            teamToken.length() - 1
                    );

            if (
                    participantToken.startsWith(
                            stem
                    )
            ) {
                String suffix =
                        participantToken.substring(
                                stem.length()
                        );

                if (
                        A_STEM_SUFFIXES.contains(
                                suffix
                        )
                ) {
                    return true;
                }
            }
        }

        return false;
    }

    private static List<String> teamVariants(
            String apiTeam
    ) {
        String normalized =
                normalize(
                        apiTeam
                );

        if (
                normalized.isBlank()
        ) {
            return List.of();
        }

        List<String> result =
                new ArrayList<>();

        result.add(
                normalized
        );

        result.addAll(
                TEAM_ALIASES.getOrDefault(
                        normalized,
                        List.of()
                )
        );

        return List.copyOf(
                result
        );
    }

    private static List<String> meaningfulTokens(
            String value
    ) {
        List<String> result =
                new ArrayList<>();

        for (
                String token :
                tokens(
                        value
                )
        ) {
            if (
                    token.length() == 1
            ) {
                continue;
            }

            if (
                    TEAM_NOISE.contains(
                            token
                    )
            ) {
                continue;
            }

            if (
                    token.chars()
                            .allMatch(
                                    Character::isDigit
                            )
            ) {
                continue;
            }

            result.add(
                    token
            );
        }

        return List.copyOf(
                result
        );
    }

    /*
     * =========================================================
     * ALIASES
     * =========================================================
     */

    private static Map<String, List<String>>
    createAliases() {

        Map<String, List<String>> result =
                new LinkedHashMap<>();

        alias(
                result,
                "Brazil",
                "Brazylia"
        );

        alias(
                result,
                "Uruguay",
                "Urugwaj"
        );

        alias(
                result,
                "England",
                "Anglia"
        );

        alias(
                result,
                "Greece",
                "Grecja"
        );

        alias(
                result,
                "AS Roma",
                "Roma",
                "Romy",
                "AS Romy"
        );

        alias(
                result,
                "Athletic Club",
                "Athletic Bilbao",
                "Athleticu Bilbao"
        );

        alias(
                result,
                "Newcastle",
                "Newcastle United"
        );

        /*
         * API-Football:
         * Sparta Praha
         *
         * Zagranie:
         * Sparta Praga / Sparty Praga
         *
         * "Sparta -> Sparty" obsługuje morfologia,
         * ale Praha -> Praga to tłumaczenie nazwy miasta,
         * więc potrzebny jest jawny alias.
         */
        alias(
                result,
                "Sparta Praha",
                "Sparta Praga",
                "Sparty Praga"
        );

        /*
         * Krótkie "Ham" ma tylko 3 znaki i celowo nie przechodzi
         * przez ogólną regułę odmiany.
         *
         * Dlatego West Ham -> West Hamu zapisujemy jawnie.
         */
        alias(
                result,
                "West Ham",
                "West Hamu"
        );

        return Map.copyOf(
                result
        );
    }

    private static void alias(
            Map<String, List<String>> map,
            String apiName,
            String... aliases
    ) {
        List<String> normalized =
                new ArrayList<>();

        for (
                String alias :
                aliases
        ) {
            normalized.add(
                    normalize(
                            alias
                    )
            );
        }

        map.put(
                normalize(
                        apiName
                ),
                List.copyOf(
                        normalized
                )
        );
    }

    /*
     * =========================================================
     * GENERAL
     * =========================================================
     */

    private static boolean containsCompositeSignal(
            String text
    ) {
        for (
                String signal :
                COMPOSITE_SIGNALS
        ) {
            if (
                    text.contains(
                            signal
                    )
            ) {
                return true;
            }
        }

        return false;
    }

    private static List<String> tokens(
            String value
    ) {
        String normalized =
                normalize(
                        value
                );

        if (
                normalized.isBlank()
        ) {
            return List.of();
        }

        return List.of(
                normalized.split(
                        " "
                )
        );
    }

    private static String normalize(
            String value
    ) {
        if (
                value == null
        ) {
            return "";
        }

        String transliterated =
                value
                        .replace(
                                'ł',
                                'l'
                        )
                        .replace(
                                'Ł',
                                'L'
                        );

        String decomposed =
                Normalizer.normalize(
                        transliterated,
                        Normalizer.Form.NFD
                );

        String withoutMarks =
                DIACRITIC_MARKS
                        .matcher(
                                decomposed
                        )
                        .replaceAll(
                                ""
                        );

        String lower =
                withoutMarks.toLowerCase(
                        Locale.ROOT
                );

        String cleaned =
                NON_ALPHANUMERIC
                        .matcher(
                                lower
                        )
                        .replaceAll(
                                " "
                        );

        return MULTIPLE_SPACES
                .matcher(
                        cleaned
                )
                .replaceAll(
                        " "
                )
                .trim();
    }

    private static ParseResult rejected(
            Status status
    ) {
        return new ParseResult(
                status,
                null,
                null
        );
    }

    public enum Status {
        PARSED,
        NOT_NO_DRAW,
        UNSUPPORTED_COMPOSITE,
        PARTICIPANTS_NOT_FOUND,
        PARTICIPANTS_MISMATCH,
        PARTICIPANTS_AMBIGUOUS
    }

    public record ParseResult(
            Status status,
            String participantA,
            String participantB
    ) {

        public boolean parsed() {
            return status
                    == Status.PARSED;
        }
    }

    private record Participants(String left, String right) {
    }
}
