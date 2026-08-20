package pl.zagranietyper.parser;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FootballDrawNoBetParser {

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
     * zakład bez remisu - 1
     * zakład bez remisu - 2
     *
     * Po normalizacji:
     *
     * zaklad bez remisu 1
     * zaklad bez remisu 2
     */
    private static final Pattern NUMERIC_DNB =
            Pattern.compile(
                    "^zaklad bez remisu\\s+([12])$"
            );

    /*
     * Zakład bez remisu: Cracovia
     *
     * Po normalizacji:
     *
     * zaklad bez remisu cracovia
     */
    private static final Pattern NAMED_PREFIX_DNB =
            Pattern.compile(
                    "^zaklad bez remisu\\s+(.+)$"
            );

    /*
     * Osasuna - remis zwrot
     * Ruch Chorzów (remis - zwrot)
     * Wiczysta Kraków - remis zwrot
     *
     * Po normalizacji:
     *
     * osasuna remis zwrot
     * ruch chorzow remis zwrot
     * wiczysta krakow remis zwrot
     */
    private static final Pattern NAMED_REFUND_DNB =
            Pattern.compile(
                    "^(.+?)\\s+remis\\s+zwrot$"
            );

    private final FootballWinnerParser winnerParser;

    public FootballDrawNoBetParser() {
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
                !looksLikeDrawNoBetNormalized(
                        text
                )
        ) {
            return rejected(
                    Status.NOT_DRAW_NO_BET
            );
        }

        /*
         * =====================================================
         * FORMAT 1:
         *
         * zakład bez remisu - 1 / 2
         * =====================================================
         */

        Matcher numeric =
                NUMERIC_DNB.matcher(
                        text
                );

        if (
                numeric.matches()
        ) {
            String value =
                    numeric.group(
                            1
                    );

            FootballWinnerParser.Selection selection =
                    switch (
                            value
                            ) {
                        case "1" ->
                                FootballWinnerParser.Selection.HOME;

                        case "2" ->
                                FootballWinnerParser.Selection.AWAY;

                        default ->
                                throw new IllegalStateException(
                                        "Unexpected DNB selection="
                                                + value
                                );
                    };

            return new ParseResult(
                    Status.PARSED,
                    selection,
                    null,
                    Format.NUMERIC
            );
        }

        /*
         * =====================================================
         * FORMAT 2:
         *
         * Zakład bez remisu: Cracovia
         * =====================================================
         */

        Matcher namedPrefix =
                NAMED_PREFIX_DNB.matcher(
                        text
                );

        if (
                namedPrefix.matches()
        ) {
            String subject =
                    normalizeSubject(
                            namedPrefix.group(
                                    1
                            )
                    );

            return parseNamedSubject(
                    subject,
                    homeTeam,
                    awayTeam,
                    Format.NAMED_PREFIX
            );
        }

        /*
         * =====================================================
         * FORMAT 3:
         *
         * Osasuna - remis zwrot
         * Ruch Chorzów (remis - zwrot)
         * =====================================================
         */

        Matcher namedRefund =
                NAMED_REFUND_DNB.matcher(
                        text
                );

        if (
                namedRefund.matches()
        ) {
            String subject =
                    normalizeSubject(
                            namedRefund.group(
                                    1
                            )
                    );

            return parseNamedSubject(
                    subject,
                    homeTeam,
                    awayTeam,
                    Format.NAMED_REFUND
            );
        }

        return rejected(
                Status.UNSUPPORTED_FORMAT
        );
    }

    public boolean looksLikeDrawNoBet(
            String tipTitle
    ) {
        return looksLikeDrawNoBetNormalized(
                normalize(
                        tipTitle
                )
        );
    }

    /*
     * =========================================================
     * NAMED PARTICIPANT
     * =========================================================
     */

    private ParseResult parseNamedSubject(
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

    private static boolean looksLikeDrawNoBetNormalized(
            String text
    ) {
        if (
                text.isBlank()
        ) {
            return false;
        }

        return text.contains(
                "bez remisu"
        )
                || text.contains(
                "remis zwrot"
        )
                || text.contains(
                "draw no bet"
        )
                || containsStandaloneDnb(
                text
        );
    }

    private static boolean containsStandaloneDnb(
            String text
    ) {
        return "dnb".equals(
                text
        )
                || text.startsWith(
                "dnb "
        )
                || text.endsWith(
                " dnb"
        )
                || text.contains(
                " dnb "
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
         * Literówka istniejąca w źródle:
         *
         * Wiczysta Kraków
         *
         * fixture:
         *
         * Wieczysta Kraków
         */
        result =
                result.replaceFirst(
                        "^wiczysta\\b",
                        "wieczysta"
                );

        /*
         * Zagranie:
         *
         * Zakład bez remisu: Cracovia
         *
         * API-Football:
         *
         * Cracovia Krakow
         *
         * Globalnego FootballWinnerParser nie ruszamy,
         * więc rozwijamy skrót lokalnie.
         */
        if (
                "cracovia".equals(
                        result
                )
        ) {
            result =
                    "cracovia krakow";
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
        NUMERIC,
        NAMED_PREFIX,
        NAMED_REFUND
    }

    public enum Status {
        PARSED,
        NOT_DRAW_NO_BET,
        UNSUPPORTED_FORMAT,
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