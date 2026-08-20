package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.FootballMarket;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FootballPonadTotalParserTest {

    private final FootballMarketParser parser =
            new FootballMarketParser();

    @Test
    void parsesStandalonePureMatchTotal() {
        assertMatchTotalOver(
                "✅ Ponad 2,5 bramki",
                "Bayern München",
                "Bayer Leverkusen",
                "2.5"
        );
    }

    @Test
    void parsesFixtureLabelledPureMatchTotal() {
        assertMatchTotalOver(
                "Espanyol vs Villarreal - ponad 1.5 gola w meczu",
                "Espanyol",
                "Villarreal",
                "1.5"
        );
    }

    @Test
    void rejectsAmbiguousTeamPlusPonad() {
        assertUnsupported(
                "Al Shabab + ponad 1.5 gola",
                "Al Shabab",
                "Al-Fateh"
        );
    }

    @Test
    void rejectsTeamSpecificPonad() {
        assertUnsupported(
                "Bayern ponad 1.5 gola",
                "FC Augsburg",
                "Bayern München"
        );
    }

    @Test
    void rejectsResultPlusPonadInsteadOfPartiallyParsingIt() {
        assertUnsupported(
                "Bayern wygra + ponad 1.5 gola",
                "FC Augsburg",
                "Bayern München"
        );
    }

    private void assertMatchTotalOver(
            String title,
            String homeTeam,
            String awayTeam,
            String expectedLine
    ) {
        FootballMarket market =
                parser.parse(
                                title,
                                homeTeam,
                                awayTeam
                        )
                        .orElseThrow();

        assertEquals(
                1,
                market.conditions().size()
        );

        FootballMarket.TotalGoals total =
                assertInstanceOf(
                        FootballMarket.TotalGoals.class,
                        market.conditions().getFirst()
                );

        assertEquals(
                FootballMarket.TotalDirection.OVER,
                total.direction()
        );

        assertEquals(
                new BigDecimal(expectedLine),
                total.line()
        );
    }

    private void assertUnsupported(
            String title,
            String homeTeam,
            String awayTeam
    ) {
        assertTrue(
                parser.parse(
                                title,
                                homeTeam,
                                awayTeam
                        )
                        .isEmpty()
        );
    }
}
