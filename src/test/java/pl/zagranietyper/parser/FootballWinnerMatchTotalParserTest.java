package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FootballWinnerMatchTotalParserTest {

    private final FootballWinnerMatchTotalParser parser =
            new FootballWinnerMatchTotalParser();

    @Test
    void parsesWinnerAndOver() {
        assertParsed(
                "Słowacja wygra i over 1.5 goli",
                "Estonia",
                "Slovakia",
                FootballWinnerParser.Selection.AWAY,
                FootballWinnerMatchTotalParser.Direction.OVER,
                1.5
        );
    }

    @Test
    void parsesWinnerAndUnderWithComma() {
        assertParsed(
                "Brazylia wygra i poniżej 4,5 gola",
                "Brazil",
                "Ecuador",
                FootballWinnerParser.Selection.HOME,
                FootballWinnerMatchTotalParser.Direction.UNDER,
                4.5
        );
    }

    @Test
    void parsesMyCombiPrefix() {
        assertParsed(
                "MyCombi: Rumunia wygra i poniżej 4,5 gola",
                "Romania",
                "Lithuania",
                FootballWinnerParser.Selection.HOME,
                FootballWinnerMatchTotalParser.Direction.UNDER,
                4.5
        );
    }

    @Test
    void parsesBetBuilderPrefix() {
        assertParsed(
                "BetBuilder: Turcja wygra i poniżej 4,5 gola",
                "Türkiye",
                "Montenegro",
                FootballWinnerParser.Selection.HOME,
                FootballWinnerMatchTotalParser.Direction.UNDER,
                4.5
        );
    }

    @Test
    void parsesWinnerPluralBeforeTotal() {
        assertParsed(
                "Włochy wygrają i powyżej 1,5 gola",
                "Israel",
                "Italy",
                FootballWinnerParser.Selection.AWAY,
                FootballWinnerMatchTotalParser.Direction.OVER,
                1.5
        );
    }

    @Test
    void fixesJagielloniaSourceTypo() {
        assertParsed(
                "Jagiellonai wygra + powyżej 1.5 goli",
                "Lechia Gdansk",
                "Jagiellonia",
                FootballWinnerParser.Selection.AWAY,
                FootballWinnerMatchTotalParser.Direction.OVER,
                1.5
        );
    }

    @Test
    void parsesRpaAlias() {
        assertParsed(
                "RPA wygra i poniżej 3.5 goli",
                "Zimbabwe",
                "South Africa",
                FootballWinnerParser.Selection.AWAY,
                FootballWinnerMatchTotalParser.Direction.UNDER,
                3.5
        );
    }

    @Test
    void preservesBayerBayernProtection() {
        assertParsed(
                "Bayern wygra i over 1.5 goli",
                "Bayer Leverkusen",
                "Bayern München",
                FootballWinnerParser.Selection.AWAY,
                FootballWinnerMatchTotalParser.Direction.OVER,
                1.5
        );
    }

    @Test
    void rejectsWinMargin() {
        assertStatus(
                "Zwycięstwo Arsenalu z Southampton minimum 2 golami",
                "Arsenal",
                "Southampton",
                FootballWinnerMatchTotalParser.Status.UNSUPPORTED_COMPOSITE
        );
    }

    @Test
    void rejectsCornerComposite() {
        assertStatus(
                "BetBuilder: Atalanta wygra i wykona powyżej 5,5 rzutu rożnego",
                "Atalanta",
                "Empoli",
                FootballWinnerMatchTotalParser.Status.UNSUPPORTED_COMPOSITE
        );
    }

    @Test
    void rejectsWinOrDrawComposite() {
        assertStatus(
                "Stal mielec powyżej 0.5 bramki i Stal Mieliec zwycięstwo lub remis",
                "Stal Mielec",
                "Ruch Chorzów",
                FootballWinnerMatchTotalParser.Status.UNSUPPORTED_COMPOSITE
        );
    }

    @Test
    void catchesWrongResolvedFixtureBeforeTotalGrammar() {
        assertStatus(
                "PSG wygra + w meczu padną co najmniej trzy gole",
                "Flamengo",
                "Bayern München",
                FootballWinnerMatchTotalParser.Status.SUBJECT_MISMATCH
        );
    }

    @Test
    void ignoresTeamTotalFamily() {
        assertStatus(
                "Barcelona wygra i strzeli więcej niż 2.5 bramki",
                "Barcelona",
                "Las Palmas",
                FootballWinnerMatchTotalParser.Status.NOT_WINNER_PLUS_TOTAL
        );
    }

    private void assertParsed(
            String tip,
            String home,
            String away,
            FootballWinnerParser.Selection selection,
            FootballWinnerMatchTotalParser.Direction direction,
            double line
    ) {
        FootballWinnerMatchTotalParser.ParseResult result =
                parser.parse(
                        tip,
                        home,
                        away
                );

        assertEquals(
                FootballWinnerMatchTotalParser.Status.PARSED,
                result.status()
        );

        assertEquals(
                selection,
                result.selection()
        );

        assertEquals(
                direction,
                result.direction()
        );

        assertEquals(
                line,
                result.line()
        );
    }

    private void assertStatus(
            String tip,
            String home,
            String away,
            FootballWinnerMatchTotalParser.Status expected
    ) {
        FootballWinnerMatchTotalParser.ParseResult result =
                parser.parse(
                        tip,
                        home,
                        away
                );

        assertEquals(
                expected,
                result.status()
        );
    }
}