package pl.zagranietyper.parser;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class FootballHalfDrawParser {

    private static final Pattern DIACRITIC_MARKS =
            Pattern.compile("\\p{M}+");

    private static final Pattern NON_ALPHANUMERIC =
            Pattern.compile("[^\\p{L}\\p{N}]+");

    private static final Pattern MULTIPLE_SPACES =
            Pattern.compile("\\s+");

    /*
     * 1. połowa: remis
     * Pierwsza połowa: remis
     */
    private static final Pattern FIRST_HALF_DRAW =
            Pattern.compile(
                    "^(?:1|pierwsza) polowa remis$"
            );

    /*
     * Remis do przerwy
     */
    private static final Pattern DRAW_AT_HALFTIME =
            Pattern.compile(
                    "^remis do przerwy$"
            );

    /*
     * 1. połowa lub mecz - REMIS
     * Pierwsza połowa lub mecz - Remis
     */
    private static final Pattern HALF_OR_MATCH_DRAW =
            Pattern.compile(
                    "^(?:1|pierwsza) polowa lub mecz remis$"
            );

    /*
     * Remis w 1. połowie lub na koniec meczu
     *
     * Jeden historyczny rekord ma dopisek:
     *
     * (0-0)
     *
     * Po normalizacji:
     *
     * remis w 1 polowie lub na koniec meczu 0 0
     *
     * Traktujemy go jako ten sam rynek.
     */
    private static final Pattern DRAW_HALF_OR_END =
            Pattern.compile(
                    "^remis w (?:1|pierwszej) polowie "
                            + "lub na koniec meczu(?: 0 0)?$"
            );

    public ParseResult parse(
            String tipTitle
    ) {
        String text =
                normalize(
                        tipTitle
                );

        if (
                FIRST_HALF_DRAW.matcher(text).matches()
                        || DRAW_AT_HALFTIME.matcher(text).matches()
        ) {
            return new ParseResult(
                    Status.PARSED,
                    Market.HALFTIME_DRAW,
                    text
            );
        }

        if (
                HALF_OR_MATCH_DRAW.matcher(text).matches()
                        || DRAW_HALF_OR_END.matcher(text).matches()
        ) {
            return new ParseResult(
                    Status.PARSED,
                    Market.HALF_OR_FULL_DRAW,
                    text
            );
        }

        if (
                looksLikeHalfDrawNormalized(
                        text
                )
        ) {
            return new ParseResult(
                    Status.UNSUPPORTED_COMPOSITE,
                    null,
                    text
            );
        }

        return new ParseResult(
                Status.NOT_HALF_DRAW,
                null,
                text
        );
    }

    public boolean looksLikeHalfDraw(
            String tipTitle
    ) {
        return looksLikeHalfDrawNormalized(
                normalize(
                        tipTitle
                )
        );
    }

    private static boolean looksLikeHalfDrawNormalized(
            String text
    ) {
        if (
                text.isBlank()
                        || !text.contains("remis")
        ) {
            return false;
        }

        return text.contains("polow")
                || text.contains("przerw");
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
                        .replace('ł', 'l')
                        .replace('Ł', 'L');

        String decomposed =
                Normalizer.normalize(
                        transliterated,
                        Normalizer.Form.NFD
                );

        String withoutMarks =
                DIACRITIC_MARKS
                        .matcher(decomposed)
                        .replaceAll("");

        String lower =
                withoutMarks.toLowerCase(
                        Locale.ROOT
                );

        String cleaned =
                NON_ALPHANUMERIC
                        .matcher(lower)
                        .replaceAll(" ");

        return MULTIPLE_SPACES
                .matcher(cleaned)
                .replaceAll(" ")
                .trim();
    }

    public enum Market {
        HALFTIME_DRAW,
        HALF_OR_FULL_DRAW
    }

    public enum Status {
        PARSED,
        NOT_HALF_DRAW,
        UNSUPPORTED_COMPOSITE
    }

    public record ParseResult(
            Status status,
            Market market,
            String normalizedTitle
    ) {

        public boolean parsed() {
            return status == Status.PARSED;
        }
    }
}