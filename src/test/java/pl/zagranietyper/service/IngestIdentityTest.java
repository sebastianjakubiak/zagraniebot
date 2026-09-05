package pl.zagranietyper.service;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.BetType;
import pl.zagranietyper.model.EventMetadata;
import pl.zagranietyper.model.OddsConsistency;
import pl.zagranietyper.model.OddsSource;
import pl.zagranietyper.model.ParsedBet;
import pl.zagranietyper.model.ParsedLeg;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class IngestIdentityTest {

    @Test
    void legIdentityIgnoresOrdinalAndOdds() {
        ParsedLeg first =
                leg(
                        1,
                        "Arsenal wygra",
                        "1.90"
                );

        ParsedLeg shiftedWithNewOdds =
                leg(
                        7,
                        "Arsenal wygra",
                        "2.10"
                );

        assertEquals(
                IngestIdentity.legKey(
                        first
                ),
                IngestIdentity.legKey(
                        shiftedWithNewOdds
                )
        );
    }

    @Test
    void changedPickTextCreatesDifferentIdentity() {
        assertNotEquals(
                IngestIdentity.legKey(
                        leg(
                                1,
                                "Arsenal wygra",
                                "1.90"
                        )
                ),
                IngestIdentity.legKey(
                        leg(
                                1,
                                "Arsenal wygra i over 1.5",
                                "1.90"
                        )
                )
        );
    }

    @Test
    void combinedIdentityIsStableWhenLegOrderChanges() {
        ParsedLeg arsenal =
                leg(
                        1,
                        "Arsenal wygra",
                        "1.90"
                );

        ParsedLeg chelsea =
                leg(
                        2,
                        "Chelsea +1.5",
                        "1.70"
                );

        assertEquals(
                IngestIdentity.betKey(
                        combined(
                                1,
                                List.of(
                                        arsenal,
                                        chelsea
                                )
                        )
                ),
                IngestIdentity.betKey(
                        combined(
                                9,
                                List.of(
                                        chelsea,
                                        arsenal
                                )
                        )
                )
        );
    }

    private static ParsedLeg leg(
            int ordinal,
            String title,
            String odds
    ) {
        EventMetadata event =
                new EventMetadata(
                        "event-123",
                        "Arsenal",
                        "Chelsea",
                        "Premier League",
                        Instant.parse(
                                "2026-09-05T18:00:00Z"
                        ),
                        null,
                        Map.of()
                );

        return new ParsedLeg(
                ordinal,
                "legacy-" + ordinal,
                "betclic",
                title,
                new BigDecimal(
                        odds
                ),
                event,
                Map.of()
        );
    }

    private static ParsedBet combined(
            int ordinal,
            List<ParsedLeg> legs
    ) {
        return new ParsedBet(
                ordinal,
                "legacy-bet-" + ordinal,
                BetType.COMBINED,
                new BigDecimal(
                        "3.23"
                ),
                new BigDecimal(
                        "3.2300"
                ),
                OddsSource.TITLE,
                true,
                OddsConsistency.MATCH,
                legs
        );
    }
}
