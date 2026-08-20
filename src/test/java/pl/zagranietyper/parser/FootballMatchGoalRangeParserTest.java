package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.FootballMarket;
import pl.zagranietyper.model.FootballScore;
import pl.zagranietyper.model.SettlementDecision;
import pl.zagranietyper.service.FootballMarketSettlementEngine;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FootballMatchGoalRangeParserTest {

    private final FootballMarketParser parser =
            new FootballMarketParser();

    private final FootballMarketSettlementEngine engine =
            new FootballMarketSettlementEngine();

    @Test
    void parsesOnlyInclusivePureMatchGoalRange() {
        FootballMarket market = parseRange("Przedział goli w meczu 2-4");

        FootballMarket.MatchGoalRange range =
                assertInstanceOf(
                        FootballMarket.MatchGoalRange.class,
                        market.conditions().getFirst()
                );

        assertEquals(2, range.minimum());
        assertEquals(4, range.maximum());
    }

    @Test
    void exactLowerBoundaryWins() {
        assertDecision(2, SettlementDecision.W);
    }

    @Test
    void exactUpperBoundaryWins() {
        assertDecision(4, SettlementDecision.W);
    }

    @Test
    void valueInsideRangeWins() {
        assertDecision(3, SettlementDecision.W);
    }

    @Test
    void valueBelowRangeLoses() {
        assertDecision(1, SettlementDecision.L);
    }

    @Test
    void valueAboveRangeLoses() {
        assertDecision(5, SettlementDecision.L);
    }

    @Test
    void teamSpecificGoalRangeRemainsUnsupported() {
        assertUnsupported("Przedział goli Arsenalu: 2-3");
        assertUnsupported("Arsenal przedział goli 2-3");
        assertUnsupported("Ajax suma goli: 1-2");
    }

    @Test
    void malformedOrReversedRangeRemainsUnsupported() {
        assertUnsupported("Przedział goli w meczu 3-2");
        assertUnsupported("Przedział goli w meczu 2");
        assertUnsupported("Przedział goli w meczu 2-");
    }

    @Test
    void compositeWordingIsNotReducedToOnlyTheRange() {
        assertUnsupported("Arsenal wygra + przedział goli w meczu 2-3");
        assertUnsupported("BTTS i przedział goli w meczu 2-3");
    }

    private FootballMarket parseRange(String title) {
        return parser.parse(title, "Arsenal", "Chelsea").orElseThrow();
    }

    private void assertDecision(
            int totalGoals,
            SettlementDecision expected
    ) {
        FootballMarket market = parseRange("Przedział goli w meczu 2-4");

        assertEquals(
                expected,
                engine.settle(
                        market,
                        new FootballScore(totalGoals, 0)
                )
        );
    }

    private void assertUnsupported(String title) {
        Optional<FootballMarket> market =
                parser.parse(title, "Arsenal", "Chelsea");

        assertTrue(market.isEmpty());
    }
}
