package pl.zagranietyper;

import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.repository.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class RepairInvalidFootballAutoSettlementMain {

    private static final Timestamp FIRST_FOOTBALL_BATCH =
            Timestamp.from(
                    Instant.parse(
                            "2026-08-16T13:23:14Z"
                    )
            );

    private static final long KNOWN_BAD_RESOLUTION_LEG_ID =
            2584L;

    private RepairInvalidFootballAutoSettlementMain() {
    }

    public static void main(
            String[] args
    ) {
        boolean apply =
                args.length == 1
                        && "--apply".equals(
                        args[0]
                );

        AppConfig config =
                AppConfig.fromEnvironment();

        Database database =
                new Database(
                        config
                );

        try (
                Connection connection =
                        database.openConnection()
        ) {
            connection.setAutoCommit(
                    false
            );

            try {
                List<AffectedLeg> affectedLegs =
                        findAffectedLegs(
                                connection
                        );

                Set<Long> affectedBetIds =
                        new LinkedHashSet<>();

                for (
                        AffectedLeg leg :
                        affectedLegs
                ) {
                    affectedBetIds.add(
                            leg.betId()
                    );
                }

                List<BetProjection> projections =
                        calculateBetProjections(
                                connection,
                                affectedBetIds,
                                affectedLegs
                        );

                printDryRun(
                        affectedLegs,
                        projections
                );

                if (
                        !apply
                ) {
                    connection.rollback();

                    System.out.println();
                    System.out.println(
                            "DRY RUN ONLY — DATABASE NOT MODIFIED"
                    );

                    System.out.println();
                    System.out.println(
                            "Apply with:"
                    );

                    System.out.println(
                            "java -cp target/zagranie-typer-0.1.0-SNAPSHOT.jar \\"
                    );

                    System.out.println(
                            "  pl.zagranietyper.RepairInvalidFootballAutoSettlementMain --apply"
                    );

                    return;
                }

                ApplyResult result =
                        applyRepair(
                                connection,
                                affectedLegs,
                                projections
                        );

                connection.commit();

                System.out.println();
                System.out.println(
                        "========================================"
                );

                System.out.println(
                        "DATABASE APPLY"
                );

                System.out.println(
                        "========================================"
                );

                System.out.println(
                        "resetLegs="
                                + result.resetLegs()
                );

                System.out.println(
                        "updatedBets="
                                + result.updatedBets()
                );

                System.out.println(
                        "betsToPending="
                                + result.betsToPending()
                );

                System.out.println(
                        "betsRemainLoss="
                                + result.betsRemainLoss()
                );

                System.out.println(
                        "betsAlreadyPending="
                                + result.betsAlreadyPending()
                );

                System.out.println();
                System.out.println(
                        "COMMIT OK"
                );

            } catch (
                    RuntimeException
                    | SQLException e
            ) {
                connection.rollback();

                throw e;
            }

        } catch (
                SQLException e
        ) {
            throw new IllegalStateException(
                    "Football settlement repair failed",
                    e
            );
        }
    }

    /*
     * =========================================================
     * AFFECTED LEGS
     * =========================================================
     */

    private static List<AffectedLeg> findAffectedLegs(
            Connection connection
    ) throws SQLException {
        String sql = """
                SELECT
                    bl.id,
                    bl.bet_id,
                    b.wp_post_id,
                    b.bet_type,

                    bl.tip_title,
                    bl.settlement_status,
                    bl.settlement_source,
                    bl.settled_at,

                    bl.resolved_event_name,
                    bl.resolved_external_event_id

                FROM bet_legs bl

                JOIN bets b
                  ON b.id = bl.bet_id
                 AND b.active = TRUE

                WHERE bl.active = TRUE
                  AND bl.settlement_source = 'AUTO'

                  AND (
                        (
                            date_trunc(
                                'second',
                                bl.settled_at
                            ) = date_trunc(
                                'second',
                                ?::timestamptz
                            )

                            AND (
                                   lower(bl.tip_title) LIKE '%połow%'
                                OR lower(bl.tip_title) LIKE '%polow%'
                                OR lower(bl.tip_title) LIKE '%do przerwy%'
                                OR lower(bl.tip_title) LIKE '%half%'
                            )
                        )

                        OR bl.id = ?
                  )

                ORDER BY bl.id
                """;

        List<AffectedLeg> result =
                new ArrayList<>();

        try (
                PreparedStatement ps =
                        connection.prepareStatement(
                                sql
                        )
        ) {
            ps.setTimestamp(
                    1,
                    FIRST_FOOTBALL_BATCH
            );

            ps.setLong(
                    2,
                    KNOWN_BAD_RESOLUTION_LEG_ID
            );

            try (
                    ResultSet rs =
                            ps.executeQuery()
            ) {
                while (
                        rs.next()
                ) {
                    result.add(
                            new AffectedLeg(
                                    rs.getLong(
                                            "id"
                                    ),

                                    rs.getLong(
                                            "bet_id"
                                    ),

                                    rs.getLong(
                                            "wp_post_id"
                                    ),

                                    rs.getString(
                                            "bet_type"
                                    ),

                                    rs.getString(
                                            "tip_title"
                                    ),

                                    rs.getString(
                                            "settlement_status"
                                    ),

                                    rs.getString(
                                            "settlement_source"
                                    ),

                                    rs.getTimestamp(
                                            "settled_at"
                                    ),

                                    rs.getString(
                                            "resolved_event_name"
                                    ),

                                    rs.getString(
                                            "resolved_external_event_id"
                                    )
                            )
                    );
                }
            }
        }

        return List.copyOf(
                result
        );
    }

    /*
     * =========================================================
     * BET PROJECTION
     * =========================================================
     *
     * Symulujemy stan PO wyzerowaniu affected legs.
     *
     * Affected leg:
     *
     * AUTO W/L/V -> PENDING/NONE
     *
     * Następnie agregujemy pozostałe aktywne legi.
     */

    private static List<BetProjection> calculateBetProjections(
            Connection connection,
            Set<Long> affectedBetIds,
            List<AffectedLeg> affectedLegs
    ) throws SQLException {
        Set<Long> affectedLegIds =
                new LinkedHashSet<>();

        for (
                AffectedLeg leg :
                affectedLegs
        ) {
            affectedLegIds.add(
                    leg.legId()
            );
        }

        List<BetProjection> result =
                new ArrayList<>();

        for (
                Long betId :
                affectedBetIds
        ) {
            BetState current =
                    findBetState(
                            connection,
                            betId
                    );

            List<LegState> legs =
                    findLegStates(
                            connection,
                            betId
                    );

            List<String> projectedStatuses =
                    new ArrayList<>();

            for (
                    LegState leg :
                    legs
            ) {
                if (
                        affectedLegIds.contains(
                                leg.legId()
                        )
                ) {
                    projectedStatuses.add(
                            "PENDING"
                    );
                } else {
                    projectedStatuses.add(
                            leg.status()
                    );
                }
            }

            ProjectedSettlement projected =
                    aggregate(
                            current.betType(),
                            projectedStatuses
                    );

            result.add(
                    new BetProjection(
                            betId,
                            current.wpPostId(),
                            current.betType(),

                            current.status(),
                            current.source(),

                            projected.status(),
                            projected.source()
                    )
            );
        }

        return List.copyOf(
                result
        );
    }

    private static BetState findBetState(
            Connection connection,
            long betId
    ) throws SQLException {
        String sql = """
                SELECT
                    id,
                    wp_post_id,
                    bet_type,
                    settlement_status,
                    settlement_source
                FROM bets
                WHERE id = ?
                  AND active = TRUE
                """;

        try (
                PreparedStatement ps =
                        connection.prepareStatement(
                                sql
                        )
        ) {
            ps.setLong(
                    1,
                    betId
            );

            try (
                    ResultSet rs =
                            ps.executeQuery()
            ) {
                if (
                        !rs.next()
                ) {
                    throw new IllegalStateException(
                            "Missing active bet id="
                                    + betId
                    );
                }

                return new BetState(
                        rs.getLong(
                                "id"
                        ),

                        rs.getLong(
                                "wp_post_id"
                        ),

                        rs.getString(
                                "bet_type"
                        ),

                        rs.getString(
                                "settlement_status"
                        ),

                        rs.getString(
                                "settlement_source"
                        )
                );
            }
        }
    }

    private static List<LegState> findLegStates(
            Connection connection,
            long betId
    ) throws SQLException {
        String sql = """
                SELECT
                    id,
                    settlement_status,
                    settlement_source
                FROM bet_legs
                WHERE bet_id = ?
                  AND active = TRUE
                ORDER BY ordinal
                """;

        List<LegState> result =
                new ArrayList<>();

        try (
                PreparedStatement ps =
                        connection.prepareStatement(
                                sql
                        )
        ) {
            ps.setLong(
                    1,
                    betId
            );

            try (
                    ResultSet rs =
                            ps.executeQuery()
            ) {
                while (
                        rs.next()
                ) {
                    result.add(
                            new LegState(
                                    rs.getLong(
                                            "id"
                                    ),

                                    rs.getString(
                                            "settlement_status"
                                    ),

                                    rs.getString(
                                            "settlement_source"
                                    )
                            )
                    );
                }
            }
        }

        return List.copyOf(
                result
        );
    }

    private static ProjectedSettlement aggregate(
            String betType,
            List<String> statuses
    ) {
        if (
                "MULTI_UNVERIFIED".equals(
                        betType
                )
        ) {
            return new ProjectedSettlement(
                    "PENDING",
                    "NONE"
            );
        }

        boolean anyLoss =
                statuses.stream()
                        .anyMatch(
                                "L"::equals
                        );

        if (
                anyLoss
        ) {
            return new ProjectedSettlement(
                    "L",
                    "AUTO"
            );
        }

        boolean anyPending =
                statuses.stream()
                        .anyMatch(
                                "PENDING"::equals
                        );

        if (
                anyPending
        ) {
            return new ProjectedSettlement(
                    "PENDING",
                    "NONE"
            );
        }

        boolean allVoid =
                !statuses.isEmpty()
                        && statuses.stream()
                        .allMatch(
                                "V"::equals
                        );

        if (
                allVoid
        ) {
            return new ProjectedSettlement(
                    "V",
                    "AUTO"
            );
        }

        boolean allWinOrVoid =
                !statuses.isEmpty()
                        && statuses.stream()
                        .allMatch(
                                status ->
                                        "W".equals(
                                                status
                                        )
                                                || "V".equals(
                                                status
                                        )
                        );

        if (
                allWinOrVoid
        ) {
            return new ProjectedSettlement(
                    "W",
                    "AUTO"
            );
        }

        throw new IllegalStateException(
                "Unexpected settlement combination: "
                        + statuses
        );
    }

    /*
     * =========================================================
     * APPLY
     * =========================================================
     */

    private static ApplyResult applyRepair(
            Connection connection,
            List<AffectedLeg> affectedLegs,
            List<BetProjection> projections
    ) throws SQLException {
        int resetLegs =
                0;

        for (
                AffectedLeg leg :
                affectedLegs
        ) {
            resetLegs +=
                    resetLeg(
                            connection,
                            leg.legId()
                    );
        }

        int updatedBets =
                0;

        int betsToPending =
                0;

        int betsRemainLoss =
                0;

        int betsAlreadyPending =
                0;

        for (
                BetProjection projection :
                projections
        ) {
            boolean changed =
                    !projection.currentStatus()
                            .equals(
                                    projection.projectedStatus()
                            )
                            || !projection.currentSource()
                            .equals(
                                    projection.projectedSource()
                            );

            if (
                    "PENDING".equals(
                            projection.currentStatus()
                    )
                            && "PENDING".equals(
                            projection.projectedStatus()
                    )
            ) {
                betsAlreadyPending++;
            }

            if (
                    "L".equals(
                            projection.currentStatus()
                    )
                            && "L".equals(
                            projection.projectedStatus()
                    )
            ) {
                betsRemainLoss++;
            }

            if (
                    !"PENDING".equals(
                            projection.currentStatus()
                    )
                            && "PENDING".equals(
                            projection.projectedStatus()
                    )
            ) {
                betsToPending++;
            }

            if (
                    !changed
            ) {
                continue;
            }

            updatedBets +=
                    updateBet(
                            connection,
                            projection
                    );
        }

        return new ApplyResult(
                resetLegs,
                updatedBets,
                betsToPending,
                betsRemainLoss,
                betsAlreadyPending
        );
    }

    private static int resetLeg(
            Connection connection,
            long legId
    ) throws SQLException {
        String sql = """
                UPDATE bet_legs
                SET
                    settlement_status = 'PENDING',
                    settlement_source = 'NONE',
                    settled_at = NULL,
                    updated_at = NOW()
                WHERE id = ?
                  AND active = TRUE
                  AND settlement_source = 'AUTO'
                  AND settlement_status IN ('W', 'L', 'V')
                """;

        try (
                PreparedStatement ps =
                        connection.prepareStatement(
                                sql
                        )
        ) {
            ps.setLong(
                    1,
                    legId
            );

            return ps.executeUpdate();
        }
    }

    private static int updateBet(
            Connection connection,
            BetProjection projection
    ) throws SQLException {
        /*
         * MANUAL jest święty.
         *
         * Tu i tak nie spodziewamy się MANUAL,
         * ale warunek jest celowo dodatkowym bezpiecznikiem.
         */
        String sql = """
                UPDATE bets
                SET
                    settlement_status = ?,
                    settlement_source = ?,
                    settled_at =
                        CASE
                            WHEN ? = 'PENDING'
                                THEN NULL
                            ELSE NOW()
                        END,
                    updated_at = NOW()
                WHERE id = ?
                  AND active = TRUE
                  AND settlement_source <> 'MANUAL'
                """;

        try (
                PreparedStatement ps =
                        connection.prepareStatement(
                                sql
                        )
        ) {
            ps.setString(
                    1,
                    projection.projectedStatus()
            );

            ps.setString(
                    2,
                    projection.projectedSource()
            );

            ps.setString(
                    3,
                    projection.projectedStatus()
            );

            ps.setLong(
                    4,
                    projection.betId()
            );

            return ps.executeUpdate();
        }
    }

    /*
     * =========================================================
     * OUTPUT
     * =========================================================
     */

    private static void printDryRun(
            List<AffectedLeg> affectedLegs,
            List<BetProjection> projections
    ) {
        System.out.println(
                "========================================"
        );

        System.out.println(
                "INVALID FOOTBALL AUTO SETTLEMENT REPAIR"
        );

        System.out.println(
                "========================================"
        );

        System.out.println(
                "affectedLegs="
                        + affectedLegs.size()
        );

        System.out.println(
                "affectedBets="
                        + projections.size()
        );

        long halfMarketLegs =
                affectedLegs.stream()
                        .filter(
                                leg ->
                                        leg.legId()
                                                != KNOWN_BAD_RESOLUTION_LEG_ID
                        )
                        .count();

        boolean containsKnownBadResolution =
                affectedLegs.stream()
                        .anyMatch(
                                leg ->
                                        leg.legId()
                                                == KNOWN_BAD_RESOLUTION_LEG_ID
                        );

        System.out.println(
                "halfMarketLegs="
                        + halfMarketLegs
        );

        System.out.println(
                "knownBadResolutionLeg2584="
                        + containsKnownBadResolution
        );

        System.out.println();

        System.out.println(
                "BET PROJECTIONS"
        );

        long changingBets =
                projections.stream()
                        .filter(
                                BetProjection::changes
                        )
                        .count();

        long toPending =
                projections.stream()
                        .filter(
                                projection ->
                                        !"PENDING".equals(
                                                projection.currentStatus()
                                        )
                                                && "PENDING".equals(
                                                projection.projectedStatus()
                                        )
                        )
                        .count();

        long remainLoss =
                projections.stream()
                        .filter(
                                projection ->
                                        "L".equals(
                                                projection.currentStatus()
                                        )
                                                && "L".equals(
                                                projection.projectedStatus()
                                        )
                        )
                        .count();

        long alreadyPending =
                projections.stream()
                        .filter(
                                projection ->
                                        "PENDING".equals(
                                                projection.currentStatus()
                                        )
                                                && "PENDING".equals(
                                                projection.projectedStatus()
                                        )
                        )
                        .count();

        System.out.println(
                "changingBets="
                        + changingBets
        );

        System.out.println(
                "toPending="
                        + toPending
        );

        System.out.println(
                "remainLoss="
                        + remainLoss
        );

        System.out.println(
                "alreadyPending="
                        + alreadyPending
        );

        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "AFFECTED LEGS"
        );

        System.out.println(
                "========================================"
        );

        for (
                AffectedLeg leg :
                affectedLegs
        ) {
            System.out.println(
                    "legId="
                            + leg.legId()
                            + " betId="
                            + leg.betId()
                            + " wpPostId="
                            + leg.wpPostId()
                            + " "
                            + leg.status()
                            + "/"
                            + leg.source()
                            + " -> PENDING/NONE"
            );

            System.out.println(
                    "  tip="
                            + leg.tipTitle()
            );

            System.out.println(
                    "  event="
                            + leg.resolvedEventName()
                            + " fixtureId="
                            + leg.resolvedExternalEventId()
            );
        }

        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "BET CHANGES"
        );

        System.out.println(
                "========================================"
        );

        for (
                BetProjection projection :
                projections
        ) {
            if (
                    !projection.changes()
            ) {
                continue;
            }

            System.out.println(
                    "betId="
                            + projection.betId()
                            + " wpPostId="
                            + projection.wpPostId()
                            + " type="
                            + projection.betType()
                            + " "
                            + projection.currentStatus()
                            + "/"
                            + projection.currentSource()
                            + " -> "
                            + projection.projectedStatus()
                            + "/"
                            + projection.projectedSource()
            );
        }
    }

    /*
     * =========================================================
     * TYPES
     * =========================================================
     */

    private record AffectedLeg(
            long legId,
            long betId,
            long wpPostId,
            String betType,
            String tipTitle,
            String status,
            String source,
            Timestamp settledAt,
            String resolvedEventName,
            String resolvedExternalEventId
    ) {
    }

    private record LegState(
            long legId,
            String status,
            String source
    ) {
    }

    private record BetState(
            long betId,
            long wpPostId,
            String betType,
            String status,
            String source
    ) {
    }

    private record ProjectedSettlement(
            String status,
            String source
    ) {
    }

    private record BetProjection(
            long betId,
            long wpPostId,
            String betType,

            String currentStatus,
            String currentSource,

            String projectedStatus,
            String projectedSource
    ) {
        boolean changes() {
            return !currentStatus.equals(
                    projectedStatus
            )
                    || !currentSource.equals(
                    projectedSource
            );
        }
    }

    private record ApplyResult(
            int resetLegs,
            int updatedBets,
            int betsToPending,
            int betsRemainLoss,
            int betsAlreadyPending
    ) {
    }
}