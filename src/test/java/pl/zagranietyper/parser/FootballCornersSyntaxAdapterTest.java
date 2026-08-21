package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.FootballFixtureStatisticCondition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FootballCornersSyntaxAdapterTest {
    private final FootballCornersSyntaxAdapter adapter = new FootballCornersSyntaxAdapter();

    @Test void parsesMatchTotalsInAuditedWording() {
        assertParsed("powyżej 8,5 rzutu rożnego", Category.MATCH_TOTAL, Subject.MATCH, Comparison.OVER);
        assertParsed("Poniżej 10.5 rzutów rożnych", Category.MATCH_TOTAL, Subject.MATCH, Comparison.UNDER);
        assertParsed("Mniej niż 9,5 rzutu rożnego w meczu", Category.MATCH_TOTAL, Subject.MATCH, Comparison.UNDER);
        assertParsed("Liczba rzutów rożnych — powyżej 9.5", Category.MATCH_TOTAL, Subject.MATCH, Comparison.OVER);
        assertParsed("powyżej 8.5 rz.rożnych", Category.MATCH_TOTAL, Subject.MATCH, Comparison.OVER);
        assertParsed("W meczu obejrzymy min. 12 rzutów rożnych", Category.MATCH_TOTAL, Subject.MATCH, Comparison.MINIMUM);
    }

    @Test void parsesTeamTotalsAndMinimumUsingSharedParticipantResolver() {
        assertParsed("Inter wykona powyżej 4,5 rzutu rożnego", "Inter", "Milan",
                Category.TEAM_TOTAL, Subject.HOME, Comparison.OVER);
        assertParsed("Legia poniżej 6,5 rzutów rożnych w meczu", "Lech Poznan", "Legia Warszawa",
                Category.TEAM_TOTAL, Subject.AWAY, Comparison.UNDER);
        assertParsed("powyżej 4,5 rzutu rożnego Napoli", "Inter", "Napoli",
                Category.TEAM_TOTAL, Subject.AWAY, Comparison.OVER);
        assertParsed("Powyżej 7.5 rożnego FC Barcelony", "FC Barcelona", "Real Madrid",
                Category.TEAM_TOTAL, Subject.HOME, Comparison.OVER);
        assertParsed("ŁKS Łódź wykona min. 6 rzutów rożnych", "LKS Lodz", "Wisla Krakow",
                Category.TEAM_MINIMUM, Subject.HOME, Comparison.MINIMUM);
    }

    @Test void inflectionAndSharedTokensRemainDeterministic() {
        assertParsed("Powyżej 5.5 rożnych Arsenalu", "Chelsea", "Arsenal",
                Category.TEAM_TOTAL, Subject.AWAY, Comparison.OVER);
        assertStatus("Stal powyżej 4.5 rzutów rożnych", "Stal Mielec", "Stal Rzeszow",
                Status.AMBIGUOUS_PARTICIPANT);
        assertStatus("Czechy wykonają powyżej 4,5 rzutu rożnego", "Montenegro", "Czechia",
                Status.AMBIGUOUS_PARTICIPANT);
        assertStatus("Belgia wykona powyżej 6,5 rzutu rożnego", "Belgium", "FYR Macedonia",
                Status.AMBIGUOUS_PARTICIPANT);
    }

    @Test void parsesAuditedSubjectlessSignedHalfLinesOnly() {
        assertSigned("+9.5 rzutów rożnych", Category.MATCH_TOTAL, Subject.MATCH,
                Comparison.OVER, SyntaxFamily.SIGNED_SUBJECTLESS_OVER);
        assertSigned("✅+7.5 rzutów rożnych", Category.MATCH_TOTAL, Subject.MATCH,
                Comparison.OVER, SyntaxFamily.SIGNED_SUBJECTLESS_OVER);
        assertSigned("⏳-8.5 rzutów rożnych", Category.MATCH_TOTAL, Subject.MATCH,
                Comparison.UNDER, SyntaxFamily.SIGNED_SUBJECTLESS_UNDER);
        assertSigned("+9,5 rzutów rożnych", Category.MATCH_TOTAL, Subject.MATCH,
                Comparison.OVER, SyntaxFamily.SIGNED_SUBJECTLESS_OVER);
        assertStatus("+9 rzutów rożnych", "Inter", "Milan", Status.UNSUPPORTED_HANDICAP);
        assertStatus("++9.5 rzutów rożnych", "Inter", "Milan", Status.UNSUPPORTED_HANDICAP);
    }

    @Test void parsesAuditedTeamSignedHalfLinesThroughExistingResolver() {
        assertSigned("West Brom +4.5 rzutów rożnych", "West Brom", "Leeds",
                Category.TEAM_TOTAL, Subject.HOME, Comparison.OVER, SyntaxFamily.SIGNED_TEAM_OVER);
        assertSigned("Tottenham +5.5 rzutów rożnych", "Arsenal", "Tottenham",
                Category.TEAM_TOTAL, Subject.AWAY, Comparison.OVER, SyntaxFamily.SIGNED_TEAM_OVER);
        assertSigned("Powyżej 5.5 rożnych Arsenalu", "Chelsea", "Arsenal",
                Category.TEAM_TOTAL, Subject.AWAY, Comparison.OVER, SyntaxFamily.UNSIGNED);
        assertStatus("Polska +4.5 rzutów rożnych", "Finland", "Poland",
                Status.AMBIGUOUS_PARTICIPANT);
        assertStatus("Portugalia +5.5 rzutów rożnych", "Scotland", "Portugal",
                Status.AMBIGUOUS_PARTICIPANT);
        assertStatus("Belgia +6.5 rzutów rożnych", "Belgium", "Ukraine",
                Status.AMBIGUOUS_PARTICIPANT);
    }

    @Test void rejectsPeriodsHandicapsAndComparisons() {
        assertStatus("Anglia +2.5 rzutów rożnych w 1. połowie", "Anglia", "Polska",
                Status.UNSUPPORTED_PERIOD);
        assertStatus("FC Barcelona handicap -0,5 rzutów rożnych", "Barcelona", "Real Madrid",
                Status.UNSUPPORTED_HANDICAP);
        assertStatus("Tottenham wykona więcej rzutów rożnych", "Tottenham", "Arsenal",
                Status.UNSUPPORTED_HANDICAP);
        assertStatus("Benfica więcej rzutów rożnych", "Benfica", "Porto",
                Status.UNSUPPORTED_HANDICAP);
    }

    @Test void neverPartiallyParsesCompositesOrOtherStatistics() {
        assertStatus("Superbets: powyżej 1,5 gola i powyżej 7,5 rzutu rożnego", "Inter", "Milan",
                Status.UNSUPPORTED_COMPOSITE);
        assertStatus("BTTS + powyżej 7.5 rzutów rożnych", "Inter", "Milan",
                Status.UNSUPPORTED_COMPOSITE);
        assertStatus("Polska powyżej 3.5 rzutów rożnych + powyżej 2.5 kartek w meczu", "Polska", "Niemcy",
                Status.UNSUPPORTED_COMPOSITE);
        assertStatus("1 oraz +5.5 rożncyh Barcelony", "Barcelona", "Espanyol",
                Status.UNSUPPORTED_COMPOSITE);
        assertEquals(FootballCornersSyntaxAdapter.Status.NOT_CORNERS_LIKE,
                adapter.parse("powyżej 8.5 celnych strzałów", "Inter", "Milan").status());
        assertEquals(FootballCornersSyntaxAdapter.Status.NOT_CORNERS_LIKE,
                adapter.parse("+8.5 celnych strzałów", "Inter", "Milan").status());
        assertEquals(FootballCornersSyntaxAdapter.Status.NOT_CORNERS_LIKE,
                adapter.parse("+4.5 kartek", "Inter", "Milan").status());
        assertEquals(FootballCornersSyntaxAdapter.Status.NOT_CORNERS_LIKE,
                adapter.parse("+2.5 gola", "Inter", "Milan").status());
    }

    private void assertParsed(String title, Category category, Subject subject, Comparison comparison) {
        assertParsed(title, "Inter", "Milan", category, subject, comparison);
    }
    private void assertParsed(String title, String home, String away,
                              Category category, Subject subject, Comparison comparison) {
        var result = adapter.parse(title, home, away);
        assertTrue(result.parsed(), () -> title + " => " + result.status());
        assertEquals(category.value, result.category());
        assertEquals(subject.value, result.condition().subject());
        assertEquals(comparison.value, result.condition().comparison());
    }
    private void assertStatus(String title, String home, String away, Status status) {
        assertEquals(status.value, adapter.parse(title, home, away).status());
    }
    private void assertSigned(String title, Category category, Subject subject,
                              Comparison comparison, SyntaxFamily family) {
        assertSigned(title, "Inter", "Milan", category, subject, comparison, family);
    }
    private void assertSigned(String title, String home, String away, Category category,
                              Subject subject, Comparison comparison, SyntaxFamily family) {
        var result = adapter.parse(title, home, away);
        assertTrue(result.parsed(), () -> title + " => " + result.status());
        assertEquals(category.value, result.category());
        assertEquals(subject.value, result.condition().subject());
        assertEquals(comparison.value, result.condition().comparison());
        assertEquals(family.value, result.syntaxFamily());
    }
    private enum Category {
        MATCH_TOTAL(FootballCornersSyntaxAdapter.Category.MATCH_TOTAL),
        TEAM_TOTAL(FootballCornersSyntaxAdapter.Category.TEAM_TOTAL),
        TEAM_MINIMUM(FootballCornersSyntaxAdapter.Category.TEAM_MINIMUM);
        private final FootballCornersSyntaxAdapter.Category value;
        Category(FootballCornersSyntaxAdapter.Category value) { this.value = value; }
    }
    private enum Subject {
        MATCH(FootballFixtureStatisticCondition.Subject.MATCH),
        HOME(FootballFixtureStatisticCondition.Subject.HOME),
        AWAY(FootballFixtureStatisticCondition.Subject.AWAY);
        private final FootballFixtureStatisticCondition.Subject value;
        Subject(FootballFixtureStatisticCondition.Subject value) { this.value = value; }
    }
    private enum Comparison {
        OVER(FootballFixtureStatisticCondition.Comparison.OVER),
        UNDER(FootballFixtureStatisticCondition.Comparison.UNDER),
        MINIMUM(FootballFixtureStatisticCondition.Comparison.MINIMUM);
        private final FootballFixtureStatisticCondition.Comparison value;
        Comparison(FootballFixtureStatisticCondition.Comparison value) { this.value = value; }
    }
    private enum Status {
        AMBIGUOUS_PARTICIPANT(FootballCornersSyntaxAdapter.Status.AMBIGUOUS_PARTICIPANT),
        UNSUPPORTED_PERIOD(FootballCornersSyntaxAdapter.Status.UNSUPPORTED_PERIOD),
        UNSUPPORTED_HANDICAP(FootballCornersSyntaxAdapter.Status.UNSUPPORTED_HANDICAP),
        UNSUPPORTED_COMPOSITE(FootballCornersSyntaxAdapter.Status.UNSUPPORTED_COMPOSITE);
        private final FootballCornersSyntaxAdapter.Status value;
        Status(FootballCornersSyntaxAdapter.Status value) { this.value = value; }
    }
    private enum SyntaxFamily {
        UNSIGNED(FootballCornersSyntaxAdapter.SyntaxFamily.UNSIGNED),
        SIGNED_SUBJECTLESS_OVER(FootballCornersSyntaxAdapter.SyntaxFamily.SIGNED_SUBJECTLESS_OVER),
        SIGNED_SUBJECTLESS_UNDER(FootballCornersSyntaxAdapter.SyntaxFamily.SIGNED_SUBJECTLESS_UNDER),
        SIGNED_TEAM_OVER(FootballCornersSyntaxAdapter.SyntaxFamily.SIGNED_TEAM_OVER);
        private final FootballCornersSyntaxAdapter.SyntaxFamily value;
        SyntaxFamily(FootballCornersSyntaxAdapter.SyntaxFamily value) { this.value = value; }
    }
}
