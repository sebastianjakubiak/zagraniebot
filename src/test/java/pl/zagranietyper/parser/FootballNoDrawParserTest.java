package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FootballNoDrawParserTest {

    private final FootballNoDrawParser parser =
            new FootballNoDrawParser();

    @Test
    void parsesSimplePolishFixture() {
        assertParsed(
                "Zwycięstwo Śląska lub Korony",
                "Slask Wroclaw",
                "Korona Kielce"
        );
    }

    @Test
    void parsesMilanLiverpool() {
        assertParsed(
                "Zwycięstwo AC Milanu lub Liverpoolu",
                "AC Milan",
                "Liverpool"
        );
    }

    @Test
    void parsesAthleticBilbaoAlias() {
        assertParsed(
                "Zwycięstwo AS Romy lub Athleticu Bilbao",
                "AS Roma",
                "Athletic Club"
        );
    }

    @Test
    void parsesCountryAliases() {
        assertParsed(
                "Zwycięstwo Anglii lub Grecji",
                "England",
                "Greece"
        );
    }

    @Test
    void parsesBrazilUruguay() {
        assertParsed(
                "Zwycięstwo Brazylii lub Urugwaju",
                "Brazil",
                "Uruguay"
        );
    }

    @Test
    void parsesManchesterCityUnitedWithoutAmbiguity() {
        assertParsed(
                "Zwycięstwo Manchesteru City lub Manchesteru United",
                "Manchester City",
                "Manchester United"
        );
    }

    @Test
    void parsesBayerNotBayern() {
        assertParsed(
                "Zwycięstwo Liverpoolu lub Bayeru",
                "Liverpool",
                "Bayer Leverkusen"
        );
    }

    @Test
    void parsesBayern() {
        assertParsed(
                "Zwycięstwo Bayernu lub Slovanu",
                "Bayern München",
                "Slovan Bratislava"
        );
    }

    @Test
    void parsesNewcastleUnitedAlias() {
        assertParsed(
                "Zwycięstwo Newcastle United lub Liverpoolu",
                "Newcastle",
                "Liverpool"
        );
    }

    @Test
    void parsesIpswichTown() {
        assertParsed(
                "Zwycięstwo Ipswich Town lub Brightonu",
                "Ipswich",
                "Brighton"
        );
    }

    @Test
    void parsesCrvenaShortName() {
        assertParsed(
                "Zwycięstwo Crveny lub Stuttgartu",
                "FK Crvena Zvezda",
                "VfB Stuttgart"
        );
    }

    @Test
    void rejectsWinOrDrawComposite() {
        assertRejected(
                "Stal mielec powyżej 0.5 bramki i Stal Mieliec zwycięstwo lub remis",
                "Stal Mielec",
                "Ruch Chorzów",
                FootballNoDrawParser.Status.UNSUPPORTED_COMPOSITE
        );
    }

    @Test
    void rejectsWrongFixtureParticipants() {
        assertRejected(
                "Zwycięstwo Liverpoolu lub Chelsea",
                "Arsenal",
                "Manchester City",
                FootballNoDrawParser.Status.PARTICIPANTS_MISMATCH
        );
    }

    @Test
    void ignoresOrdinaryDoubleChance() {
        assertRejected(
                "Korona wygra lub zremisuje mecz",
                "Widzew Łódź",
                "Korona Kielce",
                FootballNoDrawParser.Status.NOT_NO_DRAW
        );
    }

    @Test
    void parsesWygranaAlternative() {
        assertParsed(
                "Wygrana Brightonu lub Brentfordu",
                "Brighton",
                "Brentford"
        );
    }

    @Test
    void parsesRepeatedWinnerCue() {
        assertParsed(
                "Roma wygra lub Torino wygra",
                "Torino",
                "AS Roma"
        );
    }

    @Test
    void parsesOptionalMatchWord() {
        assertParsed(
                "Roma wygra mecz lub Torino wygra mecz",
                "Torino",
                "AS Roma"
        );
    }

    @Test
    void parsesReversedFixtureOrder() {
        assertParsed(
                "Milan wygra lub Napoli wygra mecz",
                "Napoli",
                "AC Milan"
        );
    }

    @Test
    void rejectsDuplicateSubject() {
        assertRejected(
                "Napoli wygra lub Napoli wygra",
                "Napoli",
                "AC Milan",
                FootballNoDrawParser.Status.PARTICIPANTS_MISMATCH
        );
    }

    @Test
    void rejectsOnlyOneResolvableSubject() {
        assertRejected(
                "Napoli wygra lub Nieznani wygra",
                "Napoli",
                "AC Milan",
                FootballNoDrawParser.Status.PARTICIPANTS_MISMATCH
        );
    }

    @Test
    void rejectsThirdTeam() {
        assertRejected(
                "Napoli wygra lub Juventus wygra",
                "Napoli",
                "AC Milan",
                FootballNoDrawParser.Status.PARTICIPANTS_MISMATCH
        );
    }

    @Test
    void rejectsExtraUnresolvedCondition() {
        assertRejected(
                "Napoli wygra lub Milan wygra i Osimhen strzeli gola",
                "Napoli",
                "AC Milan",
                FootballNoDrawParser.Status.UNSUPPORTED_COMPOSITE
        );
    }

    private void assertParsed(
            String tip,
            String home,
            String away
    ) {
        FootballNoDrawParser.ParseResult result =
                parser.parse(
                        tip,
                        home,
                        away
                );

        assertEquals(
                FootballNoDrawParser.Status.PARSED,
                result.status()
        );
    }

    private void assertRejected(
            String tip,
            String home,
            String away,
            FootballNoDrawParser.Status expected
    ) {
        FootballNoDrawParser.ParseResult result =
                parser.parse(
                        tip,
                        home,
                        away
                );

        assertEquals(
                expected,
                result.status()
        );
    }
}
