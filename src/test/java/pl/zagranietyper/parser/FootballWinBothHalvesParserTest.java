package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FootballWinBothHalvesParserTest {

    private final FootballWinBothHalvesParser parser =
            new FootballWinBothHalvesParser();

    @Test
    void parsesAlNassr() {
        assertParsed(
                "Al Nassr wygra obie połowy",
                "Al-Nassr",
                "Al-Raed",
                FootballWinnerParser.Selection.HOME,
                true
        );
    }

    @Test
    void parsesFiorentina() {
        assertParsed(
                "Fiorentina wygra obie połowy",
                "Fiorentina",
                "The New Saints",
                FootballWinnerParser.Selection.HOME,
                true
        );
    }

    @Test
    void parsesLiverpool() {
        assertParsed(
                "❌Liverpool wygra obie połowy",
                "Liverpool",
                "Southampton",
                FootballWinnerParser.Selection.HOME,
                true
        );
    }

    @Test
    void parsesEnglandAlias() {
        assertParsed(
                "Anglia wygra obie połowy",
                "England",
                "Latvia",
                FootballWinnerParser.Selection.HOME,
                true
        );
    }

    @Test
    void parsesExplicitYes() {
        assertParsed(
                "Liverpool wygra obie połowy - TAK",
                "Liverpool",
                "Southampton",
                FootballWinnerParser.Selection.HOME,
                true
        );
    }

    @Test
    void parsesExplicitNo() {
        assertParsed(
                "Liverpool wygra obie połowy - NIE",
                "Liverpool",
                "Southampton",
                FootballWinnerParser.Selection.HOME,
                false
        );
    }

    @Test
    void ignoresAtLeastOneHalf() {
        assertStatus(
                "Liverpool wygra przynajmniej jedną połowę",
                "Liverpool",
                "Southampton",
                FootballWinBothHalvesParser.Status.NOT_WIN_BOTH_HALVES
        );
    }

    @Test
    void rejectsComposite() {
        assertStatus(
                "Liverpool wygra obie połowy i powyżej 2.5 gola",
                "Liverpool",
                "Southampton",
                FootballWinBothHalvesParser.Status.UNSUPPORTED_COMPOSITE
        );
    }

    @Test
    void detectsWrongParticipant() {
        assertStatus(
                "PSG wygra obie połowy",
                "Arsenal",
                "Chelsea",
                FootballWinBothHalvesParser.Status.SUBJECT_MISMATCH
        );
    }

    private void assertParsed(
            String tip,
            String home,
            String away,
            FootballWinnerParser.Selection selection,
            boolean expectedYes
    ) {
        FootballWinBothHalvesParser.ParseResult result =
                parser.parse(
                        tip,
                        home,
                        away
                );

        assertEquals(
                FootballWinBothHalvesParser.Status.PARSED,
                result.status()
        );

        assertEquals(
                selection,
                result.selection()
        );

        assertEquals(
                expectedYes,
                result.expectedYes()
        );
    }

    private void assertStatus(
            String tip,
            String home,
            String away,
            FootballWinBothHalvesParser.Status status
    ) {
        FootballWinBothHalvesParser.ParseResult result =
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