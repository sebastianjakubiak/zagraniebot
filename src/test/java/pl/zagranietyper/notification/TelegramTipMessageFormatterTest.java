package pl.zagranietyper.notification;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.BetType;
import pl.zagranietyper.model.EventMetadata;
import pl.zagranietyper.model.OddsConsistency;
import pl.zagranietyper.model.OddsSource;
import pl.zagranietyper.model.ParsedBet;
import pl.zagranietyper.model.ParsedLeg;
import pl.zagranietyper.service.NewTipsPollingService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramTipMessageFormatterTest {

    @Test
    void formatsSingleWithExactLegOddsAndOperator() {
        ParsedLeg leg =
                new ParsedLeg(
                        1,
                        "leg-fingerprint",
                        "sts",
                        "Obie drużyny strzelą gole",
                        new BigDecimal(
                                "1.60"
                        ),
                        EventMetadata.empty(),
                        Map.of()
                );

        ParsedBet bet =
                new ParsedBet(
                        1,
                        "bet-fingerprint",
                        BetType.SINGLE,
                        new BigDecimal(
                                "1.60"
                        ),
                        new BigDecimal(
                                "1.60"
                        ),
                        OddsSource.SINGLE_LEG,
                        true,
                        OddsConsistency.MATCH,
                        List.of(
                                leg
                        )
                );

        NewTipsPollingService.DetectedBet detectedBet =
                new NewTipsPollingService.DetectedBet(
                        685829L,
                        "https://zagranie.com/test/",
                        "Korona – Wisła Kraków: Typy i kursy",
                        bet,
                        false
                );

        String message =
                TelegramTipMessageFormatter.format(
                        "Mateusz Domański",
                        detectedBet
                );

        assertTrue(
                message.contains(
                        "🔥 NOWY TYP — Mateusz Domański"
                )
        );

        assertTrue(
                message.contains(
                        "SINGLE @1.6"
                )
        );

        assertTrue(
                message.contains(
                        "• Obie drużyny strzelą gole @1.6 — sts"
                )
        );

        assertTrue(
                message.contains(
                        "https://zagranie.com/test/"
                )
        );
    }
}
