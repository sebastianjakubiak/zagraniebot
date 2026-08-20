package pl.zagranietyper.service;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.model.FootballMarket;
import pl.zagranietyper.model.FootballScore;
import pl.zagranietyper.model.FootballScorePeriod;
import pl.zagranietyper.model.FootballScoreSnapshot;
import pl.zagranietyper.model.FootballTeamMarket;
import pl.zagranietyper.model.SettlementDecision;
import pl.zagranietyper.model.UnifiedFootballMarket;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedFootballSettlementParityTest {

    private final FootballMarketSettlementEngine legacyMatchEngine =
            new FootballMarketSettlementEngine();
    private final FootballTeamMarketSettlementEngine legacyTeamEngine =
            new FootballTeamMarketSettlementEngine();
    private final UnifiedFootballSettlementEngine unifiedEngine =
            new UnifiedFootballSettlementEngine();
    private final FootballMarketUnifiedAdapter matchAdapter =
            new FootballMarketUnifiedAdapter();
    private final FootballTeamMarketUnifiedAdapter teamAdapter =
            new FootballTeamMarketUnifiedAdapter();

    @Test
    void matchResultParity() {
        for (FootballMarket.MatchResultSelection selection :
                FootballMarket.MatchResultSelection.values()) {
            FootballMarket market = new FootballMarket(
                    List.of(new FootballMarket.MatchResult(selection))
            );

            assertMatchParity(market, new FootballScore(2, 0));
            assertMatchParity(market, new FootballScore(1, 1));
            assertMatchParity(market, new FootballScore(0, 3));
        }
    }

    @Test
    void doubleChanceAndNoDrawParity() {
        for (FootballMarket.DoubleChanceSelection selection :
                FootballMarket.DoubleChanceSelection.values()) {
            FootballMarket market = new FootballMarket(
                    List.of(new FootballMarket.DoubleChance(selection))
            );

            assertMatchParity(market, new FootballScore(2, 0));
            assertMatchParity(market, new FootballScore(1, 1));
            assertMatchParity(market, new FootballScore(0, 3));
        }
    }

    @Test
    void matchTotalParityIncludingIntegerVoidBoundary() {
        for (FootballMarket.TotalDirection direction :
                FootballMarket.TotalDirection.values()) {
            FootballMarket decimal = new FootballMarket(
                    List.of(
                            new FootballMarket.TotalGoals(
                                    direction,
                                    new BigDecimal("2.5")
                            )
                    )
            );
            FootballMarket integer = new FootballMarket(
                    List.of(
                            new FootballMarket.TotalGoals(
                                    direction,
                                    new BigDecimal("2.0")
                            )
                    )
            );

            assertMatchParity(decimal, new FootballScore(1, 1));
            assertMatchParity(decimal, new FootballScore(2, 1));
            assertMatchParity(integer, new FootballScore(1, 1));
        }
    }

    @Test
    void minimumTotalParityAtAndAroundBoundary() {
        FootballMarket market = new FootballMarket(
                List.of(new FootballMarket.MinimumTotalGoals(3))
        );

        assertMatchParity(market, new FootballScore(1, 1));
        assertMatchParity(market, new FootballScore(2, 1));
        assertMatchParity(market, new FootballScore(3, 1));
    }

    @Test
    void inclusiveMatchGoalRangeParityAtAllBoundaries() {
        FootballMarket market = new FootballMarket(
                List.of(new FootballMarket.MatchGoalRange(2, 4))
        );

        assertMatchParity(market, new FootballScore(1, 0));
        assertMatchParity(market, new FootballScore(1, 1));
        assertMatchParity(market, new FootballScore(2, 1));
        assertMatchParity(market, new FootballScore(3, 1));
        assertMatchParity(market, new FootballScore(4, 1));
    }

    @Test
    void bothTeamsToScoreParityForYesAndNo() {
        for (boolean expected : List.of(true, false)) {
            FootballMarket market = new FootballMarket(
                    List.of(new FootballMarket.BothTeamsToScore(expected))
            );

            assertMatchParity(market, new FootballScore(0, 0));
            assertMatchParity(market, new FootballScore(2, 0));
            assertMatchParity(market, new FootballScore(2, 1));
        }
    }

    @Test
    void compoundAndParityIncludingLossAndVoidRules() {
        FootballMarket winnerAndTotal = new FootballMarket(
                List.of(
                        new FootballMarket.MatchResult(
                                FootballMarket.MatchResultSelection.HOME
                        ),
                        new FootballMarket.TotalGoals(
                                FootballMarket.TotalDirection.OVER,
                                new BigDecimal("2.0")
                        )
                )
        );
        FootballMarket allVoid = new FootballMarket(
                List.of(
                        new FootballMarket.TotalGoals(
                                FootballMarket.TotalDirection.OVER,
                                new BigDecimal("2.0")
                        ),
                        new FootballMarket.TotalGoals(
                                FootballMarket.TotalDirection.UNDER,
                                new BigDecimal("2.0")
                        )
                )
        );

        assertMatchParity(winnerAndTotal, new FootballScore(2, 0));
        assertMatchParity(winnerAndTotal, new FootballScore(1, 1));
        assertMatchParity(winnerAndTotal, new FootballScore(2, 1));
        assertMatchParity(allVoid, new FootballScore(1, 1));
    }

    @Test
    void teamTotalParityForBothSidesDirectionsAndVoidBoundary() {
        for (FootballTeamMarket.TeamSide side :
                FootballTeamMarket.TeamSide.values()) {
            for (FootballTeamMarket.Direction direction :
                    FootballTeamMarket.Direction.values()) {
                FootballTeamMarket decimal =
                        new FootballTeamMarket.TeamTotalGoals(
                                side,
                                direction,
                                new BigDecimal("1.5")
                        );
                FootballTeamMarket integer =
                        new FootballTeamMarket.TeamTotalGoals(
                                side,
                                direction,
                                new BigDecimal("2.0")
                        );

                assertTeamParity(decimal, new FootballScore(2, 0));
                assertTeamParity(decimal, new FootballScore(0, 2));
                assertTeamParity(integer, new FootballScore(2, 2));
            }
        }
    }

    @Test
    void teamToScoreParityForBothSidesAndAnswers() {
        for (FootballTeamMarket.TeamSide side :
                FootballTeamMarket.TeamSide.values()) {
            for (boolean expected : List.of(true, false)) {
                FootballTeamMarket market =
                        new FootballTeamMarket.TeamToScore(side, expected);

                assertTeamParity(market, new FootballScore(0, 0));
                assertTeamParity(market, new FootballScore(2, 0));
                assertTeamParity(market, new FootballScore(0, 3));
            }
        }
    }

    @Test
    void snapshotExposesExplicitAvailablePeriodsAndDerivesSecondHalf() {
        FootballScoreSnapshot snapshot =
                FootballScoreSnapshot.fullTimeAndFirstHalf(
                        new FootballScore(3, 2),
                        new FootballScore(1, 2)
                );

        assertEquals(
                new FootballScore(3, 2),
                snapshot.score(FootballScorePeriod.FULL_TIME).orElseThrow()
        );
        assertEquals(
                new FootballScore(1, 2),
                snapshot.score(FootballScorePeriod.FIRST_HALF).orElseThrow()
        );
        assertEquals(
                new FootballScore(2, 0),
                snapshot.score(FootballScorePeriod.SECOND_HALF).orElseThrow()
        );
    }

    @Test
    void snapshotDoesNotInventInvalidSecondHalf() {
        FootballScoreSnapshot snapshot =
                FootballScoreSnapshot.fullTimeAndFirstHalf(
                        new FootballScore(1, 0),
                        new FootballScore(2, 0)
                );

        assertTrue(snapshot.hasScore(FootballScorePeriod.FULL_TIME));
        assertTrue(snapshot.hasScore(FootballScorePeriod.FIRST_HALF));
        assertFalse(snapshot.hasScore(FootballScorePeriod.SECOND_HALF));
    }

    @Test
    void unavailableRequestedPeriodSettlesUnsupported() {
        UnifiedFootballMarket market = new UnifiedFootballMarket(
                List.of(
                        new UnifiedFootballMarket.Result(
                                UnifiedFootballMarket.ResultSelection.AWAY,
                                FootballScorePeriod.FULL_TIME
                        ),
                        new UnifiedFootballMarket.TotalGoals(
                                UnifiedFootballMarket.GoalSubject.MATCH,
                                FootballScorePeriod.FIRST_HALF,
                                UnifiedFootballMarket.TotalDirection.OVER,
                                new BigDecimal("0.5")
                        )
                )
        );

        assertEquals(
                SettlementDecision.UNSUPPORTED,
                unifiedEngine.settle(
                        market,
                        FootballScoreSnapshot.fullTime(
                                new FootballScore(2, 1)
                        )
                )
        );
    }

    private void assertMatchParity(
            FootballMarket market,
            FootballScore score
    ) {
        assertEquals(
                legacyMatchEngine.settle(market, score),
                unifiedEngine.settle(
                        matchAdapter.adapt(market),
                        FootballScoreSnapshot.fullTime(score)
                )
        );
    }

    private void assertTeamParity(
            FootballTeamMarket market,
            FootballScore score
    ) {
        assertEquals(
                legacyTeamEngine.settle(market, score),
                unifiedEngine.settle(
                        teamAdapter.adapt(market),
                        FootballScoreSnapshot.fullTime(score)
                )
        );
    }
}
