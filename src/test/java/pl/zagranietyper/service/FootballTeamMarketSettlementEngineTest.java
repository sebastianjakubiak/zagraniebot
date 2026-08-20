package pl.zagranietyper.service;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.FootballScore;
import pl.zagranietyper.model.FootballTeamMarket;
import pl.zagranietyper.model.SettlementDecision;
import pl.zagranietyper.parser.FootballTeamMarketParser;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FootballTeamMarketSettlementEngineTest {

    private final FootballTeamMarketParser parser =
            new FootballTeamMarketParser();

    private final FootballTeamMarketSettlementEngine engine =
            new FootballTeamMarketSettlementEngine();

    @Test
    void settlesHomeTeamUnder() {
        FootballTeamMarket market =
                parse(
                        "Śląsk poniżej 1.5 goli",
                        "Slask Wroclaw",
                        "FC ST. Gallen"
                );

        assertEquals(
                SettlementDecision.L,
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
    void settlesAwayTeamMoreThan() {
        FootballTeamMarket market =
                parse(
                        "Barcelona strzeli więcej niż 1.5 goli",
                        "Valencia",
                        "Barcelona"
                );

        assertEquals(
                SettlementDecision.W,
                engine.settle(
                        market,
                        new FootballScore(
                                1,
                                2
                        )
                )
        );
    }

    @Test
    void settlesGksJastrzebieUnder() {
        FootballTeamMarket market =
                parse(
                        "GKS Jastrzebie poniżej 1.5 goli",
                        "Jastrzębie",
                        "Polonia Bytom"
                );

        assertEquals(
                SettlementDecision.L,
                engine.settle(
                        market,
                        new FootballScore(
                                2,
                                4
                        )
                )
        );
    }

    @Test
    void settlesTorinoOverHalfGoal() {
        FootballTeamMarket market =
                parse(
                        "Torino powyżej 0.5 gola",
                        "AC Milan",
                        "Torino"
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
    void settlesTeamToScoreYes() {
        FootballTeamMarket market =
                parse(
                        "Middlesbrough strzeli gola - TAK",
                        "Leeds",
                        "Middlesbrough"
                );

        assertEquals(
                SettlementDecision.W,
                engine.settle(
                        market,
                        new FootballScore(
                                0,
                                3
                        )
                )
        );
    }

    @Test
    void settlesTeamToScoreNo() {
        FootballTeamMarket market =
                parse(
                        "Crawley strzeli gola - NIE",
                        "Brighton",
                        "Crawley Town"
                );

        assertEquals(
                SettlementDecision.W,
                engine.settle(
                        market,
                        new FootballScore(
                                4,
                                0
                        )
                )
        );
    }

    @Test
    void settlesExplicitNotToScore() {
        FootballTeamMarket market =
                parse(
                        "Arsenal nie strzeli gola",
                        "Arsenal",
                        "Chelsea"
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

    @Test
    void parsesNumberOfGoalsPrefix() {
        FootballTeamMarket market =
                parse(
                        "Liczba goli Empoli: poniżej 0,5",
                        "AS Roma",
                        "Empoli"
                );

        assertEquals(
                SettlementDecision.L,
                engine.settle(
                        market,
                        new FootballScore(
                                1,
                                2
                        )
                )
        );
    }

    @Test
    void parsesOlympiqueLyonAlias() {
        FootballTeamMarket market =
                parse(
                        "Olympique Lyon powyżej 1.5 bramki",
                        "Lyon",
                        "Olympiakos Piraeus"
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
    void integerTeamTotalCanVoid() {
        FootballTeamMarket market =
                parse(
                        "Arsenal powyżej 2.0 gola",
                        "Arsenal",
                        "Chelsea"
                );

        assertEquals(
                SettlementDecision.V,
                engine.settle(
                        market,
                        new FootballScore(
                                2,
                                0
                        )
                )
        );
    }

    /*
     * REGRESSION:
     *
     * Manchester City wygra i Haaland strzeli gola
     *
     * NIE jest team-to-score Manchesteru City.
     */
    @Test
    void doesNotParsePlayerScorerInsideCompositeBet() {
        Optional<FootballTeamMarket> market =
                parser.parse(
                        "Manchester City wygra i E. Haaland strzeli gola",
                        "West Ham",
                        "Manchester City"
                );

        assertTrue(
                market.isEmpty()
        );
    }

    /*
     * REGRESSION:
     *
     * Under jest totalem całego meczu,
     * a nie totalem Brestu.
     */
    @Test
    void doesNotParseMatchTotalFromBetBuilderAsTeamTotal() {
        Optional<FootballTeamMarket> market =
                parser.parse(
                        "BetBuilder: Brest nie przegra i poniżej 4,5 gola",
                        "Auxerre",
                        "Stade Brestois 29"
                );

        assertTrue(
                market.isEmpty()
        );
    }

    /*
     * REGRESSION:
     *
     * Over dotyczy rzutów rożnych.
     */
    @Test
    void doesNotParseCornersAsTeamGoals() {
        Optional<FootballTeamMarket> market =
                parser.parse(
                        "Girona - liczba rzutów rożnych — powyżej 3.5",
                        "Girona",
                        "Real Sociedad"
                );

        assertTrue(
                market.isEmpty()
        );
    }

    @Test
    void doesNotParseGoalInEveryHalf() {
        Optional<FootballTeamMarket> market =
                parser.parse(
                        "Arsenal strzeli gola w każdej połowie",
                        "Arsenal",
                        "Wolves"
                );

        assertTrue(
                market.isEmpty()
        );
    }

    @Test
    void doesNotParsePlayerScorer() {
        Optional<FootballTeamMarket> market =
                parser.parse(
                        "E. Haaland strzeli gola",
                        "Chelsea",
                        "Manchester City"
                );

        assertTrue(
                market.isEmpty()
        );
    }

    @Test
    void doesNotParseAmbiguousSignedTeamMarket() {
        Optional<FootballTeamMarket> market =
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
    void doesNotParseNormalMatchTotal() {
        Optional<FootballTeamMarket> market =
                parser.parse(
                        "Powyżej 2.5 goli",
                        "Arsenal",
                        "Chelsea"
                );

        assertTrue(
                market.isEmpty()
        );
    }

    @Test
    void doesNotGuessUnknownTeam() {
        Optional<FootballTeamMarket> market =
                parser.parse(
                        "Liverpool powyżej 1.5 gola",
                        "Arsenal",
                        "Chelsea"
                );

        assertTrue(
                market.isEmpty()
        );
    }

    private FootballTeamMarket parse(
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