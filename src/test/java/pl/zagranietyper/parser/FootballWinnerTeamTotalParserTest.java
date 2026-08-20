package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FootballWinnerTeamTotalParserTest {

    private final FootballWinnerTeamTotalParser parser =
            new FootballWinnerTeamTotalParser();

    @Test
    void parsesMoreThanTwoPointFive() {
        assertParsed(
                "Barcelona wygra i strzeli więcej niż 2.5 bramki",
                "Barcelona",
                "Las Palmas",
                FootballWinnerParser.Selection.HOME,
                3
        );
    }

    @Test
    void parsesOverOnePointFive() {
        assertParsed(
                "Chelsea wygra i strzeli over 1.5 goli",
                "Chelsea",
                "Fulham",
                FootballWinnerParser.Selection.HOME,
                2
        );
    }

    @Test
    void parsesZdobedzieAboveTwoPointFive() {
        assertParsed(
                "Superbets: Arsenal wygra mecz i zdobędzie powyżej 2,5 gola",
                "Arsenal",
                "Dinamo Zagreb",
                FootballWinnerParser.Selection.HOME,
                3
        );
    }

    @Test
    void parsesAwayWinnerTeamTotal() {
        assertParsed(
                "Holandia wygra i strzeli więcej niż 1.5 goli",
                "Finland",
                "Netherlands",
                FootballWinnerParser.Selection.AWAY,
                2
        );
    }

    @Test
    void parsesPolishWordMinimum() {
        assertParsed(
                "Polska wygra i strzeli co najmniej dwie bramki",
                "Poland",
                "Finland",
                FootballWinnerParser.Selection.HOME,
                2
        );
    }

    @Test
    void parsesNumericMinimumOnePointFive() {
        assertParsed(
                "Hiszpania wygra i strzeli co najmniej 1.5 goli",
                "Uruguay",
                "Spain",
                FootballWinnerParser.Selection.AWAY,
                2
        );
    }

    @Test
    void preservesBayerBayernProtection() {
        assertParsed(
                "Bayern wygra i strzeli więcej niż 2.5 gole",
                "Borussia Mönchengladbach",
                "Bayern München",
                FootballWinnerParser.Selection.AWAY,
                3
        );
    }

    @Test
    void rejectsPlayerScorerAfterWinner() {
        assertStatus(
                "Manchester City wygra i E. Haaland strzeli gola",
                "West Ham",
                "Manchester City",
                FootballWinnerTeamTotalParser.Status.UNSUPPORTED_SCORER_OR_OTHER
        );
    }

    @Test
    void rejectsPlayerScorerBeforeWinner() {
        assertStatus(
                "Mikael Ishak strzeli gola i Lech wygra mecz",
                "Lechia Gdansk",
                "Lech Poznan",
                FootballWinnerTeamTotalParser.Status.WINNER_NOT_FOUND
        );
    }

    @Test
    void rejectsHalfScoringMarketBeforeWinner() {
        assertStatus(
                "Manchester City strzeli w obu połowach i wygra mecz",
                "Leicester",
                "Manchester City",
                FootballWinnerTeamTotalParser.Status.UNSUPPORTED_HALF
        );
    }

    @Test
    void rejectsHalfScoringMarketAfterWinner() {
        assertStatus(
                "Górnik Zabrze wygra mecz i strzeli gola w obu połowach tej rywalizacji",
                "Zawisza Bydgoszcz",
                "Gornik Zabrze",
                FootballWinnerTeamTotalParser.Status.UNSUPPORTED_HALF
        );
    }

    @Test
    void rejectsTorresScorerComposite() {
        assertStatus(
                "Barcelona wygra/Torres strzeli gola/ +2.5",
                "Barcelona",
                "Valencia",
                FootballWinnerTeamTotalParser.Status.UNSUPPORTED_SCORER_OR_OTHER
        );
    }

    @Test
    void detectsWrongWinnerParticipant() {
        assertStatus(
                "PSG wygra i strzeli więcej niż 1.5 goli",
                "Flamengo",
                "Bayern München",
                FootballWinnerTeamTotalParser.Status.SUBJECT_MISMATCH
        );
    }

    private void assertParsed(
            String tip,
            String home,
            String away,
            FootballWinnerParser.Selection expectedSelection,
            int expectedMinimumGoals
    ) {
        FootballWinnerTeamTotalParser.ParseResult result =
                parser.parse(
                        tip,
                        home,
                        away
                );

        assertEquals(
                FootballWinnerTeamTotalParser.Status.PARSED,
                result.status()
        );

        assertEquals(
                expectedSelection,
                result.selection()
        );

        assertEquals(
                expectedMinimumGoals,
                result.minimumGoals()
        );
    }

    private void assertStatus(
            String tip,
            String home,
            String away,
            FootballWinnerTeamTotalParser.Status expectedStatus
    ) {
        FootballWinnerTeamTotalParser.ParseResult result =
                parser.parse(
                        tip,
                        home,
                        away
                );

        assertEquals(
                expectedStatus,
                result.status()
        );
    }
}