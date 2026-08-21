package pl.zagranietyper.repository;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FootballFixtureStatisticsBackfillTargetSelectorTest {
    @Test
    void targetsAreDistinctAndSorted() {
        var targets = FootballFixtureStatisticsBackfillTargetSelector.selectTargets(List.of(
                leg(30, "Powyżej 8.5 rzutów rożnych"),
                leg(10, "Powyżej 20.5 fauli"),
                leg(30, "Obie drużyny +2.5 celnych strzałów"),
                leg(20, "Powyżej 3.5 kartek")));

        assertEquals(List.of(10L, 20L, 30L), targets);
    }

    @Test
    void excludesPlayerPeriodAndUnrelatedMarketsButKeepsGenericCards() {
        var targets = FootballFixtureStatisticsBackfillTargetSelector.selectTargets(List.of(
                leg(1, "A. Gordon +0.5 celnych strzałów"),
                leg(2, "Bayern +2.5 rzutów rożnych w 1. połowie"),
                leg(3, "M. Hermansen +3.5 interwencji"),
                leg(4, "Powyżej 2.5 gola"),
                leg(5, "Powyżej 4.5 kartek"),
                leg(6, "Tottenham +11.5 strzałów"),
                leg(7, "BetBuilder: Espanyol strzeli gola i powyżej 3.5 celnych strzałów")));

        assertEquals(List.of(5L, 6L, 7L), targets);
    }

    private static FootballFixtureStatisticsBackfillTargetSelector.CandidateLeg leg(
            long fixtureId, String title) {
        return new FootballFixtureStatisticsBackfillTargetSelector.CandidateLeg(fixtureId, title);
    }
}
