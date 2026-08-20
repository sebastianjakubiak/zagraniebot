package pl.zagranietyper.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import pl.zagranietyper.model.BetType;
import pl.zagranietyper.model.EventMetadata;
import pl.zagranietyper.model.OddsConsistency;
import pl.zagranietyper.model.OddsSource;
import pl.zagranietyper.model.ParsedBet;
import pl.zagranietyper.model.ParsedLeg;
import pl.zagranietyper.model.ParsedPost;
import pl.zagranietyper.model.TipContext;
import pl.zagranietyper.wp.WpPost;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ZagraniePostParser {

    private static final Logger LOG =
            Logger.getLogger(
                    ZagraniePostParser.class.getName()
            );

    private static final ZoneId WARSAW =
            ZoneId.of("Europe/Warsaw");

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper();

    private static final Pattern TITLE_ODDS_PATTERN =
            Pattern.compile(
                    "(?iu)(?:kursem|ako)\\s*[:=-]?\\s*([0-9]+(?:[.,][0-9]+)?)"
            );

    private static final Pattern AKO_ODDS_PATTERN =
            Pattern.compile(
                    "(?iu)\\bako\\b\\s*[:=-]?\\s*([0-9]+(?:[.,][0-9]+)?)"
            );

    private static final Pattern SINGLE_MARKER_PATTERN =
            Pattern.compile(
                    "(?iu)\\b(?:singiel|single|singlem|singla)\\b"
            );

    private static final Pattern MULTI_AKO_SECTION_PATTERN =
            Pattern.compile(
                    "(?iu)\\bako\\b\\s+(.+)"
            );

    private static final Pattern DECIMAL_ODDS_PATTERN =
            Pattern.compile(
                    "(?<!\\d)([1-9][0-9]*[.,][0-9]+)(?!\\d)"
            );

    private static final Pattern TITLE_PAYOUT_SIGNAL_PATTERN =
            Pattern.compile(
                    "(?iu)(?:gramy\\s+)?(?:o|za)\\s+"
                            + "[0-9]+(?:[.,][0-9]+)?\\s*pln"
            );

    private static final Pattern COMBINED_TITLE_PATTERN =
            Pattern.compile(
                    "(?iu)(?:\\bako\\b|double|dubel|triple|kupon\\p{L}*)"
            );

    private static final Pattern COMBINED_CONTEXT_PATTERN =
            Pattern.compile(
                    "(?iu)(?:"
                            + "propozycja\\s+kuponu"
                            + "|gotowy\\s+kupon"
                            + "|kupon"
                            + "|\\bako\\b"
                            + "|double"
                            + "|dubel"
                            + "|triple"
                            + ")"
            );

    private static final Pattern NUMERIC_KEY_PATTERN =
            Pattern.compile(
                    "\\d+"
            );

    private static final Pattern BROKEN_TIP_TITLE_PREFIX =
            Pattern.compile(
                    "(?iu)^tip_title\\s*=\\s*['\"]?"
            );

    private static final Pattern BROKEN_HTML_ATTRIBUTE_PREFIX =
            Pattern.compile(
                    "(?is)^[\\p{L}\\p{N}_:-]+"
                            + "\\s*=\\s*"
                            + "['\"][^'\"]*['\"]"
                            + "\\s*>\\s*"
            );

    private static final BigDecimal ODDS_MATCH_TOLERANCE =
            new BigDecimal(
                    "0.03"
            );

    public ParsedPost parse(
            WpPost wpPost,
            String html
    ) {
        if (
                html == null
                        || html.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Pusty HTML dla WP post id="
                            + wpPost.id()
            );
        }

        Document document =
                Jsoup.parse(
                        html
                );

        String title =
                decodeHtml(
                        wpPost.renderedTitle()
                );

        List<Map<String, String>> rawBlocks =
                new ArrayList<>();

        List<ParsedLeg> legs =
                new ArrayList<>();

        List<Element> eventBlocks =
                document.select(
                        ".bcb-sport-block-data[data-event]"
                );

        EventMetadata defaultEvent =
                eventBlocks.size() == 1
                        ? parseEventBlock(
                        eventBlocks.getFirst()
                )
                        : EventMetadata.empty();

        EventMetadata currentEvent =
                EventMetadata.empty();

        String currentHeading =
                null;

        String previousText =
                null;

        int legOrdinal =
                0;

        for (
                Element element :
                document.getAllElements()
        ) {
            if (
                    isHeading(
                            element
                    )
            ) {
                currentHeading =
                        truncate(
                                normalizeText(
                                        element.text()
                                ),
                                500
                        );

                previousText =
                        null;

                continue;
            }

            if (
                    element.hasClass(
                            "bcb-sport-block-data"
                    )
                            && element.hasAttr(
                            "data-event"
                    )
            ) {
                rawBlocks.add(
                        attributes(
                                element
                        )
                );

                currentEvent =
                        parseEventBlock(
                                element
                        );

                continue;
            }

            if (
                    element.hasClass(
                            "bcb-atts"
                    )
            ) {
                Map<String, String> attributes =
                        extractPickAttributes(
                                element
                        );

                String type =
                        valueIgnoreCase(
                                attributes,
                                "type"
                        );

                if (
                        "Editorial Tip"
                                .equalsIgnoreCase(
                                        type
                                )
                                && EditorialTipDetector
                                .isPromotionalEditorialTip(
                                        attributes
                                )
                ) {
                    appendWarning(
                            attributes,
                            "PROMOTIONAL_EDITORIAL_TIP_SKIPPED"
                    );

                    rawBlocks.add(
                            new LinkedHashMap<>(
                                    attributes
                            )
                    );

                    LOG.info(
                            "Pomijam promocyjny pseudo Editorial Tip"
                                    + " | post="
                                    + wpPost.id()
                                    + " | tip="
                                    + nullToEmpty(
                                    valueIgnoreCase(
                                            attributes,
                                            "tip_title"
                                    )
                            )
                                    + " | tip_odds="
                                    + nullToEmpty(
                                    valueIgnoreCase(
                                            attributes,
                                            "tip_odds"
                                    )
                            )
                                    + " | cta="
                                    + nullToEmpty(
                                    valueIgnoreCase(
                                            attributes,
                                            "cta_text"
                                    )
                            )
                    );

                    continue;
                }

                rawBlocks.add(
                        new LinkedHashMap<>(
                                attributes
                        )
                );

                if (
                        !"Editorial Tip"
                                .equalsIgnoreCase(
                                        type
                                )
                ) {
                    continue;
                }

                legOrdinal++;

                EventMetadata event;

                if (
                        hasAnyEventData(
                                currentEvent
                        )
                ) {
                    event =
                            currentEvent;

                } else if (
                        hasAnyEventData(
                                defaultEvent
                        )
                ) {
                    event =
                            defaultEvent;

                } else {
                    event =
                            EventMetadata.empty();
                }

                TipContext context =
                        new TipContext(
                                currentHeading,
                                previousText
                        );

                legs.add(
                        parseLeg(
                                wpPost.id(),
                                legOrdinal,
                                attributes,
                                event,
                                context
                        )
                );

                continue;
            }

            if (
                    isUsefulProseElement(
                            element
                    )
            ) {
                String text =
                        normalizeText(
                                element.text()
                        );

                if (
                        text != null
                                && !text.isBlank()
                ) {
                    previousText =
                            truncate(
                                    text,
                                    1000
                            );
                }
            }
        }

        List<ParsedBet> bets =
                buildBets(
                        wpPost.id(),
                        title,
                        legs
                );

        Instant publishedAt =
                parseWpDate(
                        wpPost.dateGmt(),
                        wpPost.date()
                );

        Instant modifiedAt =
                parseWpDateNullable(
                        wpPost.modifiedGmt(),
                        wpPost.modified()
                );

        return new ParsedPost(
                wpPost.id(),
                wpPost.author(),
                wpPost.slug(),
                title,
                wpPost.link(),
                publishedAt,
                modifiedAt,
                html,
                sha256(
                        html
                ),
                List.copyOf(
                        rawBlocks
                ),
                bets
        );
    }

    private static List<ParsedBet> buildBets(
            long wpPostId,
            String title,
            List<ParsedLeg> legs
    ) {
        if (
                legs.isEmpty()
        ) {
            return List.of();
        }

        if (
                legs.size() == 1
        ) {
            return List.of(
                    buildSingleBet(
                            wpPostId,
                            1,
                            legs.getFirst()
                    )
            );
        }

        /*
         * Historyczny format:
         *
         * "AKO 3.23 i singiel 3.30"
         *
         * albo:
         *
         * "AKO 5.39 + single 5.00 i 5.40"
         *
         * Kursy singli zapisane w tytule mogą być już
         * nieaktualne względem tip_odds w blokach.
         *
         * Dlatego:
         *
         * 1. z tytułu odczytujemy kurs AKO,
         * 2. z tytułu odczytujemy LICZBĘ singli,
         * 3. szukamy subsetu minimum 2 legów, którego
         *    iloczyn odpowiada AKO,
         * 4. liczba pozostałych legów musi odpowiadać
         *    liczbie singli z tytułu,
         * 5. musi istnieć dokładnie jeden taki podział.
         *
         * Nie dzielimy po kolejności, ponieważ single
         * potrafią być w HTML pomiędzy legami AKO.
         */
        if (
                containsAkoPlusSingleSignal(
                        title
                )
        ) {
            AkoPlusSinglesTitle mixedTitle =
                    extractAkoPlusSinglesTitle(
                            title
                    );

            if (
                    mixedTitle != null
            ) {
                List<ParsedBet> partitioned =
                        tryPartitionAkoPlusSingles(
                                wpPostId,
                                mixedTitle,
                                legs
                        );

                if (
                        partitioned != null
                ) {
                    LOG.info(
                            "Rozbito post na AKO + SINGLE"
                                    + " | post="
                                    + wpPostId
                                    + " | ako="
                                    + mixedTitle.akoOdds()
                                    + " | advertisedSingles="
                                    + mixedTitle.singleOdds()
                                    + " | bets="
                                    + partitioned.size()
                    );

                    return partitioned;
                }

                LOG.warning(
                        "Nie udało się jednoznacznie rozbić "
                                + "AKO + SINGLE"
                                + " | post="
                                + wpPostId
                                + " | ako="
                                + mixedTitle.akoOdds()
                                + " | advertisedSingles="
                                + mixedTitle.singleOdds()
                                + " | legs="
                                + legs.size()
                );

            } else {
                LOG.warning(
                        "Tytuł wygląda na AKO + SINGLE, "
                                + "ale nie udało się odczytać kursów"
                                + " | post="
                                + wpPostId
                                + " | title="
                                + title
                );
            }

            return List.of(
                    buildMultiUnverified(
                            wpPostId,
                            1,
                            legs
                    )
            );
        }

        List<BigDecimal> advertisedAkoOdds =
                extractMultipleAkoOdds(
                        title
                );

        if (
                advertisedAkoOdds.size() > 1
        ) {
            List<ParsedBet> partitioned =
                    tryPartitionByAdvertisedOdds(
                            wpPostId,
                            advertisedAkoOdds,
                            legs
                    );

            if (
                    partitioned != null
            ) {
                LOG.info(
                        "Rozbito post na wiele COMBINED po kursach z tytułu"
                                + " | post="
                                + wpPostId
                                + " | bets="
                                + partitioned.size()
                                + " | odds="
                                + advertisedAkoOdds
                );

                return partitioned;
            }

            LOG.warning(
                    "Nie udało się rozwiązać wielu AKO z tytułu"
                            + " | post="
                            + wpPostId
                            + " | odds="
                            + advertisedAkoOdds
                            + " | legs="
                            + legs.size()
            );

            return List.of(
                    buildMultiUnverified(
                            wpPostId,
                            1,
                            legs
                    )
            );
        }

        BigDecimal titleOdds =
                extractTitleOdds(
                        title
                );

        /*
         * Osobne promocyjne typy z identycznym pełnym
         * kursem jak kurs reklamowany w tytule.
         *
         * title = 4.40
         * leg 1 = 4.40
         * leg 2 = 4.40
         * leg 3 = 4.40
         *
         * => 3 × SINGLE, a nie COMBINED @85.184.
         */
        if (
                titleOdds != null
                        && allLegOddsEqualTitleOdds(
                        legs,
                        titleOdds
                )
        ) {
            LOG.warning(
                    "Wiele legów z pełnym kursem równym kursowi z tytułu"
                            + " | post="
                            + wpPostId
                            + " | titleOdds="
                            + titleOdds
                            + " | legs="
                            + legs.size()
                            + " | zapisuję jako osobne SINGLE"
            );

            return buildSeparateSingles(
                    wpPostId,
                    legs
            );
        }

        boolean payoutSignal =
                containsPayoutSignal(
                        title
                );

        boolean combinedSignal =
                titleOdds != null
                        || payoutSignal
                        || containsCombinedTitleSignal(
                        title
                )
                        || contextSignalsCombined(
                        legs
                );

        if (
                combinedSignal
        ) {
            return List.of(
                    buildCombinedBet(
                            wpPostId,
                            1,
                            titleOdds,
                            legs
                    )
            );
        }

        return buildSeparateSingles(
                wpPostId,
                legs
        );
    }

    private static boolean containsAkoPlusSingleSignal(
            String title
    ) {
        if (
                title == null
                        || title.isBlank()
        ) {
            return false;
        }

        return AKO_ODDS_PATTERN
                .matcher(
                        title
                )
                .find()
                && SINGLE_MARKER_PATTERN
                .matcher(
                        title
                )
                .find();
    }

    private static AkoPlusSinglesTitle extractAkoPlusSinglesTitle(
            String title
    ) {
        if (
                title == null
                        || title.isBlank()
        ) {
            return null;
        }

        Matcher akoMatcher =
                AKO_ODDS_PATTERN.matcher(
                        title
                );

        if (
                !akoMatcher.find()
        ) {
            return null;
        }

        BigDecimal akoOdds =
                parseAdvertisedOdds(
                        akoMatcher.group(
                                1
                        )
                );

        if (
                akoOdds == null
        ) {
            return null;
        }

        Matcher singleMarkerMatcher =
                SINGLE_MARKER_PATTERN.matcher(
                        title
                );

        if (
                !singleMarkerMatcher.find(
                        akoMatcher.end()
                )
        ) {
            return null;
        }

        String singleSection =
                title.substring(
                        singleMarkerMatcher.end()
                );

        Matcher oddsMatcher =
                DECIMAL_ODDS_PATTERN.matcher(
                        singleSection
                );

        List<BigDecimal> singleOdds =
                new ArrayList<>();

        while (
                oddsMatcher.find()
        ) {
            BigDecimal value =
                    parseAdvertisedOdds(
                            oddsMatcher.group(
                                    1
                            )
                    );

            if (
                    value != null
            ) {
                singleOdds.add(
                        value
                );
            }
        }

        if (
                singleOdds.isEmpty()
        ) {
            return null;
        }

        return new AkoPlusSinglesTitle(
                akoOdds,
                List.copyOf(
                        singleOdds
                )
        );
    }

    private static BigDecimal parseAdvertisedOdds(
            String raw
    ) {
        if (
                raw == null
                        || raw.isBlank()
        ) {
            return null;
        }

        try {
            BigDecimal value =
                    new BigDecimal(
                            raw
                                    .trim()
                                    .replace(
                                            ',',
                                            '.'
                                    )
                    );

            if (
                    value.compareTo(
                            BigDecimal.ONE
                    ) < 0
                            || value.compareTo(
                            new BigDecimal(
                                    "100"
                            )
                    ) >= 0
            ) {
                return null;
            }

            return value;

        } catch (
                NumberFormatException e
        ) {
            return null;
        }
    }

    private static List<ParsedBet> tryPartitionAkoPlusSingles(
            long wpPostId,
            AkoPlusSinglesTitle title,
            List<ParsedLeg> legs
    ) {
        if (
                legs.size() < 3
                        || legs.size() > 20
        ) {
            return null;
        }

        for (
                ParsedLeg leg :
                legs
        ) {
            if (
                    leg.tipOdds() == null
            ) {
                return null;
            }
        }

        int expectedSingles =
                title.singleOdds()
                        .size();

        int expectedCombinedLegs =
                legs.size()
                        - expectedSingles;

        if (
                expectedSingles <= 0
                        || expectedCombinedLegs < 2
        ) {
            return null;
        }

        long maxMask =
                1L << legs.size();

        Long validCombinedMask =
                null;

        for (
                long combinedMask = 1;
                combinedMask < maxMask;
                combinedMask++
        ) {
            if (
                    Long.bitCount(
                            combinedMask
                    ) != expectedCombinedLegs
            ) {
                continue;
            }

            BigDecimal combinedProduct =
                    calculateProduct(
                            legs,
                            combinedMask
                    );

            if (
                    combinedProduct == null
                            || !matchesDisplayedOdds(
                            title.akoOdds(),
                            combinedProduct
                    )
            ) {
                continue;
            }

            /*
             * Drugi poprawny subset oznacza niejednoznaczność.
             * W takiej sytuacji świadomie NIE zgadujemy.
             */
            if (
                    validCombinedMask != null
            ) {
                return null;
            }

            validCombinedMask =
                    combinedMask;
        }

        if (
                validCombinedMask == null
        ) {
            return null;
        }

        List<ParsedLeg> combinedLegs =
                new ArrayList<>();

        List<ParsedLeg> singleLegs =
                new ArrayList<>();

        for (
                int i = 0;
                i < legs.size();
                i++
        ) {
            ParsedLeg leg =
                    legs.get(
                            i
                    );

            if (
                    (validCombinedMask & (1L << i))
                            != 0
            ) {
                combinedLegs.add(
                        leg
                );

            } else {
                singleLegs.add(
                        leg
                );
            }
        }

        if (
                singleLegs.size()
                        != expectedSingles
        ) {
            return null;
        }

        combinedLegs.sort(
                Comparator.comparingInt(
                        ParsedLeg::ordinal
                )
        );

        singleLegs.sort(
                Comparator.comparingInt(
                        ParsedLeg::ordinal
                )
        );

        List<ParsedBet> result =
                new ArrayList<>();

        int betOrdinal =
                1;

        result.add(
                buildCombinedBet(
                        wpPostId,
                        betOrdinal,
                        title.akoOdds(),
                        List.copyOf(
                                combinedLegs
                        )
                )
        );

        for (
                ParsedLeg singleLeg :
                singleLegs
        ) {
            betOrdinal++;

            result.add(
                    buildSingleBet(
                            wpPostId,
                            betOrdinal,
                            singleLeg
                    )
            );
        }

        return List.copyOf(
                result
        );
    }

    private static List<ParsedBet> buildSeparateSingles(
            long wpPostId,
            List<ParsedLeg> legs
    ) {
        List<ParsedBet> result =
                new ArrayList<>();

        int betOrdinal =
                0;

        for (
                ParsedLeg leg :
                legs
        ) {
            betOrdinal++;

            result.add(
                    buildSingleBet(
                            wpPostId,
                            betOrdinal,
                            leg
                    )
            );
        }

        return List.copyOf(
                result
        );
    }

    private static boolean allLegOddsEqualTitleOdds(
            List<ParsedLeg> legs,
            BigDecimal titleOdds
    ) {
        if (
                legs.size() < 2
                        || titleOdds == null
        ) {
            return false;
        }

        for (
                ParsedLeg leg :
                legs
        ) {
            if (
                    leg.tipOdds() == null
            ) {
                return false;
            }

            if (
                    leg.tipOdds()
                            .compareTo(
                                    titleOdds
                            ) != 0
            ) {
                return false;
            }
        }

        return true;
    }

    private static ParsedBet buildSingleBet(
            long wpPostId,
            int betOrdinal,
            ParsedLeg leg
    ) {
        BigDecimal odds =
                leg.tipOdds();

        BigDecimal calculatedOdds =
                odds == null
                        ? null
                        : odds.setScale(
                        4,
                        RoundingMode.HALF_UP
                );

        return new ParsedBet(
                betOrdinal,
                betFingerprint(
                        wpPostId,
                        betOrdinal
                ),
                BetType.SINGLE,
                odds,
                calculatedOdds,
                odds == null
                        ? OddsSource.NONE
                        : OddsSource.SINGLE_LEG,
                odds != null,
                odds == null
                        ? OddsConsistency.NOT_CHECKABLE
                        : OddsConsistency.MATCH,
                List.of(
                        leg
                )
        );
    }

    private static ParsedBet buildCombinedBet(
            long wpPostId,
            int betOrdinal,
            BigDecimal titleOdds,
            List<ParsedLeg> legs
    ) {
        BigDecimal rawProduct =
                calculateProduct(
                        legs
                );

        BigDecimal calculatedOdds =
                rawProduct == null
                        ? null
                        : rawProduct.setScale(
                        4,
                        RoundingMode.HALF_UP
                );

        OddsSource oddsSource;

        if (
                titleOdds != null
        ) {
            oddsSource =
                    OddsSource.TITLE;

        } else if (
                calculatedOdds != null
        ) {
            oddsSource =
                    OddsSource.CALCULATED;

        } else {
            oddsSource =
                    OddsSource.NONE;
        }

        boolean oddsVerified =
                titleOdds != null;

        OddsConsistency consistency =
                determineConsistency(
                        titleOdds,
                        rawProduct
                );

        return new ParsedBet(
                betOrdinal,
                betFingerprint(
                        wpPostId,
                        betOrdinal
                ),
                BetType.COMBINED,
                titleOdds,
                calculatedOdds,
                oddsSource,
                oddsVerified,
                consistency,
                legs
        );
    }

    private static ParsedBet buildMultiUnverified(
            long wpPostId,
            int betOrdinal,
            List<ParsedLeg> legs
    ) {
        BigDecimal product =
                calculateProduct(
                        legs
                );

        BigDecimal calculatedOdds =
                product == null
                        ? null
                        : product.setScale(
                        4,
                        RoundingMode.HALF_UP
                );

        return new ParsedBet(
                betOrdinal,
                betFingerprint(
                        wpPostId,
                        betOrdinal
                ),
                BetType.MULTI_UNVERIFIED,
                null,
                calculatedOdds,
                calculatedOdds == null
                        ? OddsSource.NONE
                        : OddsSource.CALCULATED,
                false,
                OddsConsistency.NOT_CHECKABLE,
                legs
        );
    }

    private static OddsConsistency determineConsistency(
            BigDecimal displayedOdds,
            BigDecimal calculatedOdds
    ) {
        if (
                displayedOdds == null
                        || calculatedOdds == null
        ) {
            return OddsConsistency.NOT_CHECKABLE;
        }

        return matchesDisplayedOdds(
                displayedOdds,
                calculatedOdds
        )
                ? OddsConsistency.MATCH
                : OddsConsistency.MISMATCH;
    }

    private static List<ParsedBet> tryPartitionByAdvertisedOdds(
            long wpPostId,
            List<BigDecimal> advertisedOdds,
            List<ParsedLeg> legs
    ) {
        for (
                ParsedLeg leg :
                legs
        ) {
            if (
                    leg.tipOdds() == null
            ) {
                return null;
            }
        }

        if (
                legs.size() > 20
        ) {
            return null;
        }

        PartitionSolution best =
                searchPartition(
                        advertisedOdds,
                        legs,
                        0,
                        0L,
                        new ArrayList<>(),
                        BigDecimal.ZERO,
                        null
                );

        if (
                best == null
        ) {
            return null;
        }

        long allMask =
                (1L << legs.size())
                        - 1L;

        if (
                best.usedMask()
                        != allMask
        ) {
            return null;
        }

        List<ParsedBet> result =
                new ArrayList<>();

        int betOrdinal =
                0;

        for (
                PartitionGroup group :
                best.groups()
        ) {
            betOrdinal++;

            List<ParsedLeg> groupLegs =
                    new ArrayList<>();

            for (
                    int i = 0;
                    i < legs.size();
                    i++
            ) {
                if (
                        (group.mask()
                                & (1L << i))
                                != 0
                ) {
                    groupLegs.add(
                            legs.get(
                                    i
                            )
                    );
                }
            }

            groupLegs.sort(
                    Comparator.comparingInt(
                            ParsedLeg::ordinal
                    )
            );

            BigDecimal calculated =
                    calculateProduct(
                            groupLegs
                    );

            OddsConsistency consistency =
                    determineConsistency(
                            group.advertisedOdds(),
                            calculated
                    );

            result.add(
                    new ParsedBet(
                            betOrdinal,
                            betFingerprint(
                                    wpPostId,
                                    betOrdinal
                            ),
                            BetType.COMBINED,
                            group.advertisedOdds(),
                            calculated == null
                                    ? null
                                    : calculated.setScale(
                                    4,
                                    RoundingMode.HALF_UP
                            ),
                            OddsSource.TITLE,
                            true,
                            consistency,
                            List.copyOf(
                                    groupLegs
                            )
                    )
            );
        }

        return List.copyOf(
                result
        );
    }

    private static PartitionSolution searchPartition(
            List<BigDecimal> targets,
            List<ParsedLeg> legs,
            int targetIndex,
            long usedMask,
            List<PartitionGroup> groups,
            BigDecimal totalError,
            PartitionSolution best
    ) {
        if (
                targetIndex >= targets.size()
        ) {
            if (
                    best == null
                            || totalError.compareTo(
                            best.totalError()
                    ) < 0
            ) {
                return new PartitionSolution(
                        usedMask,
                        List.copyOf(
                                groups
                        ),
                        totalError
                );
            }

            return best;
        }

        BigDecimal target =
                targets.get(
                        targetIndex
                );

        int legCount =
                legs.size();

        long maxMask =
                1L << legCount;

        for (
                long mask = 1;
                mask < maxMask;
                mask++
        ) {
            if (
                    (mask & usedMask)
                            != 0
            ) {
                continue;
            }

            if (
                    Long.bitCount(
                            mask
                    ) < 2
            ) {
                continue;
            }

            BigDecimal product =
                    BigDecimal.ONE;

            for (
                    int i = 0;
                    i < legCount;
                    i++
            ) {
                if (
                        (mask & (1L << i))
                                != 0
                ) {
                    product =
                            product.multiply(
                                    legs.get(i)
                                            .tipOdds()
                            );
                }
            }

            BigDecimal error =
                    target
                            .subtract(
                                    product
                            )
                            .abs();

            if (
                    !matchesDisplayedOdds(
                            target,
                            product
                    )
            ) {
                continue;
            }

            List<PartitionGroup> nextGroups =
                    new ArrayList<>(
                            groups
                    );

            nextGroups.add(
                    new PartitionGroup(
                            target,
                            mask,
                            product
                    )
            );

            BigDecimal nextError =
                    totalError.add(
                            error
                    );

            if (
                    best != null
                            && nextError.compareTo(
                            best.totalError()
                    ) >= 0
            ) {
                continue;
            }

            best =
                    searchPartition(
                            targets,
                            legs,
                            targetIndex + 1,
                            usedMask | mask,
                            nextGroups,
                            nextError,
                            best
                    );
        }

        return best;
    }

    private static List<BigDecimal> extractMultipleAkoOdds(
            String title
    ) {
        if (
                title == null
                        || title.isBlank()
        ) {
            return List.of();
        }

        Matcher sectionMatcher =
                MULTI_AKO_SECTION_PATTERN.matcher(
                        title
                );

        if (
                !sectionMatcher.find()
        ) {
            return List.of();
        }

        String section =
                sectionMatcher.group(
                        1
                );

        Matcher oddsMatcher =
                DECIMAL_ODDS_PATTERN.matcher(
                        section
                );

        List<BigDecimal> odds =
                new ArrayList<>();

        while (
                oddsMatcher.find()
        ) {
            String raw =
                    oddsMatcher.group(
                                    1
                            )
                            .replace(
                                    ',',
                                    '.'
                            );

            try {
                BigDecimal value =
                        new BigDecimal(
                                raw
                        );

                if (
                        value.compareTo(
                                BigDecimal.ONE
                        ) >= 0
                                && value.compareTo(
                                new BigDecimal(
                                        "100"
                                )
                        ) < 0
                ) {
                    odds.add(
                            value
                    );
                }

            } catch (
                    NumberFormatException ignored
            ) {
            }
        }

        return List.copyOf(
                odds
        );
    }

    private static boolean containsPayoutSignal(
            String title
    ) {
        if (
                title == null
                        || title.isBlank()
        ) {
            return false;
        }

        return TITLE_PAYOUT_SIGNAL_PATTERN
                .matcher(
                        title
                )
                .find();
    }

    private static boolean matchesDisplayedOdds(
            BigDecimal displayed,
            BigDecimal calculated
    ) {
        BigDecimal rounded =
                calculated.setScale(
                        2,
                        RoundingMode.HALF_UP
                );

        if (
                displayed.compareTo(
                        rounded
                ) == 0
        ) {
            return true;
        }

        BigDecimal difference =
                displayed
                        .subtract(
                                calculated
                        )
                        .abs();

        return difference.compareTo(
                ODDS_MATCH_TOLERANCE
        ) <= 0;
    }

    private static boolean containsCombinedTitleSignal(
            String title
    ) {
        if (
                title == null
                        || title.isBlank()
        ) {
            return false;
        }

        return COMBINED_TITLE_PATTERN
                .matcher(
                        title
                )
                .find();
    }

    private static boolean contextSignalsCombined(
            List<ParsedLeg> legs
    ) {
        String commonHeading =
                null;

        for (
                ParsedLeg leg :
                legs
        ) {
            String heading =
                    blankToNull(
                            leg.sourceAttributes()
                                    .get(
                                            "context_heading"
                                    )
                    );

            if (
                    heading == null
            ) {
                return false;
            }

            if (
                    !COMBINED_CONTEXT_PATTERN
                            .matcher(
                                    heading
                            )
                            .find()
            ) {
                return false;
            }

            if (
                    commonHeading == null
            ) {
                commonHeading =
                        heading;

            } else if (
                    !commonHeading
                            .equalsIgnoreCase(
                                    heading
                            )
            ) {
                return false;
            }
        }

        return commonHeading != null;
    }

    private static BigDecimal calculateProduct(
            List<ParsedLeg> legs
    ) {
        BigDecimal product =
                BigDecimal.ONE;

        for (
                ParsedLeg leg :
                legs
        ) {
            if (
                    leg.tipOdds() == null
            ) {
                return null;
            }

            product =
                    product.multiply(
                            leg.tipOdds()
                    );
        }

        return product;
    }

    private static BigDecimal calculateProduct(
            List<ParsedLeg> legs,
            long mask
    ) {
        BigDecimal product =
                BigDecimal.ONE;

        boolean found =
                false;

        for (
                int i = 0;
                i < legs.size();
                i++
        ) {
            if (
                    (mask & (1L << i))
                            == 0
            ) {
                continue;
            }

            BigDecimal odds =
                    legs.get(
                            i
                    ).tipOdds();

            if (
                    odds == null
            ) {
                return null;
            }

            product =
                    product.multiply(
                            odds
                    );

            found =
                    true;
        }

        return found
                ? product
                : null;
    }

    private static String betFingerprint(
            long wpPostId,
            int ordinal
    ) {
        return sha256(
                wpPostId
                        + "|bet|"
                        + ordinal
        );
    }

    private static BigDecimal extractTitleOdds(
            String title
    ) {
        if (
                title == null
                        || title.isBlank()
        ) {
            return null;
        }

        Matcher matcher =
                TITLE_ODDS_PATTERN.matcher(
                        title
                );

        if (
                !matcher.find()
        ) {
            return null;
        }

        try {
            return new BigDecimal(
                    matcher.group(
                                    1
                            )
                            .replace(
                                    ',',
                                    '.'
                            )
            );

        } catch (
                NumberFormatException e
        ) {
            return null;
        }
    }

    private static EventMetadata parseEventBlock(
            Element block
    ) {
        Map<String, String> attrs =
                attributes(
                        block
                );

        String rawEvent =
                blankToNull(
                        block.attr(
                                "data-event"
                        )
                );

        String outcomeType =
                blankToNull(
                        block.attr(
                                "data-outcome-type"
                        )
                );

        String externalId =
                normalizeEventId(
                        rawEvent,
                        outcomeType
                );

        String startRaw =
                blankToNull(
                        block.attr(
                                "data-start"
                        )
                );

        String rawSportData =
                blankToNull(
                        block.attr(
                                "data-sport_data"
                        )
                );

        String home =
                null;

        String away =
                null;

        String competition =
                null;

        String sportStartRaw =
                null;

        if (
                rawSportData != null
        ) {
            String decodedSportData =
                    URLDecoder.decode(
                            rawSportData,
                            StandardCharsets.UTF_8
                    );

            attrs.put(
                    "sport_data_decoded",
                    decodedSportData
            );

            try {
                JsonNode sportData =
                        OBJECT_MAPPER.readTree(
                                decodedSportData
                        );

                home =
                        textOrNull(
                                sportData,
                                "homeTeam"
                        );

                away =
                        textOrNull(
                                sportData,
                                "visitorTeam"
                        );

                competition =
                        textOrNull(
                                sportData,
                                "stage"
                        );

                JsonNode startDateNode =
                        sportData.get(
                                "startDate"
                        );

                if (
                        startDateNode != null
                                && !startDateNode.isNull()
                ) {
                    sportStartRaw =
                            blankToNull(
                                    startDateNode.asText()
                            );
                }

                if (
                        home != null
                ) {
                    attrs.put(
                            "sport_home_team",
                            home
                    );
                }

                if (
                        away != null
                ) {
                    attrs.put(
                            "sport_visitor_team",
                            away
                    );
                }

                if (
                        competition != null
                ) {
                    attrs.put(
                            "sport_stage",
                            competition
                    );
                }

                if (
                        sportStartRaw != null
                ) {
                    attrs.put(
                            "sport_start_date",
                            sportStartRaw
                    );
                }

            } catch (
                    Exception e
            ) {
                attrs.put(
                        "sport_data_parse_error",
                        e.getClass()
                                .getSimpleName()
                                + ": "
                                + nullToEmpty(
                                e.getMessage()
                        )
                );
            }
        }

        Instant startAt =
                parseEventTime(
                        startRaw
                );

        String effectiveStartRaw =
                startRaw;

        if (
                startAt != null
        ) {
            attrs.put(
                    "event_start_source",
                    "data-start"
            );

        } else {
            Instant sportStartAt =
                    parseEventTime(
                            sportStartRaw
                    );

            if (
                    sportStartAt != null
            ) {
                startAt =
                        sportStartAt;

                effectiveStartRaw =
                        sportStartRaw;

                attrs.put(
                        "event_start_source",
                        "sport_data.startDate"
                );

                if (
                        startRaw != null
                ) {
                    attrs.put(
                            "invalid_data_start",
                            startRaw
                    );
                }

            } else {
                if (
                        effectiveStartRaw == null
                ) {
                    effectiveStartRaw =
                            sportStartRaw;
                }

                if (
                        startRaw != null
                ) {
                    attrs.put(
                            "invalid_data_start",
                            startRaw
                    );
                }

                if (
                        sportStartRaw != null
                ) {
                    attrs.put(
                            "invalid_sport_start_date",
                            sportStartRaw
                    );
                }
            }
        }

        return new EventMetadata(
                externalId,
                blankToNull(
                        home
                ),
                blankToNull(
                        away
                ),
                blankToNull(
                        competition
                ),
                startAt,
                blankToNull(
                        effectiveStartRaw
                ),
                attrs
        );
    }

    private static String normalizeEventId(
            String rawEvent,
            String outcomeType
    ) {
        if (
                rawEvent == null
        ) {
            return null;
        }

        if (
                outcomeType != null
        ) {
            String suffix =
                    "-"
                            + outcomeType;

            if (
                    rawEvent.endsWith(
                            suffix
                    )
                            && rawEvent.length()
                            > suffix.length()
            ) {
                return rawEvent.substring(
                        0,
                        rawEvent.length()
                                - suffix.length()
                );
            }
        }

        return rawEvent;
    }

    private static Map<String, String> extractPickAttributes(
            Element element
    ) {
        Map<String, String> result =
                attributes(
                        element
                );

        String dataAtts =
                element.attr(
                        "data-atts"
                );

        if (
                dataAtts != null
                        && !dataAtts.isBlank()
        ) {
            result.putAll(
                    parseDataAtts(
                            dataAtts
                    )
            );
        }

        return result;
    }

    private static Map<String, String> attributes(
            Element element
    ) {
        Map<String, String> result =
                new LinkedHashMap<>();

        for (
                Attribute attribute :
                element.attributes()
        ) {
            result.put(
                    attribute.getKey(),
                    attribute.getValue()
            );
        }

        return result;
    }

    private static Map<String, String> parseDataAtts(
            String input
    ) {
        Map<String, String> result =
                new LinkedHashMap<>();

        if (
                input == null
                        || input.isBlank()
        ) {
            return result;
        }

        int index =
                0;

        int length =
                input.length();

        index =
                skipWhitespace(
                        input,
                        index
                );

        if (
                index < length
                        && input.charAt(
                        index
                ) == '{'
        ) {
            index++;
        }

        while (
                index < length
        ) {
            index =
                    skipWhitespaceAndCommas(
                            input,
                            index
                    );

            if (
                    index >= length
                            || input.charAt(
                            index
                    ) == '}'
            ) {
                break;
            }

            ParsedToken keyToken =
                    parseToken(
                            input,
                            index
                    );

            String key =
                    keyToken.value();

            index =
                    skipWhitespace(
                            input,
                            keyToken.nextIndex()
                    );

            if (
                    index >= length
                            || input.charAt(
                            index
                    ) != ':'
            ) {
                break;
            }

            index++;

            index =
                    skipWhitespace(
                            input,
                            index
                    );

            ParsedToken valueToken =
                    parseToken(
                            input,
                            index
                    );

            index =
                    valueToken.nextIndex();

            if (
                    key != null
                            && !key.isBlank()
            ) {
                result.put(
                        key,
                        valueToken.value()
                );
            }
        }

        return result;
    }

    private static ParsedToken parseToken(
            String input,
            int start
    ) {
        int length =
                input.length();

        if (
                start >= length
        ) {
            return new ParsedToken(
                    "",
                    start
            );
        }

        char first =
                input.charAt(
                        start
                );

        if (
                first == '\''
                        || first == '"'
        ) {
            char quote =
                    first;

            StringBuilder value =
                    new StringBuilder();

            boolean escaped =
                    false;

            int index =
                    start + 1;

            while (
                    index < length
            ) {
                char current =
                        input.charAt(
                                index
                        );

                if (
                        escaped
                ) {
                    value.append(
                            current
                    );

                    escaped =
                            false;

                    index++;

                    continue;
                }

                if (
                        current == '\\'
                ) {
                    escaped =
                            true;

                    index++;

                    continue;
                }

                if (
                        current == quote
                ) {
                    return new ParsedToken(
                            value.toString(),
                            index + 1
                    );
                }

                value.append(
                        current
                );

                index++;
            }

            return new ParsedToken(
                    value.toString(),
                    index
            );
        }

        StringBuilder value =
                new StringBuilder();

        int index =
                start;

        while (
                index < length
        ) {
            char current =
                    input.charAt(
                            index
                    );

            if (
                    current == ','
                            || current == '}'
            ) {
                break;
            }

            value.append(
                    current
            );

            index++;
        }

        return new ParsedToken(
                value.toString()
                        .trim(),
                index
        );
    }

    private static ParsedLeg parseLeg(
            long wpPostId,
            int ordinal,
            Map<String, String> attributes,
            EventMetadata event,
            TipContext context
    ) {
        String operator =
                blankToNull(
                        valueIgnoreCase(
                                attributes,
                                "operator"
                        )
                );

        String tipTitle =
                blankToNull(
                        valueIgnoreCase(
                                attributes,
                                "tip_title"
                        )
                );

        if (
                tipTitle == null
        ) {
            String recoveredTipTitle =
                    recoverTipTitleFromNumericFragments(
                            attributes
                    );

            if (
                    recoveredTipTitle != null
            ) {
                tipTitle =
                        recoveredTipTitle;

                attributes.put(
                        "tip_title",
                        recoveredTipTitle
                );

                appendWarning(
                        attributes,
                        "RECOVERED_TIP_TITLE_FROM_NUMERIC_FIELDS"
                );

                LOG.warning(
                        "Odzyskano uszkodzony tip_title"
                                + " | post="
                                + wpPostId
                                + " | ordinal="
                                + ordinal
                                + " | recovered="
                                + recoveredTipTitle
                );
            }
        }

        String rawOdds =
                blankToNull(
                        valueIgnoreCase(
                                attributes,
                                "tip_odds"
                        )
                );

        if (
                tipTitle == null
        ) {
            throw new IllegalStateException(
                    "Editorial Tip bez tip_title "
                            + "i bez możliwości odzyskania, "
                            + "post="
                            + wpPostId
                            + ", ordinal="
                            + ordinal
                            + ", attrs="
                            + attributes
            );
        }

        BigDecimal odds =
                null;

        if (
                rawOdds != null
        ) {
            try {
                odds =
                        parseOdds(
                                rawOdds
                        );

            } catch (
                    IllegalArgumentException e
            ) {
                appendWarning(
                        attributes,
                        "INVALID_TIP_ODDS"
                );

                LOG.warning(
                        "Editorial Tip z niepoprawnym tip_odds"
                                + " | post="
                                + wpPostId
                                + " | ordinal="
                                + ordinal
                                + " | tip="
                                + tipTitle
                                + " | rawOdds="
                                + rawOdds
                                + " | zapisuję odds=NULL"
                );
            }

        } else {
            appendWarning(
                    attributes,
                    "MISSING_TIP_ODDS"
            );
        }

        if (
                context != null
        ) {
            if (
                    context.heading() != null
            ) {
                attributes.put(
                        "context_heading",
                        context.heading()
                );
            }

            if (
                    context.previousText() != null
            ) {
                attributes.put(
                        "context_previous_text",
                        context.previousText()
                );
            }
        }

        String fingerprintInput =
                wpPostId
                        + "|leg|"
                        + ordinal
                        + "|"
                        + nullToEmpty(
                        operator
                )
                        + "|"
                        + tipTitle;

        return new ParsedLeg(
                ordinal,
                sha256(
                        fingerprintInput
                ),
                operator,
                tipTitle,
                odds,
                event,
                attributes
        );
    }

    private static String recoverTipTitleFromNumericFragments(
            Map<String, String> attributes
    ) {
        List<Integer> indexes =
                new ArrayList<>();

        for (
                String key :
                attributes.keySet()
        ) {
            if (
                    NUMERIC_KEY_PATTERN
                            .matcher(
                                    key
                            )
                            .matches()
            ) {
                try {
                    indexes.add(
                            Integer.parseInt(
                                    key
                            )
                    );

                } catch (
                        NumberFormatException ignored
                ) {
                }
            }
        }

        if (
                indexes.isEmpty()
        ) {
            return null;
        }

        indexes.sort(
                Integer::compareTo
        );

        StringBuilder combined =
                new StringBuilder();

        for (
                Integer index :
                indexes
        ) {
            String fragment =
                    blankToNull(
                            attributes.get(
                                    Integer.toString(
                                            index
                                    )
                            )
                    );

            if (
                    fragment == null
            ) {
                continue;
            }

            if (
                    !combined.isEmpty()
            ) {
                combined.append(
                        ' '
                );
            }

            combined.append(
                    fragment
            );
        }

        String rawCombined =
                blankToNull(
                        combined.toString()
                );

        if (
                rawCombined == null
        ) {
            return null;
        }

        boolean containsTipTitlePrefix =
                rawCombined
                        .toLowerCase(
                                Locale.ROOT
                        )
                        .contains(
                                "tip_title"
                        );

        boolean containsHtmlEvidence =
                rawCombined.contains(
                        "<"
                )
                        || rawCombined.contains(
                        ">"
                )
                        || rawCombined
                        .toLowerCase(
                                Locale.ROOT
                        )
                        .contains(
                                "</span"
                        );

        boolean containsBrokenAttributePrefix =
                BROKEN_HTML_ATTRIBUTE_PREFIX
                        .matcher(
                                rawCombined
                        )
                        .find();

        if (
                !containsTipTitlePrefix
                        && !containsHtmlEvidence
                        && !containsBrokenAttributePrefix
        ) {
            return null;
        }

        String recovered =
                rawCombined;

        if (
                containsTipTitlePrefix
        ) {
            recovered =
                    BROKEN_TIP_TITLE_PREFIX
                            .matcher(
                                    recovered
                            )
                            .replaceFirst(
                                    ""
                            );
        }

        recovered =
                BROKEN_HTML_ATTRIBUTE_PREFIX
                        .matcher(
                                recovered
                        )
                        .replaceFirst(
                                ""
                        );

        recovered =
                Jsoup.parse(
                                recovered
                        )
                        .text();

        recovered =
                Parser.unescapeEntities(
                        recovered,
                        false
                );

        recovered =
                recovered
                        .replaceAll(
                                "^[\"']+",
                                ""
                        )
                        .replaceAll(
                                "[\"']+$",
                                ""
                        );

        recovered =
                normalizeText(
                        recovered
                );

        return blankToNull(
                recovered
        );
    }

    private static void appendWarning(
            Map<String, String> attributes,
            String warning
    ) {
        String current =
                blankToNull(
                        attributes.get(
                                "import_warning"
                        )
                );

        if (
                current == null
        ) {
            attributes.put(
                    "import_warning",
                    warning
            );

            return;
        }

        if (
                !current.contains(
                        warning
                )
        ) {
            attributes.put(
                    "import_warning",
                    current
                            + ","
                            + warning
            );
        }
    }

    private static boolean isHeading(
            Element element
    ) {
        return switch (
                element.tagName()
                ) {
            case "h1",
                 "h2",
                 "h3",
                 "h4",
                 "h5",
                 "h6" ->
                    true;

            default ->
                    false;
        };
    }

    private static boolean isUsefulProseElement(
            Element element
    ) {
        String tag =
                element.tagName();

        if (
                !"p".equals(
                        tag
                )
                        && !"li".equals(
                        tag
                )
        ) {
            return false;
        }

        return element
                .select(
                        ".bcb-atts"
                )
                .isEmpty();
    }

    private static String normalizeText(
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

    private static String truncate(
            String value,
            int maxLength
    ) {
        if (
                value == null
                        || value.length()
                        <= maxLength
        ) {
            return value;
        }

        return value.substring(
                0,
                maxLength
        );
    }

    private static String textOrNull(
            JsonNode node,
            String field
    ) {
        if (
                node == null
                        || !node.has(
                        field
                )
                        || node.get(
                        field
                ).isNull()
        ) {
            return null;
        }

        return blankToNull(
                node.get(
                        field
                ).asText()
        );
    }

    private static boolean hasAnyEventData(
            EventMetadata event
    ) {
        return event != null
                && (
                event.externalId() != null
                        || event.home() != null
                        || event.away() != null
                        || event.competition() != null
                        || event.startRaw() != null
        );
    }

    private static String valueIgnoreCase(
            Map<String, String> attrs,
            String key
    ) {
        for (
                Map.Entry<String, String> entry :
                attrs.entrySet()
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

    private static BigDecimal parseOdds(
            String raw
    ) {
        String normalized =
                raw
                        .replace(
                                '\u00A0',
                                ' '
                        )
                        .trim()
                        .replace(
                                " ",
                                ""
                        )
                        .replace(
                                ',',
                                '.'
                        );

        try {
            BigDecimal value =
                    new BigDecimal(
                            normalized
                    );

            if (
                    value.compareTo(
                            BigDecimal.ONE
                    ) < 0
            ) {
                throw new IllegalArgumentException(
                        "Kurs < 1: "
                                + raw
                );
            }

            return value;

        } catch (
                NumberFormatException e
        ) {
            throw new IllegalArgumentException(
                    "Niepoprawny tip_odds: "
                            + raw,
                    e
            );
        }
    }

    private static Instant parseWpDate(
            String gmtValue,
            String localValue
    ) {
        Instant parsed =
                parseWpDateNullable(
                        gmtValue,
                        localValue
                );

        if (
                parsed == null
        ) {
            throw new IllegalArgumentException(
                    "Brak poprawnej daty publikacji: "
                            + "gmt="
                            + gmtValue
                            + ", local="
                            + localValue
            );
        }

        return parsed;
    }

    private static Instant parseWpDateNullable(
            String gmtValue,
            String localValue
    ) {
        if (
                gmtValue != null
                        && !gmtValue.isBlank()
        ) {
            try {
                return LocalDateTime
                        .parse(
                                gmtValue
                        )
                        .toInstant(
                                ZoneOffset.UTC
                        );

            } catch (
                    DateTimeParseException ignored
            ) {
            }
        }

        if (
                localValue != null
                        && !localValue.isBlank()
        ) {
            try {
                return LocalDateTime
                        .parse(
                                localValue
                        )
                        .atZone(
                                WARSAW
                        )
                        .toInstant();

            } catch (
                    DateTimeParseException ignored
            ) {
            }
        }

        return null;
    }

    private static Instant parseEventTime(
            String raw
    ) {
        if (
                raw == null
                        || raw.isBlank()
        ) {
            return null;
        }

        String value =
                raw.trim();

        try {
            long numeric =
                    Long.parseLong(
                            value
                    );

            if (
                    value.length()
                            >= 13
            ) {
                return Instant.ofEpochMilli(
                        numeric
                );
            }

            if (
                    value.length()
                            >= 10
            ) {
                return Instant.ofEpochSecond(
                        numeric
                );
            }

        } catch (
                NumberFormatException ignored
        ) {
        }

        try {
            return Instant.parse(
                    value
            );

        } catch (
                DateTimeParseException ignored
        ) {
        }

        try {
            return OffsetDateTime
                    .parse(
                            value
                    )
                    .toInstant();

        } catch (
                DateTimeParseException ignored
        ) {
        }

        try {
            return LocalDateTime
                    .parse(
                            value
                    )
                    .atZone(
                            WARSAW
                    )
                    .toInstant();

        } catch (
                DateTimeParseException ignored
        ) {
        }

        return null;
    }

    private static String decodeHtml(
            String value
    ) {
        if (
                value == null
        ) {
            return "";
        }

        return Parser.unescapeEntities(
                Jsoup.parse(
                        value
                ).text(),
                false
        );
    }

    private static String sha256(
            String value
    ) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            value.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            StringBuilder hex =
                    new StringBuilder(
                            hash.length * 2
                    );

            for (
                    byte b :
                    hash
            ) {
                hex.append(
                        String.format(
                                "%02x",
                                b
                        )
                );
            }

            return hex.toString();

        } catch (
                NoSuchAlgorithmException e
        ) {
            throw new IllegalStateException(
                    "Brak SHA-256",
                    e
            );
        }
    }

    private static int skipWhitespace(
            String input,
            int index
    ) {
        while (
                index < input.length()
                        && Character.isWhitespace(
                        input.charAt(
                                index
                        )
                )
        ) {
            index++;
        }

        return index;
    }

    private static int skipWhitespaceAndCommas(
            String input,
            int index
    ) {
        while (
                index < input.length()
        ) {
            char current =
                    input.charAt(
                            index
                    );

            if (
                    Character.isWhitespace(
                            current
                    )
                            || current == ','
            ) {
                index++;

                continue;
            }

            break;
        }

        return index;
    }

    private static String blankToNull(
            String value
    ) {
        return value == null
                || value.isBlank()
                ? null
                : value.trim();
    }

    private static String nullToEmpty(
            String value
    ) {
        return value == null
                ? ""
                : value;
    }

    private record ParsedToken(
            String value,
            int nextIndex
    ) {
    }

    private record PartitionGroup(
            BigDecimal advertisedOdds,
            long mask,
            BigDecimal calculatedOdds
    ) {
    }

    private record PartitionSolution(
            long usedMask,
            List<PartitionGroup> groups,
            BigDecimal totalError
    ) {
    }

    private record AkoPlusSinglesTitle(
            BigDecimal akoOdds,
            List<BigDecimal> singleOdds
    ) {
    }
}