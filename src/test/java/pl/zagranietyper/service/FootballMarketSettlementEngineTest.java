package pl.zagranietyper.service;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.FootballMarket;
import pl.zagranietyper.model.FootballScore;
import pl.zagranietyper.model.SettlementDecision;
import pl.zagranietyper.parser.FootballMarketParser;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FootballMarketSettlementEngineTest {

    private final FootballMarketParser parser =
            new FootballMarketParser();

    private final FootballMarketSettlementEngine engine =
            new FootballMarketSettlementEngine();

    @Test
    void settlesBttsYesAsWin() {
        FootballMarket market =
                parse(
                        "Obie drużyny strzelą gola",
                        "Cracovia Krakow",
                        "Pogon Szczecin"
                );

        assertEquals(
                SettlementDecision.W,
                engine.settle(
                        market,
                        new FootballScore(
                                2,
                                1
                        )
                )
        );
    }

    @Test
    void settlesBttsYesAsLoss() {
        FootballMarket market =
                parse(
                        "Obie drużyny strzelą gola",
                        "Cracovia Krakow",
                        "Pogon Szczecin"
                );

        assertEquals(
                SettlementDecision.L,
                engine.settle(
                        market,
                        new FootballScore(
                                2,
                                0
                        )
                )
        );
    }

    @Test
    void settlesBttsNo() {
        FootballMarket market =
                parse(
                        "Gole z obu stron: nie",
                        "Chrobry Głogów",
                        "ŁKS Łódź"
                );

        assertEquals(
                SettlementDecision.W,
                engine.settle(
                        market,
                        new FootballScore(
                                1,
                                0
                        )
                )
        );
    }

    @Test
    void parsesBramkiZObuStron() {
        FootballMarket market =
                parse(
                        "Bramki z obu stron",
                        "Bayer Leverkusen",
                        "VfB Stuttgart"
                );

        assertEquals(
                SettlementDecision.W,
                engine.settle(
                        market,
                        new FootballScore(
                                2,
                                2
                        )
                )
        );
    }

    @Test
    void settlesOverTwoPointFive() {
        FootballMarket market =
                parse(
                        "Powyżej 2.5 goli",
                        "Puszcza Niepołomice",
                        "Slask Wroclaw"
                );

        assertEquals(
                SettlementDecision.W,
                engine.settle(
                        market,
                        new FootballScore(
                                2,
                                1
                        )
                )
        );
    }

    @Test
    void settlesUnderTwoPointFive() {
        FootballMarket market =
                parse(
                        "poniżej 2,5 gola",
                        "Montenegro",
                        "Wales"
                );

        assertEquals(
                SettlementDecision.W,
                engine.settle(
                        market,
                        new FootballScore(
                                1,
                                0
                        )
                )
        );
    }

    @Test
    void parsesLessThanAsUnder() {
        FootballMarket market =
                parse(
                        "mniej niż 4,5 gola w meczu",
                        "Polonia Warszawa",
                        "Miedz Legnica"
                );

        assertEquals(
                SettlementDecision.W,
                engine.settle(
                        market,
                        new FootballScore(
                                0,
                                1
                        )
                )
        );
    }

    @Test
    void parsesMoreThanAsOver() {
        FootballMarket market =
                parse(
                        "więcej niż 2,5 gola w meczu",
                        "Arsenal",
                        "Chelsea"
                );

        assertEquals(
                SettlementDecision.W,
                engine.settle(
                        market,
                        new FootballScore(
                                2,
                                1
                        )
                )
        );
    }

    @Test
    void parsesGoalsPrefixOver() {
        FootballMarket market =
                parse(
                        "Gole: powyżej 1,5",
                        "Tychy 71",
                        "Odra Opole"
                );

        assertEquals(
                SettlementDecision.L,
                engine.settle(
                        market,
                        new FootballScore(
                                0,
                                0
                        )
                )
        );
    }

    @Test
    void integerGoalLineCanVoid() {
        FootballMarket market =
                new FootballMarket(
                        List.of(
                                new FootballMarket.TotalGoals(
                                        FootballMarket.TotalDirection.OVER,
                                        new BigDecimal(
                                                "2.0"
                                        )
                                )
                        )
                );

        assertEquals(
                SettlementDecision.V,
                engine.settle(
                        market,
                        new FootballScore(
                                1,
                                1
                        )
                )
        );
    }

    @Test
    void minimumTotalWinsAtExactThreshold() {
        FootballMarket market =
                parse(
                        "Co najmniej 3 gole",
                        "Roma",
                        "Lazio"
                );

        assertEquals(
                SettlementDecision.W,
                engine.settle(
                        market,
                        new FootballScore(
                                2,
                                1
                        )
                )
        );
    }

    @Test
    void minimumTotalWinsAboveThreshold() {
        FootballMarket market =
                parse(
                        "W meczu padnie minimum 3 bramki",
                        "Roma",
                        "Lazio"
                );

        assertEquals(
                SettlementDecision.W,
                engine.settle(
                        market,
                        new FootballScore(
                                3,
                                2
                        )
                )
        );
    }

    @Test
    void minimumTotalLosesBelowThreshold() {
        FootballMarket market =
                parse(
                        "Co najmniej trzy trafienia",
                        "Roma",
                        "Lazio"
                );

        assertEquals(
                SettlementDecision.L,
                engine.settle(
                        market,
                        new FootballScore(
                                1,
                                1
                        )
                )
        );
    }

    @Test
    void settlesHomeDoubleChance() {
        FootballMarket market =
                parse(
                        "Hutnik nie przegra meczu (1X) i over 1.5 goli",
                        "Hutnik Kraków",
                        "ŁKS Łódź II"
                );

        assertEquals(
                SettlementDecision.W,
                engine.settle(
                        market,
                        new FootballScore(
                                1,
                                1
                        )
                )
        );
    }

    @Test
    void settlesAwayDoubleChanceAndOver() {
        FootballMarket market =
                parse(
                        "Bayern nie przegra meczu (X2) i over 2.5 bramek",
                        "Eintracht Frankfurt",
                        "Bayern München"
                );

        assertEquals(
                SettlementDecision.W,
                engine.settle(
                        market,
                        new FootballScore(
                                2,
                                2
                        )
                )
        );
    }

    @Test
    void oneLostConditionLosesWholeMarket() {
        FootballMarket market =
                parse(
                        "Bayern nie przegra meczu (X2) i over 2.5 bramek",
                        "Eintracht Frankfurt",
                        "Bayern München"
                );

        assertEquals(
                SettlementDecision.L,
                engine.settle(
                        market,
                        new FootballScore(
                                2,
                                0
                        )
                )
        );
    }

    @Test
    void xSlashAwayTeamMeansAwayOrDraw() {
        FootballMarket market =
                parse(
                        "MyCombi: X/Miedź i poniżej 3,5 gola w meczu",
                        "Polonia Warszawa",
                        "Miedz Legnica"
                );

        assertEquals(
                2,
                market.conditions()
                        .size()
        );

        assertEquals(
                SettlementDecision.W,
                engine.settle(
                        market,
                        new FootballScore(
                                0,
                                1
                        )
                )
        );
    }

    @Test
    void xSlashAwayTeamAndLessThanMustNotBecomeDraw() {
        FootballMarket market =
                parse(
                        "X/Miedź i mniej niż 4,5 gola w meczu",
                        "Polonia Warszawa",
                        "Miedz Legnica"
                );

        assertEquals(
                2,
                market.conditions()
                        .size()
        );

        assertTrue(
                market.conditions()
                        .stream()
                        .anyMatch(
                                FootballMarket.DoubleChance.class::isInstance
                        )
        );

        assertTrue(
                market.conditions()
                        .stream()
                        .anyMatch(
                                FootballMarket.TotalGoals.class::isInstance
                        )
        );

        assertEquals(
                SettlementDecision.W,
                engine.settle(
                        market,
                        new FootballScore(
                                0,
                                1
                        )
                )
        );
    }

    @Test
    void homeTeamSlashXMeansHomeOrDraw() {
        FootballMarket market =
                parse(
                        "Arsenal/X i poniżej 4,5 gola",
                        "Arsenal",
                        "Chelsea"
                );

        assertEquals(
                SettlementDecision.W,
                engine.settle(
                        market,
                        new FootballScore(
                                1,
                                1
                        )
                )
        );
    }

    @Test
    void athleticBilbaoWinAndOverMustParseBothConditions() {
        FootballMarket market =
                parse(
                        "Bilbao wygra i over 1.5 goli",
                        "Athletic Club",
                        "Getafe"
                );

        assertEquals(
                2,
                market.conditions()
                        .size()
        );

        assertEquals(
                SettlementDecision.L,
                engine.settle(
                        market,
                        new FootballScore(
                                1,
                                1
                        )
                )
        );
    }

    @Test
    void parsesMonacoWinAndOverAsTwoConditions() {
        FootballMarket market =
                parse(
                        "Monaco wygra i over 1.5 goli",
                        "Monaco",
                        "Saint Etienne"
                );

        assertEquals(
                2,
                market.conditions()
                        .size()
        );

        assertEquals(
                SettlementDecision.L,
                engine.settle(
                        market,
                        new FootballScore(
                                1,
                                0
                        )
                )
        );
    }

    @Test
    void parsesX2SlashUnderThreePointFiveCompletely() {
        FootballMarket market =
                parse(
                        "X2/-3.5 goli",
                        "Auxerre",
                        "Nice"
                );

        assertEquals(
                2,
                market.conditions()
                        .size()
        );

        assertEquals(
                SettlementDecision.L,
                engine.settle(
                        market,
                        new FootballScore(
                                2,
                                1
                        )
                )
        );
    }

    @Test
    void parsesHomeSlashOverTwoPointFive() {
        FootballMarket market =
                parse(
                        "1/+2.5 goli",
                        "Newcastle",
                        "Southampton"
                );

        assertEquals(
                2,
                market.conditions()
                        .size()
        );

        assertEquals(
                SettlementDecision.L,
                engine.settle(
                        market,
                        new FootballScore(
                                1,
                                0
                        )
                )
        );
    }

    @Test
    void parsesManchesterInflection() {
        FootballMarket market =
                parse(
                        "Zwycięstwo Manchesteru United",
                        "Manchester United",
                        "Fulham"
                );

        assertEquals(
                SettlementDecision.W,
                engine.settle(
                        market,
                        new FootballScore(
                                1,
                                0
                        )
                )
        );
    }

    @Test
    void parsesSevillaInflection() {
        FootballMarket market =
                parse(
                        "Zwycięstwo Sevilli FC",
                        "Las Palmas",
                        "Sevilla"
                );

        assertEquals(
                SettlementDecision.L,
                engine.settle(
                        market,
                        new FootballScore(
                                2,
                                2
                        )
                )
        );
    }

    @Test
    void parsesLksAcronym() {
        FootballMarket market =
                parse(
                        "Wygra ŁKS",
                        "ŁKS Łódź",
                        "Kotwica Kołobrzeg"
                );

        assertEquals(
                SettlementDecision.L,
                engine.settle(
                        market,
                        new FootballScore(
                                0,
                                2
                        )
                )
        );
    }

    @Test
    void parsesBvbAlias() {
        FootballMarket market =
                parse(
                        "Wygra BVB",
                        "Phönix Lübeck",
                        "Borussia Dortmund"
                );

        assertEquals(
                SettlementDecision.W,
                engine.settle(
                        market,
                        new FootballScore(
                                1,
                                4
                        )
                )
        );
    }

    @Test
    void doesNotTreatTeamTotalAsMatchTotal() {
        Optional<FootballMarket> market =
                parser.parse(
                        "Jagiellonia powyżej 1.5 gola",
                        "Jagiellonia",
                        "Dinamo Tirana"
                );

        assertTrue(
                market.isEmpty()
        );
    }

    @Test
    void doesNotTreatSignedTeamTotalAsMatchTotal() {
        Optional<FootballMarket> market =
                parser.parse(
                        "Panathinaikos -1.5 goli",
                        "Ajax",
                        "Panathinaikos"
                );

        assertTrue(
                market.isEmpty()
        );
    }

    @Test
    void doesNotTreatTeamMoreThanGoalsAsMatchTotal() {
        Optional<FootballMarket> market =
                parser.parse(
                        "Barcelona strzeli więcej niż 1.5 goli",
                        "Valencia",
                        "Barcelona"
                );

        assertTrue(
                market.isEmpty()
        );
    }

    @Test
    void doesNotSettleCards() {
        Optional<FootballMarket> market =
                parser.parse(
                        "Powyżej 3.5 kartek",
                        "VfL Wolfsburg",
                        "Borussia Dortmund"
                );

        assertTrue(
                market.isEmpty()
        );
    }

    @Test
    void doesNotSettleCorners() {
        Optional<FootballMarket> market =
                parser.parse(
                        "powyżej 8,5 rzutu rożnego",
                        "Switzerland",
                        "Spain"
                );

        assertTrue(
                market.isEmpty()
        );
    }

    @Test
    void doesNotSettlePlayerScorer() {
        Optional<FootballMarket> market =
                parser.parse(
                        "Omar Marmoush strzeli gola i zanotuje asystę",
                        "Eintracht Frankfurt",
                        "Borussia Dortmund"
                );

        assertTrue(
                market.isEmpty()
        );
    }

    @Test
    void parsesSimpleHomeWin() {
        FootballMarket market =
                parse(
                        "Raków wygra mecz",
                        "Raków Częstochowa",
                        "Lechia Gdansk"
                );

        assertEquals(
                SettlementDecision.W,
                engine.settle(
                        market,
                        new FootballScore(
                                2,
                                0
                        )
                )
        );
    }

    @Test
    void parsesSimpleAwayWin() {
        FootballMarket market =
                parse(
                        "Inter wygra mecz",
                        "Genoa",
                        "Inter"
                );

        assertEquals(
                SettlementDecision.W,
                engine.settle(
                        market,
                        new FootballScore(
                                0,
                                2
                        )
                )
        );
    }

    private FootballMarket parse(
            String tipTitle,
            String homeTeam,
            String awayTeam
    ) {
        return parser.parse(
                        tipTitle,
                        homeTeam,
                        awayTeam
                )
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "Parser nie rozpoznał rynku: "
                                                + tipTitle
                                )
                );
    }
}
