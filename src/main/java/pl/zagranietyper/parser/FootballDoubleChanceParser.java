package pl.zagranietyper.parser;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FootballDoubleChanceParser {

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

    /*
     * Termalica nie przegra
     *
     * Dopuszczamy również ścisłe:
     *
     * Węgry nie przegrają
     * Węgry nie przegrają meczu
     *
     * ale żadnych dodatkowych warunków po tym.
     */
    private static final Pattern NOT_LOSE_PATTERN =
            Pattern.compile(
                    "^(.+?)\\s+nie\\s+przegra(?:ja)?(?:\\s+meczu)?$"
            );

    /*
     * RPA wygra lub remis
     * Francja wygra lub zremisuje
     * Szwecja wygra mecz lub zremisuje
     */
    private static final Pattern WIN_OR_DRAW_PATTERN =
            Pattern.compile(
                    "^(.+?)\\s+wygra(?:\\s+mecz)?\\s+lub\\s+"
                            + "(?:remis|zremisuje(?:\\s+mecz)?)$"
            );

    private final FootballWinnerParser winnerParser;

    public FootballDoubleChanceParser() {
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
                !looksLikeDoubleChanceNormalized(
                        text
                )
        ) {
            return rejected(
                    Status.NOT_DOUBLE_CHANCE
            );
        }

        /*
         * =====================================================
         * FORMAT 1:
         *
         * X nie przegra
         * =====================================================
         */

        Matcher notLose =
                NOT_LOSE_PATTERN.matcher(
                        text
                );

        if (
                notLose.matches()
        ) {
            String subject =
                    normalizeSubject(
                            notLose.group(
                                    1
                            )
                    );

            return parseSubject(
                    subject,
                    homeTeam,
                    awayTeam,
                    Format.NOT_LOSE
            );
        }

        /*
         * =====================================================
         * FORMAT 2:
         *
         * X wygra lub remis
         * X wygra lub zremisuje
         * X wygra mecz lub zremisuje
         * =====================================================
         */

        Matcher winOrDraw =
                WIN_OR_DRAW_PATTERN.matcher(
                        text
                );

        if (
                winOrDraw.matches()
        ) {
            String subject =
                    normalizeSubject(
                            winOrDraw.group(
                                    1
                            )
                    );

            return parseSubject(
                    subject,
                    homeTeam,
                    awayTeam,
                    Format.WIN_OR_DRAW
            );
        }

        /*
         * Jeżeli widzimy sygnał DOUBLE_CHANCE,
         * ale pełny tytuł nie pasuje do żadnej z dwóch
         * zamkniętych gramatyk, jest to composite albo
         * inny nieobsługiwany wariant.
         */
        return rejected(
                Status.UNSUPPORTED_COMPOSITE
        );
    }

    public boolean looksLikeDoubleChance(
            String tipTitle
    ) {
        return looksLikeDoubleChanceNormalized(
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

    private ParseResult parseSubject(
            String subject,
            String homeTeam,
            String awayTeam,
            Format format
    ) {
        if (
                subject == null
                        || subject.isBlank()
        ) {
            return new ParseResult(
                    Status.SUBJECT_NOT_FOUND,
                    null,
                    subject,
                    format
            );
        }

        /*
         * Reużywamy zamkniętego FootballWinnerParser.
         *
         * Double chance:
         *
         * Termalica nie przegra
         *
         * wskazuje tę samą drużynę co:
         *
         * Termalica wygra
         *
         * Różnica dotyczy wyłącznie późniejszego settlementu:
         *
         * W albo remis => W
         * porażka       => L
         */
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
                    winner.selection(),
                    subject,
                    format
            );
        }

        return switch (
                winner.status()
                ) {
            case SUBJECT_NOT_FOUND ->
                    new ParseResult(
                            Status.SUBJECT_NOT_FOUND,
                            null,
                            subject,
                            format
                    );

            case SUBJECT_MISMATCH ->
                    new ParseResult(
                            Status.SUBJECT_MISMATCH,
                            null,
                            subject,
                            format
                    );

            case SUBJECT_AMBIGUOUS ->
                    new ParseResult(
                            Status.SUBJECT_AMBIGUOUS,
                            null,
                            subject,
                            format
                    );

            case NOT_WINNER,
                 UNSUPPORTED_COMPOSITE ->
                    new ParseResult(
                            Status.SUBJECT_NOT_FOUND,
                            null,
                            subject,
                            format
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

    private static boolean looksLikeDoubleChanceNormalized(
            String text
    ) {
        if (
                text.isBlank()
        ) {
            return false;
        }

        return text.contains(
                "nie przegra"
        )
                || text.contains(
                "wygra lub remis"
        )
                || text.contains(
                "wygra lub zremis"
        )
                || text.contains(
                "wygra mecz lub zremis"
        )
                || text.contains(
                "zwyciestwo lub remis"
        )
                || text.contains(
                "podwojna szansa"
        )
                || text.contains(
                "double chance"
        );
    }

    /*
     * =========================================================
     * LOCAL SUBJECT NORMALIZATION
     * =========================================================
     */

    private static String normalizeSubject(
            String value
    ) {
        String result =
                value == null
                        ? ""
                        : value.trim();

        /*
         * FootballWinnerParser jest zamknięty.
         *
         * RPA występuje w źródle jako skrót,
         * podczas gdy API-Football ma:
         *
         * South Africa
         */
        if (
                "rpa".equals(
                        result
                )
        ) {
            result =
                    "south africa";
        }

        return result.trim();
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
                null,
                null
        );
    }

    public enum Format {
        NOT_LOSE,
        WIN_OR_DRAW
    }

    public enum Status {
        PARSED,
        NOT_DOUBLE_CHANCE,
        UNSUPPORTED_COMPOSITE,
        SUBJECT_NOT_FOUND,
        SUBJECT_MISMATCH,
        SUBJECT_AMBIGUOUS
    }

    public record ParseResult(
            Status status,
            FootballWinnerParser.Selection selection,
            String subject,
            Format format
    ) {

        public boolean parsed() {
            return status
                    == Status.PARSED;
        }
    }
}