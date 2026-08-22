package pl.zagranietyper.service;

import pl.zagranietyper.model.*;
import java.util.*;

public final class FootballPeriodStatisticSettlementEngine {
    private final FootballFixtureStatisticSettlementEngine delegate=new FootballFixtureStatisticSettlementEngine();
    public SettlementDecision settle(FootballPeriodStatisticCondition c,FootballPeriodStatisticsSnapshot s){if(c==null||s==null||s.status()!=FootballPeriodStatisticsSnapshot.FetchStatus.COMPLETE)return SettlementDecision.UNSUPPORTED;if(c.eachHalf()){var a=one(c.condition(),s,FootballPeriodStatisticsSnapshot.Period.FIRST_HALF);var b=one(c.condition(),s,FootballPeriodStatisticsSnapshot.Period.SECOND_HALF);if(a==SettlementDecision.UNSUPPORTED||b==SettlementDecision.UNSUPPORTED)return SettlementDecision.UNSUPPORTED;if(a==SettlementDecision.L||b==SettlementDecision.L)return SettlementDecision.L;if(a==SettlementDecision.V&&b==SettlementDecision.V)return SettlementDecision.V;return SettlementDecision.W;}return one(c.condition(),s,c.period());}
    private SettlementDecision one(FootballFixtureStatisticCondition c,FootballPeriodStatisticsSnapshot s,FootballPeriodStatisticsSnapshot.Period p){List<FootballFixtureStatisticsSnapshot.StatisticValue>values=new ArrayList<>();for(var side:FootballFixtureStatisticsSnapshot.TeamSide.values())s.value(p,side,c.type()).ifPresent(v->values.add(new FootballFixtureStatisticsSnapshot.StatisticValue(side==FootballFixtureStatisticsSnapshot.TeamSide.HOME?1:2,side,v.type(),v.value(),v.status(),v.rawKey())));var x=new FootballFixtureStatisticsSnapshot(s.fixtureId(),FootballFixtureStatisticsSnapshot.FetchStatus.COMPLETE,s.provider(),200,2,null,Set.of(),"{}",s.fetchedAt(),1,values);return delegate.settle(c,x);}
}
