package pl.zagranietyper.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EditorialTipDetector {

    private static final Pattern TYPE_PATTERN =
            Pattern.compile(
                    "(?i)"
                            + "['\"]type['\"]"
                            + "\\s*:\\s*"
                            + "['\"]Editorial\\s+Tip['\"]"
            );

    private static final Pattern TIP_ODDS_PATTERN =
            Pattern.compile(
                    "(?i)"
                            + "['\"]tip_odds['\"]"
                            + "\\s*:\\s*"
                            + "['\"]([^'\"]*)['\"]"
            );

    private static final Pattern CTA_TEXT_PATTERN =
            Pattern.compile(
                    "(?i)"
                            + "['\"]cta_text['\"]"
                            + "\\s*:\\s*"
                            + "['\"]([^'\"]*)['\"]"
            );

    /*
     * Jeżeli w polu tip_odds pojawia się słowo jednoznacznie
     * związane z promocją, nie jest to normalny kurs dziesiętny.
     *
     * Celowo używamy rdzeni słów:
     *
     * bonus -> bonus, bonusu, bonusów, bonusem...
     * promoc -> promocja, promocji, promocyjny...
     * premi -> premia, premii...
     * zwrot -> zwrot, zwrotu...
     */
    private static final Pattern STRONG_PROMOTIONAL_ODDS_PATTERN =
            Pattern.compile(
                    "(?iu)(?:"
                            + "\\bbonus\\p{L}*\\b"
                            + "|\\bfreebet\\p{L}*\\b"
                            + "|\\bcashback\\p{L}*\\b"
                            + "|\\bpromoc\\p{L}*\\b"
                            + "|\\bpremi\\p{L}*\\b"
                            + "|\\bzwrot\\p{L}*\\b"
                            + ")"
            );

    /*
     * Samo PLN/zł nie wystarcza do automatycznego odrzucenia.
     * Traktujemy je jako słabszy sygnał wymagający dodatkowo
     * promocyjnego CTA.
     */
    private static final Pattern CURRENCY_PATTERN =
            Pattern.compile(
                    "(?iu)(?:"
                            + "\\bpln\\b"
                            + "|\\bzł\\b"
                            + ")"
            );

    private static final Pattern PROMOTIONAL_CTA_PATTERN =
            Pattern.compile(
                    "(?iu)(?:"
                            + "\\bodbierz\\b"
                            + "|\\bzgarnij\\b"
                            + "|\\baktywuj\\b"
                            + "|\\bskorzystaj\\b"
                            + "|\\bbonus\\p{L}*\\b"
                            + "|\\bpromoc\\p{L}*\\b"
                            + "|\\bfreebet\\p{L}*\\b"
                            + "|\\bcashback\\p{L}*\\b"
                            + "|\\bpremi\\p{L}*\\b"
                            + "|\\bzwrot\\p{L}*\\b"
                            + ")"
            );

    public int countEditorialTips(
            String html
    ) {
        if (
                html == null
                        || html.isBlank()
        ) {
            return 0;
        }

        Document document =
                Jsoup.parse(
                        html
                );

        int count = 0;

        for (
                Element block :
                document.select(
                        ".bcb-atts"
                )
        ) {
            if (
                    isEditorialTip(
                            block
                    )
                            && !isPromotionalEditorialTip(
                            block
                    )
            ) {
                count++;
            }
        }

        return count;
    }

    public static boolean isPromotionalEditorialTip(
            Map<String, String> attributes
    ) {
        if (
                attributes == null
                        || attributes.isEmpty()
        ) {
            return false;
        }

        String tipOdds =
                valueIgnoreCase(
                        attributes,
                        "tip_odds"
                );

        String ctaText =
                valueIgnoreCase(
                        attributes,
                        "cta_text"
                );

        return isPromotionalValues(
                tipOdds,
                ctaText
        );
    }

    private boolean isEditorialTip(
            Element block
    ) {
        String directType =
                block.attr(
                        "type"
                );

        if (
                "Editorial Tip"
                        .equalsIgnoreCase(
                                directType.trim()
                        )
        ) {
            return true;
        }

        String dataAtts =
                block.attr(
                        "data-atts"
                );

        if (
                dataAtts == null
                        || dataAtts.isBlank()
        ) {
            return false;
        }

        return TYPE_PATTERN
                .matcher(
                        dataAtts
                )
                .find();
    }

    private boolean isPromotionalEditorialTip(
            Element block
    ) {
        String directOdds =
                blankToNull(
                        block.attr(
                                "tip_odds"
                        )
                );

        String directCta =
                blankToNull(
                        block.attr(
                                "cta_text"
                        )
                );

        String dataAtts =
                block.attr(
                        "data-atts"
                );

        String tipOdds =
                directOdds != null
                        ? directOdds
                        : extractValue(
                        TIP_ODDS_PATTERN,
                        dataAtts
                );

        String ctaText =
                directCta != null
                        ? directCta
                        : extractValue(
                        CTA_TEXT_PATTERN,
                        dataAtts
                );

        return isPromotionalValues(
                tipOdds,
                ctaText
        );
    }

    private static boolean isPromotionalValues(
            String tipOdds,
            String ctaText
    ) {
        String odds =
                normalize(
                        tipOdds
                );

        if (
                odds == null
        ) {
            return false;
        }

        /*
         * Mocny sygnał:
         *
         * "300 PLN bonus"
         * "197 PLN bonusu"
         * "bonus 300 zł"
         * "freebet"
         * "cashback"
         * "promocja"
         * itd.
         *
         * CTA nie jest już wymagane.
         */
        if (
                STRONG_PROMOTIONAL_ODDS_PATTERN
                        .matcher(
                                odds
                        )
                        .find()
        ) {
            return true;
        }

        /*
         * Słabszy przypadek:
         *
         * tip_odds = "300 PLN"
         *
         * Samo w sobie nie jest wystarczające.
         * Wymagamy dodatkowo promocyjnego CTA.
         */
        boolean containsCurrency =
                CURRENCY_PATTERN
                        .matcher(
                                odds
                        )
                        .find();

        if (
                !containsCurrency
        ) {
            return false;
        }

        String cta =
                normalize(
                        ctaText
                );

        if (
                cta == null
        ) {
            return false;
        }

        return PROMOTIONAL_CTA_PATTERN
                .matcher(
                        cta
                )
                .find();
    }

    private static String extractValue(
            Pattern pattern,
            String dataAtts
    ) {
        if (
                dataAtts == null
                        || dataAtts.isBlank()
        ) {
            return null;
        }

        Matcher matcher =
                pattern.matcher(
                        dataAtts
                );

        if (
                !matcher.find()
        ) {
            return null;
        }

        return blankToNull(
                matcher.group(1)
        );
    }

    private static String valueIgnoreCase(
            Map<String, String> attributes,
            String key
    ) {
        for (
                Map.Entry<String, String> entry :
                attributes.entrySet()
        ) {
            if (
                    entry.getKey()
                            .equalsIgnoreCase(
                                    key
                            )
            ) {
                return entry.getValue();
            }
        }

        return null;
    }

    private static String normalize(
            String value
    ) {
        if (
                value == null
        ) {
            return null;
        }

        String normalized =
                value
                        .replace(
                                '\u00A0',
                                ' '
                        )
                        .trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        );

        return normalized.isBlank()
                ? null
                : normalized;
    }

    private static String blankToNull(
            String value
    ) {
        return value == null
                || value.isBlank()
                ? null
                : value.trim();
    }
}