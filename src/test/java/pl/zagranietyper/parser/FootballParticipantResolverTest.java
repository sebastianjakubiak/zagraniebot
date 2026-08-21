package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FootballParticipantResolverTest {

    private final FootballParticipantResolver resolver =
            new FootballParticipantResolver();

    @Test
    void resolvesExactParticipantAndSafeInflection() {
        assertResolution(
                FootballParticipantResolver.Resolution.HOME,
                "Brentfordu",
                "Brentford",
                "Brighton",
                FootballParticipantResolver.MatchingPolicy.EXACT_ORDERED
        );
        assertResolution(
                FootballParticipantResolver.Resolution.AWAY,
                "Brightonu",
                "Brentford",
                "Brighton",
                FootballParticipantResolver.MatchingPolicy.EXACT_ORDERED
        );
    }

    @Test
    void sharedSingleTokenIsAmbiguous() {
        assertResolution(
                FootballParticipantResolver.Resolution.AMBIGUOUS,
                "Stal",
                "Stal Stalowa Wola",
                "Stal Rzeszów",
                FootballParticipantResolver.MatchingPolicy.SUBJECT_TOKENS_IN_TEAM
        );
    }

    @Test
    void fullDistinctSubjectResolvesInSharedTokenFixture() {
        assertResolution(
                FootballParticipantResolver.Resolution.AWAY,
                "Stal Rzeszów",
                "Stal Stalowa Wola",
                "Stal Rzeszów",
                FootballParticipantResolver.MatchingPolicy.EXACT_ORDERED
        );
    }

    @Test
    void localizedCountryNamesAreNotGuessed() {
        assertResolution(
                FootballParticipantResolver.Resolution.UNRESOLVED,
                "Dania",
                "Denmark",
                "Sweden",
                FootballParticipantResolver.MatchingPolicy.EXACT_ORDERED
        );
        assertResolution(
                FootballParticipantResolver.Resolution.UNRESOLVED,
                "Kolumbia",
                "Colombia",
                "Brazil",
                FootballParticipantResolver.MatchingPolicy.EXACT_ORDERED
        );
        assertResolution(
                FootballParticipantResolver.Resolution.UNRESOLVED,
                "Portugalia",
                "Portugal",
                "Croatia",
                FootballParticipantResolver.MatchingPolicy.STRICT_SCORED_SUBSET
        );
        assertResolution(
                FootballParticipantResolver.Resolution.UNRESOLVED,
                "Czechy",
                "Czechia",
                "Slovakia",
                FootballParticipantResolver.MatchingPolicy.STRICT_SCORED_SUBSET
        );
        assertResolution(
                FootballParticipantResolver.Resolution.UNRESOLVED,
                "Belgia",
                "Belgium",
                "Ukraine",
                FootballParticipantResolver.MatchingPolicy.STRICT_SCORED_SUBSET
        );
        assertResolution(
                FootballParticipantResolver.Resolution.UNRESOLVED,
                "Dania",
                "Denmark",
                "Sweden",
                FootballParticipantResolver.MatchingPolicy.STRICT_SCORED_SUBSET
        );
        assertResolution(
                FootballParticipantResolver.Resolution.UNRESOLVED,
                "Kolumbia",
                "Colombia",
                "Brazil",
                FootballParticipantResolver.MatchingPolicy.STRICT_SCORED_SUBSET
        );
    }

    @Test
    void resolvesOnlyApprovedExactDirectionalAliases() {
        assertResolution(FootballParticipantResolver.Resolution.AWAY,
                "Argentyna", "Uruguay", "Argentina",
                FootballParticipantResolver.MatchingPolicy.STRICT_SCORED_SUBSET);
        assertResolution(FootballParticipantResolver.Resolution.HOME,
                "Argentyna", "Argentina", "Uruguay",
                FootballParticipantResolver.MatchingPolicy.STRICT_SCORED_SUBSET);
        assertResolution(FootballParticipantResolver.Resolution.AWAY,
                "Brazylia", "Argentina", "Brazil",
                FootballParticipantResolver.MatchingPolicy.STRICT_SCORED_SUBSET);
        assertResolution(FootballParticipantResolver.Resolution.HOME,
                "Brazylia", "Brazil", "Argentina",
                FootballParticipantResolver.MatchingPolicy.STRICT_SCORED_SUBSET);
        assertResolution(FootballParticipantResolver.Resolution.AWAY,
                "Atletico Madryt", "Espanyol", "Atletico Madrid",
                FootballParticipantResolver.MatchingPolicy.STRICT_SCORED_SUBSET);
        assertResolution(FootballParticipantResolver.Resolution.HOME,
                "Atletico Madryt", "Atletico Madrid", "Getafe",
                FootballParticipantResolver.MatchingPolicy.STRICT_SCORED_SUBSET);
    }

    @Test
    void exactAliasMustIdentifyExactlyOneFixtureParticipant() {
        assertResolution(FootballParticipantResolver.Resolution.UNRESOLVED,
                "Argentyna", "Uruguay", "Brazil",
                FootballParticipantResolver.MatchingPolicy.STRICT_SCORED_SUBSET);
        assertResolution(FootballParticipantResolver.Resolution.AMBIGUOUS,
                "Brazylia", "Brazil", "Brazil",
                FootballParticipantResolver.MatchingPolicy.STRICT_SCORED_SUBSET);
    }

    @Test
    void preservesApprovedStrictInflections() {
        assertResolution(FootballParticipantResolver.Resolution.HOME,
                "Bolonii", "Bologna", "Torino",
                FootballParticipantResolver.MatchingPolicy.STRICT_SCORED_SUBSET);
        assertResolution(FootballParticipantResolver.Resolution.HOME,
                "Podbeskidzia", "Podbeskidzie", "Wisla Krakow",
                FootballParticipantResolver.MatchingPolicy.STRICT_SCORED_SUBSET);
        for (String subject : List.of("Liverpoolu", "Arsenalu", "Bayernu", "Ruchu")) {
            String team = subject.substring(0, subject.length() - 1);
            assertResolution(FootballParticipantResolver.Resolution.HOME, subject, team, "Chelsea",
                    FootballParticipantResolver.MatchingPolicy.STRICT_SCORED_SUBSET);
        }
    }

    @Test
    void sharedPrefixDoesNotCreateUnrelatedTeamAliases() {
        assertResolution(
                FootballParticipantResolver.Resolution.UNRESOLVED,
                "Milan",
                "Milano",
                "Torino",
                FootballParticipantResolver.MatchingPolicy.STRICT_SCORED_SUBSET
        );
        assertResolution(
                FootballParticipantResolver.Resolution.UNRESOLVED,
                "Roma",
                "Romania",
                "Spain",
                FootballParticipantResolver.MatchingPolicy.STRICT_SCORED_SUBSET
        );
        assertResolution(
                FootballParticipantResolver.Resolution.UNRESOLVED,
                "Inter",
                "Intel",
                "Torino",
                FootballParticipantResolver.MatchingPolicy.STRICT_SCORED_SUBSET
        );
    }

    @Test
    void nonEquivalentPolishFormsAreNotPromotedToAliases() {
        assertResolution(
                FootballParticipantResolver.Resolution.UNRESOLVED,
                "Bolonii",
                "Bologna",
                "Torino",
                FootballParticipantResolver.MatchingPolicy.EXACT_ORDERED
        );
        assertResolution(
                FootballParticipantResolver.Resolution.UNRESOLVED,
                "Podbeskidzia",
                "Podbeskidzie",
                "Wisła Kraków",
                FootballParticipantResolver.MatchingPolicy.EXACT_ORDERED
        );
    }

    @Test
    void unknownAndThirdTeamRemainUnresolved() {
        assertResolution(
                FootballParticipantResolver.Resolution.UNRESOLVED,
                "Chelsea",
                "Arsenal",
                "Liverpool",
                FootballParticipantResolver.MatchingPolicy.EXACT_ORDERED
        );
    }

    @Test
    void explicitExistingAliasIsUsedOnlyWhenSupplied() {
        Map<String, List<String>> aliases =
                Map.of("borussia dortmund", List.of("BVB"));

        assertEquals(
                FootballParticipantResolver.Resolution.HOME,
                resolver.resolve(
                        "BVB",
                        "Borussia Dortmund",
                        "Bayern Munich",
                        FootballParticipantResolver.MatchingPolicy.EXACT_ORDERED,
                        aliases
                )
        );
        assertResolution(
                FootballParticipantResolver.Resolution.UNRESOLVED,
                "BVB",
                "Borussia Dortmund",
                "Bayern Munich",
                FootballParticipantResolver.MatchingPolicy.EXACT_ORDERED
        );
    }

    private void assertResolution(
            FootballParticipantResolver.Resolution expected,
            String subject,
            String home,
            String away,
            FootballParticipantResolver.MatchingPolicy policy
    ) {
        assertEquals(
                expected,
                resolver.resolve(subject, home, away, policy, Map.of())
        );
    }
}
