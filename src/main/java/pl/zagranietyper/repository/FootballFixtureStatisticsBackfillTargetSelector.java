package pl.zagranietyper.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Read-only selection of fixtures whose pending legs need full-match canonical statistics. */
public final class FootballFixtureStatisticsBackfillTargetSelector {
    private static final Pattern SUPPORTED_STATISTIC = Pattern.compile(
            "rożn|rozn|korner|corner|faul|strzał|strzal|shots|spalon|offside|"
                    + "interwenc|obron bramkar|goalkeeper save|kart|upomnien");
    private static final Pattern PERIOD_SPECIFIC = Pattern.compile(
            "połow|polow|do przerwy|half");
    private static final Pattern PLAYER_SPECIFIC = Pattern.compile(
            "(^|[^\\p{L}\\p{N}])[\\p{L}]{1,3}\\. [\\p{L}-]+"
                    + "|alejandro garnacho|calvert-lewin|casemiro|danny welbeck"
                    + "|gabriel magal|granit xhaka|jakub kami|jakub kiwior|jan bednarek"
                    + "|julian alvarez|kevin de bruyne|kylian mbapp|mbappe|mbeumo"
                    + "|nico williams|nicolas jackson|patson daka|pavlidis|raphinha"
                    + "|robert lewandowski|santiago gimenez|virgil van dijk|carlos vicente"
                    + "|federico chiesa|palmer|lewy odda|bramkarza|hermansen.*interwenc");

    private final Database database;

    public FootballFixtureStatisticsBackfillTargetSelector(Database database) {
        this.database = database;
    }

    public List<Long> findTargets() {
        String sql = """
                SELECT bl.id, f.fixture_id, bl.tip_title
                FROM bet_legs bl
                JOIN bets b ON b.id = bl.bet_id AND b.active
                JOIN api_football_fixtures f
                  ON f.fixture_id = bl.resolved_external_event_id::bigint
                WHERE bl.active
                  AND bl.resolved_provider = 'API_FOOTBALL'
                  AND bl.settlement_status = 'PENDING'
                  AND bl.settlement_source = 'NONE'
                  AND f.status_short IN ('FT', 'AET', 'PEN')
                ORDER BY f.fixture_id, bl.id
                """;
        try (Connection connection = database.openConnection()) {
            connection.setReadOnly(true);
            try (PreparedStatement ps = connection.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                TreeSet<Long> targets = new TreeSet<>();
                while (rs.next()) {
                    CandidateLeg leg = new CandidateLeg(
                            rs.getLong("fixture_id"), rs.getString("tip_title"));
                    if (requiresSupportedFullMatchStatistics(leg.tipTitle())) {
                        targets.add(leg.fixtureId());
                    }
                }
                return List.copyOf(targets);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not select fixture-statistics backfill targets", e);
        }
    }

    public static List<Long> selectTargets(Collection<CandidateLeg> legs) {
        TreeSet<Long> targets = new TreeSet<>();
        for (CandidateLeg leg : legs) {
            if (requiresSupportedFullMatchStatistics(leg.tipTitle())) {
                targets.add(leg.fixtureId());
            }
        }
        return List.copyOf(targets);
    }

    static boolean requiresSupportedFullMatchStatistics(String title) {
        if (title == null || title.isBlank()) return false;
        String normalized = title.toLowerCase(Locale.ROOT);
        return SUPPORTED_STATISTIC.matcher(normalized).find()
                && !PERIOD_SPECIFIC.matcher(normalized).find()
                && !PLAYER_SPECIFIC.matcher(normalized).find();
    }

    public record CandidateLeg(long fixtureId, String tipTitle) {}
}
