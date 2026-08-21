package pl.zagranietyper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FootballFixtureStatisticsBackfillMainTest {
    @Test
    void defaultsToDryRun() {
        assertFalse(FootballFixtureStatisticsBackfillMain.parseArgs(new String[0]).run());
    }

    @Test
    void runRequiresLimitOrExplicitAll() {
        assertThrows(IllegalArgumentException.class,
                () -> FootballFixtureStatisticsBackfillMain.parseArgs(new String[]{"--run"}));
        assertTrue(FootballFixtureStatisticsBackfillMain.parseArgs(
                new String[]{"--run", "--limit=10"}).run());
        assertTrue(FootballFixtureStatisticsBackfillMain.parseArgs(
                new String[]{"--run", "--all"}).all());
    }

    @Test
    void rejectsInvalidLimitsAndConflictingScope() {
        assertThrows(IllegalArgumentException.class,
                () -> FootballFixtureStatisticsBackfillMain.parseArgs(
                        new String[]{"--run", "--limit=0"}));
        assertThrows(IllegalArgumentException.class,
                () -> FootballFixtureStatisticsBackfillMain.parseArgs(
                        new String[]{"--run", "--limit=-1"}));
        assertThrows(IllegalArgumentException.class,
                () -> FootballFixtureStatisticsBackfillMain.parseArgs(
                        new String[]{"--run", "--limit=nope"}));
        assertThrows(IllegalArgumentException.class,
                () -> FootballFixtureStatisticsBackfillMain.parseArgs(
                        new String[]{"--run", "--limit=10", "--all"}));
    }

    @Test
    void executionOptionsCannotBeUsedWithoutRun() {
        assertThrows(IllegalArgumentException.class,
                () -> FootballFixtureStatisticsBackfillMain.parseArgs(new String[]{"--limit=10"}));
        assertThrows(IllegalArgumentException.class,
                () -> FootballFixtureStatisticsBackfillMain.parseArgs(
                        new String[]{"--retry-incomplete"}));
    }
}
