package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FootballDoubleChanceParserTest {

    private final FootballDoubleChanceParser parser =
            new FootballDoubleChanceParser();

    @Test
    void parsesPureNotLose() {
        assertParsed(
                "Termalica nie przegra",
                "Nieciecza",
                "Cracovia Krakow",
                FootballWinnerParser.Selection.HOME,
                FootballDoubleChanceParser.Format.NOT_LOSE
        );
    }

    @Test
    void parsesRpaWinOrDraw() {
        assertParsed(
                "RPA wygra lub remis",
                "South Africa",
                "Angola",
                FootballWinnerParser.Selection.HOME,
                FootballDoubleChanceParser.Format.WIN_OR_DRAW
        );
    }

    @Test
    void parsesFranceWinOrDraw() {
        assertParsed(
                "Francja wygra lub zremisuje",
                "Brazil",
                "France",
                FootballWinnerParser.Selection.AWAY,
                FootballDoubleChanceParser.Format.WIN_OR_DRAW
        );
    }

    @Test
    void parsesParisFcWinOrDraw() {
        assertParsed(
                "Paris FC wygra lub zremisuje",
                "Paris FC",
                "Paris Saint Germain",
                FootballWinnerParser.Selection.HOME,
                FootballDoubleChanceParser.Format.WIN_OR_DRAW
        );
    }

    @Test
    void parsesSwedenWinMatchOrDraw() {
        assertParsed(
                "Szwecja wygra mecz lub zremisuje",
                "Sweden",
                "Greece",
                FootballWinnerParser.Selection.HOME,
                FootballDoubleChanceParser.Format.WIN_OR_DRAW
        );
    }

    @Test
    void parsesTermalicaWinOrDraw() {
        assertParsed(
                "Termalica wygra lub zremisuje",
                "Nieciecza",
                "Ruch Chorzów",
                FootballWinnerParser.Selection.HOME,
                FootballDoubleChanceParser.Format.WIN_OR_DRAW
        );
    }

    @Test
    void rejectsNotLoseAndMatchTotal() {
        assertStatus(
                "BetBuilder: Kolumbia nie przegra i poniżej 3,5 gola",
                "Peru",
                "Colombia",
                FootballDoubleChanceParser.Status.UNSUPPORTED_COMPOSITE
        );
    }

    @Test
    void rejectsNotLoseAndCorners() {
        assertStatus(
                "Węgrzy nie przegrają meczu i wykonają więcej rożnych",
                "Bosnia & Herzegovina",
                "Hungary",
                FootballDoubleChanceParser.Status.UNSUPPORTED_COMPOSITE
        );
    }

    @Test
    void rejectsNotLoseAndGoal() {
        assertStatus(
                "Roma nie przegra + strzeli powyżej 0,5 gola",
                "Lecce",
                "AS Roma",
                FootballDoubleChanceParser.Status.UNSUPPORTED_COMPOSITE
        );
    }

    @Test
    void rejectsNotLoseAndBtts() {
        assertStatus(
                "Litwa nie przegra + BTTS: nie",
                "Lithuania",
                "Malta",
                FootballDoubleChanceParser.Status.UNSUPPORTED_COMPOSITE
        );
    }

    @Test
    void rejectsWinOrDrawComposite() {
        assertStatus(
                "Stal mielec powyżej 0.5 bramki i Stal Mieliec zwycięstwo lub remis",
                "Stal Mielec",
                "Ruch Chorzów",
                FootballDoubleChanceParser.Status.UNSUPPORTED_COMPOSITE
        );
    }

    @Test
    void rejectsWrongParticipant() {
        assertStatus(
                "PSG nie przegra",
                "Strasbourg",
                "Lille",
                FootballDoubleChanceParser.Status.SUBJECT_MISMATCH
        );
    }

    private void assertParsed(
            String tip,
            String home,
            String away,
            FootballWinnerParser.Selection selection,
            FootballDoubleChanceParser.Format format
    ) {
        FootballDoubleChanceParser.ParseResult result =
                parser.parse(
                        tip,
                        home,
                        away
                );

        assertEquals(
                FootballDoubleChanceParser.Status.PARSED,
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
            FootballDoubleChanceParser.Status status
    ) {
        FootballDoubleChanceParser.ParseResult result =
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