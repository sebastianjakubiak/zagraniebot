package pl.zagranietyper.model;

public record FootballPeriodStatisticCondition(FootballPeriodStatisticsSnapshot.Period period,
        FootballFixtureStatisticCondition condition,boolean eachHalf) {}
