package pl.zagranietyper;

import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.repository.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FootballParticipantSanityMain {

    private static final Pattern DIACRITIC_MARKS =
            Pattern.compile("\\p{M}+");

    private static final Pattern NON_ALPHANUMERIC =
            Pattern.compile("[^\\p{L}\\p{N}]+");

    private static final Pattern MULTIPLE_SPACES =
            Pattern.compile("\\s+");

    private static final Pattern WIN_WORD =
            Pattern.compile(
                    "\\bwygra(?:ja|l|la|lo)?\\b"
            );

    private static final Pattern WIN_NOUN =
            Pattern.compile(
                    "^wygrana\\s+(.+)$"
            );

    private static final Pattern VICTORY_NOUN =
            Pattern.compile(
                    "^zwyciestwo\\s+(.+)$"
            );

    private static final Pattern WILL_WIN =
            Pattern.compile(
                    "\\bzwyciezy\\b"
            );

    private static final List<String> PREFIXES =
            List.of(
                    "mycombi",
                    "betbuilder",
                    "bet builder",
                    "bet buildera",
                    "ako",
                    "single",
                    "typ",
                    "zaklad",
                    "zaklad specjalny"
            );

    private static final Set<String> SUBJECT_NOISE =
            Set.of(
                    "reprezentacja",
                    "reprezentacji",
                    "reprezentacje",
                    "druzyna",
                    "druzyny",
                    "ekipa",
                    "ekipy",
                    "zespol",
                    "zespolu",
                    "klub",
                    "klubu",
                    "k"
            );

    private static final Set<String> TEAM_NOISE =
            Set.of(
                    "fc",
                    "cf",
                    "fk",
                    "afc",
                    "sc",
                    "ks",
                    "mks",
                    "rks",
                    "lks",
                    "ssa",
                    "sv",
                    "cp",
                    "ac",
                    "as",
                    "club"
            );

    private static final Map<String, List<String>> TEAM_ALIASES =
            createTeamAliases();

    private FootballParticipantSanityMain() {
    }

    public static void main(
            String[] args
    ) {
        AppConfig config =
                AppConfig.fromEnvironment();

        Database database =
                new Database(
                        config
                );

        List<Row> rows =
                findRows(
                        database
                );

        Map<Classification, Integer> counts =
                new EnumMap<>(
                        Classification.class
                );

        for (
                Classification classification :
                Classification.values()
        ) {
            counts.put(
                    classification,
                    0
            );
        }

        List<Result> results =
                new ArrayList<>();

        for (
                Row row :
                rows
        ) {
            Result result =
                    analyze(
                            row
                    );

            results.add(
                    result
            );

            counts.compute(
                    result.classification(),
                    (
                            key,
                            value
                    ) -> value == null
                            ? 1
                            : value + 1
            );
        }

        System.out.println(
                "========================================"
        );

        System.out.println(
                "FOOTBALL PARTICIPANT SANITY"
        );

        System.out.println(
                "MATCH_WINNER_OR_COMPOSITE"
        );

        System.out.println(
                "DRY RUN — NO DATABASE WRITES"
        );

        System.out.println(
                "========================================"
        );

        System.out.println(
                "rows="
                        + rows.size()
        );

        for (
                Classification classification :
                Classification.values()
        ) {
            System.out.println(
                    classification.name()
                            + "="
                            + counts.get(
                            classification
                    )
            );
        }

        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "SUBJECT MISMATCHES"
        );

        System.out.println(
                "========================================"
        );

        int mismatchOrdinal =
                0;

        for (
                Result result :
                results
        ) {
            if (
                    result.classification()
                            != Classification.SUBJECT_MISMATCH
            ) {
                continue;
            }

            mismatchOrdinal++;

            printResult(
                    mismatchOrdinal,
                    result
            );
        }

        if (
                mismatchOrdinal == 0
        ) {
            System.out.println(
                    "NONE"
            );
        }

        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "AMBIGUOUS"
        );

        System.out.println(
                "========================================"
        );

        int ambiguousOrdinal =
                0;

        for (
                Result result :
                results
        ) {
            if (
                    result.classification()
                            != Classification.AMBIGUOUS
            ) {
                continue;
            }

            ambiguousOrdinal++;

            printResult(
                    ambiguousOrdinal,
                    result
            );
        }

        if (
                ambiguousOrdinal == 0
        ) {
            System.out.println(
                    "NONE"
            );
        }

        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "NO EXPLICIT SUBJECT EXAMPLES"
        );

        System.out.println(
                "========================================"
        );

        int noSubjectPrinted =
                0;

        for (
                Result result :
                results
        ) {
            if (
                    result.classification()
                            != Classification.NO_EXPLICIT_SUBJECT
            ) {
                continue;
            }

            if (
                    noSubjectPrinted >= 20
            ) {
                break;
            }

            noSubjectPrinted++;

            printResult(
                    noSubjectPrinted,
                    result
            );
        }

        if (
                noSubjectPrinted == 0
        ) {
            System.out.println(
                    "NONE"
            );
        }

        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "MATCH EXAMPLES"
        );

        System.out.println(
                "========================================"
        );

        int matchPrinted =
                0;

        for (
                Result result :
                results
        ) {
            if (
                    result.classification()
                            != Classification.SUBJECT_MATCH_HOME
                            && result.classification()
                            != Classification.SUBJECT_MATCH_AWAY
            ) {
                continue;
            }

            if (
                    matchPrinted >= 30
            ) {
                break;
            }

            matchPrinted++;

            printResult(
                    matchPrinted,
                    result
            );
        }

        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "DRY RUN ONLY — DATABASE NOT MODIFIED"
        );

        System.out.println(
                "========================================"
        );
    }

    /*
     * =========================================================
     * QUERY
     * =========================================================
     *
     * To jest dokładnie ta sama klasyfikacja, której użyliśmy
     * do wyliczenia:
     *
     * MATCH_WINNER_OR_COMPOSITE = 289
     *
     * Dzięki temu audit nie działa na jakimś przybliżonym
     * podzbiorze, tylko dokładnie na tej rodzinie.
     */

    private static List<Row> findRows(
            Database database
    ) {
        String sql = """
                WITH base AS (
                    SELECT
                        bl.id AS leg_id,
                        b.id AS bet_id,
                        b.wp_post_id,
                        bl.tip_title,

                        f.fixture_id,
                        f.home_team_name,
                        f.away_team_name

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
                      AND f.status_short IN ('FT', 'AET', 'PEN')

                      AND lower(bl.tip_title) NOT LIKE '%rożn%'
                      AND lower(bl.tip_title) NOT LIKE '%rozn%'
                      AND lower(bl.tip_title) NOT LIKE '%corner%'

                      AND lower(bl.tip_title) NOT LIKE '%kart%'
                      AND lower(bl.tip_title) NOT LIKE '%upomnien%'

                      AND lower(bl.tip_title) NOT LIKE '%połow%'
                      AND lower(bl.tip_title) NOT LIKE '%polow%'
                      AND lower(bl.tip_title) NOT LIKE '%do przerwy%'
                      AND lower(bl.tip_title) NOT LIKE '%half%'

                      AND lower(bl.tip_title) NOT LIKE '%pierwszy gol%'
                      AND lower(bl.tip_title) NOT LIKE '%1. gol%'

                      AND lower(bl.tip_title) NOT LIKE '%strzał%'
                      AND lower(bl.tip_title) NOT LIKE '%strzal%'
                      AND lower(bl.tip_title) NOT LIKE '%interwenc%'
                      AND lower(bl.tip_title) NOT LIKE '%asyst%'

                      AND lower(bl.tip_title) NOT LIKE '%awans%'
                      AND lower(bl.tip_title) NOT LIKE '%trofeum%'
                      AND lower(bl.tip_title) NOT LIKE '%finał%'
                      AND lower(bl.tip_title) NOT LIKE '%final%'
                ),

                classified AS (
                    SELECT
                        *,

                        CASE
                            WHEN lower(tip_title)
                                     LIKE '%zakład bez remisu%'
                              OR lower(tip_title)
                                     LIKE '%zaklad bez remisu%'
                              OR lower(tip_title)
                                     LIKE '%remis = zwrot%'
                              OR lower(tip_title)
                                     LIKE '%remis=zwrot%'
                              OR lower(tip_title)
                                     LIKE '%draw no bet%'
                                THEN 'DRAW_NO_BET'

                            WHEN lower(tip_title)
                                     LIKE '%przedział goli%'
                              OR lower(tip_title)
                                     LIKE '%przedzial goli%'
                              OR lower(tip_title)
                                     LIKE '%suma goli:%'
                                THEN 'GOAL_RANGE'

                            WHEN lower(tip_title)
                                     LIKE '%co najmniej%'
                              AND (
                                     lower(tip_title) LIKE '%gol%'
                                  OR lower(tip_title) LIKE '%bram%'
                              )
                                THEN 'MIN_GOALS'

                            WHEN lower(tip_title)
                                     LIKE '%różnicą%'
                              OR lower(tip_title)
                                     LIKE '%roznica%'
                                THEN 'WIN_MARGIN'

                            WHEN lower(tip_title)
                                     LIKE '%handicap%'
                              OR tip_title
                                     ~ '(^|[[:space:]])[+-][0-9]+([.,][0-9]+)?'
                                THEN 'HANDICAP'

                            WHEN lower(tip_title)
                                     LIKE '%obie drużyny%'
                              OR lower(tip_title)
                                     LIKE '%obie druzyny%'
                              OR lower(tip_title)
                                     LIKE '%btts%'
                              OR lower(tip_title)
                                     LIKE '%gole z obu stron%'
                              OR lower(tip_title)
                                     LIKE '%bramki z obu stron%'
                                THEN 'BTTS_VARIANT'

                            WHEN lower(tip_title)
                                     LIKE '%nie przegra%'
                              OR lower(tip_title)
                                     LIKE '%wygra lub zremisuje%'
                              OR lower(tip_title)
                                     LIKE '%zremisuje lub wygra%'
                              OR lower(tip_title)
                                     LIKE '%lub remis%'
                              OR lower(tip_title)
                                     LIKE '%remis lub%'
                                THEN 'DOUBLE_CHANCE'

                            WHEN lower(tip_title)
                                     LIKE '%remis%'
                                THEN 'DRAW'

                            WHEN lower(tip_title)
                                     LIKE '%wygra%'
                              OR lower(tip_title)
                                     LIKE '%zwycięstw%'
                              OR lower(tip_title)
                                     LIKE '%zwyciezy%'
                                THEN 'MATCH_WINNER_OR_COMPOSITE'

                            WHEN lower(tip_title)
                                     LIKE '%powyżej%'
                              OR lower(tip_title)
                                     LIKE '%powyzej%'
                              OR lower(tip_title)
                                     LIKE '%poniżej%'
                              OR lower(tip_title)
                                     LIKE '%ponizej%'
                              OR lower(tip_title)
                                     LIKE '%over%'
                              OR lower(tip_title)
                                     LIKE '%under%'
                              OR lower(tip_title)
                                     LIKE '%więcej niż%'
                              OR lower(tip_title)
                                     LIKE '%wiecej niz%'
                              OR lower(tip_title)
                                     LIKE '%mniej niż%'
                              OR lower(tip_title)
                                     LIKE '%mniej niz%'
                              OR lower(tip_title)
                                     LIKE '%ponad %'
                                THEN 'GOAL_TOTAL_OTHER'

                            ELSE 'UNCLASSIFIED'
                        END AS family

                    FROM base
                )

                SELECT
                    leg_id,
                    bet_id,
                    wp_post_id,
                    tip_title,
                    fixture_id,
                    home_team_name,
                    away_team_name

                FROM classified

                WHERE family = 'MATCH_WINNER_OR_COMPOSITE'

                ORDER BY leg_id
                """;

        List<Row> result =
                new ArrayList<>();

        try (
                Connection connection =
                        database.openConnection();

                PreparedStatement ps =
                        connection.prepareStatement(
                                sql
                        );

                ResultSet rs =
                        ps.executeQuery()
        ) {
            while (
                    rs.next()
            ) {
                result.add(
                        new Row(
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
                                        "tip_title"
                                ),

                                rs.getLong(
                                        "fixture_id"
                                ),

                                rs.getString(
                                        "home_team_name"
                                ),

                                rs.getString(
                                        "away_team_name"
                                )
                        )
                );
            }

            return List.copyOf(
                    result
            );

        } catch (
                SQLException e
        ) {
            throw new IllegalStateException(
                    "Nie udało się pobrać MATCH_WINNER_OR_COMPOSITE",
                    e
            );
        }
    }

    /*
     * =========================================================
     * ANALYSIS
     * =========================================================
     */

    private static Result analyze(
            Row row
    ) {
        String text =
                stripPrefixes(
                        normalize(
                                row.tipTitle()
                        )
                );

        if (
                text.isBlank()
        ) {
            return result(
                    row,
                    Classification.NO_EXPLICIT_SUBJECT,
                    null,
                    "empty normalized tip"
            );
        }

        /*
         * Specjalne jednoznaczne formy niezależne od nazw.
         */
        if (
                containsAny(
                        text,
                        "wygraja gospodarze",
                        "gospodarze wygraja",
                        "wygra gospodarz",
                        "gospodarz wygra"
                )
        ) {
            return result(
                    row,
                    Classification.SUBJECT_MATCH_HOME,
                    "gospodarze",
                    "explicit home side"
            );
        }

        if (
                containsAny(
                        text,
                        "wygraja goscie",
                        "goscie wygraja",
                        "wygra gosc",
                        "gosc wygra"
                )
        ) {
            return result(
                    row,
                    Classification.SUBJECT_MATCH_AWAY,
                    "goscie",
                    "explicit away side"
            );
        }

        /*
         * Np.:
         *
         * Zwycięstwo Liverpoolu lub Chelsea
         * Napoli wygra lub Milan wygra
         *
         * Tu jawnie występują dwie strony. To nie jest mismatch,
         * ale też nie mamy jednego subjectu do sanity checku.
         */
        if (
                isTwoSubjectAlternative(
                        text
                )
        ) {
            return result(
                    row,
                    Classification.AMBIGUOUS,
                    null,
                    "two-team alternative"
            );
        }

        String subject =
                extractSubject(
                        text
                );

        if (
                subject == null
                        || subject.isBlank()
        ) {
            return result(
                    row,
                    Classification.NO_EXPLICIT_SUBJECT,
                    null,
                    "winner cue found but subject not extracted"
            );
        }

        String cleanedSubject =
                cleanSubject(
                        subject
                );

        if (
                cleanedSubject.isBlank()
        ) {
            return result(
                    row,
                    Classification.NO_EXPLICIT_SUBJECT,
                    subject,
                    "subject became empty after cleanup"
            );
        }

        boolean home =
                subjectMatchesTeam(
                        cleanedSubject,
                        row.homeTeamName()
                );

        boolean away =
                subjectMatchesTeam(
                        cleanedSubject,
                        row.awayTeamName()
                );

        if (
                home
                        && !away
        ) {
            return result(
                    row,
                    Classification.SUBJECT_MATCH_HOME,
                    cleanedSubject,
                    "subject matches HOME"
            );
        }

        if (
                away
                        && !home
        ) {
            return result(
                    row,
                    Classification.SUBJECT_MATCH_AWAY,
                    cleanedSubject,
                    "subject matches AWAY"
            );
        }

        if (
                home
                        && away
        ) {
            return result(
                    row,
                    Classification.AMBIGUOUS,
                    cleanedSubject,
                    "subject matches both fixture participants"
            );
        }

        return result(
                row,
                Classification.SUBJECT_MISMATCH,
                cleanedSubject,
                "explicit subject matches neither fixture participant"
        );
    }

    /*
     * =========================================================
     * SUBJECT EXTRACTION
     * =========================================================
     */

    private static String extractSubject(
            String text
    ) {
        /*
         * Wygra PSG
         * Wygra Termalica
         * Wygra reprezentacja Holandii
         */
        Matcher win =
                WIN_WORD.matcher(
                        text
                );

        if (
                win.find()
        ) {
            String before =
                    text.substring(
                                    0,
                                    win.start()
                            )
                            .trim();

            String after =
                    text.substring(
                                    win.end()
                            )
                            .trim();

            if (
                    !before.isBlank()
            ) {
                /*
                 * Mikael Ishak strzeli gola i Lech wygra mecz
                 *
                 * interesuje nas tylko ostatni człon przed "wygra".
                 */
                before =
                        tailAfterConnector(
                                before
                        );

                before =
                        trimSubject(
                                before
                        );

                if (
                        !before.isBlank()
                ) {
                    return before;
                }
            }

            if (
                    !after.isBlank()
            ) {
                after =
                        trimSubject(
                                after
                        );

                if (
                        !after.isBlank()
                ) {
                    return after;
                }
            }
        }

        /*
         * Wygrana Japonii
         */
        Matcher winNoun =
                WIN_NOUN.matcher(
                        text
                );

        if (
                winNoun.matches()
        ) {
            return trimSubject(
                    winNoun.group(
                            1
                    )
            );
        }

        /*
         * Zwycięstwo Liverpoolu z Bournemouth
         * Zwycięstwo Hiszpanii
         */
        Matcher victoryNoun =
                VICTORY_NOUN.matcher(
                        text
                );

        if (
                victoryNoun.matches()
        ) {
            return trimSubject(
                    victoryNoun.group(
                            1
                    )
            );
        }

        /*
         * X zwycięży
         * Zwycięży X
         */
        Matcher willWin =
                WILL_WIN.matcher(
                        text
                );

        if (
                willWin.find()
        ) {
            String before =
                    text.substring(
                                    0,
                                    willWin.start()
                            )
                            .trim();

            String after =
                    text.substring(
                                    willWin.end()
                            )
                            .trim();

            if (
                    !before.isBlank()
            ) {
                return trimSubject(
                        tailAfterConnector(
                                before
                        )
                );
            }

            if (
                    !after.isBlank()
            ) {
                return trimSubject(
                        after
                );
            }
        }

        return null;
    }

    private static String trimSubject(
            String raw
    ) {
        if (
                raw == null
                        || raw.isBlank()
        ) {
            return "";
        }

        String value =
                normalize(
                        raw
                );

        /*
         * Najpierw odcinamy to, co jest już kolejnym warunkiem
         * rynku albo nazwą przeciwnika.
         */
        value =
                cutAtFirst(
                        value,
                        " i ",
                        " oraz ",
                        " + ",
                        " z ",
                        " przeciwko ",
                        " vs ",
                        " versus ",
                        " powyzej ",
                        " ponizej ",
                        " over ",
                        " under ",
                        " wiecej niz ",
                        " mniej niz ",
                        " strzeli ",
                        " zdobedzie ",
                        " handicap ",
                        " roznica ",
                        " roznica min ",
                        " minimum ",
                        " min ",
                        " do zera ",
                        " bez straty gola "
                );

        value =
                removeTrailingMarketWords(
                        value
                );

        return value.trim();
    }

    private static String tailAfterConnector(
            String value
    ) {
        if (
                value == null
                        || value.isBlank()
        ) {
            return "";
        }

        String result =
                value;

        int lastAnd =
                result.lastIndexOf(
                        " i "
                );

        int lastPlus =
                result.lastIndexOf(
                        " + "
                );

        int lastCommaLike =
                result.lastIndexOf(
                        " oraz "
                );

        int position =
                Math.max(
                        lastAnd,
                        Math.max(
                                lastPlus,
                                lastCommaLike
                        )
                );

        if (
                position >= 0
        ) {
            int skip =
                    result.startsWith(
                            " oraz ",
                            position
                    )
                            ? " oraz ".length()
                            : 3;

            result =
                    result.substring(
                                    position + skip
                            )
                            .trim();
        }

        return result;
    }

    private static String removeTrailingMarketWords(
            String value
    ) {
        String result =
                value;

        boolean changed;

        do {
            changed =
                    false;

            for (
                    String suffix :
                    List.of(
                            " mecz",
                            " meczu",
                            " spotkanie",
                            " spotkania"
                    )
            ) {
                if (
                        result.endsWith(
                                suffix
                        )
                ) {
                    result =
                            result.substring(
                                            0,
                                            result.length()
                                                    - suffix.length()
                                    )
                                    .trim();

                    changed =
                            true;
                }
            }

        } while (
                changed
        );

        return result;
    }

    private static String cleanSubject(
            String raw
    ) {
        String normalized =
                normalize(
                        raw
                );

        List<String> result =
                new ArrayList<>();

        for (
                String token :
                normalized.split(
                        "\\s+"
                )
        ) {
            if (
                    token.isBlank()
                            || SUBJECT_NOISE.contains(
                            token
                    )
            ) {
                continue;
            }

            result.add(
                    token
            );
        }

        return String.join(
                " ",
                result
        );
    }

    private static boolean isTwoSubjectAlternative(
            String text
    ) {
        if (
                !text.contains(
                        " lub "
                )
        ) {
            return false;
        }

        if (
                text.startsWith(
                        "zwyciestwo "
                )
                        || text.startsWith(
                        "wygrana "
                )
        ) {
            return true;
        }

        Matcher matcher =
                WIN_WORD.matcher(
                        text
                );

        int wins =
                0;

        while (
                matcher.find()
        ) {
            wins++;

            if (
                    wins >= 2
            ) {
                return true;
            }
        }

        return false;
    }

    /*
     * =========================================================
     * TEAM MATCHING
     * =========================================================
     */

    private static boolean subjectMatchesTeam(
            String subject,
            String apiTeam
    ) {
        List<String> subjectTokens =
                meaningfulTokens(
                        subject
                );

        if (
                subjectTokens.isEmpty()
        ) {
            return false;
        }

        for (
                String variant :
                teamVariants(
                        apiTeam
                )
        ) {
            List<String> teamTokens =
                    meaningfulTokens(
                            variant
                    );

            if (
                    teamTokens.isEmpty()
            ) {
                continue;
            }

            if (
                    allSubjectTokensMatch(
                            subjectTokens,
                            teamTokens
                    )
            ) {
                return true;
            }
        }

        return false;
    }

    private static boolean allSubjectTokensMatch(
            List<String> subjectTokens,
            List<String> teamTokens
    ) {
        List<Integer> used =
                new ArrayList<>();

        for (
                String subjectToken :
                subjectTokens
        ) {
            boolean matched =
                    false;

            for (
                    int i = 0;
                    i < teamTokens.size();
                    i++
            ) {
                if (
                        used.contains(
                                i
                        )
                ) {
                    continue;
                }

                if (
                        tokenEquivalent(
                                teamTokens.get(
                                        i
                                ),
                                subjectToken
                        )
                ) {
                    used.add(
                            i
                    );

                    matched =
                            true;

                    break;
                }
            }

            if (
                    !matched
            ) {
                return false;
            }
        }

        return true;
    }

    private static boolean tokenEquivalent(
            String teamToken,
            String subjectToken
    ) {
        if (
                teamToken == null
                        || subjectToken == null
        ) {
            return false;
        }

        if (
                teamToken.equals(
                        subjectToken
                )
        ) {
            return true;
        }

        if (
                teamToken.length() < 4
                        || subjectToken.length() < 4
        ) {
            return false;
        }

        /*
         * Liverpool -> Liverpoolu
         * Pogon     -> Pogoni
         * Widzew    -> Widzewa
         */
        if (
                subjectToken.startsWith(
                        teamToken
                )
                        && subjectToken.length()
                        <= teamToken.length() + 7
        ) {
            return true;
        }

        /*
         * W drugą stronę, dla skrótów/odmian.
         */
        if (
                teamToken.startsWith(
                        subjectToken
                )
                        && teamToken.length()
                        <= subjectToken.length() + 7
        ) {
            return true;
        }

        /*
         * Hiszpania -> Hiszpanii
         * Korona    -> Korony
         * Cracovia  -> Cracovii
         */
        if (
                teamToken.endsWith(
                        "a"
                )
                        && teamToken.length() >= 5
        ) {
            String stem =
                    teamToken.substring(
                            0,
                            teamToken.length() - 1
                    );

            if (
                    subjectToken.startsWith(
                            stem
                    )
                            && subjectToken.length()
                            <= teamToken.length() + 6
            ) {
                return true;
            }
        }

        if (
                subjectToken.endsWith(
                        "a"
                )
                        && subjectToken.length() >= 5
        ) {
            String stem =
                    subjectToken.substring(
                            0,
                            subjectToken.length() - 1
                    );

            return teamToken.startsWith(
                    stem
            );
        }

        return false;
    }

    private static List<String> meaningfulTokens(
            String value
    ) {
        String normalized =
                normalize(
                        value
                );

        if (
                normalized.isBlank()
        ) {
            return List.of();
        }

        List<String> result =
                new ArrayList<>();

        for (
                String token :
                normalized.split(
                        "\\s+"
                )
        ) {
            if (
                    token.isBlank()
                            || TEAM_NOISE.contains(
                            token
                    )
                            || token.chars()
                            .allMatch(
                                    Character::isDigit
                            )
            ) {
                continue;
            }

            result.add(
                    token
            );
        }

        return List.copyOf(
                result
        );
    }

    private static List<String> teamVariants(
            String apiTeam
    ) {
        String normalized =
                normalize(
                        apiTeam
                );

        List<String> result =
                new ArrayList<>();

        if (
                !normalized.isBlank()
        ) {
            result.add(
                    normalized
            );
        }

        result.addAll(
                TEAM_ALIASES.getOrDefault(
                        normalized,
                        List.of()
                )
        );

        /*
         * Kilka bardzo częstych skrótów klubowych z danych Zagranie.
         * To jest wyłącznie audit helper — niczego nie zapisuje.
         */

        if (
                normalized.equals(
                        "paris saint germain"
                )
        ) {
            result.add(
                    "psg"
            );
        }

        if (
                normalized.equals(
                        "nieciecza"
                )
        ) {
            result.add(
                    "termalica"
            );

            result.add(
                    "bruk bet termalica"
            );

            result.add(
                    "bruk bet"
            );
        }

        if (
                normalized.startsWith(
                        "tychy"
                )
        ) {
            result.add(
                    "gks tychy"
            );

            result.add(
                    "gks"
            );
        }

        if (
                normalized.contains(
                        "jastrzebie"
                )
        ) {
            result.add(
                    "gks jastrzebie"
            );

            result.add(
                    "gks"
            );
        }

        if (
                normalized.equals(
                        "manchester city"
                )
        ) {
            result.add(
                    "man city"
            );

            result.add(
                    "city"
            );
        }

        if (
                normalized.equals(
                        "manchester united"
                )
        ) {
            result.add(
                    "man united"
            );
        }

        if (
                normalized.equals(
                        "bayern munchen"
                )
        ) {
            result.add(
                    "bayern monachium"
            );

            result.add(
                    "bayern"
            );
        }

        if (
                normalized.equals(
                        "borussia dortmund"
                )
        ) {
            result.add(
                    "bvb"
            );

            result.add(
                    "borussia"
            );
        }

        if (
                normalized.equals(
                        "rb leipzig"
                )
        ) {
            result.add(
                    "rb lipsk"
            );

            result.add(
                    "lipsk"
            );
        }

        if (
                normalized.equals(
                        "shakhtar donetsk"
                )
        ) {
            result.add(
                    "szachtar"
            );
        }

        if (
                normalized.equals(
                        "marseille"
                )
        ) {
            result.add(
                    "marsylia"
            );
        }

        if (
                normalized.equals(
                        "athletic club"
                )
        ) {
            result.add(
                    "athletic bilbao"
            );

            result.add(
                    "bilbao"
            );

            result.add(
                    "athletic"
            );
        }

        if (
                normalized.equals(
                        "sporting cp"
                )
        ) {
            result.add(
                    "sporting"
            );
        }

        if (
                normalized.equals(
                        "lyon"
                )
                        || normalized.equals(
                        "olympique lyonnais"
                )
        ) {
            result.add(
                    "olympique lyon"
            );

            result.add(
                    "lyon"
            );
        }

        if (
                normalized.equals(
                        "jagiellonia"
                )
        ) {
            result.add(
                    "jaga"
            );
        }

        return List.copyOf(
                result
        );
    }

    /*
     * =========================================================
     * ALIASES
     * =========================================================
     */

    private static Map<String, List<String>>
    createTeamAliases() {

        Map<String, List<String>> result =
                new LinkedHashMap<>();

        alias(
                result,
                "Poland",
                "Polska"
        );

        alias(
                result,
                "Germany",
                "Niemcy"
        );

        alias(
                result,
                "Spain",
                "Hiszpania"
        );

        alias(
                result,
                "Italy",
                "Wlochy"
        );

        alias(
                result,
                "France",
                "Francja"
        );

        alias(
                result,
                "England",
                "Anglia"
        );

        alias(
                result,
                "Netherlands",
                "Holandia"
        );

        alias(
                result,
                "Switzerland",
                "Szwajcaria"
        );

        alias(
                result,
                "Sweden",
                "Szwecja"
        );

        alias(
                result,
                "Denmark",
                "Dania"
        );

        alias(
                result,
                "Norway",
                "Norwegia"
        );

        alias(
                result,
                "Finland",
                "Finlandia"
        );

        alias(
                result,
                "Belgium",
                "Belgia"
        );

        alias(
                result,
                "Portugal",
                "Portugalia"
        );

        alias(
                result,
                "Luxembourg",
                "Luksemburg"
        );

        alias(
                result,
                "Czech Republic",
                "Czechy"
        );

        alias(
                result,
                "Czechia",
                "Czechy"
        );

        alias(
                result,
                "Slovakia",
                "Slowacja"
        );

        alias(
                result,
                "Slovenia",
                "Slowenia"
        );

        alias(
                result,
                "Croatia",
                "Chorwacja"
        );

        alias(
                result,
                "Hungary",
                "Wegry"
        );

        alias(
                result,
                "Romania",
                "Rumunia"
        );

        alias(
                result,
                "Greece",
                "Grecja"
        );

        alias(
                result,
                "Turkey",
                "Turcja"
        );

        alias(
                result,
                "Ukraine",
                "Ukraina"
        );

        alias(
                result,
                "Scotland",
                "Szkocja"
        );

        alias(
                result,
                "Wales",
                "Walia"
        );

        alias(
                result,
                "Iceland",
                "Islandia"
        );

        alias(
                result,
                "Georgia",
                "Gruzja"
        );

        alias(
                result,
                "North Macedonia",
                "Macedonia Polnocna"
        );

        alias(
                result,
                "Bosnia & Herzegovina",
                "Bosnia i Hercegowina"
        );

        alias(
                result,
                "Montenegro",
                "Czarnogora"
        );

        alias(
                result,
                "Belarus",
                "Bialorus"
        );

        alias(
                result,
                "Lithuania",
                "Litwa"
        );

        alias(
                result,
                "Latvia",
                "Lotwa"
        );

        alias(
                result,
                "Moldova",
                "Moldawia"
        );

        alias(
                result,
                "Colombia",
                "Kolumbia"
        );

        alias(
                result,
                "Brazil",
                "Brazylia"
        );

        alias(
                result,
                "Argentina",
                "Argentyna"
        );

        alias(
                result,
                "Uruguay",
                "Urugwaj"
        );

        alias(
                result,
                "Paraguay",
                "Paragwaj"
        );

        alias(
                result,
                "Ecuador",
                "Ekwador"
        );

        alias(
                result,
                "Bolivia",
                "Boliwia"
        );

        alias(
                result,
                "Venezuela",
                "Wenezuela"
        );

        alias(
                result,
                "Mexico",
                "Meksyk"
        );

        alias(
                result,
                "Canada",
                "Kanada"
        );

        alias(
                result,
                "USA",
                "Stany Zjednoczone",
                "USA"
        );

        alias(
                result,
                "United States",
                "Stany Zjednoczone",
                "USA"
        );

        alias(
                result,
                "Japan",
                "Japonia"
        );

        alias(
                result,
                "China",
                "Chiny"
        );

        alias(
                result,
                "South Korea",
                "Korea Poludniowa"
        );

        alias(
                result,
                "North Korea",
                "Korea Polnocna"
        );

        alias(
                result,
                "New Zealand",
                "Nowa Zelandia"
        );

        alias(
                result,
                "Saudi Arabia",
                "Arabia Saudyjska"
        );

        alias(
                result,
                "Qatar",
                "Katar"
        );

        alias(
                result,
                "Morocco",
                "Maroko"
        );

        alias(
                result,
                "Egypt",
                "Egipt"
        );

        alias(
                result,
                "Algeria",
                "Algieria"
        );

        alias(
                result,
                "Tunisia",
                "Tunezja"
        );

        alias(
                result,
                "Cameroon",
                "Kamerun"
        );

        alias(
                result,
                "Ivory Coast",
                "Wybrzeze Kosci Sloniowej"
        );

        alias(
                result,
                "South Africa",
                "RPA",
                "Republika Poludniowej Afryki"
        );

        alias(
                result,
                "Congo DR",
                "DR Konga",
                "Demokratyczna Republika Konga"
        );

        return Map.copyOf(
                result
        );
    }

    private static void alias(
            Map<String, List<String>> result,
            String apiName,
            String... aliases
    ) {
        List<String> normalizedAliases =
                new ArrayList<>();

        for (
                String alias :
                aliases
        ) {
            normalizedAliases.add(
                    normalize(
                            alias
                    )
            );
        }

        result.put(
                normalize(
                        apiName
                ),
                List.copyOf(
                        normalizedAliases
                )
        );
    }

    /*
     * =========================================================
     * HELPERS
     * =========================================================
     */

    private static String stripPrefixes(
            String raw
    ) {
        String result =
                raw == null
                        ? ""
                        : raw.trim();

        boolean changed;

        do {
            changed =
                    false;

            for (
                    String rawPrefix :
                    PREFIXES
            ) {
                String prefix =
                        normalize(
                                rawPrefix
                        );

                if (
                        result.equals(
                                prefix
                        )
                ) {
                    return "";
                }

                if (
                        result.startsWith(
                                prefix + " "
                        )
                ) {
                    result =
                            result.substring(
                                            prefix.length() + 1
                                    )
                                    .trim();

                    changed =
                            true;

                    break;
                }
            }

        } while (
                changed
        );

        return result;
    }

    private static String cutAtFirst(
            String value,
            String... delimiters
    ) {
        int best =
                -1;

        for (
                String delimiter :
                delimiters
        ) {
            int index =
                    value.indexOf(
                            delimiter
                    );

            if (
                    index >= 0
                            && (
                            best < 0
                                    || index < best
                    )
            ) {
                best =
                        index;
            }
        }

        if (
                best < 0
        ) {
            return value;
        }

        return value.substring(
                0,
                best
        );
    }

    private static boolean containsAny(
            String text,
            String... values
    ) {
        for (
                String value :
                values
        ) {
            if (
                    text.contains(
                            value
                    )
            ) {
                return true;
            }
        }

        return false;
    }

    private static String normalize(
            String value
    ) {
        if (
                value == null
        ) {
            return "";
        }

        String transliterated =
                value
                        .replace(
                                'ł',
                                'l'
                        )
                        .replace(
                                'Ł',
                                'L'
                        );

        String decomposed =
                Normalizer.normalize(
                        transliterated,
                        Normalizer.Form.NFD
                );

        String withoutMarks =
                DIACRITIC_MARKS
                        .matcher(
                                decomposed
                        )
                        .replaceAll(
                                ""
                        );

        String lower =
                withoutMarks.toLowerCase(
                        Locale.ROOT
                );

        String cleaned =
                NON_ALPHANUMERIC
                        .matcher(
                                lower
                        )
                        .replaceAll(
                                " "
                        );

        return MULTIPLE_SPACES
                .matcher(
                        cleaned
                )
                .replaceAll(
                        " "
                )
                .trim();
    }

    private static Result result(
            Row row,
            Classification classification,
            String subject,
            String reason
    ) {
        return new Result(
                row,
                classification,
                subject,
                reason
        );
    }

    private static void printResult(
            int ordinal,
            Result result
    ) {
        Row row =
                result.row();

        System.out.println(
                "["
                        + ordinal
                        + "] leg="
                        + row.legId()
                        + " bet="
                        + row.betId()
                        + " wp="
                        + row.wpPostId()
        );

        System.out.println(
                "    fixture="
                        + row.fixtureId()
                        + " | "
                        + row.homeTeamName()
                        + " – "
                        + row.awayTeamName()
        );

        System.out.println(
                "    tip="
                        + row.tipTitle()
        );

        System.out.println(
                "    subject="
                        + (
                        result.subject() == null
                                ? "NONE"
                                : result.subject()
                )
        );

        System.out.println(
                "    classification="
                        + result.classification()
        );

        System.out.println(
                "    reason="
                        + result.reason()
        );
    }

    /*
     * =========================================================
     * TYPES
     * =========================================================
     */

    private enum Classification {
        SUBJECT_MATCH_HOME,
        SUBJECT_MATCH_AWAY,
        NO_EXPLICIT_SUBJECT,
        SUBJECT_MISMATCH,
        AMBIGUOUS
    }

    private record Row(
            long legId,
            long betId,
            long wpPostId,
            String tipTitle,
            long fixtureId,
            String homeTeamName,
            String awayTeamName
    ) {
    }

    private record Result(
            Row row,
            Classification classification,
            String subject,
            String reason
    ) {
    }
}