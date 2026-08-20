package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FootballDrawParserTest {

    private final FootballDrawParser parser =
            new FootballDrawParser();

    @Test
    void parsesSimpleDraw() {
        assertParsed(
                "Remis"
        );
    }

    @Test
    void parsesDrawInMatch() {
        assertParsed(
                "Remis w meczu"
        );
    }

    @Test
    void parsesPadnieRemis() {
        assertParsed(
                "Padnie remis"
        );
    }

    @Test
    void parsesBedzieRemis() {
        assertParsed(
                "Będzie remis"
        );
    }

    @Test
    void parsesEmojiDraw() {
        assertParsed(
                "❌ Remis"
        );
    }

    @Test
    void parsesPendingEmojiDraw() {
        assertParsed(
                "⏳ Remis"
        );
    }

    @Test
    void parsesSentenceDraw() {
        assertParsed(
                "Mecz zakończy się remisem"
        );
    }

    @Test
    void rejectsDrawNoBetNumeric() {
        assertStatus(
                "zakład bez remisu - 2",
                FootballDrawParser.Status.UNSUPPORTED_DRAW_NO_BET
        );
    }

    @Test
    void rejectsDrawRefund() {
        assertStatus(
                "Osasuna - remis zwrot",
                FootballDrawParser.Status.UNSUPPORTED_DRAW_NO_BET
        );
    }

    @Test
    void rejectsParenthesizedDrawRefund() {
        assertStatus(
                "Ruch Chorzów (remis - zwrot)",
                FootballDrawParser.Status.UNSUPPORTED_DRAW_NO_BET
        );
    }

    @Test
    void rejectsHalfTimeDraw() {
        assertStatus(
                "Remis do przerwy",
                FootballDrawParser.Status.UNSUPPORTED_HALF
        );
    }

    @Test
    void rejectsFirstHalfDraw() {
        assertStatus(
                "1. połowa: remis",
                FootballDrawParser.Status.UNSUPPORTED_HALF
        );
    }

    @Test
    void rejectsHalfOrFullTimeDraw() {
        assertStatus(
                "Remis w 1. połowie lub na koniec meczu",
                FootballDrawParser.Status.UNSUPPORTED_HALF
        );
    }

    @Test
    void rejectsWinOrDraw() {
        assertStatus(
                "RPA wygra lub remis",
                FootballDrawParser.Status.UNSUPPORTED_DOUBLE_CHANCE
        );
    }

    @Test
    void rejectsWinOrTieVerb() {
        assertStatus(
                "Francja wygra lub zremisuje",
                FootballDrawParser.Status.UNSUPPORTED_DOUBLE_CHANCE
        );
    }

    @Test
    void rejectsDoubleChanceWithScorer() {
        assertStatus(
                "Benfica lub remis + Pavlidis strzeli gola",
                FootballDrawParser.Status.UNSUPPORTED_DOUBLE_CHANCE
        );
    }

    @Test
    void ignoresOrdinaryTotal() {
        assertStatus(
                "Poniżej 3.5 goli",
                FootballDrawParser.Status.NOT_DRAW_LIKE
        );
    }

    private void assertParsed(
            String tip
    ) {
        FootballDrawParser.ParseResult result =
                parser.parse(
                        tip
                );

        assertEquals(
                FootballDrawParser.Status.PARSED,
                result.status()
        );
    }

    private void assertStatus(
            String tip,
            FootballDrawParser.Status expected
    ) {
        FootballDrawParser.ParseResult result =
                parser.parse(
                        tip
                );

        assertEquals(
                expected,
                result.status()
        );
    }
}