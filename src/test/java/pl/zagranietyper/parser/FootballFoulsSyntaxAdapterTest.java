package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.FootballFixtureStatisticCondition;
import pl.zagranietyper.model.FootballFixtureStatisticType;

import static org.junit.jupiter.api.Assertions.*;

final class FootballFoulsSyntaxAdapterTest {
    private final FootballFoulsSyntaxAdapter adapter = new FootballFoulsSyntaxAdapter();

    @Test void parsesAuditedMatchTotalsAndMinimum() {
        assertParsed("Powyżej 24.5 fauli", "Arsenal", "Chelsea", Subject.MATCH, Comparison.OVER);
        assertParsed("Poniżej 24,5 fauli", "Arsenal", "Chelsea", Subject.MATCH, Comparison.UNDER);
        assertParsed("W meczu obejrzymy min. 29 fauli", "Arsenal", "Chelsea", Subject.MATCH, Comparison.MINIMUM);
        assertParsed("Powyżej 24 fauli", "Arsenal", "Chelsea", Subject.MATCH, Comparison.OVER);
    }

    @Test void parsesNamedTeamMarketsThroughSharedResolver() {
        assertStatus("Anglia powyżej 9.5 fauli", "Albania", "England", Status.AMBIGUOUS_PARTICIPANT);
        assertParsed("Mainz powyżej 12.5 fauli", "FSV Mainz 05", "Borussia Mönchengladbach", Subject.HOME, Comparison.OVER);
        assertParsed("Stal Stalowa Wola odnotuje min. 13 fauli", "Ruch Chorzów", "Stal Stalowa Wola", Subject.AWAY, Comparison.MINIMUM);
        assertParsed("Chelsea poniżej 10 fauli", "Chelsea", "Fulham", Subject.HOME, Comparison.UNDER);
        assertStatus("United powyżej 9.5 fauli", "Manchester United", "Newcastle United", Status.AMBIGUOUS_PARTICIPANT);
    }

    @Test void parsesOnlyIndependentlyAuditedSignedTeamFamily() {
        assertParsed("Ipswich +9.5 fauli", "Manchester City", "Ipswich", Subject.AWAY, Comparison.OVER);
        assertParsed("Arsenal +9.5 fauli", "Arsenal", "Brentford", Subject.HOME, Comparison.OVER);
        assertStatus("+24.5 fauli", "Arsenal", "Chelsea", Status.UNSUPPORTED_SIGNED_NOTATION);
        assertStatus("Chelsea -9.5 fauli", "Chelsea", "Fulham", Status.UNSUPPORTED_SIGNED_NOTATION);
    }

    @Test void rejectsPlayerPeriodComparisonHandicapAndComposites() {
        assertStatus("J. Palhinha +1.5 fauli", "Portugal", "Croatia", Status.UNSUPPORTED_PLAYER);
        assertStatus("L. Delap +1.5 fauli", "Arsenal", "Ipswich", Status.UNSUPPORTED_PLAYER);
        assertStatus("Arsenal powyżej 4.5 fauli w 1. połowie", "Arsenal", "Chelsea", Status.UNSUPPORTED_PERIOD);
        assertStatus("Arsenal więcej fauli", "Arsenal", "Chelsea", Status.UNSUPPORTED_HANDICAP_OR_COMPARISON);
        assertStatus("Arsenal handicap -1.5 fauli", "Arsenal", "Chelsea", Status.UNSUPPORTED_HANDICAP_OR_COMPARISON);
        assertStatus("Liczba fauli - 2.drużyna — powyżej 10.5", "Newcastle", "Arsenal", Status.UNSUPPORTED_HANDICAP_OR_COMPARISON);
        assertStatus("Bournemouth +12.5 fauli i +1.5 kartek", "Bournemouth", "Manchester City", Status.UNSUPPORTED_COMPOSITE);
        assertStatus("Obie drużyny powyżej 8.5 fauli", "Arsenal", "Bayern", Status.UNSUPPORTED_COMPOSITE);
    }

    @Test void doesNotCaptureOtherStatisticsAndSupportsKnownZeroInEngineShape() {
        assertEquals(FootballFoulsSyntaxAdapter.Status.NOT_FOULS_LIKE,
                adapter.parse("Powyżej 8.5 rzutów rożnych", "Arsenal", "Chelsea").status());
        assertEquals(FootballFoulsSyntaxAdapter.Status.NOT_FOULS_LIKE,
                adapter.parse("Powyżej 4.5 kartek", "Arsenal", "Chelsea").status());
        assertEquals(FootballFoulsSyntaxAdapter.Status.NOT_FOULS_LIKE,
                adapter.parse("Powyżej 8.5 celnych strzałów", "Arsenal", "Chelsea").status());
        var parsed = adapter.parse("Chelsea powyżej 0 fauli", "Arsenal", "Chelsea");
        assertEquals(0, parsed.condition().threshold().signum());
    }

    private void assertParsed(String title, String home, String away, Subject subject, Comparison comparison) {
        var result = adapter.parse(title, home, away);
        assertTrue(result.parsed(), () -> title + " => " + result.status());
        assertEquals(FootballFixtureStatisticType.FOULS, result.condition().type());
        assertEquals(subject.value, result.condition().subject());
        assertEquals(comparison.value, result.condition().comparison());
    }
    private void assertStatus(String title, String home, String away, Status status) {
        assertEquals(status.value, adapter.parse(title, home, away).status());
    }
    private enum Subject {
        MATCH(FootballFixtureStatisticCondition.Subject.MATCH), HOME(FootballFixtureStatisticCondition.Subject.HOME), AWAY(FootballFixtureStatisticCondition.Subject.AWAY);
        final FootballFixtureStatisticCondition.Subject value; Subject(FootballFixtureStatisticCondition.Subject value) { this.value=value; }
    }
    private enum Comparison {
        OVER(FootballFixtureStatisticCondition.Comparison.OVER), UNDER(FootballFixtureStatisticCondition.Comparison.UNDER), MINIMUM(FootballFixtureStatisticCondition.Comparison.MINIMUM);
        final FootballFixtureStatisticCondition.Comparison value; Comparison(FootballFixtureStatisticCondition.Comparison value) { this.value=value; }
    }
    private enum Status {
        AMBIGUOUS_PARTICIPANT(FootballFoulsSyntaxAdapter.Status.AMBIGUOUS_PARTICIPANT),
        UNSUPPORTED_PERIOD(FootballFoulsSyntaxAdapter.Status.UNSUPPORTED_PERIOD),
        UNSUPPORTED_PLAYER(FootballFoulsSyntaxAdapter.Status.UNSUPPORTED_PLAYER),
        UNSUPPORTED_HANDICAP_OR_COMPARISON(FootballFoulsSyntaxAdapter.Status.UNSUPPORTED_HANDICAP_OR_COMPARISON),
        UNSUPPORTED_COMPOSITE(FootballFoulsSyntaxAdapter.Status.UNSUPPORTED_COMPOSITE),
        UNSUPPORTED_SIGNED_NOTATION(FootballFoulsSyntaxAdapter.Status.UNSUPPORTED_SIGNED_NOTATION);
        final FootballFoulsSyntaxAdapter.Status value; Status(FootballFoulsSyntaxAdapter.Status value) { this.value=value; }
    }
}
