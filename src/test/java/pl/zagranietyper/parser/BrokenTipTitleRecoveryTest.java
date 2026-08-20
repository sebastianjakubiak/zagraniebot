package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.ParsedLeg;
import pl.zagranietyper.model.ParsedPost;
import pl.zagranietyper.wp.WpPost;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrokenTipTitleRecoveryTest {

    private final ZagraniePostParser parser =
            new ZagraniePostParser();

    @Test
    void recoversOldBrokenTipTitleStartingWithTipTitleField() {
        String html =
                """
                <div>
                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','template':'2','operator':'superbet','cta_text':'Zagraj w Superbet!','0':'tip_title=\\'Powyżej','1':'8,5','2':'strzałów','3':'obu','4':'drużyn','tip_odds':'1.37'}">
                    </div>
                </div>
                """;

        ParsedPost parsed =
                parser.parse(
                        wpPost(
                                684558L,
                                html
                        ),
                        html
                );

        assertEquals(
                1,
                parsed.bets()
                        .size()
        );

        ParsedLeg leg =
                parsed.bets()
                        .getFirst()
                        .legs()
                        .getFirst();

        assertEquals(
                "Powyżej 8,5 strzałów obu drużyn",
                leg.tipTitle()
        );

        assertEquals(
                new BigDecimal(
                        "1.37"
                ),
                leg.tipOdds()
        );

        assertTrue(
                leg.sourceAttributes()
                        .getOrDefault(
                                "import_warning",
                                ""
                        )
                        .contains(
                                "RECOVERED_TIP_TITLE_FROM_NUMERIC_FIELDS"
                        )
        );
    }

    @Test
    void recoversBrokenTipTitleWithLostSpanOpeningTag() {
        String html =
                """
                <div>
                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','template':'2','operator':'betclic','cta_text':'Zagraj w BETCLIC!','0':'','1':'data-contrast=\\'auto\\'>Andreasa','2':'Hountondji','3':'strzeli','4':'gola<\\/span>','tip_odds':'3.22'}">
                    </div>
                </div>
                """;

        ParsedPost parsed =
                parser.parse(
                        wpPost(
                                640513L,
                                html
                        ),
                        html
                );

        assertEquals(
                1,
                parsed.bets()
                        .size()
        );

        ParsedLeg leg =
                parsed.bets()
                        .getFirst()
                        .legs()
                        .getFirst();

        assertEquals(
                "Andreasa Hountondji strzeli gola",
                leg.tipTitle()
        );

        assertEquals(
                new BigDecimal(
                        "3.22"
                ),
                leg.tipOdds()
        );

        assertTrue(
                leg.sourceAttributes()
                        .getOrDefault(
                                "import_warning",
                                ""
                        )
                        .contains(
                                "RECOVERED_TIP_TITLE_FROM_NUMERIC_FIELDS"
                        )
        );
    }

    private static WpPost wpPost(
            long id,
            String html
    ) {
        return new WpPost(
                id,
                6565L,
                "https://zagranie.com/test-" + id + "/",
                "test-" + id,
                "2024-01-01T12:00:00",
                "2024-01-01T11:00:00",
                "2024-01-01T12:00:00",
                "2024-01-01T11:00:00",
                new WpPost.Rendered(
                        "Test " + id
                ),
                new WpPost.Rendered(
                        html
                )
        );
    }
}