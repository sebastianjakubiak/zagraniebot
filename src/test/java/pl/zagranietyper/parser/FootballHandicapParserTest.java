package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FootballHandicapParserTest {

    private final FootballHandicapParser parser =
            new FootballHandicapParser();

    @Test
    void parsesPrefixLineHome() {
        FootballHandicapParser.ParseResult result =
                parser.parse(
                        "handicap -1.5 Manchester City",
                        "Manchester City",
                        "Brentford"
                );

        assertTrue(
                result.parsed()
        );

        assertEquals(
                FootballWinnerParser.Selection.HOME,
                result.selection()
        );

        assertEquals(
                new BigDecimal("-1.5"),
                result.line()
        );
    }

    @Test
    void parsesPrefixLineAway() {
        FootballHandicapParser.ParseResult result =
                parser.parse(
                        "handicap -1.5 Manchester City",
                        "West Ham",
                        "Manchester City"
                );

        assertTrue(
                result.parsed()
        );

        assertEquals(
                FootballWinnerParser.Selection.AWAY,
                result.selection()
        );

        assertEquals(
                new BigDecimal("-1.5"),
                result.line()
        );
    }

    @Test
    void parsesPositiveHandicap() {
        FootballHandicapParser.ParseResult result =
                parser.parse(
                        "handicap +1.5 Stoke City",
                        "Southampton",
                        "Stoke City"
                );

        assertTrue(
                result.parsed()
        );

        assertEquals(
                FootballWinnerParser.Selection.AWAY,
                result.selection()
        );

        assertEquals(
                new BigDecimal("1.5"),
                result.line()
        );
    }

    @Test
    void parsesTeamBeforeLine() {
        FootballHandicapParser.ParseResult result =
                parser.parse(
                        "handicap Chelsea -2.5",
                        "Chelsea",
                        "Shamrock Rovers"
                );

        assertTrue(
                result.parsed()
        );

        assertEquals(
                FootballWinnerParser.Selection.HOME,
                result.selection()
        );

        assertEquals(
                new BigDecimal("-2.5"),
                result.line()
        );
    }

    @Test
    void parsesColonAfterLine() {
        FootballHandicapParser.ParseResult result =
                parser.parse(
                        "Handicap -2,5: Bayern",
                        "Bayern München",
                        "Holstein Kiel"
                );

        assertTrue(
                result.parsed()
        );

        assertEquals(
                FootballWinnerParser.Selection.HOME,
                result.selection()
        );

        assertEquals(
                new BigDecimal("-2.5"),
                result.line()
        );
    }

    @Test
    void parsesColonAfterHandicapWord() {
        FootballHandicapParser.ParseResult result =
                parser.parse(
                        "Handicap: Arsenal -2,5",
                        "Arsenal",
                        "Burnley"
                );

        assertTrue(
                result.parsed()
        );

        assertEquals(
                FootballWinnerParser.Selection.HOME,
                result.selection()
        );

        assertEquals(
                new BigDecimal("-2.5"),
                result.line()
        );
    }

    @Test
    void parsesSuffixHandicap() {
        FootballHandicapParser.ParseResult result =
                parser.parse(
                        "Newcastle United +1.5 handicap",
                        "Manchester City",
                        "Newcastle"
                );

        assertTrue(
                result.parsed()
        );

        assertEquals(
                FootballWinnerParser.Selection.AWAY,
                result.selection()
        );

        assertEquals(
                new BigDecimal("1.5"),
                result.line()
        );
    }

    @Test
    void parsesParenthesizedHandicap() {
        FootballHandicapParser.ParseResult result =
                parser.parse(
                        "Arsenal handicap (-1.5)",
                        "Arsenal",
                        "Olympiakos Piraeus"
                );

        assertTrue(
                result.parsed()
        );

        assertEquals(
                FootballWinnerParser.Selection.HOME,
                result.selection()
        );

        assertEquals(
                new BigDecimal("-1.5"),
                result.line()
        );
    }

    @Test
    void parsesWinnerWithHandicap() {
        FootballHandicapParser.ParseResult result =
                parser.parse(
                        "Wisła Kraków wygra z handicapem (-1.5)",
                        "Hutnik Kraków",
                        "Wisla Krakow"
                );

        assertTrue(
                result.parsed()
        );

        assertEquals(
                FootballWinnerParser.Selection.AWAY,
                result.selection()
        );

        assertEquals(
                new BigDecimal("-1.5"),
                result.line()
        );
    }

    @Test
    void parsesCountryAlias() {
        FootballHandicapParser.ParseResult result =
                parser.parse(
                        "Anglia wygra (handicap -1,5)",
                        "England",
                        "Congo DR"
                );

        assertTrue(
                result.parsed()
        );

        assertEquals(
                FootballWinnerParser.Selection.HOME,
                result.selection()
        );

        assertEquals(
                new BigDecimal("-1.5"),
                result.line()
        );
    }

    @Test
    void parsesIntegerLine() {
        FootballHandicapParser.ParseResult result =
                parser.parse(
                        "Hiszpania wygra (handicap -3)",
                        "Spain",
                        "Bulgaria"
                );

        assertTrue(
                result.parsed()
        );

        assertEquals(
                FootballWinnerParser.Selection.HOME,
                result.selection()
        );

        assertEquals(
                new BigDecimal("-3"),
                result.line()
        );
    }

    @Test
    void parsesColonHandicapWithNamedHomeTeam() {
        FootballHandicapParser.ParseResult result =
                parser.parse(
                        "handicap 0:1 - Brighton",
                        "Brighton",
                        "Crawley Town"
                );

        assertTrue(
                result.parsed()
        );

        assertEquals(
                FootballWinnerParser.Selection.HOME,
                result.selection()
        );

        assertEquals(
                new BigDecimal("-1"),
                result.line()
        );

        assertEquals(
                FootballHandicapParser.Format.COLON_0_1_PREFIX,
                result.format()
        );
    }

    @Test
    void parsesWinnerColonHandicap() {
        FootballHandicapParser.ParseResult result =
                parser.parse(
                        "PSG wygra mecz z handicapem 0:1",
                        "Paris Saint Germain",
                        "Metz"
                );

        assertTrue(
                result.parsed()
        );

        assertEquals(
                FootballWinnerParser.Selection.HOME,
                result.selection()
        );

        assertEquals(
                new BigDecimal("-1"),
                result.line()
        );

        assertEquals(
                FootballHandicapParser.Format.COLON_0_1_WINNER,
                result.format()
        );
    }

    @Test
    void subjectlessColonIsNotGuessed() {
        FootballHandicapParser.ParseResult result =
                parser.parse(
                        "Handicap 0:1",
                        "Bayern München",
                        "1899 Hoffenheim"
                );

        assertFalse(
                result.parsed()
        );

        assertEquals(
                FootballHandicapParser.Status.SUBJECT_NOT_FOUND,
                result.status()
        );

        assertEquals(
                new BigDecimal("-1"),
                result.line()
        );

        assertEquals(
                FootballHandicapParser.Format.COLON_0_1_SUBJECTLESS,
                result.format()
        );
    }

    @Test
    void rejectsCornerHandicap() {
        FootballHandicapParser.ParseResult result =
                parser.parse(
                        "FC Barcelona handicap -0,5 rzutów rożnych",
                        "Villarreal",
                        "Barcelona"
                );

        assertFalse(
                result.parsed()
        );

        assertEquals(
                FootballHandicapParser.Status.UNSUPPORTED_NON_SCORE_HANDICAP,
                result.status()
        );
    }

    @Test
    void barePlusTwoPointFiveIsNotHandicap() {
        FootballHandicapParser.ParseResult result =
                parser.parse(
                        "+2.5",
                        "Wieczysta Kraków",
                        "Stal Mielec"
                );

        assertFalse(
                result.parsed()
        );

        assertEquals(
                FootballHandicapParser.Status.NOT_HANDICAP,
                result.status()
        );
    }

    @Test
    void rejectsUnknownSubject() {
        FootballHandicapParser.ParseResult result =
                parser.parse(
                        "handicap -1.5 Arsenal",
                        "Chelsea",
                        "Liverpool"
                );

        assertFalse(
                result.parsed()
        );

        assertEquals(
                FootballHandicapParser.Status.SUBJECT_MISMATCH,
                result.status()
        );
    }

    @Test
    void parsesCzarniShortName() {
        FootballHandicapParser.ParseResult result =
                parser.parse(
                        "handicap -1.5 Czarni",
                        "Czarni Połaniec",
                        "Unia Tarnow"
                );

        assertTrue(
                result.parsed()
        );

        assertEquals(
                FootballWinnerParser.Selection.HOME,
                result.selection()
        );

        assertEquals(
                new BigDecimal("-1.5"),
                result.line()
        );
    }

    @Test
    void parsesWieczystaShortName() {
        FootballHandicapParser.ParseResult result =
                parser.parse(
                        "✅handicap -1.5 Wieczysta",
                        "Wieczysta Kraków",
                        "Kalisz"
                );

        assertTrue(
                result.parsed()
        );

        assertEquals(
                FootballWinnerParser.Selection.HOME,
                result.selection()
        );

        assertEquals(
                new BigDecimal("-1.5"),
                result.line()
        );
    }

    @Test
    void parsesWalesPositiveHandicap() {
        FootballHandicapParser.ParseResult result =
                parser.parse(
                        "⏳Walia +1.5 handicap",
                        "Belgium",
                        "Wales"
                );

        assertTrue(
                result.parsed()
        );

        assertEquals(
                FootballWinnerParser.Selection.AWAY,
                result.selection()
        );

        assertEquals(
                new BigDecimal("1.5"),
                result.line()
        );
    }

    @Test
    void parsesRomaniaAwayHandicap() {
        FootballHandicapParser.ParseResult result =
                parser.parse(
                        "✅handicap -2.5 Rumunia",
                        "San Marino",
                        "Romania"
                );

        assertTrue(
                result.parsed()
        );

        assertEquals(
                FootballWinnerParser.Selection.AWAY,
                result.selection()
        );

        assertEquals(
                new BigDecimal("-2.5"),
                result.line()
        );
    }

    @Test
    void parsesMoroccoWinnerHandicap() {
        FootballHandicapParser.ParseResult result =
                parser.parse(
                        "Maroko wygra mecz z handicapem (-1.5)",
                        "Morocco",
                        "Haiti"
                );

        assertTrue(
                result.parsed()
        );

        assertEquals(
                FootballWinnerParser.Selection.HOME,
                result.selection()
        );

        assertEquals(
                new BigDecimal("-1.5"),
                result.line()
        );
    }
}