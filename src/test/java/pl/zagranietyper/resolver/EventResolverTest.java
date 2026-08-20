package pl.zagranietyper.resolver;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.BetType;
import pl.zagranietyper.model.EventResolutionCandidate;
import pl.zagranietyper.model.ResolutionConfidence;
import pl.zagranietyper.model.ResolutionSource;
import pl.zagranietyper.model.ResolvedEvent;
import pl.zagranietyper.model.ResolvedSport;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventResolverTest {

    private final EventResolver resolver =
            new EventResolver();

    @Test
    void sourceMetadataWins() {

        ResolvedEvent result =
                resolver.resolve(
                        candidate(
                                BetType.SINGLE,
                                1,
                                "Rangers – Jagiellonia",
                                "BTTS: tak",
                                null,
                                null,
                                "Rangers",
                                "Jagiellonia Bialystok",
                                "UEFA Europa League",
                                Instant.parse(
                                        "2026-08-13T18:30:00Z"
                                )
                        )
                );

        assertTrue(
                result.resolved()
        );

        assertEquals(
                ResolutionSource.SOURCE_METADATA,
                result.source()
        );

        assertEquals(
                "Rangers",
                result.participantA()
        );

        assertEquals(
                "Jagiellonia Bialystok",
                result.participantB()
        );

        assertEquals(
                ResolvedSport.FOOTBALL,
                result.sport()
        );
    }

    @Test
    void resolvesEsportFromHeading() {

        ResolvedEvent result =
                resolver.resolve(
                        candidate(
                                BetType.COMBINED,
                                2,
                                "Spirit poradzi sobie z rywalem? Gramy esport z kursem 2.06!",
                                "Spirit wygra mecz z handicapem (-1.5)",
                                "Luminosity – Spirit",
                                "Wbijaj w kod promocyjny...",
                                null,
                                null,
                                null,
                                null
                        )
                );

        assertEquals(
                ResolutionSource.CONTEXT_HEADING,
                result.source()
        );

        assertEquals(
                "Luminosity",
                result.participantA()
        );

        assertEquals(
                "Spirit",
                result.participantB()
        );

        assertEquals(
                ResolvedSport.ESPORT,
                result.sport()
        );
    }

    @Test
    void resolvesSingleFromStructuredPostTitle() {

        ResolvedEvent result =
                resolver.resolve(
                        candidate(
                                BetType.SINGLE,
                                1,
                                "Górnik Zabrze – Ferencvaros: Typy i kursy (13.08.2026)",
                                "Gole z obu stron",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                        )
                );

        assertEquals(
                ResolutionSource.POST_TITLE,
                result.source()
        );

        assertEquals(
                "Górnik Zabrze",
                result.participantA()
        );

        assertEquals(
                "Ferencvaros",
                result.participantB()
        );

        assertEquals(
                LocalDate.of(
                        2026,
                        8,
                        13
                ),
                result.eventDate()
        );
    }

    @Test
    void doesNotUsePostTitleForCombinedBet() {

        /*
         * Krytyczna regresja.
         *
         * Jeden artykuł AKO może zawierać legi z kilku
         * różnych eventów.
         */
        ResolvedEvent result =
                resolver.resolve(
                        candidate(
                                BetType.COMBINED,
                                2,
                                "Rangers – Jagiellonia: LE AKO 2.61!",
                                "Lech Poznań poniżej 2,5 goli",
                                "Co obstawiać?",
                                "Lech jedzie na Wyspy Owcze w roli faworyta.",
                                null,
                                null,
                                null,
                                null
                        )
                );

        assertFalse(
                result.resolved()
        );

        assertEquals(
                ResolutionSource.NONE,
                result.source()
        );
    }

    @Test
    void resolvesNarrativeDashMatchup() {

        ResolvedEvent result =
                resolver.resolve(
                        candidate(
                                BetType.COMBINED,
                                2,
                                "GieKSa jest w stanie odrobić straty?",
                                "Gol w obu połowach",
                                "Co obstawiać?",
                                "Pojedynek GKS Katowice – Hapoel Tel Aviv czeka nas 12 sierpnia 2026 roku.",
                                null,
                                null,
                                "UEFA Conference League",
                                null
                        )
                );

        assertEquals(
                ResolutionSource.PREVIOUS_TEXT,
                result.source()
        );

        assertEquals(
                "GKS Katowice",
                result.participantA()
        );

        assertEquals(
                "Hapoel Tel Aviv",
                result.participantB()
        );

        assertEquals(
                LocalDate.of(
                        2026,
                        8,
                        12
                ),
                result.eventDate()
        );
    }

    @Test
    void resolvesNarrativeWithMatchup() {

        ResolvedEvent result =
                resolver.resolve(
                        candidate(
                                BetType.SINGLE,
                                1,
                                "Sparta Wrocław – Motor Lublin",
                                "Wygra Sparta Wrocław",
                                "Co obstawiać?",
                                "Pojedynek Sparty Wrocław z Motorem Lublin zaplanowano na 9 sierpnia 2026 roku.",
                                null,
                                null,
                                null,
                                null
                        )
                );

        /*
         * Structured single title ma wyższy priorytet.
         */
        assertEquals(
                ResolutionSource.POST_TITLE,
                result.source()
        );

        assertEquals(
                "Sparta Wrocław",
                result.participantA()
        );

        assertEquals(
                "Motor Lublin",
                result.participantB()
        );
    }

    @Test
    void resolvesHostPatternFromPreviousText() {

        ResolvedEvent result =
                resolver.resolve(
                        candidate(
                                BetType.COMBINED,
                                2,
                                "Żużel na dziś",
                                "Zwycięży PSŻ Poznań",
                                "Co obstawiać?",
                                "Miejscowy PSŻ podejmie Wilki Krosno.",
                                null,
                                null,
                                null,
                                null
                        )
                );

        assertEquals(
                ResolutionSource.PREVIOUS_TEXT,
                result.source()
        );

        assertEquals(
                "PSŻ",
                result.participantA()
        );

        assertEquals(
                "Wilki Krosno",
                result.participantB()
        );
    }

    @Test
    void footballAkoAcronymIsRecognized() {

        ResolvedEvent result =
                resolver.resolve(
                        candidate(
                                BetType.COMBINED,
                                2,
                                "Jagiellonia wytrzyma napór Rangers na Ibrox? LE AKO 2.61!",
                                "Rangers więcej niż 3,5 kartek",
                                "Co obstawiać?",
                                "Ten rewanż zapowiada się na spotkanie pełne emocji.",
                                null,
                                null,
                                null,
                                null
                        )
                );

        assertEquals(
                ResolvedSport.FOOTBALL,
                result.sport()
        );

        assertFalse(
                result.resolved()
        );
    }

    @Test
    void doesNotInventEventDateFromPublishedAt() {

        ResolvedEvent result =
                resolver.resolve(
                        candidate(
                                BetType.COMBINED,
                                2,
                                "Spirit wygra?",
                                "Spirit handicap",
                                "Luminosity – Spirit",
                                null,
                                null,
                                null,
                                null,
                                null
                        )
                );

        assertTrue(
                result.resolved()
        );

        assertNull(
                result.eventDate()
        );

        assertEquals(
                ResolutionConfidence.HIGH,
                result.confidence()
        );
    }

    private static EventResolutionCandidate candidate(
            BetType betType,
            int betLegCount,
            String postTitle,
            String tipTitle,
            String heading,
            String previousText,
            String sourceHome,
            String sourceAway,
            String sourceCompetition,
            Instant sourceStartAt
    ) {
        return new EventResolutionCandidate(
                1L,
                10L,
                betType,
                betLegCount,

                123L,

                Instant.parse(
                        "2026-08-12T08:00:00Z"
                ),

                postTitle,
                tipTitle,

                heading,
                previousText,

                null,
                sourceHome,
                sourceAway,
                sourceCompetition,
                sourceStartAt
        );
    }
}