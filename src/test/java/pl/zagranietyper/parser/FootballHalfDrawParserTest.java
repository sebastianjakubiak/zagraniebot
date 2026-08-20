package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FootballHalfDrawParserTest {

    private final FootballHalfDrawParser parser =
            new FootballHalfDrawParser();

    @Test
    void parsesDrawAtHalftime() {
        assertParsed(
                "Remis do przerwy",
                FootballHalfDrawParser.Market.HALFTIME_DRAW
        );
    }

    @Test
    void parsesFirstHalfDraw() {
        assertParsed(
                "1. połowa: remis",
                FootballHalfDrawParser.Market.HALFTIME_DRAW
        );
    }

    @Test
    void parsesEmojiDrawAtHalftime() {
        assertParsed(
                "✅ Remis do przerwy",
                FootballHalfDrawParser.Market.HALFTIME_DRAW
        );
    }

    @Test
    void parsesHalfOrMatchDraw() {
        assertParsed(
                "1. połowa lub mecz - REMIS",
                FootballHalfDrawParser.Market.HALF_OR_FULL_DRAW
        );
    }

    @Test
    void parsesFirstHalfOrMatchDraw() {
        assertParsed(
                "⏳Pierwsza połowa lub mecz - Remis",
                FootballHalfDrawParser.Market.HALF_OR_FULL_DRAW
        );
    }

    @Test
    void parsesDrawInHalfOrAtEnd() {
        assertParsed(
                "Remis w 1. połowie lub na koniec meczu",
                FootballHalfDrawParser.Market.HALF_OR_FULL_DRAW
        );
    }

    @Test
    void parsesDrawInHalfOrAtEndWithScoreSuffix() {
        assertParsed(
                "Remis w 1. połowie lub na koniec meczu (0-0)",
                FootballHalfDrawParser.Market.HALF_OR_FULL_DRAW
        );
    }

    @Test
    void ignoresPureDraw() {
        assertStatus(
                "Remis",
                FootballHalfDrawParser.Status.NOT_HALF_DRAW
        );
    }

    @Test
    void rejectsUnknownHalfDrawComposite() {
        assertStatus(
                "Remis w pierwszej połowie i powyżej 2.5 gola",
                FootballHalfDrawParser.Status.UNSUPPORTED_COMPOSITE
        );
    }

    private void assertParsed(
            String tip,
            FootballHalfDrawParser.Market market
    ) {
        FootballHalfDrawParser.ParseResult result =
                parser.parse(
                        tip
                );

        assertEquals(
                FootballHalfDrawParser.Status.PARSED,
                result.status()
        );

        assertEquals(
                market,
                result.market()
        );
    }

    private void assertStatus(
            String tip,
            FootballHalfDrawParser.Status status
    ) {
        FootballHalfDrawParser.ParseResult result =
                parser.parse(
                        tip
                );

        assertEquals(
                status,
                result.status()
        );
    }
}