package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import pl.zagranietyper.model.FootballMarket;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FootballMinimumTotalParserTest {

    private final FootballMarketParser parser =
            new FootballMarketParser();

    @ParameterizedTest
    @CsvSource({
            "'Co najmniej dwa gole', 2",
            "'Co najmniej 2 gole', 2",
            "'Co najmniej 3 gole', 3",
            "'Co najmniej 3 bramki', 3",
            "'W meczu padnie minimum 3 bramki', 3",
            "'Co najmniej dwa trafienia', 2",
            "'Minimum jedna bramka', 1",
            "'Co najmniej dziesięć goli', 10"
    })
    void parsesUnambiguousWholeGoalMinimum(
            String title,
            int expectedMinimum
    ) {
        FootballMarket market =
                parser.parse(
                                title,
                                "Roma",
                                "Lazio"
                        )
                        .orElseThrow();

        assertEquals(
                1,
                market.conditions()
                        .size()
        );

        FootballMarket.MinimumTotalGoals condition =
                assertInstanceOf(
                        FootballMarket.MinimumTotalGoals.class,
                        market.conditions()
                                .getFirst()
                );

        assertEquals(
                expectedMinimum,
                condition.minimum()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Co najmniej 2,5 gola",
            "Co najmniej 2.5 gola",
            "W meczu padnie minimum 3,5 bramki"
    })
    void leavesDecimalMinimumUnsupported(
            String title
    ) {
        assertUnsupported(
                title
        );
    }

    @Test
    void doesNotTreatTrailingTeamAsMatchTotal() {
        assertUnsupported(
                "Co najmniej dwa gole Lazio"
        );
    }

    @ParameterizedTest
    @CsvSource({
            "'Co najmniej jeden gol Bolonii', Napoli, Bologna",
            "'Co najmniej jeden gol Podbeskidzia', 'Olimpia Grudziądz', Podbeskidzie",
            "'Tottenham strzeli co najmniej dwa gole', Ipswich, Tottenham",
            "'Brazylia strzeli co najmniej 2 gole', Scotland, Brazil",
            "'Co najmniej jeden gol HOME', HOME, AWAY",
            "'Co najmniej jeden gol AWAY', HOME, AWAY",
            "'HOME strzeli co najmniej dwa gole', HOME, AWAY",
            "'AWAY strzeli co najmniej dwa gole', HOME, AWAY"
    })
    void doesNotTreatTeamSpecificMinimumAsMatchTotal(
            String title,
            String homeTeam,
            String awayTeam
    ) {
        assertTrue(
                parser.parse(
                                title,
                                homeTeam,
                                awayTeam
                        )
                        .isEmpty()
        );
    }

    @Test
    void decimalMinimumCannotBeSilentlyDroppedFromComposite() {
        assertUnsupported(
                "Roma wygra i co najmniej 2,5 gola"
        );
    }

    @Test
    void minimumTeamWordingCannotBeSilentlyDroppedFromComposite() {
        assertUnsupported(
                "Roma wygra i co najmniej dwa gole Lazio"
        );
    }

    private void assertUnsupported(
            String title
    ) {
        Optional<FootballMarket> market =
                parser.parse(
                        title,
                        "Roma",
                        "Lazio"
                );

        assertTrue(
                market.isEmpty()
        );
    }
}
