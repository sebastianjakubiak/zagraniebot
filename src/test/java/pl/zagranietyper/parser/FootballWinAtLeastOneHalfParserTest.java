package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FootballWinAtLeastOneHalfParserTest {

    private final FootballWinAtLeastOneHalfParser parser =
            new FootballWinAtLeastOneHalfParser();

    @Test
    void parsesAtLeastOneHalfWord() {
        assertParsed(
                "Termalica wygra przynajmniej jedną połowę",
                "Nieciecza",
                "Wisla Plock",
                FootballWinnerParser.Selection.HOME,
                true
        );
    }

    @Test
    void parsesAtLeastOneHalfNumeric() {
        assertParsed(
                "Nottingham wygra przynajmniej 1 połowę",
                "Nottingham Forest",
                "Newcastle",
                FootballWinnerParser.Selection.HOME,
                true
        );
    }

    @Test
    void parsesCoNajmniejNumeric() {
        assertParsed(
                "Watford wygra co najmniej 1 połowę",
                "Plymouth",
                "Watford",
                FootballWinnerParser.Selection.AWAY,
                true
        );
    }

    @Test
    void parsesCoNajmniejWord() {
        assertParsed(
                "Finlandia wygra co najmniej jedną połowę",
                "Lithuania",
                "Finland",
                FootballWinnerParser.Selection.AWAY,
                true
        );
    }

    @Test
    void parsesMinimumOneHalf() {
        assertParsed(
                "Aston Villa wygra minimum 1 połowę - tak",
                "Aston Villa",
                "Manchester City",
                FootballWinnerParser.Selection.HOME,
                true
        );
    }

    @Test
    void parsesAnyHalf() {
        assertParsed(
                "Newcastle wygra dowolną połowę",
                "Manchester United",
                "Newcastle",
                FootballWinnerParser.Selection.AWAY,
                true
        );
    }

    @Test
    void parsesAnyHalfOfMatch() {
        assertParsed(
                "Arsenal wygra dowolną połowę meczu",
                "Newcastle",
                "Arsenal",
                FootballWinnerParser.Selection.AWAY,
                true
        );
    }

    @Test
    void parsesExplicitNo() {
        assertParsed(
                "Espanyol wygra przynajmniej jedną połowę: NIE",
                "Espanyol",
                "Alaves",
                FootballWinnerParser.Selection.HOME,
                false
        );
    }

    @Test
    void parsesExplicitYes() {
        assertParsed(
                "Barcelona wygra przynajmniej jedną połowę: TAK",
                "Athletic Club",
                "Barcelona",
                FootballWinnerParser.Selection.AWAY,
                true
        );
    }

    @Test
    void parsesMoenchengladbachAlias() {
        assertParsed(
                "Moenchengladbach wygra przynajmniej jedną połowę: NIE",
                "Eintracht Frankfurt",
                "Borussia Mönchengladbach",
                FootballWinnerParser.Selection.AWAY,
                false
        );
    }

    @Test
    void parsesBvbAlias() {
        assertParsed(
                "BVB wygra przynajmniej jedną połowę: NIE",
                "Borussia Dortmund",
                "Barcelona",
                FootballWinnerParser.Selection.HOME,
                false
        );
    }

    @Test
    void parsesStuttgartAlias() {
        assertParsed(
                "Stuttgart wygra przynajmniej jedną połowę",
                "SC Freiburg",
                "VfB Stuttgart",
                FootballWinnerParser.Selection.AWAY,
                true
        );
    }

    @Test
    void parsesLechAlias() {
        assertParsed(
                "Lech wygra przynajmniej jedną połowę",
                "Radomiak Radom",
                "Lech Poznan",
                FootballWinnerParser.Selection.AWAY,
                true
        );
    }

    @Test
    void parsesZaglebieAlias() {
        assertParsed(
                "Zagłębie wygra co najmniej 1 połowę",
                "Zaglebie Lubin",
                "Widzew Łódź",
                FootballWinnerParser.Selection.HOME,
                true
        );
    }

    @Test
    void ignoresWinBothHalves() {
        assertStatus(
                "Liverpool wygra obie połowy",
                "Liverpool",
                "Southampton",
                FootballWinAtLeastOneHalfParser.Status.NOT_AT_LEAST_ONE_HALF
        );
    }

    @Test
    void ignoresFirstHalfWinner() {
        assertStatus(
                "Pogoń wygra 1. połowę",
                "Motor Lublin",
                "Pogon Szczecin",
                FootballWinAtLeastOneHalfParser.Status.NOT_AT_LEAST_ONE_HALF
        );
    }

    @Test
    void ignoresFirstHalfOrMatch() {
        assertStatus(
                "Jagiellonia wygra 1. połowę lub mecz",
                "Jagiellonia",
                "Piast Gliwice",
                FootballWinAtLeastOneHalfParser.Status.NOT_AT_LEAST_ONE_HALF
        );
    }

    @Test
    void detectsWrongParticipant() {
        assertStatus(
                "PSG wygra przynajmniej jedną połowę",
                "Arsenal",
                "Chelsea",
                FootballWinAtLeastOneHalfParser.Status.SUBJECT_MISMATCH
        );
    }

    private void assertParsed(
            String tip,
            String home,
            String away,
            FootballWinnerParser.Selection selection,
            boolean expectedYes
    ) {
        FootballWinAtLeastOneHalfParser.ParseResult result =
                parser.parse(
                        tip,
                        home,
                        away
                );

        assertEquals(
                FootballWinAtLeastOneHalfParser.Status.PARSED,
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
            FootballWinAtLeastOneHalfParser.Status status
    ) {
        FootballWinAtLeastOneHalfParser.ParseResult result =
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