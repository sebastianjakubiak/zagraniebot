package pl.zagranietyper.parser;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.*;
import pl.zagranietyper.service.UnifiedFootballSettlementEngine;

import static org.junit.jupiter.api.Assertions.*;

final class FootballHalfMarketSyntaxAdapterTest {
    private final FootballHalfMarketSyntaxAdapter parser=new FootballHalfMarketSyntaxAdapter();private final UnifiedFootballSettlementEngine engine=new UnifiedFootballSettlementEngine();
    @Test void firstHalfOverSupportsWinLossAndVoid(){var market=parse("powyżej 1 gola w 1. połowie","Inter","Milan");assertEquals(SettlementDecision.W,settle(market,2,0,3,0));assertEquals(SettlementDecision.L,settle(market,0,0,2,0));assertEquals(SettlementDecision.V,settle(market,1,0,2,0));}
    @Test void secondHalfUsesFullTimeMinusHalfTimeAndRejectsInvalidSnapshot(){var market=parse("+1 goli w 2. połowie","Inter","Milan");assertEquals(SettlementDecision.W,settle(market,1,0,3,0));assertEquals(SettlementDecision.L,settle(market,1,0,1,0));assertEquals(SettlementDecision.V,settle(market,1,0,2,0));var invalid=FootballScoreSnapshot.fullTimeAndFirstHalf(new FootballScore(0,0),new FootballScore(1,0));assertEquals(SettlementDecision.UNSUPPORTED,engine.settle(market,invalid));}
    @Test void resolvesTeamTotalsAndHalfResults(){assertTrue(parser.parse("Villarreal powyżej 0.5 gola w 1. połowie","Villarreal","Getafe").parsed());assertTrue(parser.parse("Getafe powyżej 0.5 gola w 2. połowie","Villarreal","Getafe").parsed());assertTrue(parser.parse("Villarreal wygra 1. połowę","Villarreal","Getafe").parsed());assertEquals(FootballHalfMarketSyntaxAdapter.Status.PARTICIPANT_UNRESOLVED,parser.parse("Hiszpania powyżej 0.5 gola w 1. połowie","Spain","Switzerland").status());}
    @Test void rejectsPartialAndAmbiguousMarkets(){for(String title:java.util.List.of("1. połowa lub mecz - Crystal Palace","Chelsea wygra i +1.5 goli w 2. połowie","powyżej 0.5 gola i 3.5 kartek w 1. połowie","Przedział goli w każdej połowie: 1-3"))assertFalse(parser.parse(title,"Chelsea","Arsenal").parsed());}
    private UnifiedFootballMarket parse(String title,String home,String away){var result=parser.parse(title,home,away);assertTrue(result.parsed(),()->result.status()+" "+result.normalized());return result.market();}
    private SettlementDecision settle(UnifiedFootballMarket market,int hh,int ha,int fh,int fa){return engine.settle(market,FootballScoreSnapshot.fullTimeAndFirstHalf(new FootballScore(fh,fa),new FootballScore(hh,ha)));}
}
