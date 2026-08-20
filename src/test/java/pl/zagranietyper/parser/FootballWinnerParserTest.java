package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FootballWinnerParserTest {

    private final FootballWinnerParser parser =
            new FootballWinnerParser();

    @Test
    void parsesSimpleHomeWinner() {
        assertParsed(
                "Niemcy wygrają",
                "Germany",
                "Hungary",
                FootballWinnerParser.Selection.HOME
        );
    }

    @Test
    void parsesSimpleAwayWinner() {
        assertParsed(
                "Wygra Hiszpania",
                "Serbia",
                "Spain",
                FootballWinnerParser.Selection.AWAY
        );
    }

    @Test
    void parsesVictoryNoun() {
        assertParsed(
                "Zwycięstwo Hiszpanii",
                "Serbia",
                "Spain",
                FootballWinnerParser.Selection.AWAY
        );
    }

    @Test
    void parsesWinNoun() {
        assertParsed(
                "Wygrana Japonii",
                "Japan",
                "China",
                FootballWinnerParser.Selection.HOME
        );
    }

    @Test
    void parsesVictoryAgainstOpponent() {
        assertParsed(
                "Zwycięstwo Liverpoolu z Bournemouth",
                "Liverpool",
                "Bournemouth",
                FootballWinnerParser.Selection.HOME
        );
    }

    @Test
    void parsesVictoryAgainstOpponentUsingZe() {
        assertParsed(
                "Zwycięstwo Hiszpanii ze Szwajcarią",
                "Spain",
                "Switzerland",
                FootballWinnerParser.Selection.HOME
        );
    }

    @Test
    void parsesWidzewShortName() {
        assertParsed(
                "Zwycięstwo Widzewa z Lechią Zielona Góra",
                "Lechia Zielona Góra",
                "Widzew Łódź",
                FootballWinnerParser.Selection.AWAY
        );
    }

    @Test
    void parsesLegiaShortName() {
        assertParsed(
                "Zwycięstwo Legii z Miedzią",
                "Miedz Legnica",
                "Legia Warszawa",
                FootballWinnerParser.Selection.AWAY
        );
    }

    @Test
    void parsesLechShortName() {
        assertParsed(
                "Zwycięstwo Lecha z Puszczą",
                "Puszcza Niepołomice",
                "Lech Poznan",
                FootballWinnerParser.Selection.AWAY
        );
    }

    @Test
    void parsesExplicitHomeSide() {
        assertParsed(
                "Wygrają gospodarze",
                "Manchester City",
                "Feyenoord",
                FootballWinnerParser.Selection.HOME
        );
    }

    @Test
    void parsesExplicitAwaySide() {
        assertParsed(
                "Wygrają goście",
                "Hoffenheim",
                "VfB Stuttgart",
                FootballWinnerParser.Selection.AWAY
        );
    }

    @Test
    void parsesGksAliasForTychy() {
        assertParsed(
                "Wygra GKS",
                "Tychy 71",
                "Odra Opole",
                FootballWinnerParser.Selection.HOME
        );
    }

    @Test
    void parsesGksAliasForJastrzebie() {
        assertParsed(
                "Wygra GKS",
                "SKRA Częstochowa",
                "Jastrzębie",
                FootballWinnerParser.Selection.AWAY
        );
    }

    @Test
    void parsesTermalicaAlias() {
        assertParsed(
                "Wygra Termalica",
                "Nieciecza",
                "Stal Stalowa Wola",
                FootballWinnerParser.Selection.HOME
        );
    }

    @Test
    void parsesPsgAlias() {
        assertParsed(
                "Wygra PSG",
                "Monaco",
                "Paris Saint Germain",
                FootballWinnerParser.Selection.AWAY
        );
    }

    @Test
    void parsesGlasgowAlias() {
        assertParsed(
                "Wygra Glasgow",
                "Rangers",
                "Panathinaikos",
                FootballWinnerParser.Selection.HOME
        );
    }

    @Test
    void parsesTsgAlias() {
        assertParsed(
                "Wygra TSG",
                "Werder Bremen",
                "1899 Hoffenheim",
                FootballWinnerParser.Selection.AWAY
        );
    }

    @Test
    void parsesNetherlandsWomen() {
        assertParsed(
                "Holandia K wygra mecz",
                "Poland W",
                "Netherlands W",
                FootballWinnerParser.Selection.AWAY
        );
    }

    @Test
    void bayernDoesNotMatchBayer() {
        FootballWinnerParser.ParseResult result =
                parser.parse(
                        "Wygra Bayern",
                        "Bayer Leverkusen",
                        "Bayern München"
                );

        assertEquals(
                FootballWinnerParser.Status.PARSED,
                result.status()
        );

        assertEquals(
                FootballWinnerParser.Selection.AWAY,
                result.selection()
        );
    }

    @Test
    void rejectsBttsComposite() {
        assertRejected(
                "Brazylia wygra mecz i brak BTTS-u",
                "Brazil",
                "Peru"
        );
    }

    @Test
    void rejectsFirstGoalComposite() {
        assertRejected(
                "BetBuilder: Celtic 1. gol, Aston Villa wygra",
                "Aston Villa",
                "Celtic"
        );
    }

    @Test
    void rejectsMinimumGoalsComposite() {
        assertRejected(
                "PSG wygra + w meczu padną co najmniej trzy gole",
                "Paris Saint Germain",
                "Lyon"
        );
    }

    @Test
    void rejectsTeamGoalsComposite() {
        assertRejected(
                "Polska wygra i strzeli co najmniej dwie bramki",
                "Poland",
                "Finland"
        );
    }

    @Test
    void rejectsWinMargin() {
        assertRejected(
                "Wygrają Włochy co najmniej 4 golami",
                "Italy",
                "Norway"
        );
    }

    @Test
    void rejectsDifferenceWinMargin() {
        assertRejected(
                "ŁKS Łódź wygra różnicą min. 2 bramek",
                "ŁKS Łódź",
                "Polonia Warszawa"
        );
    }

    @Test
    void rejectsWinnerPlusCorners() {
        assertRejected(
                "Superbets: Holandia wygra mecz i wykona więcej rzutów rożnych",
                "Netherlands",
                "Bosnia & Herzegovina"
        );
    }

    @Test
    void rejectsTwoTeamNoDraw() {
        assertRejected(
                "Zwycięstwo Liverpoolu lub Chelsea",
                "Liverpool",
                "Chelsea"
        );
    }

    @Test
    void purePsgOnWrongFixtureIsMismatch() {
        FootballWinnerParser.ParseResult result =
                parser.parse(
                        "Wygra PSG",
                        "Flamengo",
                        "Bayern München"
                );

        assertEquals(
                FootballWinnerParser.Status.SUBJECT_MISMATCH,
                result.status()
        );
    }

    private void assertParsed(
            String tip,
            String home,
            String away,
            FootballWinnerParser.Selection expected
    ) {
        FootballWinnerParser.ParseResult result =
                parser.parse(
                        tip,
                        home,
                        away
                );

        assertEquals(
                FootballWinnerParser.Status.PARSED,
                result.status()
        );

        assertEquals(
                expected,
                result.selection()
        );
    }

    private void assertRejected(
            String tip,
            String home,
            String away
    ) {
        FootballWinnerParser.ParseResult result =
                parser.parse(
                        tip,
                        home,
                        away
                );

        assertEquals(
                FootballWinnerParser.Status.UNSUPPORTED_COMPOSITE,
                result.status()
        );
    }
}