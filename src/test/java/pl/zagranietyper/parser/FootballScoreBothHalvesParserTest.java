package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FootballScoreBothHalvesParserTest {

    private final FootballScoreBothHalvesParser parser =
            new FootballScoreBothHalvesParser();

    @Test
    void parsesTeamScoresBothHalves() {
        assertTeamParsed(
                "Barcelona strzeli gola w obu połowach",
                "Barcelona",
                "Celta Vigo",
                FootballWinnerParser.Selection.HOME,
                true
        );
    }

    @Test
    void parsesWithoutWordGoal() {
        assertTeamParsed(
                "Chelsea strzeli w obu połowach",
                "Servette FC",
                "Chelsea",
                FootballWinnerParser.Selection.AWAY,
                true
        );
    }

    @Test
    void parsesZdobedzie() {
        assertTeamParsed(
                "Cercle zdobędzie gola w obu połowach",
                "Cercle Brugge",
                "Wisla Krakow",
                FootballWinnerParser.Selection.HOME,
                true
        );
    }

    @Test
    void parsesEveryHalf() {
        assertTeamParsed(
                "Arsenal strzeli gola w każdej połowie",
                "Arsenal",
                "Wolves",
                FootballWinnerParser.Selection.HOME,
                true
        );
    }

    @Test
    void parsesTeamNegative() {
        assertTeamParsed(
                "Liverpool strzeli gola w obu połowach - NIE",
                "Liverpool",
                "Everton",
                FootballWinnerParser.Selection.HOME,
                false
        );
    }

    @Test
    void parsesGenericMatchNegative() {
        FootballScoreBothHalvesParser.ParseResult result =
                parser.parse(
                        "Gol w obu połowach - NIE",
                        "Atletico Madrid",
                        "Getafe"
                );

        assertEquals(
                FootballScoreBothHalvesParser.Status.PARSED,
                result.status()
        );

        assertEquals(
                FootballScoreBothHalvesParser.Market.MATCH_GOAL_BOTH_HALVES,
                result.market()
        );

        assertEquals(
                false,
                result.expectedYes()
        );
    }

    @Test
    void parsesPsgAlias() {
        assertTeamParsed(
                "PSG strzeli gola w obu połowach",
                "Paris Saint Germain",
                "Strasbourg",
                FootballWinnerParser.Selection.HOME,
                true
        );
    }

    @Test
    void parsesHollandAlias() {
        assertTeamParsed(
                "Holandia strzeli gola w obu połowach",
                "Netherlands",
                "Bosnia & Herzegovina",
                FootballWinnerParser.Selection.HOME,
                true
        );
    }

    @Test
    void parsesMilanAlias() {
        assertTeamParsed(
                "Milan strzeli gola w obu połowach",
                "Slovan Bratislava",
                "AC Milan",
                FootballWinnerParser.Selection.AWAY,
                true
        );
    }

    @Test
    void preservesBayernVsBayerSafety() {
        assertTeamParsed(
                "Bayern strzeli gola w obu połowach",
                "Bayer Leverkusen",
                "Bayern München",
                FootballWinnerParser.Selection.AWAY,
                true
        );
    }

    @Test
    void rejectsWinnerComposite() {
        assertStatus(
                "Manchester City strzeli w obu połowach i wygra mecz",
                "Leicester",
                "Manchester City",
                FootballScoreBothHalvesParser.Status.UNSUPPORTED_COMPOSITE
        );
    }

    @Test
    void rejectsWinnerCompositeReverseOrder() {
        assertStatus(
                "Górnik Zabrze wygra mecz i strzeli gola w obu połowach tej rywalizacji",
                "Zawisza Bydgoszcz",
                "Gornik Zabrze",
                FootballScoreBothHalvesParser.Status.UNSUPPORTED_COMPOSITE
        );
    }

    @Test
    void ignoresWinBothHalves() {
        assertStatus(
                "Liverpool wygra obie połowy",
                "Liverpool",
                "Southampton",
                FootballScoreBothHalvesParser.Status.NOT_SCORE_BOTH_HALVES
        );
    }

    @Test
    void detectsWrongParticipant() {
        assertStatus(
                "PSG strzeli gola w obu połowach",
                "Arsenal",
                "Chelsea",
                FootballScoreBothHalvesParser.Status.SUBJECT_MISMATCH
        );
    }

    private void assertTeamParsed(
            String tip,
            String home,
            String away,
            FootballWinnerParser.Selection selection,
            boolean expectedYes
    ) {
        FootballScoreBothHalvesParser.ParseResult result =
                parser.parse(
                        tip,
                        home,
                        away
                );

        assertEquals(
                FootballScoreBothHalvesParser.Status.PARSED,
                result.status()
        );

        assertEquals(
                FootballScoreBothHalvesParser.Market.TEAM_SCORES_BOTH_HALVES,
                result.market()
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
            FootballScoreBothHalvesParser.Status status
    ) {
        FootballScoreBothHalvesParser.ParseResult result =
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