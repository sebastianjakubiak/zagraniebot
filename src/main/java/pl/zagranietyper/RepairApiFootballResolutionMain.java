package pl.zagranietyper;

import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.repository.Database;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class RepairApiFootballResolutionMain {

    /*
     * Tylko przypadki, które zostały już ręcznie/audytowo
     * potwierdzone jako błędne.
     *
     * Nie próbujemy tutaj automatycznie naprawiać wszystkich
     * NOW_NONE z pełnego revalidatora.
     */
    private static final List<FixtureCorrection> CORRECTIONS =
            List.of(
                    new FixtureCorrection(
                            2584L,
                            1208132L,
                            1208124L,
                            "TIP_SUBJECT_BRIGHTON"
                    ),

                    new FixtureCorrection(
                            2791L,
                            1172935L,
                            1172930L,
                            "TIP_SUBJECT_CZECHIA"
                    ),

                    new FixtureCorrection(
                            675L,
                            1172951L,
                            1172952L,
                            "STRUCTURAL_HEADING_ISRAEL_ITALY"
                    ),

                    new FixtureCorrection(
                            756L,
                            1213780L,
                            1213782L,
                            "STRUCTURAL_HEADING_PSG_BREST"
                    ),

                    new FixtureCorrection(
                            1397L,
                            1212918L,
                            1299300L,
                            "STRUCTURAL_HEADING_LEGIA_BETIS"
                    ),

                    new FixtureCorrection(
                            1866L,
                            1158408L,
                            1213818L,
                            "STRUCTURAL_HEADING_PSG_STRASBOURG"
                    ),

                    new FixtureCorrection(
                            2007L,
                            1211680L,
                            1211667L,
                            "STRUCTURAL_HEADING_ZAGLEBIE_II_POGON"
                    ),

                    new FixtureCorrection(
                            17812L,
                            1515519L,
                            1515521L,
                            "TIP_SUBJECT_BAYER_LEVERKUSEN"
                    )
            );

    /*
     * Te rekordy nie mają poprawnego football fixture.
     *
     * Nie próbujemy zgadywać sportu/eventu w tej klasie.
     * Usuwamy wyłącznie fałszywe API_FOOTBALL resolution
     * i przywracamy je do unresolved.
     */
    private static final List<ResolutionClear> CLEARS =
            List.of(
                    new ResolutionClear(
                            663L,
                            1230295L,
                            "STRUCTURAL_HEADING_REJECTED_ARGED_MALESA"
                    ),

                    new ResolutionClear(
                            664L,
                            1230295L,
                            "STRUCTURAL_HEADING_REJECTED_ARGED_MALESA"
                    )
            );

    private RepairApiFootballResolutionMain() {
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
                List<CorrectionPreview> corrections =
                        loadCorrections(
                                connection
                        );

                List<ClearPreview> clears =
                        loadClears(
                                connection
                        );

                validate(
                        corrections,
                        clears
                );

                printDryRun(
                        corrections,
                        clears
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
                            "  pl.zagranietyper.RepairApiFootballResolutionMain --apply"
                    );

                    return;
                }

                int corrected =
                        0;

                for (
                        CorrectionPreview preview :
                        corrections
                ) {
                    corrected +=
                            applyCorrection(
                                    connection,
                                    preview
                            );
                }

                int cleared =
                        0;

                for (
                        ClearPreview preview :
                        clears
                ) {
                    cleared +=
                            applyClear(
                                    connection,
                                    preview
                            );
                }

                if (
                        corrected != CORRECTIONS.size()
                ) {
                    throw new IllegalStateException(
                            "Expected "
                                    + CORRECTIONS.size()
                                    + " corrected legs, got "
                                    + corrected
                    );
                }

                if (
                        cleared != CLEARS.size()
                ) {
                    throw new IllegalStateException(
                            "Expected "
                                    + CLEARS.size()
                                    + " cleared legs, got "
                                    + cleared
                    );
                }

                verifyAfterApply(
                        connection,
                        corrections,
                        clears
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
                        "correctedFixtures="
                                + corrected
                );

                System.out.println(
                        "clearedFalseFootballResolutions="
                                + cleared
                );

                System.out.println(
                        "totalResolutionChanges="
                                + (
                                corrected
                                        + cleared
                        )
                );

                System.out.println();
                System.out.println(
                        "settlementsChanged=0"
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
                    "API-Football resolution repair failed",
                    e
            );
        }
    }

    /*
     * =========================================================
     * LOAD
     * =========================================================
     */

    private static List<CorrectionPreview> loadCorrections(
            Connection connection
    ) throws SQLException {
        List<CorrectionPreview> result =
                new ArrayList<>();

        for (
                FixtureCorrection correction :
                CORRECTIONS
        ) {
            CurrentLeg leg =
                    findCurrentLeg(
                            connection,
                            correction.legId()
                    );

            Fixture fixture =
                    findFixture(
                            connection,
                            correction.newFixtureId()
                    );

            result.add(
                    new CorrectionPreview(
                            correction,
                            leg,
                            fixture
                    )
            );
        }

        return List.copyOf(
                result
        );
    }

    private static List<ClearPreview> loadClears(
            Connection connection
    ) throws SQLException {
        List<ClearPreview> result =
                new ArrayList<>();

        for (
                ResolutionClear clear :
                CLEARS
        ) {
            CurrentLeg leg =
                    findCurrentLeg(
                            connection,
                            clear.legId()
                    );

            result.add(
                    new ClearPreview(
                            clear,
                            leg
                    )
            );
        }

        return List.copyOf(
                result
        );
    }

    private static CurrentLeg findCurrentLeg(
            Connection connection,
            long legId
    ) throws SQLException {
        String sql = """
                SELECT
                    id,
                    bet_id,
                    tip_title,

                    resolved_sport,
                    resolved_event_name,
                    resolved_participant_a,
                    resolved_participant_b,
                    resolved_event_date,

                    resolution_source,
                    resolution_confidence,
                    resolution_evidence,

                    resolved_provider,
                    resolved_external_event_id,

                    settlement_status,
                    settlement_source

                FROM bet_legs

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
                    legId
            );

            try (
                    ResultSet rs =
                            ps.executeQuery()
            ) {
                if (
                        !rs.next()
                ) {
                    throw new IllegalStateException(
                            "Missing active leg id="
                                    + legId
                    );
                }

                Date eventDate =
                        rs.getDate(
                                "resolved_event_date"
                        );

                return new CurrentLeg(
                        rs.getLong(
                                "id"
                        ),

                        rs.getLong(
                                "bet_id"
                        ),

                        rs.getString(
                                "tip_title"
                        ),

                        rs.getString(
                                "resolved_sport"
                        ),

                        rs.getString(
                                "resolved_event_name"
                        ),

                        rs.getString(
                                "resolved_participant_a"
                        ),

                        rs.getString(
                                "resolved_participant_b"
                        ),

                        eventDate == null
                                ? null
                                : eventDate.toLocalDate(),

                        rs.getString(
                                "resolution_source"
                        ),

                        rs.getString(
                                "resolution_confidence"
                        ),

                        rs.getString(
                                "resolution_evidence"
                        ),

                        rs.getString(
                                "resolved_provider"
                        ),

                        rs.getString(
                                "resolved_external_event_id"
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

    private static Fixture findFixture(
            Connection connection,
            long fixtureId
    ) throws SQLException {
        String sql = """
                SELECT
                    fixture_id,
                    fixture_date,
                    home_team_name,
                    away_team_name

                FROM api_football_fixtures

                WHERE fixture_id = ?
                """;

        try (
                PreparedStatement ps =
                        connection.prepareStatement(
                                sql
                        )
        ) {
            ps.setLong(
                    1,
                    fixtureId
            );

            try (
                    ResultSet rs =
                            ps.executeQuery()
            ) {
                if (
                        !rs.next()
                ) {
                    throw new IllegalStateException(
                            "Missing cached API-Football fixture id="
                                    + fixtureId
                    );
                }

                return new Fixture(
                        rs.getLong(
                                "fixture_id"
                        ),

                        rs.getDate(
                                "fixture_date"
                        ).toLocalDate(),

                        rs.getString(
                                "home_team_name"
                        ),

                        rs.getString(
                                "away_team_name"
                        )
                );
            }
        }
    }

    /*
     * =========================================================
     * VALIDATION
     * =========================================================
     */

    private static void validate(
            List<CorrectionPreview> corrections,
            List<ClearPreview> clears
    ) {
        if (
                corrections.size()
                        != CORRECTIONS.size()
        ) {
            throw new IllegalStateException(
                    "Correction count mismatch"
            );
        }

        if (
                clears.size()
                        != CLEARS.size()
        ) {
            throw new IllegalStateException(
                    "Clear count mismatch"
            );
        }

        for (
                CorrectionPreview preview :
                corrections
        ) {
            validateCurrentLeg(
                    preview.leg(),
                    preview.correction()
                            .expectedOldFixtureId()
            );
        }

        for (
                ClearPreview preview :
                clears
        ) {
            validateCurrentLeg(
                    preview.leg(),
                    preview.clear()
                            .expectedOldFixtureId()
            );
        }
    }

    private static void validateCurrentLeg(
            CurrentLeg leg,
            long expectedOldFixtureId
    ) {
        if (
                !"PENDING".equals(
                        leg.settlementStatus()
                )
                        || !"NONE".equals(
                        leg.settlementSource()
                )
        ) {
            throw new IllegalStateException(
                    "Leg "
                            + leg.legId()
                            + " is not PENDING/NONE: "
                            + leg.settlementStatus()
                            + "/"
                            + leg.settlementSource()
            );
        }

        if (
                !"API_FOOTBALL".equals(
                        leg.resolutionSource()
                )
        ) {
            throw new IllegalStateException(
                    "Leg "
                            + leg.legId()
                            + " resolution_source is "
                            + leg.resolutionSource()
                            + ", expected API_FOOTBALL"
            );
        }

        if (
                !"API_FOOTBALL".equals(
                        leg.resolvedProvider()
                )
        ) {
            throw new IllegalStateException(
                    "Leg "
                            + leg.legId()
                            + " resolved_provider is "
                            + leg.resolvedProvider()
                            + ", expected API_FOOTBALL"
            );
        }

        String expected =
                Long.toString(
                        expectedOldFixtureId
                );

        if (
                !expected.equals(
                        leg.resolvedExternalEventId()
                )
        ) {
            throw new IllegalStateException(
                    "Leg "
                            + leg.legId()
                            + " old fixture mismatch. expected="
                            + expected
                            + ", actual="
                            + leg.resolvedExternalEventId()
            );
        }
    }

    /*
     * =========================================================
     * APPLY — FIXTURE CHANGE
     * =========================================================
     */

    private static int applyCorrection(
            Connection connection,
            CorrectionPreview preview
    ) throws SQLException {
        FixtureCorrection correction =
                preview.correction();

        Fixture fixture =
                preview.fixture();

        String evidence =
                "repair=REVALIDATED"
                        + "; reason="
                        + correction.reason()
                        + "; oldFixtureId="
                        + correction.expectedOldFixtureId()
                        + "; fixtureId="
                        + fixture.fixtureId()
                        + "; fixture="
                        + fixture.homeTeamName()
                        + " vs "
                        + fixture.awayTeamName()
                        + "; date="
                        + fixture.fixtureDate();

        String sql = """
                UPDATE bet_legs

                SET
                    resolved_sport = 'FOOTBALL',

                    resolved_event_name = ?,
                    resolved_participant_a = ?,
                    resolved_participant_b = ?,
                    resolved_event_date = ?,

                    resolution_source = 'API_FOOTBALL',
                    resolution_confidence = 'HIGH',
                    resolution_evidence = ?,

                    resolved_provider = 'API_FOOTBALL',
                    resolved_external_event_id = ?,

                    resolved_at = NOW(),
                    updated_at = NOW()

                WHERE id = ?
                  AND active = TRUE

                  AND settlement_status = 'PENDING'
                  AND settlement_source = 'NONE'

                  AND resolution_source = 'API_FOOTBALL'
                  AND resolved_provider = 'API_FOOTBALL'

                  AND resolved_external_event_id = ?
                """;

        try (
                PreparedStatement ps =
                        connection.prepareStatement(
                                sql
                        )
        ) {
            ps.setString(
                    1,
                    fixture.homeTeamName()
                            + " – "
                            + fixture.awayTeamName()
            );

            ps.setString(
                    2,
                    fixture.homeTeamName()
            );

            ps.setString(
                    3,
                    fixture.awayTeamName()
            );

            ps.setDate(
                    4,
                    Date.valueOf(
                            fixture.fixtureDate()
                    )
            );

            ps.setString(
                    5,
                    evidence
            );

            ps.setString(
                    6,
                    Long.toString(
                            fixture.fixtureId()
                    )
            );

            ps.setLong(
                    7,
                    correction.legId()
            );

            ps.setString(
                    8,
                    Long.toString(
                            correction.expectedOldFixtureId()
                    )
            );

            return ps.executeUpdate();
        }
    }

    /*
     * =========================================================
     * APPLY — REMOVE FALSE FOOTBALL RESOLUTION
     * =========================================================
     */

    private static int applyClear(
            Connection connection,
            ClearPreview preview
    ) throws SQLException {
        ResolutionClear clear =
                preview.clear();

        String evidence =
                "repair=REVALIDATED"
                        + "; reason="
                        + clear.reason()
                        + "; rejectedFixtureId="
                        + clear.expectedOldFixtureId();

        String sql = """
                UPDATE bet_legs

                SET
                    resolved_sport = 'UNKNOWN',

                    resolved_event_name = NULL,
                    resolved_participant_a = NULL,
                    resolved_participant_b = NULL,
                    resolved_event_date = NULL,

                    resolution_source = 'NONE',
                    resolution_confidence = NULL,
                    resolution_evidence = ?,

                    resolved_provider = NULL,
                    resolved_external_event_id = NULL,

                    resolved_at = NULL,
                    updated_at = NOW()

                WHERE id = ?
                  AND active = TRUE

                  AND settlement_status = 'PENDING'
                  AND settlement_source = 'NONE'

                  AND resolution_source = 'API_FOOTBALL'
                  AND resolved_provider = 'API_FOOTBALL'

                  AND resolved_external_event_id = ?
                """;

        try (
                PreparedStatement ps =
                        connection.prepareStatement(
                                sql
                        )
        ) {
            ps.setString(
                    1,
                    evidence
            );

            ps.setLong(
                    2,
                    clear.legId()
            );

            ps.setString(
                    3,
                    Long.toString(
                            clear.expectedOldFixtureId()
                    )
            );

            return ps.executeUpdate();
        }
    }

    /*
     * =========================================================
     * POST-APPLY VERIFICATION
     * =========================================================
     */

    private static void verifyAfterApply(
            Connection connection,
            List<CorrectionPreview> corrections,
            List<ClearPreview> clears
    ) throws SQLException {
        for (
                CorrectionPreview preview :
                corrections
        ) {
            CurrentLeg current =
                    findCurrentLeg(
                            connection,
                            preview.correction()
                                    .legId()
                    );

            String expectedFixture =
                    Long.toString(
                            preview.fixture()
                                    .fixtureId()
                    );

            if (
                    !"API_FOOTBALL".equals(
                            current.resolutionSource()
                    )
                            || !"API_FOOTBALL".equals(
                            current.resolvedProvider()
                    )
                            || !expectedFixture.equals(
                            current.resolvedExternalEventId()
                    )
            ) {
                throw new IllegalStateException(
                        "Post-apply verification failed for leg="
                                + current.legId()
                );
            }

            if (
                    !"PENDING".equals(
                            current.settlementStatus()
                    )
                            || !"NONE".equals(
                            current.settlementSource()
                    )
            ) {
                throw new IllegalStateException(
                        "Settlement unexpectedly changed for leg="
                                + current.legId()
                );
            }
        }

        for (
                ClearPreview preview :
                clears
        ) {
            CurrentLeg current =
                    findCurrentLeg(
                            connection,
                            preview.clear()
                                    .legId()
                    );

            if (
                    !"NONE".equals(
                            current.resolutionSource()
                    )
                            || current.resolvedProvider() != null
                            || current.resolvedExternalEventId() != null
            ) {
                throw new IllegalStateException(
                        "Post-clear verification failed for leg="
                                + current.legId()
                );
            }

            if (
                    !"PENDING".equals(
                            current.settlementStatus()
                    )
                            || !"NONE".equals(
                            current.settlementSource()
                    )
            ) {
                throw new IllegalStateException(
                        "Settlement unexpectedly changed for cleared leg="
                                + current.legId()
                );
            }
        }
    }

    /*
     * =========================================================
     * OUTPUT
     * =========================================================
     */

    private static void printDryRun(
            List<CorrectionPreview> corrections,
            List<ClearPreview> clears
    ) {
        System.out.println(
                "========================================"
        );

        System.out.println(
                "API-FOOTBALL RESOLUTION REPAIR"
        );

        System.out.println(
                "========================================"
        );

        System.out.println(
                "fixtureCorrections="
                        + corrections.size()
        );

        System.out.println(
                "resolutionClears="
                        + clears.size()
        );

        System.out.println(
                "totalChanges="
                        + (
                        corrections.size()
                                + clears.size()
                )
        );

        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "FIXTURE CORRECTIONS"
        );

        System.out.println(
                "========================================"
        );

        for (
                CorrectionPreview preview :
                corrections
        ) {
            FixtureCorrection correction =
                    preview.correction();

            CurrentLeg leg =
                    preview.leg();

            Fixture fixture =
                    preview.fixture();

            System.out.println();

            System.out.println(
                    "legId="
                            + correction.legId()
                            + " betId="
                            + leg.betId()
            );

            System.out.println(
                    "tip="
                            + leg.tipTitle()
            );

            System.out.println(
                    "old="
                            + correction.expectedOldFixtureId()
                            + " | "
                            + leg.resolvedEventName()
            );

            System.out.println(
                    "new="
                            + fixture.fixtureId()
                            + " | "
                            + fixture.homeTeamName()
                            + " – "
                            + fixture.awayTeamName()
                            + " | "
                            + fixture.fixtureDate()
            );

            System.out.println(
                    "reason="
                            + correction.reason()
            );

            System.out.println(
                    "settlement="
                            + leg.settlementStatus()
                            + "/"
                            + leg.settlementSource()
            );
        }

        System.out.println();
        System.out.println(
                "========================================"
        );

        System.out.println(
                "FALSE FOOTBALL RESOLUTIONS TO CLEAR"
        );

        System.out.println(
                "========================================"
        );

        for (
                ClearPreview preview :
                clears
        ) {
            ResolutionClear clear =
                    preview.clear();

            CurrentLeg leg =
                    preview.leg();

            System.out.println();

            System.out.println(
                    "legId="
                            + clear.legId()
                            + " betId="
                            + leg.betId()
            );

            System.out.println(
                    "tip="
                            + leg.tipTitle()
            );

            System.out.println(
                    "old="
                            + clear.expectedOldFixtureId()
                            + " | "
                            + leg.resolvedEventName()
            );

            System.out.println(
                    "new=UNRESOLVED"
            );

            System.out.println(
                    "reason="
                            + clear.reason()
            );

            System.out.println(
                    "settlement="
                            + leg.settlementStatus()
                            + "/"
                            + leg.settlementSource()
            );
        }
    }

    /*
     * =========================================================
     * TYPES
     * =========================================================
     */

    private record FixtureCorrection(
            long legId,
            long expectedOldFixtureId,
            long newFixtureId,
            String reason
    ) {
    }

    private record ResolutionClear(
            long legId,
            long expectedOldFixtureId,
            String reason
    ) {
    }

    private record Fixture(
            long fixtureId,
            LocalDate fixtureDate,
            String homeTeamName,
            String awayTeamName
    ) {
    }

    private record CurrentLeg(
            long legId,
            long betId,
            String tipTitle,

            String resolvedSport,
            String resolvedEventName,
            String resolvedParticipantA,
            String resolvedParticipantB,
            LocalDate resolvedEventDate,

            String resolutionSource,
            String resolutionConfidence,
            String resolutionEvidence,

            String resolvedProvider,
            String resolvedExternalEventId,

            String settlementStatus,
            String settlementSource
    ) {
    }

    private record CorrectionPreview(
            FixtureCorrection correction,
            CurrentLeg leg,
            Fixture fixture
    ) {
    }

    private record ClearPreview(
            ResolutionClear clear,
            CurrentLeg leg
    ) {
    }
}