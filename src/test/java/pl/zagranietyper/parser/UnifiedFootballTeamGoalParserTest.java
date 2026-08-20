package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.FootballScore;
import pl.zagranietyper.model.FootballScoreSnapshot;
import pl.zagranietyper.model.SettlementDecision;
import pl.zagranietyper.model.UnifiedFootballMarket;
import pl.zagranietyper.service.FootballTeamMarketSettlementEngine;
import pl.zagranietyper.service.UnifiedFootballSettlementEngine;

import static org.junit.jupiter.api.Assertions.*;

final class UnifiedFootballTeamGoalParserTest {
    private final UnifiedFootballTeamGoalParser parser = new UnifiedFootballTeamGoalParser();
    private final UnifiedFootballSettlementEngine engine = new UnifiedFootballSettlementEngine();

    @Test void teamTotalBoundariesAndOutcomes() {
        assertDecision("Bayern strzeli więcej niż 1.5 goli", "Bayern", "Mainz", 2, 0, SettlementDecision.W);
        assertDecision("Bayern strzeli więcej niż 1.5 goli", "Bayern", "Mainz", 1, 0, SettlementDecision.L);
        assertDecision("Betis poniżej 1.5 gola", "Betis", "Sevilla", 1, 0, SettlementDecision.W);
        assertDecision("Betis poniżej 1.5 gola", "Betis", "Sevilla", 2, 0, SettlementDecision.L);
        assertDecision("Arsenal powyżej 2.0 gola", "Arsenal", "Chelsea", 2, 0, SettlementDecision.V);
    }

    @Test void teamMinimumSupportsBothSubjectPositionsAndInclusiveThreshold() {
        assertDecision("Tottenham strzeli co najmniej dwa gole", "Tottenham", "Arsenal", 2, 0, SettlementDecision.W);
        assertDecision("Brazylia strzeli co najmniej 2 gole", "Brazylia", "Argentina", 3, 0, SettlementDecision.W);
        assertDecision("Co najmniej dwa gole Lazio", "Roma", "Lazio", 0, 1, SettlementDecision.L);
        assertDecision("Co najmniej jeden gol Bolonii", "Bologna", "Torino", 1, 0, SettlementDecision.W);
        assertDecision("Co najmniej jeden gol Podbeskidzia", "Wisla", "Podbeskidzie", 0, 0, SettlementDecision.L);
    }

    @Test void teamRangeIsInclusiveOnEveryBoundary() {
        String tip = "Athletic Club przedział goli 1-3";
        assertDecision(tip, "Athletic Club", "Barcelona", 1, 0, SettlementDecision.W);
        assertDecision(tip, "Athletic Club", "Barcelona", 2, 0, SettlementDecision.W);
        assertDecision(tip, "Athletic Club", "Barcelona", 3, 0, SettlementDecision.W);
        assertDecision(tip, "Athletic Club", "Barcelona", 0, 0, SettlementDecision.L);
        assertDecision(tip, "Athletic Club", "Barcelona", 4, 0, SettlementDecision.L);
    }

    @Test void parsesRangeVariantsAndRejectsReversedRange() {
        assertParsed("Przedział goli Slavii: 1-2", "Slavia Praha", "Sparta Praha",
                UnifiedFootballTeamGoalParser.Category.TEAM_RANGE, UnifiedFootballMarket.GoalSubject.HOME);
        assertParsed("przedział goli Liverpoolu: 1-2", "Chelsea", "Liverpool",
                UnifiedFootballTeamGoalParser.Category.TEAM_RANGE, UnifiedFootballMarket.GoalSubject.AWAY);
        assertParsed("Ajax suma goli: 1-2", "Ajax", "PSV",
                UnifiedFootballTeamGoalParser.Category.TEAM_RANGE, UnifiedFootballMarket.GoalSubject.HOME);
        assertStatus("przedział goli Arsenalu: 3-2", "Arsenal", "Chelsea",
                UnifiedFootballTeamGoalParser.Status.MALFORMED_RANGE);
    }

