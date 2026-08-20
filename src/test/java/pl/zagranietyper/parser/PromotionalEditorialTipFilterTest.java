package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.ParsedLeg;
import pl.zagranietyper.model.ParsedPost;
import pl.zagranietyper.wp.WpPost;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromotionalEditorialTipFilterTest {

    private final EditorialTipDetector detector =
            new EditorialTipDetector();

    private final ZagraniePostParser parser =
            new ZagraniePostParser();

    @Test
    void detectorDoesNotCountPromotionalPseudoEditorialTip() {
        String html =
                """
                <div>
                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','template':'2','operator':'sts','tip_title':'Puszcza wygra','tip_odds':'1.67','cta_text':'Zagraj!'}">
                    </div>

                    <h3>Promocje i bonusy na mecz Puszcza Niepołomice – Lechia Gdańsk</h3>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','template':'1','tip_odds':'300 PLN bonus','cta_text':'Odbierz bonus','operator':'sts','tip_title':'Puszcza wygra/Lechia wygra'}">
                    </div>
                </div>
                """;

        assertEquals(
                1,
                detector.countEditorialTips(
                        html
                )
        );
    }

    @Test
    void parserSkipsPromotionalPseudoEditorialTipButKeepsRealTip() {
        String html =
                """
                <div>
                    <h3>Typ na mecz</h3>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','template':'2','operator':'sts','tip_title':'Puszcza wygra','tip_odds':'1.67','cta_text':'Zagraj!'}">
                    </div>

                    <h3>Promocje i bonusy na mecz Puszcza Niepołomice – Lechia Gdańsk</h3>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','template':'1','tip_odds':'300 PLN bonus','cta_text':'Odbierz bonus','operator':'sts','tip_title':'Puszcza wygra/Lechia wygra'}">
                    </div>
                </div>
                """;

        ParsedPost parsed =
                parser.parse(
                        wpPost(
                                549857L,
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
                1,
                parsed.bets()
                        .getFirst()
                        .legs()
                        .size()
        );

        ParsedLeg leg =
                parsed.bets()
                        .getFirst()
                        .legs()
                        .getFirst();

        assertEquals(
                "Puszcza wygra",
                leg.tipTitle()
        );

        assertEquals(
                new BigDecimal(
                        "1.67"
                ),
                leg.tipOdds()
        );
    }

    @Test
    void parserReturnsNoBetsForPromoOnlyPost() {
        String html =
                """
                <div>
                    <h3>Promocje i bonusy na mecz Puszcza Niepołomice – Lechia Gdańsk</h3>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','template':'1','tip_odds':'300 PLN bonus','cta_text':'Odbierz bonus','operator':'sts','tip_title':'Puszcza wygra/Lechia wygra'}">
                    </div>
                </div>
                """;

        ParsedPost parsed =
                parser.parse(
                        wpPost(
                                549857L,
                                html
                        ),
                        html
                );

        assertTrue(
                parsed.bets()
                        .isEmpty()
        );

        assertEquals(
                0,
                detector.countEditorialTips(
                        html
                )
        );
    }

    @Test
    void skipsInflectedBonusEvenWithOrdinaryCta() {
        String html =
                """
                <div>
                    <h3>Oferta bukmachera</h3>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','template':'1','tip_odds':'197 PLN bonusu','cta_text':'Zagraj!','operator':'sts','tip_title':'Oferta specjalna'}">
                    </div>
                </div>
                """;

        ParsedPost parsed =
                parser.parse(
                        wpPost(
                                584424L,
                                html
                        ),
                        html
                );

        assertTrue(
                parsed.bets()
                        .isEmpty()
        );

        assertEquals(
                0,
                detector.countEditorialTips(
                        html
                )
        );
    }

    @Test
    void skipsCurrencyAmountWhenCtaIsClearlyPromotional() {
        String html =
                """
                <div>
                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','template':'1','tip_odds':'300 PLN','cta_text':'Odbierz bonus','operator':'sts','tip_title':'Oferta specjalna'}">
                    </div>
                </div>
                """;

        ParsedPost parsed =
                parser.parse(
                        wpPost(
                                584425L,
                                html
                        ),
                        html
                );

        assertTrue(
                parsed.bets()
                        .isEmpty()
        );

        assertEquals(
                0,
                detector.countEditorialTips(
                        html
                )
        );
    }

    @Test
    void doesNotSkipNormalOddsEvenWhenCtaContainsBonusWord() {
        String html =
                """
                <div>
                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','template':'2','tip_odds':'1.97','cta_text':'Zagraj i odbierz bonus','operator':'sts','tip_title':'Powyżej 2,5 gola'}">
                    </div>
                </div>
                """;

        ParsedPost parsed =
                parser.parse(
                        wpPost(
                                584426L,
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
                new BigDecimal(
                        "1.97"
                ),
                parsed.bets()
                        .getFirst()
                        .legs()
                        .getFirst()
                        .tipOdds()
        );

        assertEquals(
                1,
                detector.countEditorialTips(
                        html
                )
        );
    }

    @Test
    void keepsRealTipWithInvalidOddsAsNullInsteadOfCrashing() {
        String html =
                """
                <div>
                    <h3>Typ na wydarzenie</h3>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','template':'2','operator':'sts','tip_title':'Testowy typ','tip_odds':'???','cta_text':'Zagraj!'}">
                    </div>
                </div>
                """;

        ParsedPost parsed =
                parser.parse(
                        wpPost(
                                632546L,
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
                1,
                parsed.bets()
                        .getFirst()
                        .legs()
                        .size()
        );

        ParsedLeg leg =
                parsed.bets()
                        .getFirst()
                        .legs()
                        .getFirst();

        assertEquals(
                "Testowy typ",
                leg.tipTitle()
        );

        assertNull(
                leg.tipOdds()
        );

        assertTrue(
                leg.sourceAttributes()
                        .getOrDefault(
                                "import_warning",
                                ""
                        )
                        .contains(
                                "INVALID_TIP_ODDS"
                        )
        );
    }

    @Test
    void detectorStillCountsNormalEditorialTipWithBookmakerCta() {
        String html =
                """
                <div class="bcb-atts"
                     data-atts="{'type':'Editorial Tip','template':'2','operator':'superbet','tip_title':'Powyżej 2,5 gola','tip_odds':'1.65','cta_text':'Zagraj w Superbet!'}">
                </div>
                """;

        assertEquals(
                1,
                detector.countEditorialTips(
                        html
                )
        );
    }

    private static WpPost wpPost(
            long id,
            String html
    ) {
        return new WpPost(
                id,
                62L,
                "https://zagranie.com/typy/test-" + id + "/",
                "test-" + id,
                "2024-08-16T10:00:00",
                "2024-08-16T08:00:00",
                "2024-08-16T10:05:00",
                "2024-08-16T08:05:00",
                new WpPost.Rendered(
                        "Test post " + id
                ),
                new WpPost.Rendered(
                        html
                )
        );
    }
}