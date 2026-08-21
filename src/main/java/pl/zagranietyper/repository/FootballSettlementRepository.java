package pl.zagranietyper.repository;

import pl.zagranietyper.model.SettlementDecision;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class FootballSettlementRepository {

    private final Database database;

    public FootballSettlementRepository(
            Database database
    ) {
        this.database =
                database;
    }

    public List<Candidate> findPendingApiFootballCandidates() {
        String sql =
                """
                SELECT
                    bl.id AS leg_id,
                    bl.bet_id,
                    b.wp_post_id,
                    b.bet_type,

                    bl.tip_title,

                    f.fixture_id,
                    f.fixture_date,
                    f.status_short,

                    f.home_team_name,
                    f.away_team_name,

                    NULLIF(
                        f.raw_json #>> '{score,fulltime,home}',
                        ''
                    )::integer AS ft_home,

                    NULLIF(
                        f.raw_json #>> '{score,fulltime,away}',
                        ''
                    )::integer AS ft_away

                FROM bet_legs bl

                JOIN bets b
                  ON b.id = bl.bet_id
                 AND b.active = TRUE

                JOIN api_football_fixtures f
                  ON f.fixture_id =
                     bl.resolved_external_event_id::bigint

                WHERE bl.active = TRUE
                  AND bl.resolved_provider = 'API_FOOTBALL'
                  AND bl.settlement_status = 'PENDING'
                  AND bl.settlement_source = 'NONE'

                ORDER BY bl.id
                """;

        List<Candidate> result =
                new ArrayList<>();

        try (
                Connection connection =
                        database.openConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        );

                ResultSet rs =
                        statement.executeQuery()
        ) {
            while (
                    rs.next()
            ) {
                result.add(
                        new Candidate(
                                rs.getLong(
                                        "leg_id"
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
                                rs.getLong(
                                        "fixture_id"
                                ),
                                rs.getObject(
                                        "fixture_date",
                                        LocalDate.class
                                ),
                                rs.getString(
                                        "status_short"
                                ),
                                rs.getString(
                                        "home_team_name"
                                ),
                                rs.getString(
                                        "away_team_name"
                                ),
                                nullableInteger(
                                        rs,
                                        "ft_home"
                                ),
                                nullableInteger(
                                        rs,
                                        "ft_away"
                                )
                        )
                );
            }

            return List.copyOf(
                    result
            );

        } catch (
                SQLException exception
        ) {
            throw new IllegalStateException(
                    "Nie udało się pobrać kandydatów do football settlement",
                    exception
            );
        }
    }

    public ApplyResult apply(
            List<SettlementUpdate> updates
    ) {
        return apply(updates, false);
    }

    /** Applies one reviewed batch atomically; any concurrently changed leg rolls back the whole batch. */
    public ApplyResult applyExact(
            List<SettlementUpdate> updates
    ) {
        return apply(updates, true);
    }

    private ApplyResult apply(
            List<SettlementUpdate> updates,
            boolean requireEveryLeg
    ) {
        if (
                updates == null
                        || updates.isEmpty()
        ) {
            return ApplyResult.empty();
        }

        try (
                Connection connection =
                        database.openConnection()
        ) {
            connection.setAutoCommit(
                    false
            );

            try {
                LegApplyResult legResult =
                        applyLegs(
                                connection,
                                updates
                        );

                if (requireEveryLeg
                        && (legResult.skippedLegs() != 0
                        || legResult.updatedLegs() != updates.size())) {
                    throw new IllegalStateException(
                            "Exact settlement batch changed concurrently: expected=" + updates.size()
                                    + ", updated=" + legResult.updatedLegs()
                                    + ", skipped=" + legResult.skippedLegs()
                    );
                }

                BetApplyResult betResult =
                        aggregateAffectedBets(
                                connection,
                                legResult.affectedBetIds()
                        );

                connection.commit();

                return new ApplyResult(
                        legResult.updatedLegs(),
                        legResult.skippedLegs(),
                        legResult.winLegs(),
                        legResult.lossLegs(),
                        legResult.voidLegs(),

                        betResult.updatedBets(),
                        betResult.winBets(),
                        betResult.lossBets(),
                        betResult.voidBets(),
                        betResult.pendingBets(),
                        betResult.multiUnverifiedBets()
                );

            } catch (
                    Exception exception
            ) {
                rollbackQuietly(
                        connection
                );

                if (
                        exception instanceof RuntimeException runtimeException
                ) {
                    throw runtimeException;
                }

                throw new IllegalStateException(
                        "Football settlement transaction failed",
                        exception
                );
            }

        } catch (
                SQLException exception
        ) {
            throw new IllegalStateException(
                    "Nie udało się otworzyć transakcji football settlement",
                    exception
            );
        }
    }

    private LegApplyResult applyLegs(
            Connection connection,
            List<SettlementUpdate> updates
    ) throws SQLException {
        String sql =
                """
                UPDATE bet_legs
                SET
                    settlement_status = ?,
                    settlement_source = 'AUTO',
                    settled_at = now(),
                    updated_at = now()

                WHERE id = ?
                  AND active = TRUE

                  /*
                   * AUTO nigdy nie rusza ręcznie
                   * rozliczonego rekordu.
                   *
                   * W praktyce wymagamy jeszcze silniej:
                   * rekord musi być nadal PENDING / NONE.
                   */
                  AND settlement_status = 'PENDING'
                  AND settlement_source = 'NONE'
                """;

        int updated =
                0;

        int skipped =
                0;

        int wins =
                0;

        int losses =
                0;

        int voids =
                0;

        Set<Long> affectedBetIds =
                new LinkedHashSet<>();

        try (
                PreparedStatement statement =
                        connection.prepareStatement(
                                sql
                        )
        ) {
            for (
                    SettlementUpdate update :
                    updates
            ) {
                if (
                        update.decision() == SettlementDecision.UNSUPPORTED
                ) {
                    continue;
                }

                statement.setString(
                        1,
                        update.decision()
                                .name()
                );

                statement.setLong(
                        2,
                        update.legId()
                );

                int changed =
                        statement.executeUpdate();

                if (
                        changed == 1
                ) {
                    updated++;

                    affectedBetIds.add(
                            update.betId()
                    );

                    switch (
                            update.decision()
                    ) {
                        case W ->
                                wins++;

                        case L ->
                                losses++;

                        case V ->
                                voids++;

                        case UNSUPPORTED -> {
                            // impossible here
                        }
                    }

                } else {
                    /*
                     * Rekord zmienił się między dry-runem
                     * a apply albo został ręcznie rozliczony.
                     *
                     * Nie traktujemy tego jako błąd.
                     * Po prostu go nie dotykamy.
                     */
                    skipped++;
                }
            }
        }

        return new LegApplyResult(
                updated,
                skipped,
                wins,
                losses,
                voids,
                Set.copyOf(
                        affectedBetIds
                )
        );
    }

    private BetApplyResult aggregateAffectedBets(
            Connection connection,
            Set<Long> affectedBetIds
    ) throws SQLException {
        if (
                affectedBetIds == null
                        || affectedBetIds.isEmpty()
        ) {
            return BetApplyResult.empty();
        }

        String selectSql =
                """
                SELECT
                    b.id AS bet_id,
                    b.bet_type,

                    COUNT(*) AS total_legs,

                    COUNT(*) FILTER (
                        WHERE bl.settlement_status = 'PENDING'
                    ) AS pending_legs,

                    COUNT(*) FILTER (
                        WHERE bl.settlement_status = 'W'
                    ) AS win_legs,

                    COUNT(*) FILTER (
                        WHERE bl.settlement_status = 'L'
                    ) AS loss_legs,

                    COUNT(*) FILTER (
                        WHERE bl.settlement_status = 'V'
                    ) AS void_legs

                FROM bets b

                JOIN bet_legs bl
                  ON bl.bet_id = b.id
                 AND bl.active = TRUE

                WHERE b.active = TRUE
                  AND b.id = ANY (?)

                GROUP BY
                    b.id,
                    b.bet_type

                ORDER BY b.id
                """;

        String updateSql =
                """
                UPDATE bets
                SET
                    settlement_status = ?,
                    settlement_source = 'AUTO',
                    settled_at = now(),
                    updated_at = now()

                WHERE id = ?
                  AND active = TRUE
                  AND settlement_source <> 'MANUAL'
                """;

        int updated =
                0;

        int wins =
                0;

        int losses =
                0;

        int voids =
                0;

        int pending =
                0;

        int multiUnverified =
                0;

        Long[] ids =
                affectedBetIds.toArray(
                        Long[]::new
                );

        try (
                PreparedStatement select =
                        connection.prepareStatement(
                                selectSql
                        );

                PreparedStatement update =
                        connection.prepareStatement(
                                updateSql
                        )
        ) {
            Array sqlArray =
                    connection.createArrayOf(
                            "bigint",
                            ids
                    );

            try {
                select.setArray(
                        1,
                        sqlArray
                );

                try (
                        ResultSet rs =
                                select.executeQuery()
                ) {
                    while (
                            rs.next()
                    ) {
                        long betId =
                                rs.getLong(
                                        "bet_id"
                                );

                        String betType =
                                rs.getString(
                                        "bet_type"
                                );

                        int total =
                                rs.getInt(
                                        "total_legs"
                                );

                        int pendingCount =
                                rs.getInt(
                                        "pending_legs"
                                );

                        int winCount =
                                rs.getInt(
                                        "win_legs"
                                );

                        int lossCount =
                                rs.getInt(
                                        "loss_legs"
                                );

                        int voidCount =
                                rs.getInt(
                                        "void_legs"
                                );

                        /*
                         * Grupowanie MULTI_UNVERIFIED z definicji
                         * nie jest wystarczająco pewne, żeby
                         * automatycznie ustalać wynik całego betu.
                         *
                         * Legi mogą być rozliczone, bet zostaje PENDING.
                         */
                        if (
                                "MULTI_UNVERIFIED".equals(
                                        betType
                                )
                        ) {
                            multiUnverified++;

                            continue;
                        }

                        SettlementDecision decision =
                                aggregateBetDecision(
                                        total,
                                        pendingCount,
                                        winCount,
                                        lossCount,
                                        voidCount
                                );

                        if (
                                decision == SettlementDecision.UNSUPPORTED
                        ) {
                            pending++;

                            continue;
                        }

                        update.setString(
                                1,
                                decision.name()
                        );

                        update.setLong(
                                2,
                                betId
                        );

                        int changed =
                                update.executeUpdate();

                        if (
                                changed != 1
                        ) {
                            /*
                             * Najbardziej prawdopodobny powód:
                             * bet jest MANUAL.
                             *
                             * Nie ruszamy go.
                             */
                            continue;
                        }

                        updated++;

                        switch (
                                decision
                        ) {
                            case W ->
                                    wins++;

                            case L ->
                                    losses++;

                            case V ->
                                    voids++;

                            case UNSUPPORTED ->
                                    pending++;
                        }
                    }
                }

            } finally {
                sqlArray.free();
            }
        }

        return new BetApplyResult(
                updated,
                wins,
                losses,
                voids,
                pending,
                multiUnverified
        );
    }

    private static SettlementDecision aggregateBetDecision(
            int total,
            int pending,
            int wins,
            int losses,
            int voids
    ) {
        if (
                total <= 0
        ) {
            return SettlementDecision.UNSUPPORTED;
        }

        /*
         * W akumulatorze jedna przegrana
         * natychmiast przegrywa cały kupon,
         * niezależnie od pozostałych pendingów.
         */
        if (
                losses > 0
        ) {
            return SettlementDecision.L;
        }

        /*
         * Nie ma przegranej, ale coś wciąż
         * nie jest rozliczone.
         */
        if (
                pending > 0
        ) {
            return SettlementDecision.UNSUPPORTED;
        }

        /*
         * Wszystkie legi void.
         */
        if (
                voids == total
        ) {
            return SettlementDecision.V;
        }

        /*
         * W + V => cały bet jest wygrany.
         *
         * Efektywny kurs po voidzie policzymy
         * później na etapie ROI.
         */
        if (
                wins > 0
                        && wins + voids == total
        ) {
            return SettlementDecision.W;
        }

        return SettlementDecision.UNSUPPORTED;
    }

    private static Integer nullableInteger(
            ResultSet rs,
            String column
    ) throws SQLException {
        int value =
                rs.getInt(
                        column
                );

        return rs.wasNull()
                ? null
                : value;
    }

    private static void rollbackQuietly(
            Connection connection
    ) {
        try {
            connection.rollback();

        } catch (
                SQLException ignored
        ) {
            // original exception is more important
        }
    }

    public record Candidate(
            long legId,
            long betId,
            long wpPostId,
            String betType,
            String tipTitle,
            long fixtureId,
            LocalDate fixtureDate,
            String statusShort,
            String homeTeam,
            String awayTeam,
            Integer fulltimeHome,
            Integer fulltimeAway
    ) {
    }

    public record SettlementUpdate(
            long legId,
            long betId,
            SettlementDecision decision
    ) {
    }

    public record ApplyResult(
            int updatedLegs,
            int skippedLegs,
            int winLegs,
            int lossLegs,
            int voidLegs,

            int updatedBets,
            int winBets,
            int lossBets,
            int voidBets,
            int pendingBets,
            int multiUnverifiedBets
    ) {

        public static ApplyResult empty() {
            return new ApplyResult(
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }
    }

    private record LegApplyResult(
            int updatedLegs,
            int skippedLegs,
            int winLegs,
            int lossLegs,
            int voidLegs,
            Set<Long> affectedBetIds
    ) {
    }

    private record BetApplyResult(
            int updatedBets,
            int winBets,
            int lossBets,
            int voidBets,
            int pendingBets,
            int multiUnverifiedBets
    ) {

        static BetApplyResult empty() {
            return new BetApplyResult(
                    0,
                    0,
                    0,
                    0,
                    0,
                    0
            );
        }
    }
}
