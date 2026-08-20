package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FootballWinMarginParserTest {

    private final FootballWinMarginParser parser =
            new FootballWinMarginParser();

    @Test
    void parsesVictoryMinimumTwoGoals() {
        assertParsed(
                "Zwycięstwo Arsenalu z Southampton minimum 2 golami",
                "Arsenal",
                "Southampton",
                FootballWinnerParser.Selection.HOME,
                2
        );
    }

    @Test
    void parsesDifferenceMinTwoGoals() {
        assertParsed(
                "ŁKS Łódź wygra różnicą min. 2 bramek",
                "ŁKS Łódź",
                "Polonia Warszawa",
                FootballWinnerParser.Selection.HOME,
                2
        );
    }

    @Test
    void parsesAwayWinByAtLeastFour() {
        assertParsed(
                "Wygrają Włochy co najmniej 4 golami",
                "Moldova",
                "Italy",
                FootballWinnerParser.Selection.AWAY,
                4
        );
    }

    @Test
    void doesNotTreatMatchTotalAsWinMargin() {
        assertStatus(
                "Niemcy wygrają i powyżej 2.5 goli",
                "Bosnia & Herzegovina",
                "Germany",
                FootballWinMarginParser.Status.NOT_WIN_MARGIN
        );
    }

    @Test
    void doesNotTreatTeamTotalAsWinMargin() {
        assertStatus(
                "Barcelona wygra i strzeli więcej niż 2.5 bramki",
                "Barcelona",
                "Las Palmas",
                FootballWinMarginParser.Status.NOT_WIN_MARGIN
        );
    }

    @Test
    void detectsWrongParticipant() {
        assertStatus(
                "PSG wygra różnicą min. 2 bramek",
                "Flamengo",
                "Bayern München",
                FootballWinMarginParser.Status.SUBJECT_MISMATCH
        );
    }

    private void assertParsed(
            String tip,
            String home,
            String away,
            FootballWinnerParser.Selection expectedSelection,
            int expectedMargin
    ) {
        FootballWinMarginParser.ParseResult result =
                parser.parse(
                        tip,
                        home,
                        away
                );

        assertEquals(
                FootballWinMarginParser.Status.PARSED,
                result.status()
        );

        assertEquals(
                expectedSelection,
                result.selection()
        );

        assertEquals(
                expectedMargin,
                result.minimumMargin()
        );
    }

    private void assertStatus(
            String tip,
            String home,
            String away,
            FootballWinMarginParser.Status expectedStatus
    ) {
        FootballWinMarginParser.ParseResult result =
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