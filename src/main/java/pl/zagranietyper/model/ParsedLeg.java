package pl.zagranietyper.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ParsedLeg(
        int ordinal,
        String sourceFingerprint,
        String operator,
        String tipTitle,
        BigDecimal tipOdds,
        EventMetadata event,
        Map<String, String> sourceAttributes
) {
    public ParsedLeg {
        sourceAttributes = sourceAttributes == null
                ? Map.of()
                : Collections.unmodifiableMap(
                new LinkedHashMap<>(
                        sourceAttributes
                )
        );
    }
}