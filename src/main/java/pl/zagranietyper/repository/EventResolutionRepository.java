package pl.zagranietyper.repository;

import pl.zagranietyper.model.BetType;
import pl.zagranietyper.model.EventResolutionCandidate;
import pl.zagranietyper.model.ResolvedEvent;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public final class EventResolutionRepository {

    private final Database database;

    public EventResolutionRepository(
            Database database
    ) {
        this.database = database;
    }

    public List<EventResolutionCandidate> findActiveLegs() {

        String sql = """
                SELECT
                    l.id AS leg_id,

                    b.id AS bet_id,
                    b.bet_type,

                    COUNT(*) OVER (
                        PARTITION BY b.id
                    ) AS bet_leg_count,

                    p.wp_post_id,
                    p.published_at,
                    p.title AS post_title,

                    l.tip_title,

                    l.source_attributes ->> 'context_heading'
                        AS context_heading,

                    l.source_attributes ->> 'context_previous_text'
                        AS context_previous_text,

                    l.event_external_id,
                    l.event_home,
                    l.event_away,
                    l.event_competition,
                    l.event_start_at

                FROM bet_legs l

                JOIN bets b
                    ON b.id = l.bet_id
                   AND b.active = TRUE

                JOIN posts p
                    ON p.wp_post_id = b.wp_post_id

                WHERE l.active = TRUE

                ORDER BY
                    p.published_at,
                    p.wp_post_id,
                    b.ordinal,
                    l.ordinal
                """;

        List<EventResolutionCandidate> result =
                new ArrayList<>();

        try (
                Connection connection =
                        database.openConnection();

                PreparedStatement ps =
                        connection.prepareStatement(sql);

                ResultSet rs =
                        ps.executeQuery()
        ) {
            while (rs.next()) {

                Timestamp published =
                        rs.getTimestamp(
                                "published_at"
                        );

                Timestamp eventStart =
                        rs.getTimestamp(
                                "event_start_at"
                        );

                result.add(
                        new EventResolutionCandidate(
                                rs.getLong(
                                        "leg_id"
                                ),

                                rs.getLong(
                                        "bet_id"
                                ),

                                BetType.valueOf(
                                        rs.getString(
                                                "bet_type"
                                        )
                                ),

                                rs.getInt(
                                        "bet_leg_count"
                                ),

                                rs.getLong(
                                        "wp_post_id"
                                ),

                                published == null
                                        ? null
                                        : published.toInstant(),

                                rs.getString(
                                        "post_title"
                                ),

                                rs.getString(
                                        "tip_title"
                                ),

                                rs.getString(
                                        "context_heading"
                                ),

                                rs.getString(
                                        "context_previous_text"
                                ),

                                rs.getString(
                                        "event_external_id"
                                ),

                                rs.getString(
                                        "event_home"
                                ),

                                rs.getString(
                                        "event_away"
                                ),

                                rs.getString(
                                        "event_competition"
                                ),

                                eventStart == null
                                        ? null
                                        : eventStart.toInstant()
                        )
                );
            }

            return List.copyOf(
                    result
            );

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Nie udało się pobrać legów do event resolution",
                    e
            );
        }
    }

    public void saveResolution(
            long legId,
            ResolvedEvent event
    ) {
        String sql = """
                UPDATE bet_legs
                SET
                    resolved_sport = ?,
                    resolved_event_name = ?,
                    resolved_participant_a = ?,
                    resolved_participant_b = ?,
                    resolved_event_date = ?,
                    resolution_source = ?,
                    resolution_confidence = ?,
                    resolution_evidence = ?,
                    resolved_at = NOW(),
                    updated_at = NOW()

                WHERE id = ?
                """;

        try (
                Connection connection =
                        database.openConnection();

                PreparedStatement ps =
                        connection.prepareStatement(sql)
        ) {
            ps.setString(
                    1,
                    event.sport().name()
            );

            ps.setString(
                    2,
                    event.eventName()
            );

            ps.setString(
                    3,
                    event.participantA()
            );

            ps.setString(
                    4,
                    event.participantB()
            );

            if (
                    event.eventDate() == null
            ) {
                ps.setDate(
                        5,
                        null
                );

            } else {
                ps.setDate(
                        5,
                        Date.valueOf(
                                event.eventDate()
                        )
                );
            }

            ps.setString(
                    6,
                    event.source().name()
            );

            ps.setString(
                    7,
                    event.confidence() == null
                            ? null
                            : event.confidence().name()
            );

            ps.setString(
                    8,
                    event.evidence()
            );

            ps.setLong(
                    9,
                    legId
            );

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Nie udało się zapisać event resolution dla leg="
                            + legId,
                    e
            );
        }
    }
}