package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.BetType;
import pl.zagranietyper.model.OddsConsistency;
import pl.zagranietyper.model.OddsSource;
import pl.zagranietyper.model.ParsedBet;
import pl.zagranietyper.model.ParsedLeg;
import pl.zagranietyper.model.ParsedPost;
import pl.zagranietyper.wp.WpPost;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZagraniePostParserTest {

    @Test
    void parsesVerifiedSingle() {

        String html = """
                <div>
                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'superbet','tip_title':'BTTS: tak','tip_odds':'1.80'}">
                    </div>
                </div>
                """;

        ParsedPost parsed =
                parser().parse(
                        wpPost(
                                1L,
                                "Test",
                                html
                        ),
                        html
                );

        ParsedBet bet =
                parsed.bets().getFirst();

        assertEquals(
                BetType.SINGLE,
                bet.type()
        );

        assertEquals(
                new BigDecimal("1.80"),
                bet.displayedOdds()
        );

        assertEquals(
                new BigDecimal("1.8000"),
                bet.calculatedOdds()
        );

        assertEquals(
                OddsSource.SINGLE_LEG,
                bet.oddsSource()
        );

        assertTrue(
                bet.oddsVerified()
        );

        assertEquals(
                OddsConsistency.MATCH,
                bet.oddsConsistency()
        );
    }

    @Test
    void acceptsSingleWithoutTipOdds() {

        String html = """
                <div>
                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Wygra Stal'}">
                    </div>
                </div>
                """;

        ParsedBet bet =
                parser()
                        .parse(
                                wpPost(
                                        679278L,
                                        "Stal Gorzów – Falubaz",
                                        html
                                ),
                                html
                        )
                        .bets()
                        .getFirst();

        assertNull(
                bet.displayedOdds()
        );

        assertNull(
                bet.calculatedOdds()
        );

        assertEquals(
                OddsSource.NONE,
                bet.oddsSource()
        );

        assertFalse(
                bet.oddsVerified()
        );

        assertEquals(
                OddsConsistency.NOT_CHECKABLE,
                bet.oddsConsistency()
        );

        assertEquals(
                "MISSING_TIP_ODDS",
                bet.legs()
                        .getFirst()
                        .sourceAttributes()
                        .get(
                                "import_warning"
                        )
        );
    }

    @Test
    void titleOddsAreSourceOfTruthEvenWhenLegProductDiffers() {

        /*
         * Odpowiednik realnych przypadków Kacprzaka:
         *
         * finalny kurs reklamowany w tytule = źródło prawdy.
         * Iloczyn legów służy tylko do quality check.
         */
        String html = """
                <div>
                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'superbet','tip_title':'Heroic handicap map (-1.5)','tip_odds':'1.52'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'superbet','tip_title':'Story powyżej 14.5 zabójstw','tip_odds':'1.85'}">
                    </div>
                </div>
                """;

        ParsedBet bet =
                parser()
                        .parse(
                                wpPost(
                                        681342L,
                                        "Heroic wyjdzie na prostą? Gramy esport z kursem 2.96!",
                                        html
                                ),
                                html
                        )
                        .bets()
                        .getFirst();

        assertEquals(
                BetType.COMBINED,
                bet.type()
        );

        assertEquals(
                new BigDecimal("2.96"),
                bet.displayedOdds()
        );

        assertEquals(
                new BigDecimal("2.8120"),
                bet.calculatedOdds()
        );

        assertEquals(
                OddsSource.TITLE,
                bet.oddsSource()
        );

        /*
         * TRUE, bo znamy historyczny finalny kurs
         * bezpośrednio ze źródła.
         */
        assertTrue(
                bet.oddsVerified()
        );

        /*
         * Ale jednocześnie wiemy, że aktualne kursy
         * legów nie składają się do 2.96.
         */
        assertEquals(
                OddsConsistency.MISMATCH,
                bet.oddsConsistency()
        );
    }

    @Test
    void titleOddsMatchingProductHaveMatchConsistency() {

        String html = """
                <div>
                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'superbet','tip_title':'Spirit handicap','tip_odds':'1.53'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'superbet','tip_title':'Sinners wygra','tip_odds':'1.41'}">
                    </div>
                </div>
                """;

        ParsedBet bet =
                parser()
                        .parse(
                                wpPost(
                                        684275L,
                                        "Gramy esport z kursem 2.15!",
                                        html
                                ),
                                html
                        )
                        .bets()
                        .getFirst();

        assertEquals(
                OddsSource.TITLE,
                bet.oddsSource()
        );

        assertTrue(
                bet.oddsVerified()
        );

        assertEquals(
                OddsConsistency.MATCH,
                bet.oddsConsistency()
        );
    }

    @Test
    void combinedWithoutTitleOddsUsesCalculatedSourceButIsNotVerified() {

        String html = """
                <div>
                    <h2>Propozycja kuponu</h2>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Typ A','tip_odds':'1.44'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Typ B','tip_odds':'1.48'}">
                    </div>
                </div>
                """;

        ParsedBet bet =
                parser()
                        .parse(
                                wpPost(
                                        679959L,
                                        "Gramy o 206 PLN!",
                                        html
                                ),
                                html
                        )
                        .bets()
                        .getFirst();

        assertEquals(
                BetType.COMBINED,
                bet.type()
        );

        assertNull(
                bet.displayedOdds()
        );

        assertEquals(
                new BigDecimal("2.1312"),
                bet.calculatedOdds()
        );

        assertEquals(
                OddsSource.CALCULATED,
                bet.oddsSource()
        );

        assertFalse(
                bet.oddsVerified()
        );

        assertEquals(
                OddsConsistency.NOT_CHECKABLE,
                bet.oddsConsistency()
        );
    }

    @Test
    void payoutTitleIsOnlyGroupingSignalAndNeverOddsVerification() {

        String html = """
                <div>
                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Boca strzeli bramkę','tip_odds':'1.41'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Cruzeiro strzeli bramkę','tip_odds':'1.32'}">
                    </div>
                </div>
                """;

        ParsedBet bet =
                parser()
                        .parse(
                                wpPost(
                                        680910L,
                                        "Boca Juniors pokona rewelację z Chile? Gramy o 279 PLN z Copa Libertadores!",
                                        html
                                ),
                                html
                        )
                        .bets()
                        .getFirst();

        assertEquals(
                BetType.COMBINED,
                bet.type()
        );

        assertEquals(
                new BigDecimal("1.8612"),
                bet.calculatedOdds()
        );

        assertNull(
                bet.displayedOdds()
        );

        assertEquals(
                OddsSource.CALCULATED,
                bet.oddsSource()
        );

        /*
         * 279 PLN absolutnie nie potwierdza kursu,
         * bo nie znamy historycznej stawki.
         */
        assertFalse(
                bet.oddsVerified()
        );

        assertEquals(
                OddsConsistency.NOT_CHECKABLE,
                bet.oddsConsistency()
        );
    }

    @Test
    void realTriplePayoutDoesNotVerifyOddsAnymore() {

        String html = """
                <div>
                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Typ A','tip_odds':'1.30'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Typ B','tip_odds':'1.45'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Typ C','tip_odds':'1.63'}">
                    </div>
                </div>
                """;

        ParsedBet bet =
                parser()
                        .parse(
                                wpPost(
                                        680523L,
                                        "Finałowa kolejka Ekstraklasy: kto wywalczy utrzymanie? Kupon triple o 271 PLN",
                                        html
                                ),
                                html
                        )
                        .bets()
                        .getFirst();

        assertEquals(
                BetType.COMBINED,
                bet.type()
        );

        assertEquals(
                3,
                bet.legs().size()
        );

        assertEquals(
                OddsSource.CALCULATED,
                bet.oddsSource()
        );

        assertFalse(
                bet.oddsVerified()
        );

        assertEquals(
                OddsConsistency.NOT_CHECKABLE,
                bet.oddsConsistency()
        );
    }

    @Test
    void splitsMultipleTipsWithoutCombinedSignalIntoSingles() {

        String html = """
                <div>
                    <h2>Co obstawiać?</h2>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Typ A','tip_odds':'1.55'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Typ B','tip_odds':'1.70'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Typ C','tip_odds':'1.67'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Typ D','tip_odds':'1.95'}">
                    </div>
                </div>
                """;

        ParsedPost parsed =
                parser().parse(
                        wpPost(
                                683444L,
                                "XTB KSW 120: typy i kursy",
                                html
                        ),
                        html
                );

        assertEquals(
                4,
                parsed.bets().size()
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
                    OddsSource.SINGLE_LEG,
                    bet.oddsSource()
            );

            assertTrue(
                    bet.oddsVerified()
            );

            assertEquals(
                    OddsConsistency.MATCH,
                    bet.oddsConsistency()
            );
        }
    }

    @Test
    void splitsInterleavedLegsIntoTwoVerifiedCombinedBets() {

        String html = """
                <div>
                    <h2>Co obstawiać?</h2>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Szwajcaria zwycięstwo','tip_odds':'1.73'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Sven Andrighetto strzeli gola','tip_odds':'4.70'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Norwegia strzeli gola','tip_odds':'1.21'}">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'Macklin Celebrini strzeli gola','tip_odds':'1.90'}">
                    </div>
                </div>
                """;

        ParsedPost parsed =
                parser().parse(
                        wpPost(
                                681125L,
                                "Szwajcaria po raz pierwszy zostanie Mistrzem Świata? AKO 8.93 i 2.09 na hokej!",
                                html
                        ),
                        html
                );

        assertEquals(
                2,
                parsed.bets().size()
        );

        ParsedBet first =
                parsed.bets().get(0);

        assertEquals(
                new BigDecimal("8.93"),
                first.displayedOdds()
        );

        assertEquals(
                new BigDecimal("8.9300"),
                first.calculatedOdds()
        );

        assertEquals(
                OddsSource.TITLE,
                first.oddsSource()
        );

        assertTrue(
                first.oddsVerified()
        );

        assertEquals(
                OddsConsistency.MATCH,
                first.oddsConsistency()
        );

        assertEquals(
                2,
                first.legs().get(0).ordinal()
        );

        assertEquals(
                4,
                first.legs().get(1).ordinal()
        );

        ParsedBet second =
                parsed.bets().get(1);

        assertEquals(
                new BigDecimal("2.09"),
                second.displayedOdds()
        );

        assertEquals(
                new BigDecimal("2.0933"),
                second.calculatedOdds()
        );

        assertEquals(
                OddsSource.TITLE,
                second.oddsSource()
        );

        assertTrue(
                second.oddsVerified()
        );

        assertEquals(
                OddsConsistency.MATCH,
                second.oddsConsistency()
        );

        assertEquals(
                1,
                second.legs().get(0).ordinal()
        );

        assertEquals(
                3,
                second.legs().get(1).ordinal()
        );
    }

    @Test
    void parsesEventMetadata() {

        String html = """
                <div>
                    <div class="bcb-sport-block-data"
                         data-type="event-odds"
                         data-outcome-type="1x2"
                         data-event="210438960-1x2"
                         data-start="1786645800"
                         data-sport_data="%7B%22homeTeam%22%3A%22Rangers%22%2C%22visitorTeam%22%3A%22Jagiellonia+Bialystok%22%2C%22stage%22%3A%22UEFA+Europa+League%22%7D">
                    </div>

                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'sts','tip_title':'BTTS: tak','tip_odds':'1.80'}">
                    </div>
                </div>
                """;

        ParsedLeg leg =
                parser()
                        .parse(
                                wpPost(
                                        684676L,
                                        "Rangers – Jagiellonia",
                                        html
                                ),
                                html
                        )
                        .bets()
                        .getFirst()
                        .legs()
                        .getFirst();

        assertEquals(
                "210438960",
                leg.event().externalId()
        );

        assertEquals(
                "Rangers",
                leg.event().home()
        );

        assertEquals(
                "Jagiellonia Bialystok",
                leg.event().away()
        );

        assertEquals(
                "UEFA Europa League",
                leg.event().competition()
        );

        assertEquals(
                Instant.ofEpochSecond(
                        1786645800L
                ),
                leg.event().startAt()
        );
    }

    @Test
    void recoversBrokenTipTitleFromNumericFields() {

        String html = """
                <div>
                    <div class="bcb-atts"
                         data-atts="{'type':'Editorial Tip','operator':'superbet','0':'tip_title=\\'Powyżej','1':'8,5','2':'strzałów','3':'obu','4':'drużyn','tip_odds':'1.37'}">
                    </div>
                </div>
                """;

        ParsedLeg leg =
                parser()
                        .parse(
                                wpPost(
                                        684558L,
                                        "Test",
                                        html
                                ),
                                html
                        )
                        .bets()
                        .getFirst()
                        .legs()
                        .getFirst();

        assertEquals(
                "Powyżej 8,5 strzałów obu drużyn",
                leg.tipTitle()
        );

        assertEquals(
                new BigDecimal("1.37"),
                leg.tipOdds()
        );

        assertTrue(
                leg.sourceAttributes()
                        .get(
                                "import_warning"
                        )
                        .contains(
                                "RECOVERED_TIP_TITLE_FROM_NUMERIC_FIELDS"
                        )
        );
    }

    private static ZagraniePostParser parser() {
        return new ZagraniePostParser();
    }

    private static WpPost wpPost(
            long id,
            String title,
            String html
    ) {
        return new WpPost(
                id,
                8560L,
                "https://zagranie.com/test/",
                "test",
                "2026-08-08T21:20:00",
                "2026-08-08T19:20:00",
                "2026-08-08T21:25:00",
                "2026-08-08T19:25:00",
                new WpPost.Rendered(
                        title
                ),
                new WpPost.Rendered(
                        html
                )
        );
    }
}