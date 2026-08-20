package pl.zagranietyper.parser;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class FootballDrawParser {

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
     * Zamknięta lista jednoznacznych PURE_DRAW.
     *
     * Symbole typu:
     *
     * ❌ Remis
     * ⏳ Remis
     *
     * znikają podczas normalizacji i kończą jako "remis".
     */
    private static final Set<String> PURE_DRAW_FORMS =
            Set.of(
                    "remis",
                    "remis w meczu",
                    "padnie remis",
                    "bedzie remis",
                    "mecz zakonczy sie remisem",
                    "spotkanie zakonczy sie remisem",
                    "zakonczy sie remisem"
            );

    public ParseResult parse(
            String tipTitle
    ) {
        String text =
                normalize(
                        tipTitle
                );

        if (
                !looksLikeDrawNormalized(
                        text
                )
        ) {
            return new ParseResult(
                    Status.NOT_DRAW_LIKE,
                    text
            );
        }

        /*
         * DNB musi mieć pierwszeństwo przed czystym DRAW.
         *
         * Przykłady:
         *
         * zakład bez remisu - 2
         * Osasuna - remis zwrot
         * Ruch Chorzów (remis - zwrot)
         */
        if (
                isDrawNoBet(
                        text
                )
        ) {
            return new ParseResult(
                    Status.UNSUPPORTED_DRAW_NO_BET,
                    text
            );
        }

        /*
         * Remis do przerwy / remis w 1. połowie
         * jest osobną rodziną.
         */
        if (
                isHalfDraw(
                        text
                )
        ) {
            return new ParseResult(
                    Status.UNSUPPORTED_HALF,
                    text
            );
        }

        /*
         * Double chance:
         *
         * A wygra lub remis
         * A wygra lub zremisuje
         * A nie przegra
         */
        if (
                isDoubleChance(
                        text
                )
        ) {
            return new ParseResult(
                    Status.UNSUPPORTED_DOUBLE_CHANCE,
                    text
            );
        }

        if (
                PURE_DRAW_FORMS.contains(
                        text
                )
        ) {
            return new ParseResult(
                    Status.PARSED,
                    text
            );
        }

        /*
         * Wygląda na rynek związany z remisem,
         * ale nie należy do naszej zamkniętej gramatyki.
         */
        return new ParseResult(
                Status.UNSUPPORTED_COMPOSITE,
                text
        );
    }

    public boolean looksLikeDraw(
            String tipTitle
    ) {
        return looksLikeDrawNormalized(
                normalize(
                        tipTitle
                )
        );
    }

    /*
     * =========================================================
     * CLASSIFICATION
     * =========================================================
     */

    private static boolean looksLikeDrawNormalized(
            String text
    ) {
        if (
                text.isBlank()
        ) {
            return false;
        }

        return text.contains(
                "remis"
        )
                || text.contains(
                "zremis"
        )
                || text.contains(
                "draw"
        )
                || text.contains(
                "podzial punktow"
        )
                || text.contains(
                "bez remisu"
        );
    }

    private static boolean isDrawNoBet(
            String text
    ) {
        return text.contains(
                "bez remisu"
        )
                || text.contains(
                "draw no bet"
        )
                || containsStandaloneDnb(
                text
        )
                || text.contains(
                "remis zwrot"
        )
                || text.contains(
                "remis to zwrot"
        )
                || text.contains(
                "remis rowna sie zwrot"
        );
    }

    private static boolean containsStandaloneDnb(
            String text
    ) {
        if (
                "dnb".equals(
                        text
                )
        ) {
            return true;
        }

        return text.startsWith(
                "dnb "
        )
                || text.endsWith(
                " dnb"
        )
                || text.contains(
                " dnb "
        );
    }

    private static boolean isHalfDraw(
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

    private static boolean isDoubleChance(
            String text
    ) {
        return text.contains(
                "nie przegra"
        )
                || text.contains(
                "lub remis"
        )
                || text.contains(
                "lub zremis"
        )
                || text.contains(
                "wygra lub"
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

    public enum Status {
        PARSED,
        NOT_DRAW_LIKE,
        UNSUPPORTED_DRAW_NO_BET,
        UNSUPPORTED_HALF,
        UNSUPPORTED_DOUBLE_CHANCE,
        UNSUPPORTED_COMPOSITE
    }

    public record ParseResult(
            Status status,
            String normalizedTitle
    ) {

        public boolean parsed() {
            return status
                    == Status.PARSED;
        }
    }
}