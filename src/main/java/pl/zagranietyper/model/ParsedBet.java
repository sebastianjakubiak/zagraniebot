package pl.zagranietyper.model;

import java.math.BigDecimal;
import java.util.List;

public record ParsedBet(
        int ordinal,
        String sourceFingerprint,
        BetType type,
        BigDecimal displayedOdds,
        BigDecimal calculatedOdds,
        OddsSource oddsSource,
        boolean oddsVerified,
        OddsConsistency oddsConsistency,
        List<ParsedLeg> legs
) {
    public ParsedBet {
        legs = legs == null
                ? List.of()
                : List.copyOf(legs);
    }
}