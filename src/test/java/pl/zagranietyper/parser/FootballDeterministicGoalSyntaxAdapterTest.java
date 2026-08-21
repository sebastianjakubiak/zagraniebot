package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.UnifiedFootballMarket;

import static org.junit.jupiter.api.Assertions.*;

class FootballDeterministicGoalSyntaxAdapterTest {
    private final FootballDeterministicGoalSyntaxAdapter parser=new FootballDeterministicGoalSyntaxAdapter();
    @Test void supportsExplicitTeamGoalFamilies(){
        assertTrue(parser.parse("Górnik Łęczna strzeli min. 1 bramkę","Wisla Plock","Górnik Łęczna").parsed());
        assertTrue(parser.parse("Przynajmniej jedna bramka Górnika","Kotwica Kołobrzeg","Górnik Łęczna").parsed());
        assertTrue(parser.parse("Olimpia bez bramki","Olimpia Elbląg","Wieczysta Kraków").parsed());
        assertTrue(parser.parse("Goście strzelą gola: tak","Leicester","Tranmere").parsed());
    }
    @Test void supportsAuditedSignedTeamFamily(){
        var r=parser.parse("Bodo/Glimt +1.5 goli","Bodo/Glimt","Crvena Zvezda");
        assertTrue(r.parsed());assertInstanceOf(UnifiedFootballMarket.TotalGoals.class,r.market().conditions().getFirst());
    }
    @Test void supportsExplicitBttsRangeAndEitherFixtureTeam(){
        assertTrue(parser.parse("Oba zespoły strzelą gola: nie","A","B").parsed());
        assertTrue(parser.parse("Zakres liczby goli: 2-3","A","B").parsed());
        assertTrue(parser.parse("Gol Arsenal lub Chelsea","Arsenal","Chelsea").parsed());
    }
    @Test void rejectsUnsafeOrUnresolvedGoalWording(){
        assertFalse(parser.parse("Kolumbia strzeli gola","Colombia","Brazil").parsed());
        assertFalse(parser.parse("Lewandowski strzeli gola","Poland","Croatia").parsed());
        assertFalse(parser.parse("Roma strzeli gola w 1. połowie","Roma","Milan").parsed());
        assertFalse(parser.parse("Roma wygra i strzeli gola","Roma","Milan").parsed());
        assertFalse(parser.parse("+2 gole","Roma","Milan").parsed());
        assertFalse(parser.parse("Manchester City +1.5 goli i +4.5 rzutów rożnych","Manchester City","Arsenal").parsed());
        assertFalse(parser.parse("✅Everton +0.5 goli","Everton","Liverpool").parsed());
    }
    @Test void supportsOnlyAuditedTeamToScoreWordingVariants(){
        assertTrue(parser.parseVariant("Betis powyżej 0.5 gols","Real Sociedad","Real Betis").parsed());
        assertTrue(parser.parseVariant("Torino strzeli gla","Udinese","Torino").parsed());
        assertTrue(parser.parseVariant("Wolves strzelą gola","Wolves","Newcastle").parsed());
        assertTrue(parser.parseVariant("Nottingham strzeli przynajmniej jednego gola","Nottingham Forest","Arsenal").parsed());
        var no=parser.parseVariant("Dagenham & Red strzeli gola - NIE","Millwall","Dagenham & Redbridge");
        assertFalse(no.parsed());
        var exactNo=parser.parseVariant("Dagenham & Redbridge strzeli gola - NIE","Millwall","Dagenham & Redbridge");
        assertTrue(exactNo.parsed());assertFalse(((UnifiedFootballMarket.TeamToScore)exactNo.market().conditions().getFirst()).expected());
    }
    @Test void variantBoundaryRejectsUnsafeSubjectsAndPartialTitles(){
        assertFalse(parser.parseVariant("Kolumbia strzelą gola","Colombia","Brazil").parsed());
        assertFalse(parser.parseVariant("Lewandowski strzeli gla","Barcelona","Real Madrid").parsed());
        assertFalse(parser.parseVariant("Roma strzelą gola w 1. połowie","Roma","Milan").parsed());
        assertFalse(parser.parseVariant("Gospodarze strzelą gola","Roma","Milan").parsed());
        assertFalse(parser.parseVariant("Roma strzelą gola i wygra","Roma","Milan").parsed());
    }
}
