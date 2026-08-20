package pl.zagranietyper.fixture;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.ApiFootballFixture;
import pl.zagranietyper.model.ApiFootballMatch;
import pl.zagranietyper.model.ApiFootballResolutionCandidate;
import pl.zagranietyper.model.BetType;
import pl.zagranietyper.model.ResolvedSport;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiFootballMatcherTest {

    private final ApiFootballMatcher matcher =
            new ApiFootballMatcher();

    @Test
    void resolvesBothDistinctTeams() {

        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.COMBINED,
                        2,
                        ResolvedSport.FOOTBALL,
                        "AKO",
                        "BTTS",
                        null,
                        "Valencia – Barcelona"
                );

        ApiFootballFixture fixture =
                fixture(
                        100L,
                        "Valencia",
                        "Barcelona"
                );

        ApiFootballMatch result =
                matcher.match(
                        candidate,
                        List.of(
                                fixture
                        )
                );

        assertNotNull(
                result
        );

        assertEquals(
                100L,
                result.fixture()
                        .fixtureId()
        );
    }

    @Test
    void commonTeamTokenIsNotEnoughForBothTeams() {

        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.COMBINED,
                        2,
                        ResolvedSport.FOOTBALL,
                        "Typy na dziś",
                        "Risto Radunovic otrzyma kartkę",
                        null,
                        "Będzie gorąco, bo są to derby Dinamo."
                );

        ApiFootballFixture fixture =
                fixture(
                        200L,
                        "Dinamo Bryansk",
                        "Dinamo Vladivostok"
                );

        assertNull(
                matcher.match(
                        candidate,
                        List.of(
                                fixture
                        )
                )
        );
    }

    @Test
    void distinctDinamoTeamsCanStillMatchWhenCitiesPresent() {

        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.COMBINED,
                        2,
                        ResolvedSport.FOOTBALL,
                        "AKO",
                        "BTTS",
                        null,
                        "Dinamo Bryansk – Dinamo Vladivostok"
                );

        ApiFootballFixture fixture =
                fixture(
                        300L,
                        "Dinamo Bryansk",
                        "Dinamo Vladivostok"
                );

        assertNotNull(
                matcher.match(
                        candidate,
                        List.of(
                                fixture
                        )
                )
        );
    }

    @Test
    void unknownPointHandicapIsNotFootball() {

        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.COMBINED,
                        2,
                        ResolvedSport.UNKNOWN,
                        "Typy reprezentacyjne",
                        "Handicap punktowy: USA(-6.5) - Polska - TAK",
                        null,
                        "Amerykanki są faworytkami."
                );

        ApiFootballFixture fixture =
                fixture(
                        400L,
                        "USA",
                        "Belgium"
                );

        assertNull(
                matcher.match(
                        candidate,
                        List.of(
                                fixture
                        )
                )
        );
    }

    @Test
    void unknownThailandUsaPointHandicapIsNotFootball() {

        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.COMBINED,
                        2,
                        ResolvedSport.UNKNOWN,
                        "Typy na dziś",
                        "Handicap punktowy: Tajlandia(+16.5) - USA - TAK",
                        null,
                        null
                );

        ApiFootballFixture fixture =
                fixture(
                        500L,
                        "USA",
                        "Belgium"
                );

        assertNull(
                matcher.match(
                        candidate,
                        List.of(
                                fixture
                        )
                )
        );
    }

    @Test
    void volleyballUnknownIsRejected() {

        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.COMBINED,
                        2,
                        ResolvedSport.UNKNOWN,
                        "Polscy siatkarze podejmą Japonię",
                        "USA wygra",
                        null,
                        null
                );

        ApiFootballFixture fixture =
                fixture(
                        600L,
                        "USA",
                        "Paraguay"
                );

        assertNull(
                matcher.match(
                        candidate,
                        List.of(
                                fixture
                        )
                )
        );
    }

    @Test
    void womenFixtureIsRejectedWithoutWomenEvidence() {

        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.COMBINED,
                        2,
                        ResolvedSport.FOOTBALL,
                        "Serie A",
                        "Roma wygra",
                        null,
                        "Roma walczy o Ligę Mistrzów."
                );

        ApiFootballFixture fixture =
                fixture(
                        700L,
                        "Juventus W",
                        "Roma W"
                );

        assertNull(
                matcher.match(
                        candidate,
                        List.of(
                                fixture
                        )
                )
        );
    }

    @Test
    void reserveFixtureRequiresExplicitReserveName() {

        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.COMBINED,
                        2,
                        ResolvedSport.FOOTBALL,
                        "Typy na dziś",
                        "Śląsk wygra",
                        null,
                        null
                );

        ApiFootballFixture fixture =
                fixture(
                        800L,
                        "Podbeskidzie",
                        "Śląsk Wrocław II"
                );

        assertNull(
                matcher.match(
                        candidate,
                        List.of(
                                fixture
                        )
                )
        );
    }

    @Test
    void reserveFixtureCanMatchWhenExplicitlyNamed() {

        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.COMBINED,
                        2,
                        ResolvedSport.FOOTBALL,
                        "Typy barażowe",
                        "Podbeskidzie - Śląsk Wrocław II",
                        null,
                        "Podbeskidzie – Śląsk Wrocław II"
                );

        ApiFootballFixture fixture =
                fixture(
                        900L,
                        "Podbeskidzie",
                        "Śląsk Wrocław II"
                );

        assertNotNull(
                matcher.match(
                        candidate,
                        List.of(
                                fixture
                        )
                )
        );
    }

    @Test
    void barcelonaDoesNotMeanBarcelonaSc() {

        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.COMBINED,
                        2,
                        ResolvedSport.FOOTBALL,
                        "AKO",
                        "Barcelona strzeli gola",
                        null,
                        null
                );

        ApiFootballFixture fixture =
                fixture(
                        1000L,
                        "Emelec",
                        "Barcelona SC"
                );

        assertNull(
                matcher.match(
                        candidate,
                        List.of(
                                fixture
                        )
                )
        );
    }

    @Test
    void uniqueSingleTeamIsRejected() {

        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.COMBINED,
                        2,
                        ResolvedSport.FOOTBALL,
                        "AKO",
                        "Polonia Bytom powyżej 4.5 rzutu rożnego",
                        null,
                        null
                );

        ApiFootballFixture fixture =
                fixture(
                        1100L,
                        "Polonia Bytom",
                        "Warta Poznań"
                );

        assertNull(
                matcher.match(
                        candidate,
                        List.of(
                                fixture
                        )
                )
        );
    }

    @Test
    void strongTipTeamAnchorRejectsHistoricalTeamsFromPreviousText() {

        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.COMBINED,
                        2,
                        ResolvedSport.FOOTBALL,
                        "Barcelona zrobi kolejny krok po mistrzostwo?",
                        "Las Palmas strzeli gola",
                        "Co obstawiać?",
                        """
                        Postawię na gola Las Palmas.
                        Ekipa z Gran Canaria prezentuje się dobrze ofensywnie.
                        Atletico dalekie jest od defensywnej perfekcji.
                        Tracili gola przeciwko takim drużynom jak
                        Espanyol, Getafe czy Valladolid.
                        """
                );

        ApiFootballFixture correct =
                fixture(
                        1200L,
                        "Las Palmas",
                        "Atletico Madrid",
                        LocalDate.of(
                                2026,
                                5,
                                24
                        )
                );

        ApiFootballFixture historical =
                fixture(
                        1201L,
                        "Espanyol",
                        "Getafe",
                        LocalDate.of(
                                2026,
                                5,
                                24
                        )
                );

        ApiFootballMatch result =
                matcher.match(
                        candidate,
                        List.of(
                                historical,
                                correct
                        )
                );

        assertNotNull(
                result
        );

        assertEquals(
                1200L,
                result.fixture()
                        .fixtureId()
        );
    }

    @Test
    void dateBonusBreaksTieBetweenSameMatchupCandidates() {

        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.COMBINED,
                        2,
                        ResolvedSport.FOOTBALL,
                        "AKO",
                        "BTTS",
                        null,
                        "Valencia – Barcelona"
                );

        ApiFootballFixture preferred =
                fixture(
                        1300L,
                        "Valencia",
                        "Barcelona",
                        LocalDate.of(
                                2026,
                                5,
                                24
                        )
                );

        ApiFootballFixture previousDay =
                fixture(
                        1301L,
                        "Valencia",
                        "Barcelona",
                        LocalDate.of(
                                2026,
                                5,
                                22
                        )
                );

        ApiFootballMatch result =
                matcher.match(
                        candidate,
                        List.of(
                                previousDay,
                                preferred
                        )
                );

        assertNotNull(
                result
        );

        assertEquals(
                1300L,
                result.fixture()
                        .fixtureId()
        );
    }

    @Test
    void equallyStrongBothTeamsCandidatesAreRejected() {

        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.COMBINED,
                        2,
                        ResolvedSport.FOOTBALL,
                        "AKO",
                        "BTTS",
                        null,
                        """
                        Valencia – Barcelona.
                        Arsenal – Chelsea.
                        """
                );

        ApiFootballFixture first =
                fixture(
                        1400L,
                        "Valencia",
                        "Barcelona"
                );

        ApiFootballFixture second =
                fixture(
                        1401L,
                        "Arsenal",
                        "Chelsea"
                );

        assertNull(
                matcher.match(
                        candidate,
                        List.of(
                                first,
                                second
                        )
                )
        );
    }

    @Test
    void singleCurrentMatchupBeatsHistoricalOpponentList() {

        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.SINGLE,
                        1,
                        ResolvedSport.UNKNOWN,

                        Instant.parse(
                                "2025-01-17T08:00:00Z"
                        ),

                        "Żegnamy egipskiego księcia Frankfurtu i Nuriego Sahina z BVB z singlem 7.00 na hit Bundesligi!",

                        "Omar Marmoush strzeli gola i zanotuje asystę",

                        "Co obstawiać?",

                        """
                        Borussia prezentuje się beznadziejnie.
                        Ma spore problemy w defensywie i wierzę, że mocna ofensywa SGE da radę.
                        Ten ryzykowny zakład pokryty był w ostatnim starciu z Freiburgiem.
                        W tym sezonie w ligowych zmaganiach udało się Omarowi strzelić gola
                        i zaliczyć asystę w spotkaniach z Hoffenheim, Holstein Kiel,
                        Bayernem Monachium, Bochum, Stuttgartem i Freiburgiem.
                        Jeżeli Marmoush nie wybiegnie już w koszulce Eintrachtu, otrzymamy zwrot.
                        """
                );

        ApiFootballFixture correct =
                fixture(
                        1500L,
                        "Eintracht Frankfurt",
                        "Borussia Dortmund",
                        LocalDate.of(
                                2025,
                                1,
                                17
                        )
                );

        ApiFootballFixture historical =
                fixture(
                        1501L,
                        "Holstein Kiel",
                        "1899 Hoffenheim",
                        LocalDate.of(
                                2025,
                                1,
                                18
                        )
                );

        ApiFootballMatch result =
                matcher.match(
                        candidate,
                        List.of(
                                historical,
                                correct
                        )
                );

        assertNotNull(
                result
        );

        assertEquals(
                1500L,
                result.fixture()
                        .fixtureId()
        );
    }

    @Test
    void combinedCurrentMatchupBeatsHistoricalOpponentList() {

        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.COMBINED,
                        2,
                        ResolvedSport.UNKNOWN,

                        Instant.parse(
                                "2026-02-14T08:00:00Z"
                        ),

                        "Nowy trener, nowe nadzieje – czy Eintracht pokaże charakter?",

                        "1X",

                        "Co typuję w tym spotkaniu?",

                        """
                        Hamburger u siebie jest naprawdę solidną ekipą.
                        W tym sezonie w 10 domowych meczach przegrał tylko raz.
                        Na Volksparkstadion grali już Bayern Monachium,
                        Borussia, Stuttgart czy Eintracht.
                        Unionowi będzie więc bardzo ciężko o komplet punktów.
                        Biorąc pod uwagę domową solidność HSV oraz problemy
                        berlińczyków, typuję zwycięstwo Hamburgera lub remis.
                        """
                );

        ApiFootballFixture correct =
                fixture(
                        1600L,
                        "Hamburger SV",
                        "Union Berlin",
                        LocalDate.of(
                                2026,
                                2,
                                14
                        )
                );

        ApiFootballFixture historical =
                fixture(
                        1601L,
                        "Eintracht Frankfurt",
                        "Borussia Mönchengladbach",
                        LocalDate.of(
                                2026,
                                2,
                                14
                        )
                );

        ApiFootballMatch result =
                matcher.match(
                        candidate,
                        List.of(
                                historical,
                                correct
                        )
                );

        assertNotNull(
                result
        );

        assertEquals(
                1600L,
                result.fixture()
                        .fixtureId()
        );
    }

    @Test
    void zachowalDoesNotTriggerSpeedwayOwalSignal() {

        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.COMBINED,
                        2,
                        ResolvedSport.UNKNOWN,
                        "Typy La Liga",
                        "BTTS",
                        null,
                        """
                        Barcelona zachowała ostatnio czyste konto.
                        Atletico Madrid nadal jest bardzo groźne.
                        Barcelona i Atletico powinny stworzyć otwarty mecz.
                        """
                );

        ApiFootballFixture fixture =
                fixture(
                        1700L,
                        "Barcelona",
                        "Atletico Madrid"
                );

        assertNotNull(
                matcher.match(
                        candidate,
                        List.of(
                                fixture
                        )
                )
        );
    }

    /*
     * =========================================================
     * NOWE REGRESJE Z AUDYTU 730 DNI
     * =========================================================
     */

    @Test
    void manchesterUnitedDoesNotBecomeGlacisUnitedManchester62() {

        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.COMBINED,
                        2,
                        ResolvedSport.UNKNOWN,

                        Instant.parse(
                                "2024-10-24T08:00:00Z"
                        ),

                        "Czy The Special One weźmie odwet na starych znajomych z Manchesteru?",

                        "BTTS",

                        "Co obstawiać?",

                        """
                        Mecz zapowiada się niezwykle ciekawie.
                        Manchester United będzie musiał zmierzyć się na Ülker Stadium.
                        Moim typem na to spotkanie jest BTTS.
                        Biorąc pod uwagę problemy defensywne Manchesteru United
                        oraz ofensywny potencjał Fenerbahçe,
                        możemy być świadkami bramek po obu stronach.
                        """
                );

        ApiFootballFixture correct =
                fixture(
                        1800L,
                        "Fenerbahce",
                        "Manchester United",
                        LocalDate.of(
                                2024,
                                10,
                                24
                        )
                );

        ApiFootballFixture falsePositive =
                fixture(
                        1801L,
                        "Glacis United",
                        "Manchester 62",
                        LocalDate.of(
                                2024,
                                10,
                                26
                        )
                );

        ApiFootballMatch result =
                matcher.match(
                        candidate,
                        List.of(
                                falsePositive,
                                correct
                        )
                );

        assertNotNull(
                result
        );

        assertEquals(
                1800L,
                result.fixture()
                        .fixtureId()
        );
    }

    @Test
    void arsenalTipSubjectRejectsHistoricalMonacoBarcelonaExample() {

        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.COMBINED,
                        2,
                        ResolvedSport.FOOTBALL,

                        Instant.parse(
                                "2024-09-19T08:00:00Z"
                        ),

                        "Robert Lewandowski rozpocznie strzelanie w tym sezonie LM?",

                        "Arsenal nie przegra i powyżej 1,5 gola",

                        "Co obstawiać?",

                        """
                        W roli faworytów tego starcia wystąpią goście.
                        Trudno spodziewać się, by Arsenal miał wrócić do domu bez punktów.
                        Moim typem będzie połączenie podobne do tego,
                        które proponowałem przy okazji meczu Monaco z Barceloną.
                        Atalanta to ekipa, która stara się grać odważnie.
                        """
                );

        ApiFootballFixture correct =
                fixture(
                        1900L,
                        "Atalanta",
                        "Arsenal",
                        LocalDate.of(
                                2024,
                                9,
                                19
                        )
                );

        ApiFootballFixture historical =
                fixture(
                        1901L,
                        "Monaco",
                        "Barcelona",
                        LocalDate.of(
                                2024,
                                9,
                                19
                        )
                );

        ApiFootballMatch result =
                matcher.match(
                        candidate,
                        List.of(
                                historical,
                                correct
                        )
                );

        assertNotNull(
                result
        );

        assertEquals(
                1900L,
                result.fixture()
                        .fixtureId()
        );
    }

    @Test
    void plusLigaAndKprUnknownCannotBecomeFootball() {

        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.COMBINED,
                        2,
                        ResolvedSport.UNKNOWN,

                        Instant.parse(
                                "2025-04-06T08:00:00Z"
                        ),

                        "Lubelski LUK czy kędzierzyńska ZAKSA – kto awansuje do TOP4 PlusLigi?",

                        "Podwójna szansa: remis lub wygrana Energi MKS-u Kalisz - TAK",

                        "Co obstawiać?",

                        """
                        PGE Wybrzeże Gdańsk i Górnik Zabrze mają przewagę.
                        WKS Śląsk Wrocław traci punkty do Zepteru KPR-u Legionowa.
                        Energa MKS Kalisz pozostaje rywalem gospodarzy.
                        """
                );

        ApiFootballFixture fixture =
                fixture(
                        2000L,
                        "Górnik Polkowice",
                        "Ślęza Wrocław",
                        LocalDate.of(
                                2025,
                                4,
                                5
                        )
                );

        assertNull(
                matcher.match(
                        candidate,
                        List.of(
                                fixture
                        )
                )
        );
    }

    @Test
    void polishInflectionMakesSevillaBeatFutureSlovanFixture() {

        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.COMBINED,
                        2,
                        ResolvedSport.FOOTBALL,

                        Instant.parse(
                                "2024-12-08T08:00:00Z"
                        ),

                        "Atletico dorówna kroku czołówce?",

                        "Atletico wygra",

                        "Co obstawiać?",

                        """
                        Stawiam na wygraną Atletico.
                        Los Colchoneros złapali potężny wiatr w żagle.
                        W Europie grają przeciwko Slovanowi Bratysława,
                        więc nie będą musieli oszczędzać sił,
                        ale z całym impetem zaatakować Sevillę.
                        Zespół z Andaluzji jest cieniem samego siebie.
                        """
                );

        ApiFootballFixture correct =
                fixture(
                        2100L,
                        "Atletico Madrid",
                        "Sevilla",
                        LocalDate.of(
                                2024,
                                12,
                                8
                        )
                );

        ApiFootballFixture future =
                fixture(
                        2101L,
                        "Atletico Madrid",
                        "Slovan Bratislava",
                        LocalDate.of(
                                2024,
                                12,
                                11
                        )
                );

        ApiFootballMatch result =
                matcher.match(
                        candidate,
                        List.of(
                                future,
                                correct
                        )
                );

        assertNotNull(
                result
        );

        assertEquals(
                2100L,
                result.fixture()
                        .fixtureId()
        );
    }

    @Test
    void myCombiWislaSubjectRejectsUnrelatedContextFixture() {

        /*
         * Parser/context historyczny może być niedoskonały.
         *
         * tip_title:
         *
         * MyCombi: Wisła wygra...
         *
         * context:
         *
         * Chrobry / Polonia
         *
         * Nie mamy wystarczającego evidence do automatycznego
         * ustalenia rywala Wisły, więc właściwy wynik to NONE,
         * a nie Chrobry - Polonia.
         */
        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.COMBINED,
                        2,
                        ResolvedSport.UNKNOWN,

                        Instant.parse(
                                "2025-07-26T08:00:00Z"
                        ),

                        "Kto górą w hicie Betclic 1. Ligi: Wisła czy ŁKS?",

                        "MyCombi: Wisła wygra i powyżej 1,5 gola",

                        "Co obstawiać?",

                        """
                        W tym pojedynku trudno będzie wskazać faworyta.
                        Jeśli zarówno Chrobry, jak i Polonia pokażą
                        tak skuteczną ofensywę jak przed tygodniem,
                        możemy zobaczyć bramki.
                        """
                );

        ApiFootballFixture correctButInsufficientEvidence =
                fixture(
                        2200L,
                        "Wisła Kraków",
                        "ŁKS Łódź",
                        LocalDate.of(
                                2025,
                                7,
                                26
                        )
                );

        ApiFootballFixture falseContextFixture =
                fixture(
                        2201L,
                        "Chrobry Głogów",
                        "Polonia Bytom",
                        LocalDate.of(
                                2025,
                                7,
                                26
                        )
                );

        assertNull(
                matcher.match(
                        candidate,
                        List.of(
                                falseContextFixture,
                                correctButInsufficientEvidence
                        )
                )
        );
    }

    private static ApiFootballResolutionCandidate candidate(
            BetType betType,
            int betLegCount,
            ResolvedSport sport,
            String postTitle,
            String tipTitle,
            String heading,
            String previousText
    ) {
        return candidate(
                betType,
                betLegCount,
                sport,
                Instant.parse(
                        "2026-05-23T08:00:00Z"
                ),
                postTitle,
                tipTitle,
                heading,
                previousText
        );
    }

    private static ApiFootballResolutionCandidate candidate(
            BetType betType,
            int betLegCount,
            ResolvedSport sport,
            Instant publishedAt,
            String postTitle,
            String tipTitle,
            String heading,
            String previousText
    ) {
        return new ApiFootballResolutionCandidate(
                1L,
                123L,
                betType,
                betLegCount,
                publishedAt,
                sport,
                postTitle,
                tipTitle,
                heading,
                previousText
        );
    }

    private static ApiFootballFixture fixture(
            long id,
            String home,
            String away
    ) {
        return fixture(
                id,
                home,
                away,
                LocalDate.of(
                        2026,
                        5,
                        24
                )
        );
    }

    private static ApiFootballFixture fixture(
            long id,
            String home,
            String away,
            LocalDate date
    ) {
        return new ApiFootballFixture(
                id,
                date
                        .atStartOfDay(
                                java.time.ZoneOffset.UTC
                        )
                        .toInstant(),
                date,
                1L,
                "Test League",
                "World",
                2026,
                "Test",
                10L,
                home,
                20L,
                away,
                null,
                null,
                "FT",
                "Match Finished",
                "{}"
        );
    }

    @Test
    void wislaWithoutDiacriticInApiStillActivatesTipSubjectAnchor() {

        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.COMBINED,
                        2,
                        ResolvedSport.UNKNOWN,

                        Instant.parse(
                                "2025-07-26T08:00:00Z"
                        ),

                        "Kto górą w hicie Betclic 1. Ligi: Wisła czy ŁKS?",

                        "MyCombi: Wisła wygra i powyżej 1,5 gola",

                        "Co obstawiać?",

                        """
                        W tym pojedynku trudno będzie wskazać faworyta.
                        Jeśli zarówno Chrobry, jak i Polonia pokażą
                        tak skuteczną ofensywę jak przed tygodniem,
                        możemy zobaczyć bramki.
                        """
                );

        ApiFootballFixture correct =
                fixture(
                        2300L,
                        "Wisla Krakow",
                        "ŁKS Łódź",
                        LocalDate.of(
                                2025,
                                7,
                                26
                        )
                );

        ApiFootballFixture wrong =
                fixture(
                        2301L,
                        "Chrobry Głogów",
                        "Polonia Bytom",
                        LocalDate.of(
                                2025,
                                7,
                                26
                        )
                );

        /*
         * Context konkretnego lega jest ewidentnie pomylony,
         * więc nie ma wystarczającego evidence do poprawnego
         * automatycznego resolution Wisła - ŁKS.
         *
         * Ale anchor "Wisła" MUSI się aktywować mimo:
         *
         * Wisła
         * vs
         * Wisla
         *
         * i zablokować Chrobry - Polonia.
         */
        assertNull(
                matcher.match(
                        candidate,
                        List.of(
                                wrong,
                                correct
                        )
                )
        );
    }

    @Test
    void colombiaCongoDrBeatsHistoricalPortugalUzbekistan() {

        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.SINGLE,
                        1,
                        ResolvedSport.UNKNOWN,

                        Instant.parse(
                                "2026-06-22T08:00:00Z"
                        ),

                        "Kolumbia – DR Konga: typy i kursy (24.06.2026)",

                        "Kolumbia wygra mecz",

                        null,

                        """
                        Kolumbia podejmie DR Konga w ramach drugiej kolejki zmagań
                        w grupie K na Mistrzostwach Świata 2026.
                        Pierwszy mecz wygrali dość pewnie z Uzbekistanem,
                        ale za to DR Konga zatrzymała Portugalię i wyrwała im remis.
                        Jak potoczy się to spotkanie?
                        """
                );

        ApiFootballFixture correct =
                fixture(
                        3000L,
                        "Colombia",
                        "Congo DR",
                        LocalDate.of(
                                2026,
                                6,
                                24
                        )
                );

        ApiFootballFixture historical =
                fixture(
                        3001L,
                        "Portugal",
                        "Uzbekistan",
                        LocalDate.of(
                                2026,
                                6,
                                23
                        )
                );

        ApiFootballMatch result =
                matcher.match(
                        candidate,
                        List.of(
                                historical,
                                correct
                        )
                );

        assertNotNull(
                result
        );

        assertEquals(
                3000L,
                result.fixture()
                        .fixtureId()
        );
    }

    @Test
    void brukBetNiecieczaAnchorBeatsWislaArkaReference() {

        ApiFootballResolutionCandidate candidate =
                candidate(
                        BetType.COMBINED,
                        2,
                        ResolvedSport.UNKNOWN,

                        Instant.parse(
                                "2025-05-11T08:00:00Z"
                        ),

                        "Bruk-Bet jedną nogą w Ekstraklasie a Warta ciągle w grze…",

                        "Bruk-Bet Nieciecza nie przegra a w meczu obejrzymy min. 2 bramki",

                        null,

                        """
                        Po tym co ostatnio prezentuje Górnik,
                        ciężko dawać im jakiekolwiek szanse w tym starciu.
                        Poza tym Słonie wygrywając ten mecz będą już jedną nogą
                        w Ekstraklasie i jak Wisła Płock nie zwycięży w poniedziałek
                        z liderem tabeli Arką Gdynia,
                        to klub z Niecieczy będzie świętował awans.
                        Zdecydowałem się postawić zakład kombinowany mówiący o tym,
                        że Bruk-Bet nie przegra tego spotkania.
                        """
                );

        ApiFootballFixture correct =
                fixture(
                        3100L,
                        "Nieciecza",
                        "Górnik Łęczna",
                        LocalDate.of(
                                2025,
                                5,
                                11
                        )
                );

        ApiFootballFixture historical =
                fixture(
                        3101L,
                        "Wisla Plock",
                        "Arka Gdynia",
                        LocalDate.of(
                                2025,
                                5,
                                12
                        )
                );

        ApiFootballMatch result =
                matcher.match(
                        candidate,
                        List.of(
                                historical,
                                correct
                        )
                );

        assertNotNull(
                result
        );

        assertEquals(
                3100L,
                result.fixture()
                        .fixtureId()
        );
    }
}