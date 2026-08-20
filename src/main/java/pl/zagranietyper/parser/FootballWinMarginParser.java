package pl.zagranietyper.parser;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FootballWinMarginParser {

    private static final Pattern DIACRITIC_MARKS =
            Pattern.compile(
                    "\\p{M}+"
            );

    private static final Pattern NON_TEXT =
            Pattern.compile(
                    "[^\\p{L}\\p{N}]+"
            );

    private static final Pattern MULTIPLE_SPACES =
            Pattern.compile(
                    "\\s+"
            );

    /*
     * Obsługiwane formy:
     *
     * ŁKS wygra różnicą min. 2 bramek
     * Arsenal ... minimum 2 golami
     * Włochy co najmniej 4 golami
     *
     * Po normalizacji:
     *
     * roznica min 2 bramek
     * minimum 2 golami
     * co najmniej 4 golami
     */
    private static final Pattern MARGIN_PATTERN =
            Pattern.compile(
                    "\\b(?:"
                            + "roznica\\s+(?:min|minimum|co najmniej)"
                            + "|minimum"
                            + "|co najmniej"
                            + ")\\s+"
                            + "(\\d+)\\s+"
                            + "(?:"
                            + "bramek"
                            + "|bramkami"
                            + "|goli"
                            + "|golami"
                            + ")\\b"
            );

    private final FootballWinnerParser winnerParser;

    public FootballWinMarginParser() {
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

        Matcher marginMatcher =
                MARGIN_PATTERN.matcher(
                        text
                );

        if (
                !marginMatcher.find()
        ) {
            return rejected(
                    Status.NOT_WIN_MARGIN
            );
        }

        int minimumMargin =
                Integer.parseInt(
                        marginMatcher.group(
                                1
                        )
                );

        if (
                minimumMargin <= 0
        ) {
            return new ParseResult(
                    Status.INVALID_MARGIN,
                    null,
                    null,
                    null
            );
        }

        /*
         * Nie obsługujemy dwóch różnych marginów
         * w jednym tytule.
         */
        if (
                marginMatcher.find()
        ) {
            return new ParseResult(
                    Status.UNSUPPORTED_COMPOSITE,
                    null,
                    null,
                    null
            );
        }

        /*
         * Do FootballWinnerParser przekazujemy tylko
         * część dotyczącą zwycięzcy.
         *
         * Przykłady:
         *
         * "ŁKS Łódź wygra"
         *
         * "Zwycięstwo Arsenalu z Southampton"
         *
         * "Wygrają Włochy"
         */
        marginMatcher =
                MARGIN_PATTERN.matcher(
                        text
                );

        if (
                !marginMatcher.find()
        ) {
            return rejected(
                    Status.MARGIN_NOT_FOUND
            );
        }

        String winnerClause =
                text.substring(
                                0,
                                marginMatcher.start()
                        )
                        .trim();

        /*
         * W konstrukcji:
         *
         * "ŁKS wygra różnicą..."
         *
         * winnerClause jest idealny.
         *
         * W konstrukcji:
         *
         * "Zwycięstwo Arsenalu z Southampton minimum..."
         *
         * również dostajemy poprawny czysty winner.
         */
        if (
                winnerClause.isBlank()
        ) {
            return rejected(
                    Status.WINNER_NOT_FOUND
            );
        }

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
                        new ParseResult(
                                Status.SUBJECT_NOT_FOUND,
                                null,
                                winner.subject(),
                                minimumMargin
                        );

                case SUBJECT_MISMATCH ->
                        new ParseResult(
                                Status.SUBJECT_MISMATCH,
                                null,
                                winner.subject(),
                                minimumMargin
                        );

                case SUBJECT_AMBIGUOUS ->
                        new ParseResult(
                                Status.SUBJECT_AMBIGUOUS,
                                null,
                                winner.subject(),
                                minimumMargin
                        );

                case UNSUPPORTED_COMPOSITE ->
                        new ParseResult(
                                Status.UNSUPPORTED_COMPOSITE,
                                null,
                                winner.subject(),
                                minimumMargin
                        );

                case NOT_WINNER ->
                        new ParseResult(
                                Status.WINNER_NOT_FOUND,
                                null,
                                winner.subject(),
                                minimumMargin
                        );

                case PARSED ->
                        throw new IllegalStateException(
                                "Unexpected FootballWinnerParser state"
                        );
            };
        }

        return new ParseResult(
                Status.PARSED,
                winner.selection(),
                winner.subject(),
                minimumMargin
        );
    }

    public boolean looksLikeWinMargin(
            String tipTitle
    ) {
        String text =
                normalize(
                        tipTitle
                );

        return MARGIN_PATTERN
                .matcher(
                        text
                )
                .find();
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
                NON_TEXT
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
                null,
                null
        );
    }

    public enum Status {
        PARSED,
        NOT_WIN_MARGIN,
        UNSUPPORTED_COMPOSITE,
        WINNER_NOT_FOUND,
        SUBJECT_NOT_FOUND,
        SUBJECT_MISMATCH,
        SUBJECT_AMBIGUOUS,
        MARGIN_NOT_FOUND,
        INVALID_MARGIN
    }

    public record ParseResult(
            Status status,
            FootballWinnerParser.Selection selection,
            String subject,
            Integer minimumMargin
    ) {

        public boolean parsed() {
            return status
                    == Status.PARSED;
        }
    }
}