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
}
