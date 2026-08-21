package pl.zagranietyper.service;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.SettlementDecision;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FootballCompositeSettlementEngineTest {
    @Test void appliesAndSemanticsForTwoAndThreeBranches() {
        assertEquals(SettlementDecision.W, FootballCompositeSettlementEngine.combine(List.of(SettlementDecision.W,SettlementDecision.W)));
        assertEquals(SettlementDecision.L, FootballCompositeSettlementEngine.combine(List.of(SettlementDecision.W,SettlementDecision.L)));
        assertEquals(SettlementDecision.W, FootballCompositeSettlementEngine.combine(List.of(SettlementDecision.W,SettlementDecision.V)));
        assertEquals(SettlementDecision.V, FootballCompositeSettlementEngine.combine(List.of(SettlementDecision.V,SettlementDecision.V)));
        assertEquals(SettlementDecision.L, FootballCompositeSettlementEngine.combine(List.of(SettlementDecision.W,SettlementDecision.UNSUPPORTED,SettlementDecision.L)));
        assertEquals(SettlementDecision.W, FootballCompositeSettlementEngine.combine(List.of(SettlementDecision.W,SettlementDecision.V,SettlementDecision.W)));
    }

    @Test void supportedPlusUnsupportedIsUnsupported() {
        assertEquals(SettlementDecision.UNSUPPORTED,FootballCompositeSettlementEngine.combine(List.of(SettlementDecision.W,SettlementDecision.UNSUPPORTED)));
    }
}
