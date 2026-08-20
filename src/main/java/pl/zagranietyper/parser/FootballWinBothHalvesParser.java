package pl.zagranietyper.parser;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FootballWinBothHalvesParser {

    private static final Pattern DIACRITIC_MARKS =
            Pattern.compile("\\p{M}+");

    private static final Pattern NON_ALPHANUMERIC =
            Pattern.compile("[^\\p{L}\\p{N}]+");

    private static final Pattern MULTIPLE_SPACES =
            Pattern.compile("\\s+");

    /*
     * Obsługiwane:
     *
     * Al Nassr wygra obie połowy
     * Fiorentina wygra obie połowy
     * Liverpool wygra obie połowy
     * Anglia wygra obie połowy
     *
     * Dopuszczamy też jawne:
     *
     * X wygra obie połowy - TAK
     * X wygra obie połowy - NIE
     *
     * choć historycznie w obecnej puli ich nie ma.
     */
    private static final Pattern MARKET_PATTERN =
            Pattern.compile(
                    "^(.+?)\\s+wygra\\s+obie\\s+polowy"
                            + "(?:\\s+(tak|nie))?$"
            );

    /*
     * Lokalne aliasy tej rodziny.
     */
    private static final Map<String, String> LOCAL_SUBJECT_ALIASES =
            Map.ofEntries(
                    Map.entry(
                            "anglia",
                            "england"
                    )
            );

    private final FootballWinnerParser winnerParser;

    public FootballWinBothHalvesParser() {
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
                !looksLikeWinBothHalvesNormalized(
                        text
                )
        ) {
            return rejected(
                    Status.NOT_WIN_BOTH_HALVES
            );
        }

        Matcher matcher =
                MARKET_PATTERN.matcher(
                        text
                );

        if (
                !matcher.matches()
        ) {
            return rejected(
                    Status.UNSUPPORTED_COMPOSITE
            );
        }

        String subject =
                normalizeSubject(
                        matcher.group(
                                1
                        )
                );

        String explicitAnswer =
                matcher.group(
                        2
                );

        boolean expectedYes =
                !"nie".equals(
                        explicitAnswer
                );

        return parseSubject(
                subject,
                homeTeam,
                awayTeam,
                expectedYes
        );
    }

    public boolean looksLikeWinBothHalves(
            String tipTitle
    ) {
        return looksLikeWinBothHalvesNormalized(
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
            boolean expectedYes
    ) {
        if (
                subject == null
                        || subject.isBlank()
        ) {
            return new ParseResult(
                    Status.SUBJECT_NOT_FOUND,
                    null,
                    subject,
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
                            null,
                            subject,
                            expectedYes
                    );

            case SUBJECT_MISMATCH ->
                    new ParseResult(
                            Status.SUBJECT_MISMATCH,
                            null,
                            subject,
                            expectedYes
                    );

            case SUBJECT_AMBIGUOUS ->
                    new ParseResult(
                            Status.SUBJECT_AMBIGUOUS,
                            null,
                            subject,
                            expectedYes
                    );

            case NOT_WINNER,
                 UNSUPPORTED_COMPOSITE ->
                    new ParseResult(
                            Status.SUBJECT_NOT_FOUND,
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

    private static boolean looksLikeWinBothHalvesNormalized(
            String text
    ) {
        return !text.isBlank()
                && text.contains(
                "wygra obie polowy"
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
                false
        );
    }

    public enum Status {
        PARSED,
        NOT_WIN_BOTH_HALVES,
        UNSUPPORTED_COMPOSITE,
        SUBJECT_NOT_FOUND,
        SUBJECT_MISMATCH,
        SUBJECT_AMBIGUOUS
    }

    public record ParseResult(
            Status status,
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