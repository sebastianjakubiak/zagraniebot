package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.FootballFixtureStatisticCondition;
import pl.zagranietyper.model.FootballFixtureStatisticType;

import static org.junit.jupiter.api.Assertions.*;

final class FootballShotsSyntaxAdapterTest {
    private final FootballShotsSyntaxAdapter adapter=new FootballShotsSyntaxAdapter();

    @Test void distinguishesCanonicalStatisticsForMatchMarkets(){
        assertParsed("powyżej 20.5 strzałów",Type.TOTAL,Subject.MATCH,Comparison.OVER);
        assertParsed("Poniżej 26.5 strzałów",Type.TOTAL,Subject.MATCH,Comparison.UNDER);
        assertParsed("powyżej 8.5 celnych strzałów",Type.ON_TARGET,Subject.MATCH,Comparison.OVER);
        assertParsed("Poniżej 7.5 strzałów celnych",Type.ON_TARGET,Subject.MATCH,Comparison.UNDER);
        assertParsed("W meczu obejrzymy min. 9 strzałów",Type.TOTAL,Subject.MATCH,Comparison.MINIMUM);
        assertParsed("minimum 6 celnych strzałów",Type.ON_TARGET,Subject.MATCH,Comparison.MINIMUM);
        assertParsed("powyżej 6.5 strzałów w światło bramki",Type.ON_TARGET,Subject.MATCH,Comparison.OVER);
    }

    @Test void parsesAuditedTeamFormsThroughSharedResolver(){
        assertParsed("Bodo/Glimt powyżej 12.5 strzałów","Bodo/Glimt","Monaco",Type.TOTAL,Subject.HOME,Comparison.OVER);
        assertParsed("Ghana poniżej 3.5 strzałów celnych","Brazil","Ghana",Type.ON_TARGET,Subject.AWAY,Comparison.UNDER);
        assertParsed("FC Barcelona strzały celne - powyżej 6,5","Leganes","Barcelona",Type.ON_TARGET,Subject.AWAY,Comparison.OVER);
        assertParsed("AS Monaco odda min. 4 celne strzały","Paris Saint Germain","Monaco",Type.ON_TARGET,Subject.AWAY,Comparison.MINIMUM);
        assertStatus("United powyżej 9.5 strzałów","Manchester United","Newcastle United",Status.AMBIGUOUS_PARTICIPANT);
    }

    @Test void signedConventionsAreStatisticSpecificAndTeamOnly(){
        assertParsed("Liverpool +14.5 strzałów","Manchester United","Liverpool",Type.TOTAL,Subject.AWAY,Comparison.OVER);
        assertParsed("Arsenal -15.5 strzałów","Aston Villa","Arsenal",Type.TOTAL,Subject.AWAY,Comparison.UNDER);
        assertParsed("Celtic +5.5 celnych strzałów","Celtic","Slovan Bratislava",Type.ON_TARGET,Subject.HOME,Comparison.OVER);
        assertParsed("Nottingham -5.5 celnych strzałów","Nottingham Forest","Chelsea",Type.ON_TARGET,Subject.HOME,Comparison.UNDER);
        assertEquals(Type.TOTAL.value,adapter.parse("Liverpool +10.5 strzałów","Liverpool","Chelsea").statisticType());
        assertEquals(Type.ON_TARGET.value,adapter.parse("Liverpool +4.5 celnych strzałów","Liverpool","Chelsea").statisticType());
        assertStatus("+10.5 strzałów","Liverpool","Chelsea",Status.UNSUPPORTED_SIGNED_NOTATION);
        assertStatus("+4.5 celnych strzałów","Liverpool","Chelsea",Status.UNSUPPORTED_SIGNED_NOTATION);
    }