    @Test void teamToScoreUsesUnifiedEngine() {
        assertDecision("Middlesbrough strzeli gola", "Leeds", "Middlesbrough", 0, 1, SettlementDecision.W);
        assertDecision("Middlesbrough strzeli gola", "Leeds", "Middlesbrough", 1, 0, SettlementDecision.L);
    }

    @Test void participantSafetyAndHistoricalInflection() {
        assertParsed("ŁKS strzeli więcej niż 1.5 goli", "LKS Lodz", "Wisla",
                UnifiedFootballTeamGoalParser.Category.TEAM_TOTAL, UnifiedFootballMarket.GoalSubject.HOME);
        assertStatus("Stal powyżej 0.5 gola", "Stal Stalowa Wola", "Stal Rzeszów",
                UnifiedFootballTeamGoalParser.Status.AMBIGUOUS_PARTICIPANT);
        assertStatus("Liverpool powyżej 1.5 gola", "Arsenal", "Chelsea",
                UnifiedFootballTeamGoalParser.Status.UNRESOLVED_PARTICIPANT);
        assertStatus("Dania strzeli co najmniej 1 gola", "Denmark", "Sweden",
                UnifiedFootballTeamGoalParser.Status.UNRESOLVED_PARTICIPANT);
    }

    @Test void exclusionsRemainUnsupported() {
        assertStatus("Panathinaikos -1.5 goli", "Ajax", "Panathinaikos",
                UnifiedFootballTeamGoalParser.Status.UNSUPPORTED_SIGNED_FORMAT);
        assertStatus("Team +1.5 goli", "Team", "Other",
                UnifiedFootballTeamGoalParser.Status.UNSUPPORTED_SIGNED_FORMAT);
        assertStatus("Al Shabab + ponad 1.5 gola", "Al Shabab", "Al Nassr",
                UnifiedFootballTeamGoalParser.Status.UNSUPPORTED_SIGNED_FORMAT);
        assertStatus("Roma wygra i Lazio strzeli co najmniej dwa gole", "Roma", "Lazio",
                UnifiedFootballTeamGoalParser.Status.UNSUPPORTED_COMPOSITE);
        assertFalse(parser.parse("Powyżej 2.5 goli", "Arsenal", "Chelsea").parsed());
        assertFalse(parser.parse("E. Haaland strzeli gola", "Manchester City", "Chelsea").parsed());
    }

    @Test void legacyTeamMarketsHaveExactUnifiedDecisionParity() {
        var result = parser.parse("Barcelona strzeli więcej niż 1.5 goli", "Valencia", "Barcelona");
        assertTrue(result.parsed());
        assertNotNull(result.legacyMarket());
        FootballScore score = new FootballScore(1, 2);
        assertEquals(new FootballTeamMarketSettlementEngine().settle(result.legacyMarket(), score),
                engine.settle(result.market(), FootballScoreSnapshot.fullTime(score)));
    }

    private void assertDecision(String tip, String home, String away, int hg, int ag, SettlementDecision expected) {
        var result = parser.parse(tip, home, away);
        assertTrue(result.parsed(), () -> tip + " => " + result.status());
        assertEquals(expected, engine.settle(result.market(),
                FootballScoreSnapshot.fullTime(new FootballScore(hg, ag))));
    }
    private void assertParsed(String tip, String home, String away,
                              UnifiedFootballTeamGoalParser.Category category,
                              UnifiedFootballMarket.GoalSubject subject) {
        var result = parser.parse(tip, home, away);
        assertTrue(result.parsed(), () -> tip + " => " + result.status());
        assertEquals(category, result.category());
        assertEquals(subject, result.subject());
    }
    private void assertStatus(String tip, String home, String away, UnifiedFootballTeamGoalParser.Status status) {
        assertEquals(status, parser.parse(tip, home, away).status());
    }
}
