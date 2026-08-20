package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.BetType;
import pl.zagranietyper.model.ParsedBet;
import pl.zagranietyper.model.ParsedPost;
import pl.zagranietyper.wp.WpPost;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepeatedFullOddsAreSinglesTest {

    private final ZagraniePostParser parser =
            new ZagraniePostParser();

    @Test
    void repeatedFullOddsEqualToTitleOddsBecomeSeparateSingles() {
        String html =
                """
                <div>
                    <h3>Co obstawiać?</h3>

                    <p>Pierwszy mecz.</p>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','template':'2','operator':'betclic','cta_text':'Zagraj!','tip_title':'Typ pierwszy','tip_odds':'4.40'}">
                    </div>

                    <p>Drugi mecz.</p>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','template':'2','operator':'betclic','cta_text':'Zagraj!','tip_title':'Typ drugi','tip_odds':'4.40'}">
                    </div>

                    <p>Trzeci mecz.</p>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','template':'2','operator':'betclic','cta_text':'Zagraj!','tip_title':'Typ trzeci','tip_odds':'4.40'}">
                    </div>
                </div>
                """;

        ParsedPost parsed =
                parser.parse(
                        wpPost(
                                563540L,
                                "Byki będą dalej niepokonane? Gramy zakład specjalny z kursem 4.40!",
                                html
                        ),
                        html
                );

        assertEquals(
                3,
                parsed.bets()
                        .size()
        );

        for (
                ParsedBet bet :
                parsed.bets()
        ) {
            assertEquals(
                    BetType.SINGLE,
                    bet.type()
            );

            assertEquals(
                    new BigDecimal(
                            "4.40"
                    ),
                    bet.displayedOdds()
            );

            assertEquals(
                    1,
                    bet.legs()
                            .size()
            );
        }

        assertEquals(
                "Typ pierwszy",
                parsed.bets()
                        .get(0)
                        .legs()
                        .getFirst()
                        .tipTitle()
        );

        assertEquals(
                "Typ drugi",
                parsed.bets()
                        .get(1)
                        .legs()
                        .getFirst()
                        .tipTitle()
        );

        assertEquals(
                "Typ trzeci",
                parsed.bets()
                        .get(2)
                        .legs()
                        .getFirst()
                        .tipTitle()
        );
    }

    @Test
    void repeatedThreePointZeroOddsBecomeSeparateSingles() {
        String html =
                """
                <div>
                    <h3>Co obstawiać?</h3>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','template':'2','operator':'betclic','tip_title':'TOTALBOOST pierwszy','tip_odds':'3.00','cta_text':'Zagraj!'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','template':'2','operator':'betclic','tip_title':'TOTALBOOST drugi','tip_odds':'3.00','cta_text':'Zagraj!'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','template':'2','operator':'betclic','tip_title':'TOTALBOOST trzeci','tip_odds':'3.00','cta_text':'Zagraj!'}">
                    </div>
                </div>
                """;

        ParsedPost parsed =
                parser.parse(
                        wpPost(
                                587058L,
                                "Nasz TOTALBOOST z kursem 3.00 na MŚ U20!",
                                html
                        ),
                        html
                );

        assertEquals(
                3,
                parsed.bets()
                        .size()
        );

        for (
                ParsedBet bet :
                parsed.bets()
        ) {
            assertEquals(
                    BetType.SINGLE,
                    bet.type()
            );

            assertEquals(
                    new BigDecimal(
                            "3.00"
                    ),
                    bet.displayedOdds()
            );

            assertEquals(
                    1,
                    bet.legs()
                            .size()
            );
        }
    }

    @Test
    void realCombinedWithDifferentLegOddsStaysCombined() {
        String html =
                """
                <div>
                    <h3>Kupon dnia</h3>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','template':'2','operator':'betclic','tip_title':'Leg pierwszy','tip_odds':'2.00','cta_text':'Zagraj!'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','template':'2','operator':'betclic','tip_title':'Leg drugi','tip_odds':'2.20','cta_text':'Zagraj!'}">
                    </div>
                </div>
                """;

        ParsedPost parsed =
                parser.parse(
                        wpPost(
                                999999L,
                                "Kupon dnia z kursem 4.40!",
                                html
                        ),
                        html
                );

        assertEquals(
                1,
                parsed.bets()
                        .size()
        );

        ParsedBet bet =
                parsed.bets()
                        .getFirst();

        assertEquals(
                BetType.COMBINED,
                bet.type()
        );

        assertEquals(
                new BigDecimal(
                        "4.40"
                ),
                bet.displayedOdds()
        );

        assertEquals(
                new BigDecimal(
                        "4.4000"
                ),
                bet.calculatedOdds()
        );

        assertEquals(
                2,
                bet.legs()
                        .size()
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
                "2024-10-04T11:00:47",
                "2024-10-04T09:00:47",
                "2024-10-04T11:00:47",
                "2024-10-04T09:00:47",
                new WpPost.Rendered(
                        title
                ),
                new WpPost.Rendered(
                        html
                )
        );
    }
}