    @Test void rejectsPlayersPeriodsComparisonsHandicapsAndComposites(){
        assertStatus("D. Solanke +2.5 strzałów","Leicester","Tottenham",Status.UNSUPPORTED_PLAYER);
        assertStatus("A. Gordon +0.5 celnych strzałów","Newcastle","Tottenham",Status.UNSUPPORTED_PLAYER);
        assertStatus("Arsenal +5.5 strzałów w 1. połowie","Arsenal","Chelsea",Status.UNSUPPORTED_PERIOD);
        assertStatus("Tottenham więcej strzałów od Aston Villi","Tottenham","Aston Villa",Status.UNSUPPORTED_HANDICAP_OR_COMPARISON);
        assertStatus("Arsenal handicap -2.5 strzałów","Arsenal","Chelsea",Status.UNSUPPORTED_HANDICAP_OR_COMPARISON);
        assertStatus("Poniżej 3,5 gola + poniżej 9,5 celnych strzałów","Espanyol","Real Sociedad",Status.UNSUPPORTED_COMPOSITE);
        assertStatus("Obie drużyny +3.5 celnych strzałów","Brighton","Liverpool",Status.UNSUPPORTED_COMPOSITE);
    }

    @Test void unrelatedStatisticsAreNotCaptured(){
        assertEquals(FootballShotsSyntaxAdapter.Status.NOT_SHOTS_LIKE,adapter.parse("Powyżej 8.5 rzutów rożnych","A","B").status());
        assertEquals(FootballShotsSyntaxAdapter.Status.NOT_SHOTS_LIKE,adapter.parse("Powyżej 12.5 fauli","A","B").status());
        assertEquals(FootballShotsSyntaxAdapter.Status.NOT_SHOTS_LIKE,adapter.parse("Powyżej 4.5 kartek","A","B").status());
    }

    private void assertParsed(String title,Type type,Subject subject,Comparison comparison){assertParsed(title,"Arsenal","Chelsea",type,subject,comparison);}
    private void assertParsed(String title,String home,String away,Type type,Subject subject,Comparison comparison){var r=adapter.parse(title,home,away);assertTrue(r.parsed(),()->title+" => "+r.status());assertEquals(type.value,r.statisticType());assertEquals(subject.value,r.condition().subject());assertEquals(comparison.value,r.condition().comparison());}
    private void assertStatus(String title,String home,String away,Status status){assertEquals(status.value,adapter.parse(title,home,away).status());}
    private enum Type{TOTAL(FootballFixtureStatisticType.SHOTS_TOTAL),ON_TARGET(FootballFixtureStatisticType.SHOTS_ON_TARGET);final FootballFixtureStatisticType value;Type(FootballFixtureStatisticType v){value=v;}}
    private enum Subject{MATCH(FootballFixtureStatisticCondition.Subject.MATCH),HOME(FootballFixtureStatisticCondition.Subject.HOME),AWAY(FootballFixtureStatisticCondition.Subject.AWAY);final FootballFixtureStatisticCondition.Subject value;Subject(FootballFixtureStatisticCondition.Subject v){value=v;}}
    private enum Comparison{OVER(FootballFixtureStatisticCondition.Comparison.OVER),UNDER(FootballFixtureStatisticCondition.Comparison.UNDER),MINIMUM(FootballFixtureStatisticCondition.Comparison.MINIMUM);final FootballFixtureStatisticCondition.Comparison value;Comparison(FootballFixtureStatisticCondition.Comparison v){value=v;}}
    private enum Status{AMBIGUOUS_PARTICIPANT(FootballShotsSyntaxAdapter.Status.AMBIGUOUS_PARTICIPANT),UNSUPPORTED_PLAYER(FootballShotsSyntaxAdapter.Status.UNSUPPORTED_PLAYER),UNSUPPORTED_PERIOD(FootballShotsSyntaxAdapter.Status.UNSUPPORTED_PERIOD),UNSUPPORTED_HANDICAP_OR_COMPARISON(FootballShotsSyntaxAdapter.Status.UNSUPPORTED_HANDICAP_OR_COMPARISON),UNSUPPORTED_COMPOSITE(FootballShotsSyntaxAdapter.Status.UNSUPPORTED_COMPOSITE),UNSUPPORTED_SIGNED_NOTATION(FootballShotsSyntaxAdapter.Status.UNSUPPORTED_SIGNED_NOTATION);final FootballShotsSyntaxAdapter.Status value;Status(FootballShotsSyntaxAdapter.Status v){value=v;}}
}
