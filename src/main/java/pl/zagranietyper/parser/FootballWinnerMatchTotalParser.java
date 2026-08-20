package pl.zagranietyper.parser;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FootballWinnerMatchTotalParser {

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

    private static final Pattern TOTAL_CUE_PATTERN =
            Pattern.compile(
                    "\\b(over|under|powyzej|ponizej|co najmniej|minimum)\\b"
            );

    private static final Pattern NUMERIC_TOTAL_PATTERN =
            Pattern.compile(
                    "\\b(over|under|powyzej|ponizej)\\s+(\\d+(?:\\.\\d+)?)\\b"
            );

    private static final Pattern SUBJECT_BEFORE_WINNER_PATTERN =
            Pattern.compile(
                    "^(.+?\\b(?:wygra|wygraja|zwyciezy))\\b"
            );

    private final FootballWinnerParser winnerParser;

    public FootballWinnerMatchTotalParser() {
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
                !looksLikeWinnerMatchTotalNormalized(
                        text
                )
        ) {
            return rejected(
                    Status.NOT_WINNER_PLUS_TOTAL
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
                                null,
                                null
                        );

                case SUBJECT_AMBIGUOUS ->
                        new ParseResult(
                                Status.SUBJECT_AMBIGUOUS,
                                null,
                                winner.subject(),
                                null,
                                null
                        );

                case UNSUPPORTED_COMPOSITE ->
                        rejected(
                                Status.UNSUPPORTED_COMPOSITE
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

        Matcher totalMatcher =
                NUMERIC_TOTAL_PATTERN.matcher(
                        text
                );

        if (
                !totalMatcher.find()
        ) {
            return new ParseResult(
                    Status.TOTAL_NOT_FOUND,
                    winner.selection(),
                    winner.subject(),
                    null,
                    null
            );
        }

        String operator =
                totalMatcher.group(
                        1
                );

        double line =
                Double.parseDouble(
                        totalMatcher.group(
                                2
                        )
                );

        /*
         * Na tym etapie obsługujemy tylko linie x.5.
         */
        if (
                isInteger(
                        line
                )
        ) {
            return new ParseResult(
                    Status.INTEGER_TOTAL_UNSUPPORTED,
                    winner.selection(),
                    winner.subject(),
                    null,
                    line
            );
        }

        /*
         * Drugi total w tym samym tytule = nie zgadujemy.
         */
        if (
                totalMatcher.find()
        ) {
            return new ParseResult(
                    Status.UNSUPPORTED_COMPOSITE,
                    winner.selection(),
                    winner.subject(),
                    null,
                    null
            );
        }

        Direction direction =
                switch (
                        operator
                        ) {
                    case "over",
                         "powyzej" ->
                            Direction.OVER;

                    case "under",
                         "ponizej" ->
                            Direction.UNDER;

                    default ->
                            throw new IllegalStateException(
                                    "Unknown total operator="
                                            + operator
                            );
                };

        return new ParseResult(
                Status.PARSED,
                winner.selection(),
                winner.subject(),
                direction,
                line
        );
    }

    public boolean looksLikeWinnerMatchTotal(
            String tipTitle
    ) {
        return looksLikeWinnerMatchTotalNormalized(
                normalize(
                        tipTitle
                )
        );
    }

    private static boolean looksLikeWinnerMatchTotalNormalized(
            String normalized
    ) {
        if (
                normalized.isBlank()
        ) {
            return false;
        }

        boolean winner =
                normalized.contains(
                        "wygra"
                )
                        || normalized.contains(
                        "zwyciestwo"
                )
                        || normalized.contains(
                        "zwyciezy"
                );

        if (
                !winner
        ) {
            return false;
        }

        /*
         * Team total / scorer dostanie osobny moduł.
         */
        if (
                normalized.contains(
                        "strzeli"
                )
                        || normalized.contains(
                        "zdobed"
                )
        ) {
            return false;
        }

        return TOTAL_CUE_PATTERN
                .matcher(
                        normalized
                )
                .find();
    }

    private static boolean containsCompositeSignal(
            String text
    ) {
        if (
                text.contains(
                        "remis"
                )
        ) {
            return true;
        }

        /*
         * Win margin.
         */
        if (
                text.contains(
                        "roznic"
                )
                        || text.contains(
                        "golami"
                )
                        || text.contains(
                        "bramkami"
                )
        ) {
            return true;
        }

        /*
         * Corners / cards / inne statystyki.
         */
        if (
                text.contains(
                        "rzut"
                )
                        || text.contains(
                        "corner"
                )
                        || text.contains(
                        "korner"
                )
                        || text.contains(
                        "kart"
                )
                        || text.contains(
                        "strzal"
                )
                        || text.contains(
                        "faul"
                )
                        || text.contains(
                        "spalony"
                )
        ) {
            return true;
        }

        /*
         * Half markets.
         */
        if (
                text.contains(
                        "polow"
                )
                        || text.contains(
                        "przerw"
                )
                        || text.contains(
                        "half"
                )
        ) {
            return true;
        }

        /*
         * Scorer / team total.
         */
        return text.contains(
                "strzeli"
        )
                || text.contains(
                "zdobed"
        );
    }

    private static String extractWinnerClause(
            String text
    ) {
        String cleaned =
                stripMarketingPrefix(
                        text
                );

        Matcher subjectBefore =
                SUBJECT_BEFORE_WINNER_PATTERN.matcher(
                        cleaned
                );

        if (
                subjectBefore.find()
                        && !cleaned.startsWith(
                        "wygra"
                )
        ) {
            return subjectBefore
                    .group(
                            1
                    )
                    .trim();
        }

        /*
         * Formy:
         *
         * Wygrają Włochy ...
         * Zwycięstwo Arsenalu z Southampton ...
         */
        Matcher cue =
                TOTAL_CUE_PATTERN.matcher(
                        cleaned
                );

        if (
                !cue.find()
        ) {
            return null;
        }

        return cleaned
                .substring(
                        0,
                        cue.start()
                )
                .trim();
    }

    private static String normalizeWinnerClause(
            String value
    ) {
        String result =
                value;

        /*
         * Literówka z danych Zagranie.
         */
        result =
                result.replace(
                        "jagiellonai",
                        "jagiellonia"
                );

        /*
         * Polski skrót reprezentacji.
         */
        result =
                result.replaceAll(
                        "\\brpa\\b",
                        "south africa"
                );

        /*
         * FootballWinnerParser jest już zamknięty.
         * Tłumaczenie potrzebne wyłącznie tej rodzinie
         * robimy lokalnie, zamiast rozszerzać jego aliasy.
         */
        result =
                result.replaceAll(
                        "\\bkanada\\b",
                        "canada"
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
                        "^mycombi\\s+",
                        ""
                );

        result =
                result.replaceFirst(
                        "^betbuilder\\s+",
                        ""
                );

        result =
                result.replaceFirst(
                        "^superbets\\s+",
                        ""
                );

        return result.trim();
    }

    private static boolean isInteger(
            double value
    ) {
        return Math.abs(
                value - Math.rint(
                        value
                )
        ) < 0.000001;
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
                null,
                null
        );
    }

    public enum Direction {
        OVER,
        UNDER
    }

    public enum Status {
        PARSED,
        NOT_WINNER_PLUS_TOTAL,
        UNSUPPORTED_COMPOSITE,
        WINNER_NOT_FOUND,
        SUBJECT_NOT_FOUND,
        SUBJECT_MISMATCH,
        SUBJECT_AMBIGUOUS,
        TOTAL_NOT_FOUND,
        INTEGER_TOTAL_UNSUPPORTED
    }

    public record ParseResult(
            Status status,
            FootballWinnerParser.Selection selection,
            String subject,
            Direction direction,
            Double line
    ) {

        public boolean parsed() {
            return status
                    == Status.PARSED;
        }
    }
}