package pl.zagranietyper.parser;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FootballWinnerTeamTotalParser {

    private static final Pattern DIACRITIC_MARKS =
            Pattern.compile(
                    "\\p{M}+"
            );

    private static final Pattern NON_TEXT =
            Pattern.compile(
                    "[^\\p{L}\\p{N}.]+"
            );

    private static final Pattern MULTIPLE_SPACES =
            Pattern.compile(
                    "\\s+"
            );

    private static final Pattern SUBJECT_BEFORE_WINNER_PATTERN =
            Pattern.compile(
                    "^(.+?\\b(?:wygra|wygraja|zwyciezy))\\b"
            );

    /*
     * Barcelona wygra i strzeli więcej niż 2.5 bramki
     * Chelsea wygra i strzeli over 1.5 goli
     * Legia wygra i strzeli powyżej 1.5 gola
     */
    private static final Pattern NUMERIC_TEAM_TOTAL_PATTERN =
            Pattern.compile(
                    "\\bi\\s+(?:strzeli|zdobedzie)\\s+"
                            + "(wiecej niz|powyzej|over|co najmniej)\\s+"
                            + "(\\d+(?:\\.\\d+)?)\\b"
            );

    /*
     * Polska wygra i strzeli co najmniej dwie bramki
     */
    private static final Pattern WORD_TEAM_TOTAL_PATTERN =
            Pattern.compile(
                    "\\bi\\s+(?:strzeli|zdobedzie)\\s+"
                            + "co najmniej\\s+"
                            + "(jedna|jeden|jedno|dwie|dwa|trzy|cztery|piec)\\b"
            );

    private final FootballWinnerParser winnerParser;

    public FootballWinnerTeamTotalParser() {
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

        if (
                !looksLikeWinnerTeamTotalNormalized(
                        text
                )
        ) {
            return rejected(
                    Status.NOT_WINNER_PLUS_TEAM_TOTAL
            );
        }

        /*
         * "strzeli w obu połowach"
         * nie jest zwykłym team totalem.
         */
        if (
                containsHalfSignal(
                        text
                )
        ) {
            return rejected(
                    Status.UNSUPPORTED_HALF
            );
        }

        String winnerClause =
                extractWinnerClause(
                        text
                );

        if (
                winnerClause == null
                        || winnerClause.isBlank()
        ) {
            return rejected(
                    Status.WINNER_NOT_FOUND
            );
        }

        winnerClause =
                normalizeWinnerClause(
                        winnerClause
                );

        FootballWinnerParser.ParseResult winner =
                winnerParser.parse(
                        winnerClause,
                        homeTeam,
                        awayTeam
                );

        if (
                !winner.parsed()
        ) {
            return switch (
                    winner.status()
                    ) {
                case SUBJECT_NOT_FOUND ->
                        rejected(
                                Status.SUBJECT_NOT_FOUND
                        );

                case SUBJECT_MISMATCH ->
                        new ParseResult(
                                Status.SUBJECT_MISMATCH,
                                null,
                                winner.subject(),
                                null
                        );

                case SUBJECT_AMBIGUOUS ->
                        new ParseResult(
                                Status.SUBJECT_AMBIGUOUS,
                                null,
                                winner.subject(),
                                null
                        );

                case UNSUPPORTED_COMPOSITE ->
                        rejected(
                                Status.UNSUPPORTED_OTHER_COMPOSITE
                        );

                case NOT_WINNER ->
                        rejected(
                                Status.WINNER_NOT_FOUND
                        );

                case PARSED ->
                        throw new IllegalStateException(
                                "Unexpected FootballWinnerParser state"
                        );
            };
        }

        Integer minimumGoals =
                parseMinimumGoals(
                        text
                );

        if (
                minimumGoals == null
        ) {
            /*
             * Winner + "strzeli", ale nie w naszej ścisłej
             * gramatyce.
             *
             * Przykłady:
             *
             * City wygra i E. Haaland strzeli gola
             * Aston Villa wygra i M. Rashford strzeli gola
             * Barcelona wygra / Torres strzeli gola / +2.5
             */
            return new ParseResult(
                    Status.UNSUPPORTED_SCORER_OR_OTHER,
                    winner.selection(),
                    winner.subject(),
                    null
            );
        }

        return new ParseResult(
                Status.PARSED,
                winner.selection(),
                winner.subject(),
                minimumGoals
        );
    }

    public boolean looksLikeWinnerTeamTotal(
            String tipTitle
    ) {
        return looksLikeWinnerTeamTotalNormalized(
                normalize(
                        tipTitle
                )
        );
    }

    private static boolean looksLikeWinnerTeamTotalNormalized(
            String text
    ) {
        if (
                text.isBlank()
        ) {
            return false;
        }

        boolean winner =
                text.contains(
                        "wygra"
                )
                        || text.contains(
                        "zwyciestwo"
                )
                        || text.contains(
                        "zwyciezy"
                );

        boolean score =
                text.contains(
                        "strzeli"
                )
                        || text.contains(
                        "zdobedzie"
                );

        return winner
                && score;
    }

    private static String extractWinnerClause(
            String text
    ) {
        String cleaned =
                stripMarketingPrefix(
                        text
                );

        /*
         * Jeżeli "strzeli" występuje przed "wygra",
         * mamy konstrukcję typu:
         *
         * Mikael Ishak strzeli gola i Lech wygra
         *
         * albo:
         *
         * Manchester City strzeli w obu połowach i wygra
         *
         * To nie jest nasza rodzina.
         */
        int firstScore =
                firstScoreIndex(
                        cleaned
                );

        int firstWinner =
                firstWinnerIndex(
                        cleaned
                );

        if (
                firstScore >= 0
                        && (
                        firstWinner < 0
                                || firstScore < firstWinner
                )
        ) {
            return null;
        }

        Matcher matcher =
                SUBJECT_BEFORE_WINNER_PATTERN.matcher(
                        cleaned
                );

        if (
                !matcher.find()
        ) {
            return null;
        }

        return matcher
                .group(
                        1
                )
                .trim();
    }

    private static Integer parseMinimumGoals(
            String text
    ) {
        Matcher numeric =
                NUMERIC_TEAM_TOTAL_PATTERN.matcher(
                        text
                );

        if (
                numeric.find()
        ) {
            String operator =
                    numeric.group(
                            1
                    );

            double value =
                    Double.parseDouble(
                            numeric.group(
                                    2
                            )
                    );

            if (
                    numeric.find()
            ) {
                return null;
            }

            return switch (
                    operator
                    ) {
                /*
                 * więcej niż 1.5
                 * powyżej 1.5
                 * over 1.5
                 */
                case "wiecej niz",
                     "powyzej",
                     "over" ->
                        ((int) Math.floor(
                                value
                        )) + 1;

                /*
                 * co najmniej 1.5 => minimum 2
                 * co najmniej 2   => minimum 2
                 */
                case "co najmniej" ->
                        (int) Math.ceil(
                                value
                        );

                default ->
                        throw new IllegalStateException(
                                "Unknown team-total operator="
                                        + operator
                        );
            };
        }

        Matcher word =
                WORD_TEAM_TOTAL_PATTERN.matcher(
                        text
                );

        if (
                !word.find()
        ) {
            return null;
        }

        int minimumGoals =
                switch (
                        word.group(
                                1
                        )
                        ) {
                    case "jedna",
                         "jeden",
                         "jedno" ->
                            1;

                    case "dwie",
                         "dwa" ->
                            2;

                    case "trzy" ->
                            3;

                    case "cztery" ->
                            4;

                    case "piec" ->
                            5;

                    default ->
                            throw new IllegalStateException(
                                    "Unknown Polish number="
                                            + word.group(
                                            1
                                    )
                            );
                };

        if (
                word.find()
        ) {
            return null;
        }

        return minimumGoals;
    }

    private static boolean containsHalfSignal(
            String text
    ) {
        return text.contains(
                "polow"
        )
                || text.contains(
                "przerw"
        )
                || text.contains(
                "half"
        );
    }

    private static int firstScoreIndex(
            String text
    ) {
        int strzeli =
                text.indexOf(
                        "strzeli"
                );

        int zdobedzie =
                text.indexOf(
                        "zdobedzie"
                );

        return firstNonNegative(
                strzeli,
                zdobedzie
        );
    }

    private static int firstWinnerIndex(
            String text
    ) {
        int wygra =
                text.indexOf(
                        "wygra"
                );

        int zwyciestwo =
                text.indexOf(
                        "zwyciestwo"
                );

        int zwyciezy =
                text.indexOf(
                        "zwyciezy"
                );

        return firstNonNegative(
                firstNonNegative(
                        wygra,
                        zwyciestwo
                ),
                zwyciezy
        );
    }

    private static int firstNonNegative(
            int a,
            int b
    ) {
        if (
                a < 0
        ) {
            return b;
        }

        if (
                b < 0
        ) {
            return a;
        }

        return Math.min(
                a,
                b
        );
    }

    private static String normalizeWinnerClause(
            String value
    ) {
        String result =
                value.trim();

        /*
         * API-Football:
         * Atletico Madrid
         *
         * Zagranie:
         * Atletico wygra...
         * Atletico Madryt wygra...
         *
         * FootballWinnerParser jest już zamknięty,
         * więc oba warianty normalizujemy lokalnie.
         *
         * Kolejność jest ważna:
         * najpierw "Atletico Madryt", potem sam skrót
         * "Atletico".
         */
        result =
                result.replaceFirst(
                        "^atletico madryt\\b",
                        "atletico madrid"
                );

        result =
                result.replaceFirst(
                        "^atletico(?=\\s+wygra\\b)",
                        "atletico madrid"
                );

        return result.trim();
    }

    private static String stripMarketingPrefix(
            String value
    ) {
        String result =
                value;

        result =
                result.replaceFirst(
                        "^superbets\\s+",
                        ""
                );

        result =
                result.replaceFirst(
                        "^betbuilder\\s+",
                        ""
                );

        result =
                result.replaceFirst(
                        "^mycombi\\s+",
                        ""
                );

        return result.trim();
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

        String decimalNormalized =
                lower.replaceAll(
                        "(\\d),(\\d)",
                        "$1.$2"
                );

        String cleaned =
                NON_TEXT
                        .matcher(
                                decimalNormalized
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
                null,
                null
        );
    }

    public enum Status {
        PARSED,
        NOT_WINNER_PLUS_TEAM_TOTAL,
        UNSUPPORTED_HALF,
        UNSUPPORTED_SCORER_OR_OTHER,
        UNSUPPORTED_OTHER_COMPOSITE,
        WINNER_NOT_FOUND,
        SUBJECT_NOT_FOUND,
        SUBJECT_MISMATCH,
        SUBJECT_AMBIGUOUS
    }

    public record ParseResult(
            Status status,
            FootballWinnerParser.Selection selection,
            String subject,
            Integer minimumGoals
    ) {

        public boolean parsed() {
            return status
                    == Status.PARSED;
        }
    }
}