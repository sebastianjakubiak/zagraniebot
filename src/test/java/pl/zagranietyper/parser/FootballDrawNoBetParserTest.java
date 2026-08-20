package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FootballDrawNoBetParserTest {

    private final FootballDrawNoBetParser parser =
            new FootballDrawNoBetParser();

    @Test
    void parsesNumericHome() {
        assertParsed(
                "zakład bez remisu - 1",
                "West Ham",
                "Everton",
                FootballWinnerParser.Selection.HOME,
                FootballDrawNoBetParser.Format.NUMERIC
        );
    }

    @Test
    void parsesNumericAway() {
        assertParsed(
                "zakład bez remisu - 2",
                "Hull City",
                "Sheffield Utd",
                FootballWinnerParser.Selection.AWAY,
                FootballDrawNoBetParser.Format.NUMERIC
        );
    }

    @Test
    void parsesEmojiNumeric() {
        assertParsed(
                "✅zakład bez remisu - 2",
                "Leicester",
                "Brentford",
                FootballWinnerParser.Selection.AWAY,
                FootballDrawNoBetParser.Format.NUMERIC
        );
    }

    @Test
    void parsesNamedPrefix() {
        assertParsed(
                "Zakład bez remisu: Cracovia",
                "Puszcza Niepołomice",
                "Cracovia Krakow",
                FootballWinnerParser.Selection.AWAY,
                FootballDrawNoBetParser.Format.NAMED_PREFIX
        );
    }

    @Test
    void parsesNamedRefundAway() {
        assertParsed(
                "Osasuna - remis zwrot",
                "Alaves",
                "Osasuna",
                FootballWinnerParser.Selection.AWAY,
                FootballDrawNoBetParser.Format.NAMED_REFUND
        );
    }

    @Test
    void parsesNamedRefundHome() {
        assertParsed(
                "Ruch Chorzów (remis - zwrot)",
                "Ruch Chorzów",
                "Puszcza Niepołomice",
                FootballWinnerParser.Selection.HOME,
                FootballDrawNoBetParser.Format.NAMED_REFUND
        );
    }

    @Test
    void fixesWieczystaTypoLocally() {
        assertParsed(
                "Wiczysta Kraków - remis zwrot",
                "Pogoń Siedlce",
                "Wieczysta Kraków",
                FootballWinnerParser.Selection.AWAY,
                FootballDrawNoBetParser.Format.NAMED_REFUND
        );
    }

    @Test
    void detectsWrongNamedParticipant() {
        assertStatus(
                "PSG - remis zwrot",
                "Flamengo",
                "Bayern München",
                FootballDrawNoBetParser.Status.SUBJECT_MISMATCH
        );
    }

    @Test
    void ignoresPureDraw() {
        assertStatus(
                "Remis",
                "Arsenal",
                "Chelsea",
                FootballDrawNoBetParser.Status.NOT_DRAW_NO_BET
        );
    }

    @Test
    void ignoresDoubleChance() {
        assertStatus(
                "Francja wygra lub zremisuje",
                "Brazil",
                "France",
                FootballDrawNoBetParser.Status.NOT_DRAW_NO_BET
        );
    }

    private void assertParsed(
            String tip,
            String home,
            String away,
            FootballWinnerParser.Selection selection,
            FootballDrawNoBetParser.Format format
    ) {
        FootballDrawNoBetParser.ParseResult result =
                parser.parse(
                        tip,
                        home,
                        away
                );

        assertEquals(
                FootballDrawNoBetParser.Status.PARSED,
                result.status()
        );

        assertEquals(
                selection,
                result.selection()
        );

        assertEquals(
                format,
                result.format()
        );
    }

    private void assertStatus(
            String tip,
            String home,
            String away,
            FootballDrawNoBetParser.Status status
    ) {
        FootballDrawNoBetParser.ParseResult result =
                parser.parse(
                        tip,
                        home,
                        away
                );

        assertEquals(
                status,
                result.status()
        );
    }
}