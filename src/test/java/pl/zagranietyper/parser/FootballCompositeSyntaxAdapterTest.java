package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class FootballCompositeSyntaxAdapterTest {
    private final FootballCompositeSyntaxAdapter parser=new FootballCompositeSyntaxAdapter();

    @Test void parsesTwoAndThreeFullySupportedBranches() {
        assertParsed("BetBuilder: Colombia nie przegra i poniżej 3,5 gola","Colombia","Brazil",2);
        assertParsed("Bayern powyżej 1.5 gola + Bayern powyżej 4.5 rzutów rożnych","Bayern Munich","Dortmund",2);
        assertParsed("powyżej 1,5 gola i powyżej 7,5 rzutu rożnego oraz poniżej 30,5 strzałów","Inter","Milan",3);
    }

    @Test void neverPartiallyParsesUnsafeOrUnknownBranches() {
        assertStatus("Barcelona wygra i nieznany rynek 7.5","Barcelona","Real Madrid",FootballCompositeSyntaxAdapter.Status.NOT_COMPOSITE);
        assertStatus("Barcelona wygra i Lewy odda celny strzał","Barcelona","Real Madrid",FootballCompositeSyntaxAdapter.Status.PLAYER_BRANCH);
        assertStatus("Barcelona wygra i powyżej 0.5 gola w 1. połowie","Barcelona","Real Madrid",FootballCompositeSyntaxAdapter.Status.PERIOD_BRANCH);
        assertStatus("Barcelona wygra i powyżej 3.5 kartek","Barcelona","Real Madrid",FootballCompositeSyntaxAdapter.Status.CARD_SEMANTIC_UNKNOWN);
        assertStatus("Barcelona wygra i wykona więcej rożnych","Barcelona","Real Madrid",FootballCompositeSyntaxAdapter.Status.COMPARISON_OR_HANDICAP);
    }

    @Test void rejectsDuplicateOverlappingBranches() {
        assertStatus("powyżej 1.5 gola i powyżej 1.5 gola","Inter","Milan",FootballCompositeSyntaxAdapter.Status.AMBIGUOUS_GRAMMAR);
    }

    @Test void unresolvedTranslationCannotEnterComposite() {
        assertStatus("BetBuilder: Kolumbia nie przegra i poniżej 3,5 gola","Colombia","Brazil",
                FootballCompositeSyntaxAdapter.Status.UNSUPPORTED_BRANCH);
    }

    @Test void rejectsEmbeddedMatchTotalFragmentsButKeepsWholeBranchComposite() {
        assertStatus("Hiszpania -3.5 goli i -7.5 rzutów rożnych","Spain","Switzerland",
                FootballCompositeSyntaxAdapter.Status.UNSUPPORTED_BRANCH);
        assertStatus("Roma nie przegra + strzeli powyżej 0,5 gola","Lecce","AS Roma",
                FootballCompositeSyntaxAdapter.Status.PLAYER_BRANCH);
        assertParsed("Poniżej 3,5 gola + poniżej 9,5 celnych strzałów","Espanyol","Real Sociedad",2);
    }

    @Test void teamGoalFallbackIsWholeBranchOnlyAndNeverAddsSignedTeamGoals() {
        assertParsed("Villarreal CF powyżej 0,5 gola + powyżej 2,5 rzutów rożnych",
                "Villarreal","Getafe",2);
        assertStatus("Manchester City +1.5 goli i +4.5 rzutów rożnych",
                "Manchester City","Tottenham",FootballCompositeSyntaxAdapter.Status.UNSUPPORTED_BRANCH);
        assertStatus("Hiszpania -3.5 goli i -7.5 rzutów rożnych",
                "Spain","Switzerland",FootballCompositeSyntaxAdapter.Status.UNSUPPORTED_BRANCH);
        assertStatus("Roma nie przegra + strzeli powyżej 0,5 gola",
                "Lecce","AS Roma",FootballCompositeSyntaxAdapter.Status.PLAYER_BRANCH);
    }

    private void assertParsed(String title,String home,String away,int branches){var result=parser.parse(title,home,away);assertEquals(FootballCompositeSyntaxAdapter.Status.PARSED,result.status());assertEquals(branches,result.condition().branches().size());}
    private void assertStatus(String title,String home,String away,FootballCompositeSyntaxAdapter.Status status){assertEquals(status,parser.parse(title,home,away).status());}
}
