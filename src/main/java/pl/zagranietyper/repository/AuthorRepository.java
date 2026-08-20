package pl.zagranietyper.repository;

import pl.zagranietyper.model.DiscoveredAuthor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class AuthorRepository {

    private final Database database;

    public AuthorRepository(
            Database database
    ) {
        this.database = database;
    }

    public void upsertDiscoveredAuthor(
            DiscoveredAuthor author,
            Instant discoveryFrom,
            Instant discoveryTo
    ) {
        String sql = """
                INSERT INTO authors (
                    wp_author_id,
                    display_name,
                    slug,
                    sample_post_id,
                    sample_post_url,
                    is_tipster_candidate,
                    last_discovered_at,
                    last_discovery_from,
                    last_discovery_to,
                    last_discovery_editorial_posts,
                    last_discovery_editorial_legs
                )
                VALUES (
                    ?, ?, ?, ?, ?,
                    TRUE,
                    NOW(),
                    ?, ?,
                    ?, ?
                )

                ON CONFLICT (wp_author_id)
                DO UPDATE SET

                    display_name =
                        CASE
                            WHEN EXCLUDED.display_name
                                 LIKE 'author-%'
                            THEN authors.display_name
                            ELSE EXCLUDED.display_name
                        END,

                    slug =
                        COALESCE(
                            EXCLUDED.slug,
                            authors.slug
                        ),

                    sample_post_id =
                        EXCLUDED.sample_post_id,

                    sample_post_url =
                        EXCLUDED.sample_post_url,

                    is_tipster_candidate =
                        TRUE,

                    last_discovered_at =
                        NOW(),

                    last_discovery_from =
                        EXCLUDED.last_discovery_from,

                    last_discovery_to =
                        EXCLUDED.last_discovery_to,

                    last_discovery_editorial_posts =
                        EXCLUDED.last_discovery_editorial_posts,

                    last_discovery_editorial_legs =
                        EXCLUDED.last_discovery_editorial_legs,

                    last_seen_at =
                        NOW()
                """;

        try (
                Connection connection =
                        database.openConnection();

                PreparedStatement ps =
                        connection.prepareStatement(sql)
        ) {
            ps.setLong(
                    1,
                    author.authorId()
            );

            ps.setString(
                    2,
                    author.displayName()
            );

            ps.setString(
                    3,
                    author.slug()
            );

            ps.setLong(
                    4,
                    author.samplePostId()
            );

            ps.setString(
                    5,
                    author.samplePostUrl()
            );

            ps.setTimestamp(
                    6,
                    Timestamp.from(
                            discoveryFrom
                    )
            );

            ps.setTimestamp(
                    7,
                    Timestamp.from(
                            discoveryTo
                    )
            );

            ps.setInt(
                    8,
                    author.editorialPosts()
            );

            ps.setInt(
                    9,
                    author.editorialLegs()
            );

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Nie udało się zapisać odkrytego autora "
                            + author.authorId(),
                    e
            );
        }
    }

    public List<TipsterCandidate> findTipsterCandidates() {

        String sql = """
                SELECT
                    wp_author_id,
                    display_name,
                    slug
                FROM authors
                WHERE is_tipster_candidate = TRUE
                ORDER BY wp_author_id
                """;

        List<TipsterCandidate> result =
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
                result.add(
                        new TipsterCandidate(
                                rs.getLong(
                                        "wp_author_id"
                                ),
                                rs.getString(
                                        "display_name"
                                ),
                                rs.getString(
                                        "slug"
                                )
                        )
                );
            }

            return List.copyOf(
                    result
            );

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Nie udało się pobrać kandydatów na typerów",
                    e
            );
        }
    }

    public record TipsterCandidate(
            long authorId,
            String displayName,
            String slug
    ) {
    }
}