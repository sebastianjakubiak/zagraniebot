package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.BetType;
import pl.zagranietyper.model.ParsedBet;
import pl.zagranietyper.model.ParsedPost;
import pl.zagranietyper.wp.WpPost;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AkoPlusSinglesGroupingTest {

    private final ZagraniePostParser parser =
            new ZagraniePostParser();

    @Test
    void splitsAkoAndSingleForLiamKirkExample() {
        String html =
                """
                <div>
                    <h3>Co obstawiać?</h3>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','template':'2','operator':'sts','tip_title':'Libor Hudacek powyżej 0.5 punktu w punktacji kanadyjskiej','tip_odds':'1.90','cta_text':'Zagraj!'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','template':'2','operator':'sts','tip_title':'Liam Kirk powyżej 0.5 punktu w punktacji kanadyjskiej','tip_odds':'1.70','cta_text':'Zagraj!'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','template':'2','operator':'sts','tip_title':'Liam Kirk strzeli gola','tip_odds':'3.30','cta_text':'Zagraj!'}">
                    </div>
                </div>
                """;

        ParsedPost parsed =
                parser.parse(
                        wpPost(
                                574859L,
                                "Liam Kirk nauczy swoich rodaków grać w hokeja? Walka w LM z AKO 3.23 i singlem 3.30!",
                                html
                        ),
                        html
                );

        assertEquals(
                2,
                parsed.bets()
                        .size()
        );

        ParsedBet combined =
                parsed.bets()
                        .get(0);

        ParsedBet single =
                parsed.bets()
                        .get(1);

        assertEquals(
                BetType.COMBINED,
                combined.type()
        );

        assertEquals(
                new BigDecimal(
                        "3.23"
                ),
                combined.displayedOdds()
        );

        assertEquals(
                new BigDecimal(
                        "3.2300"
                ),
                combined.calculatedOdds()
        );

        assertEquals(
                2,
                combined.legs()
                        .size()
        );

        assertEquals(
                BetType.SINGLE,
                single.type()
        );

        assertEquals(
                new BigDecimal(
                        "3.30"
                ),
                single.displayedOdds()
        );

        assertEquals(
                "Liam Kirk strzeli gola",
                single.legs()
                        .getFirst()
                        .tipTitle()
        );
    }

    @Test
    void splitsInterleavedAkoAndTwoSingles() {
        String html =
                """
                <div>
                    <h3>Co obstawiać?</h3>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Powyżej 3.5 kartek mecz 1','tip_odds':'1.57'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Czerwona kartka 1','tip_odds':'5.40'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Powyżej 3.5 kartek mecz 2','tip_odds':'1.36'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Czerwona kartka 2','tip_odds':'5.00'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Powyżej 3.5 kartek mecz 3','tip_odds':'1.61'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Powyżej 3.5 kartek mecz 4','tip_odds':'1.57'}">
                    </div>
                </div>
                """;

        ParsedPost parsed =
                parser.parse(
                        wpPost(
                                647977L,
                                "Topspiel w Bundeslidze znów pod znakiem czerwonej kartki? AKO 5.39 + single 5.00 i 5.40!",
                                html
                        ),
                        html
                );

        assertEquals(
                3,
                parsed.bets()
                        .size()
        );

        ParsedBet combined =
                parsed.bets()
                        .getFirst();

        assertEquals(
                BetType.COMBINED,
                combined.type()
        );

        assertEquals(
                new BigDecimal(
                        "5.39"
                ),
                combined.displayedOdds()
        );

        assertEquals(
                new BigDecimal(
                        "5.3971"
                ),
                combined.calculatedOdds()
        );

        assertEquals(
                4,
                combined.legs()
                        .size()
        );

        List<BigDecimal> singleOdds =
                parsed.bets()
                        .subList(
                                1,
                                3
                        )
                        .stream()
                        .map(
                                ParsedBet::displayedOdds
                        )
                        .sorted(
                                BigDecimal::compareTo
                        )
                        .toList();

        assertEquals(
                List.of(
                        new BigDecimal("5.00"),
                        new BigDecimal("5.40")
                ),
                singleOdds
        );
    }

    @Test
    void splitsWhenSingleOddsChangedFrom315To325() {
        String html =
                """
                <div>
                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Wisła Płock strzeli gola','tip_odds':'1.35'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Łukasz Sekulski strzeli gola','tip_odds':'3.25'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Powyżej 4.5 kartek','tip_odds':'1.95'}">
                    </div>
                </div>
                """;

        ParsedPost parsed =
                parser.parse(
                        wpPost(
                                634468L,
                                "Łukasz Sekulski poprowadzi Wisłę do kolejnej wygranej? Ekstraklasa z AKO 2.63 i singlem 3.15!",
                                html
                        ),
                        html
                );

        assertEquals(
                2,
                parsed.bets()
                        .size()
        );

        ParsedBet combined =
                parsed.bets()
                        .getFirst();

        assertEquals(
                BetType.COMBINED,
                combined.type()
        );

        assertEquals(
                new BigDecimal("2.63"),
                combined.displayedOdds()
        );

        assertEquals(
                new BigDecimal("2.6325"),
                combined.calculatedOdds()
        );

        assertEquals(
                2,
                combined.legs()
                        .size()
        );

        ParsedBet single =
                parsed.bets()
                        .get(1);

        assertEquals(
                BetType.SINGLE,
                single.type()
        );

        /*
         * Do SINGLE idzie rzeczywisty kurs z bloku,
         * nie stary kurs 3.15 z tytułu.
         */
        assertEquals(
                new BigDecimal("3.25"),
                single.displayedOdds()
        );

        assertEquals(
                "Łukasz Sekulski strzeli gola",
                single.legs()
                        .getFirst()
                        .tipTitle()
        );
    }

    @Test
    void splitsWhenSingleOddsChangedFrom320To330() {
        String html =
                """
                <div>
                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Widzew Łódź powyżej 1.5 gola','tip_odds':'1.75'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Sebastian Bergier strzeli gola','tip_odds':'3.30'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Powyżej 1.5 gola + powyżej 8.5 rzutów rożnych','tip_odds':'1.55'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Lech Poznań powyżej 4.5 rzutów rożnych','tip_odds':'1.40'}">
                    </div>
                </div>
                """;

        ParsedPost parsed =
                parser.parse(
                        wpPost(
                                634610L,
                                "Sebastian Bergier przypomni się byłym kolegom? AKO 3.80 i singiel 3.20 na Ekstraklasę!",
                                html
                        ),
                        html
                );

        assertEquals(
                2,
                parsed.bets()
                        .size()
        );

        ParsedBet combined =
                parsed.bets()
                        .getFirst();

        assertEquals(
                BetType.COMBINED,
                combined.type()
        );

        assertEquals(
                new BigDecimal("3.80"),
                combined.displayedOdds()
        );

        assertEquals(
                new BigDecimal("3.7975"),
                combined.calculatedOdds()
        );

        assertEquals(
                3,
                combined.legs()
                        .size()
        );

        assertEquals(
                BetType.SINGLE,
                parsed.bets()
                        .get(1)
                        .type()
        );

        assertEquals(
                new BigDecimal("3.30"),
                parsed.bets()
                        .get(1)
                        .displayedOdds()
        );
    }

    @Test
    void combinedLegsCanBeNonContiguousInSource() {
        String html =
                """
                <div>
                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Wild Wings powyżej 2.5 gola ml','tip_odds':'1.79'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Alexander Karachun strzeli gola','tip_odds':'3.10'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Pinguins powyżej 1.5 gola ml','tip_odds':'1.36'}">
                    </div>
                </div>
                """;

        ParsedPost parsed =
                parser.parse(
                        wpPost(
                                672970L,
                                "Karachun i jego Wild Wings zaskoczą zardzewiałe Rekiny? AKO 2.43 i singiel 3.10!",
                                html
                        ),
                        html
                );

        assertEquals(
                2,
                parsed.bets()
                        .size()
        );

        ParsedBet combined =
                parsed.bets()
                        .getFirst();

        assertEquals(
                2,
                combined.legs()
                        .size()
        );

        assertEquals(
                1,
                combined.legs()
                        .get(0)
                        .ordinal()
        );

        assertEquals(
                3,
                combined.legs()
                        .get(1)
                        .ordinal()
        );

        assertEquals(
                new BigDecimal("2.4344"),
                combined.calculatedOdds()
        );

        assertEquals(
                BetType.SINGLE,
                parsed.bets()
                        .get(1)
                        .type()
        );
    }

    @Test
    void usesNumberOfAdvertisedSinglesButNotTheirExactOdds() {
        String html =
                """
                <div>
                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Leg A','tip_odds':'1.50'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Single X','tip_odds':'5.20'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Leg B','tip_odds':'2.00'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Single Y','tip_odds':'5.60'}">
                    </div>
                </div>
                """;

        ParsedPost parsed =
                parser.parse(
                        wpPost(
                                999992L,
                                "Test AKO 3.00 + single 5.00 i 5.40!",
                                html
                        ),
                        html
                );

        assertEquals(
                3,
                parsed.bets()
                        .size()
        );

        assertEquals(
                BetType.COMBINED,
                parsed.bets()
                        .getFirst()
                        .type()
        );

        assertEquals(
                2,
                parsed.bets()
                        .getFirst()
                        .legs()
                        .size()
        );

        assertEquals(
                2,
                parsed.bets()
                        .stream()
                        .filter(
                                bet ->
                                        bet.type()
                                                == BetType.SINGLE
                        )
                        .count()
        );
    }

    @Test
    void keepsAmbiguousPartitionUnverified() {
        /*
         * AKO 3.00 można tutaj uzyskać na dwa sposoby:
         *
         * 1.50 × 2.00
         * 1.20 × 2.50
         *
         * Tytuł mówi o jednym singlu, więc subset AKO
         * powinien mieć dwa legi, ale istnieją dwa
         * poprawne rozwiązania.
         *
         * Nie zgadujemy.
         */
        String html =
                """
                <div>
                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'A','tip_odds':'1.50'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'B','tip_odds':'2.00'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'C','tip_odds':'1.20'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'D','tip_odds':'2.50'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Single','tip_odds':'4.00'}">
                    </div>
                </div>
                """;

        /*
         * Tytuł mówi o trzech singlach, więc AKO powinno
         * składać się dokładnie z dwóch legów.
         */
        ParsedPost parsed =
                parser.parse(
                        wpPost(
                                999993L,
                                "Test AKO 3.00 + single 4.00 i 5.00 i 6.00!",
                                html
                        ),
                        html
                );

        assertEquals(
                1,
                parsed.bets()
                        .size()
        );

        assertEquals(
                BetType.MULTI_UNVERIFIED,
                parsed.bets()
                        .getFirst()
                        .type()
        );
    }

    @Test
    void keepsUnresolvedPartitionUnverified() {
        String html =
                """
                <div>
                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'A','tip_odds':'1.40'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'B','tip_odds':'1.50'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Single','tip_odds':'4.20'}">
                    </div>
                </div>
                """;

        ParsedPost parsed =
                parser.parse(
                        wpPost(
                                999994L,
                                "Test AKO 3.00 i singiel 4.00!",
                                html
                        ),
                        html
                );

        assertEquals(
                1,
                parsed.bets()
                        .size()
        );

        assertEquals(
                BetType.MULTI_UNVERIFIED,
                parsed.bets()
                        .getFirst()
                        .type()
        );
    }

    @Test
    void keepsRealMultipleAkoWithoutSingleMarkerUnverifiedWhenPartitionCannotBeResolved() {
        String html =
                """
                <div>
                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'BTTS','tip_odds':'2.10'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Harry Kane strzeli gola','tip_odds':'1.59'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Powyżej 3.5 kartek','tip_odds':'1.39'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Stuttgart gol + rożne','tip_odds':'1.35'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Deniz Undav strzeli gola','tip_odds':'1.90'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Freiburg gol + rożne','tip_odds':'1.40'}">
                    </div>
                </div>
                """;

        ParsedPost parsed =
                parser.parse(
                        wpPost(
                                652826L,
                                "Harry i jego Bayern odczaruje stadion przy Starej Leśniczówce? DFB Pokal z AKO 3.10 i 5.51!",
                                html
                        ),
                        html
                );

        assertEquals(
                1,
                parsed.bets()
                        .size()
        );

        assertEquals(
                BetType.MULTI_UNVERIFIED,
                parsed.bets()
                        .getFirst()
                        .type()
        );
    }

    @Test
    void resultContainsExactlyOneCombinedAndExpectedNumberOfSingles() {
        String html =
                """
                <div>
                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'A','tip_odds':'1.50'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Single X','tip_odds':'5.20'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'B','tip_odds':'2.00'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Single Y','tip_odds':'5.60'}">
                    </div>
                </div>
                """;

        ParsedPost parsed =
                parser.parse(
                        wpPost(
                                999995L,
                                "Test AKO 3.00 + single 5.00 i 5.40!",
                                html
                        ),
                        html
                );

        long combinedCount =
                parsed.bets()
                        .stream()
                        .filter(
                                bet ->
                                        bet.type()
                                                == BetType.COMBINED
                        )
                        .count();

        long singleCount =
                parsed.bets()
                        .stream()
                        .filter(
                                bet ->
                                        bet.type()
                                                == BetType.SINGLE
                        )
                        .count();

        assertEquals(
                1L,
                combinedCount
        );

        assertEquals(
                2L,
                singleCount
        );

        assertTrue(
                parsed.bets()
                        .stream()
                        .noneMatch(
                                bet ->
                                        bet.type()
                                                == BetType.MULTI_UNVERIFIED
                        )
        );
    }

    private static WpPost wpPost(
            long id,
            String title,
            String html
    ) {
        return new WpPost(
                id,
                6L,
                "https://zagranie.com/test-" + id + "/",
                "test-" + id,
                "2025-01-01T12:00:00",
                "2025-01-01T11:00:00",
                "2025-01-01T12:05:00",
                "2025-01-01T11:05:00",
                new WpPost.Rendered(
                        title
                ),
                new WpPost.Rendered(
                        html
                )
        );
    }
}