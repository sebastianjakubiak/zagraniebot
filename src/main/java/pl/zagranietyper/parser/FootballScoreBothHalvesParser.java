package pl.zagranietyper.parser;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FootballScoreBothHalvesParser {

    private static final Pattern DIACRITIC_MARKS =
            Pattern.compile("\\p{M}+");

    private static final Pattern NON_ALPHANUMERIC =
            Pattern.compile("[^\\p{L}\\p{N}]+");

    private static final Pattern MULTIPLE_SPACES =
            Pattern.compile("\\s+");

    /*
     * Milan strzeli gola w obu połowach
     * Chelsea strzeli w obu połowach
     * Cercle zdobędzie gola w obu połowach
     */
    private static final Pattern TEAM_BOTH_HALVES =
            Pattern.compile(
                    "^(.+?)\\s+"
                            + "(?:strzeli|zdobedzie)"
                            + "(?:\\s+gola)?"
                            + "\\s+w\\s+obu\\s+polowach"
                            + "(?:\\s+(nie))?$"
            );

    /*
     * Arsenal strzeli gola w każdej połowie
     */
    private static final Pattern TEAM_EACH_HALF =
            Pattern.compile(
                    "^(.+?)\\s+"
                            + "(?:strzeli|zdobedzie)"
                            + "(?:\\s+gola)?"
                            + "\\s+w\\s+kazdej\\s+polowie"
                            + "(?:\\s+(nie))?$"
            );

    /*
     * Gol w obu połowach - NIE
     *
     * To rynek meczowy:
     * czy w KAŻDEJ z dwóch połów padnie przynajmniej jeden gol.
     */
    private static final Pattern MATCH_GOAL_BOTH_HALVES =
            Pattern.compile(
                    "^gol\\s+w\\s+obu\\s+polowach\\s+(tak|nie)$"
            );

    /*
     * Lokalne, dokładne aliasy potrzebne tej rodzinie.
     * Nie ruszamy zamkniętego FootballWinnerParser.
     */
    private static final Map<String, String> LOCAL_SUBJECT_ALIASES =
            Map.ofEntries(
                    Map.entry(
                            "cercle",
                            "cercle brugge"
                    ),
                    Map.entry(
                            "holandia",
                            "netherlands"
                    ),
                    Map.entry(
                            "psg",
                            "paris saint germain"
                    ),
                    Map.entry(
                            "milan",
                            "ac milan"
                    ),
                    Map.entry(
                            "bayern",
                            "bayern munchen"
                    ),
                    Map.entry(
                            "polska",
                            "poland"
                    ),
                    Map.entry(
                            "francja",
                            "france"
                    ),
                    Map.entry(
                            "lech",
                            "lech poznan"
                    ),
                    Map.entry(
                            "hiszpania",
                            "spain"
                    ),
                    Map.entry(
                            "wieczysta",
                            "wieczysta krakow"
                    )
            );

    private final FootballWinnerParser winnerParser;

    public FootballScoreBothHalvesParser() {
        this.winnerParser =
                new FootballWinnerParser();
    }

    public ParseResult parse(
            String tipTitle,
            String homeTeam,
            String awayTeam
    ) {
        String text =
                normalize(
                        tipTitle
                );

        /*
         * =====================================================
         * MATCH-LEVEL:
         *
         * Gol w obu połowach - NIE
         * =====================================================
         */

        Matcher matchLevel =
                MATCH_GOAL_BOTH_HALVES.matcher(
                        text
                );

        if (
                matchLevel.matches()
        ) {
            boolean expectedYes =
                    "tak".equals(
                            matchLevel.group(
                                    1
                            )
                    );

            return new ParseResult(
                    Status.PARSED,
                    Market.MATCH_GOAL_BOTH_HALVES,
                    null,
                    null,
                    expectedYes
            );
        }

        /*
         * =====================================================
         * TEAM-LEVEL:
         *
         * X strzeli gola w obu połowach
         * X strzeli gola w każdej połowie
         * =====================================================
         */

        Matcher teamMatcher =
                TEAM_BOTH_HALVES.matcher(
                        text
                );

        if (
                !teamMatcher.matches()
        ) {
            teamMatcher =
                    TEAM_EACH_HALF.matcher(
                            text
                    );
        }

        if (
                teamMatcher.matches()
        ) {
            String subject =
                    normalizeSubject(
                            teamMatcher.group(
                                    1
                            )
                    );

            boolean expectedYes =
                    teamMatcher.group(
                            2
                    ) == null;

            return parseTeamSubject(
                    subject,
                    homeTeam,
                    awayTeam,
                    expectedYes
            );
        }

        /*
         * Widzimy semantykę "strzeli w obu/każdej połowie",
         * ale pełny tytuł zawiera dodatkowy warunek.
         *
         * Przykłady:
         *
         * Manchester City strzeli w obu połowach i wygra mecz
         *
         * Górnik Zabrze wygra mecz i strzeli gola
         * w obu połowach tej rywalizacji
         */
        if (
                looksLikeScoreBothHalvesNormalized(
                        text
                )
        ) {
            return rejected(
                    Status.UNSUPPORTED_COMPOSITE
            );
        }

        return rejected(
                Status.NOT_SCORE_BOTH_HALVES
        );
    }

    public boolean looksLikeScoreBothHalves(
            String tipTitle
    ) {
        return looksLikeScoreBothHalvesNormalized(
                normalize(
                        tipTitle
                )
        );
    }

    /*
     * =========================================================
     * PARTICIPANT
     * =========================================================
     */

    private ParseResult parseTeamSubject(
            String subject,
            String homeTeam,
            String awayTeam,
            boolean expectedYes
    ) {
        if (
                subject == null
                        || subject.isBlank()
        ) {
            return new ParseResult(
                    Status.SUBJECT_NOT_FOUND,
                    Market.TEAM_SCORES_BOTH_HALVES,
                    null,
                    null,
                    expectedYes
            );
        }

        FootballWinnerParser.ParseResult winner =
                winnerParser.parse(
                        subject + " wygra",
                        homeTeam,
                        awayTeam
                );

        if (
                winner.parsed()
        ) {
            return new ParseResult(
                    Status.PARSED,
                    Market.TEAM_SCORES_BOTH_HALVES,
                    winner.selection(),
                    subject,
                    expectedYes
            );
        }

        return switch (
                winner.status()
                ) {
            case SUBJECT_NOT_FOUND ->
                    new ParseResult(
                            Status.SUBJECT_NOT_FOUND,
                            Market.TEAM_SCORES_BOTH_HALVES,
                            null,
                            subject,
                            expectedYes
                    );

            case SUBJECT_MISMATCH ->
                    new ParseResult(
                            Status.SUBJECT_MISMATCH,
                            Market.TEAM_SCORES_BOTH_HALVES,
                            null,
                            subject,
                            expectedYes
                    );

            case SUBJECT_AMBIGUOUS ->
                    new ParseResult(
                            Status.SUBJECT_AMBIGUOUS,
                            Market.TEAM_SCORES_BOTH_HALVES,
                            null,
                            subject,
                            expectedYes
                    );

            case NOT_WINNER,
                 UNSUPPORTED_COMPOSITE ->
                    new ParseResult(
                            Status.SUBJECT_NOT_FOUND,
                            Market.TEAM_SCORES_BOTH_HALVES,
                            null,
                            subject,
                            expectedYes
                    );

            case PARSED ->
                    throw new IllegalStateException(
                            "Unexpected FootballWinnerParser state"
                    );
        };
    }

    /*
     * =========================================================
     * CLASSIFICATION
     * =========================================================
     */

    private static boolean looksLikeScoreBothHalvesNormalized(
            String text
    ) {
        if (
                text.isBlank()
        ) {
            return false;
        }

        if (
                text.startsWith(
                        "gol w obu polowach"
                )
        ) {
            return true;
        }

        boolean scoringVerb =
                text.contains(
                        "strzeli"
                )
                        || text.contains(
                        "zdobedzie"
                );

        if (
                !scoringVerb
        ) {
            return false;
        }

        return text.contains(
                "w obu polowach"
        )
                || text.contains(
                "w kazdej polowie"
        );
    }

    /*
     * =========================================================
     * LOCAL ALIASES
     * =========================================================
     */

    private static String normalizeSubject(
            String value
    ) {
        String subject =
                value == null
                        ? ""
                        : value.trim();

        return LOCAL_SUBJECT_ALIASES.getOrDefault(
                subject,
                subject
        );
    }

    /*
     * =========================================================
     * NORMALIZATION
     * =========================================================
     */

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
                        .replaceAll("");

        String lower =
                withoutMarks.toLowerCase(
                        Locale.ROOT
                );

        String cleaned =
                NON_ALPHANUMERIC
                        .matcher(
                                lower
                        )
                        .replaceAll(" ");

        return MULTIPLE_SPACES
                .matcher(
                        cleaned
                )
                .replaceAll(" ")
                .trim();
    }

    private static ParseResult rejected(
            Status status
    ) {
        return new ParseResult(
                status,
                null,
                null,
                null,
                false
        );
    }

    public enum Market {
        TEAM_SCORES_BOTH_HALVES,
        MATCH_GOAL_BOTH_HALVES
    }

    public enum Status {
        PARSED,
        NOT_SCORE_BOTH_HALVES,
        UNSUPPORTED_COMPOSITE,
        SUBJECT_NOT_FOUND,
        SUBJECT_MISMATCH,
        SUBJECT_AMBIGUOUS
    }

    public record ParseResult(
            Status status,
            Market market,
            FootballWinnerParser.Selection selection,
            String subject,
            boolean expectedYes
    ) {

        public boolean parsed() {
            return status
                    == Status.PARSED;
        }
    }
}