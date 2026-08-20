package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FootballMarketParserHalfMarketGuardTest {

    private final FootballMarketParser parser =
            new FootballMarketParser();

    @Test
    void rejectsFirstHalfTotal() {
        assertUnsupported(
                "-1.5 goli w 1. połowie",
                "AC Milan",
                "Liverpool"
        );
    }

    @Test
    void rejectsSecondHalfTeamTotal() {
        assertUnsupported(
                "Czechy powyżej 0.5 gola w 2. połowie",
                "Albania",
                "Czechia"
        );
    }

    @Test
    void rejectsTeamWinningAtLeastOneHalf() {
        assertUnsupported(
                "Stuttgart wygra przynajmniej jedną połowę",
                "SC Freiburg",
                "VfB Stuttgart"
        );
    }

    @Test
    void rejectsWinningBothHalves() {
        assertUnsupported(
                "Fiorentina wygra obie połowy",
                "Fiorentina",
                "The New Saints"
        );
    }

    @Test
    void rejectsHalfTimeMarket() {
        assertUnsupported(
                "Remis do przerwy",
                "Arsenal",
                "Chelsea"
        );
    }

    @Test
    void rejectsEnglishHalfMarket() {
        assertUnsupported(
                "Liverpool to win first half",
                "Liverpool",
                "Everton"
        );
    }

    @Test
    void rejectsCompositeContainingHalfCondition() {
        assertUnsupported(
                "BetBuilder: Powyżej 1.5 gola w meczu, powyżej 0.5 gola w 1. połowie",
                "Brentford",
                "Everton"
        );
    }

    @Test
    void stillParsesOrdinaryFullMatchTotal() {
        assertSupported(
                "Powyżej 2.5 gola w meczu",
                "Arsenal",
                "Chelsea"
        );
    }

    @Test
    void stillParsesOrdinaryMatchWinner() {
        assertSupported(
                "Liverpool wygra mecz",
                "Liverpool",
                "Everton"
        );
    }

    @Test
    void stillParsesOrdinaryBtts() {
        assertSupported(
                "Obie drużyny strzelą gola",
                "Arsenal",
                "Chelsea"
        );
    }

    private void assertUnsupported(
            String tipTitle,
            String homeTeam,
            String awayTeam
    ) {
        assertTrue(
                parser.parse(
                        tipTitle,
                        homeTeam,
                        awayTeam
                ).isEmpty(),
                () -> "Expected UNSUPPORTED but parser accepted: " + tipTitle
        );
    }

    private void assertSupported(
            String tipTitle,
            String homeTeam,
            String awayTeam
    ) {
        assertFalse(
                parser.parse(
                        tipTitle,
                        homeTeam,
                        awayTeam
                ).isEmpty(),
                () -> "Expected supported market but parser rejected: " + tipTitle
        );
    }
}