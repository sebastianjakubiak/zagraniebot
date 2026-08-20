package pl.zagranietyper.parser;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FootballHandicapParser {

    private static final Pattern DIACRITIC_MARKS =
            Pattern.compile("\\p{M}+");

    /*
     * Zachowujemy:
     *
     * + - . :
     *
     * bo są istotne dla handicapu.
     */
    private static final Pattern NON_ALLOWED =
            Pattern.compile(
                    "[^\\p{L}\\p{N}+\\-.:]+"
            );

    private static final Pattern MULTIPLE_SPACES =
            Pattern.compile("\\s+");

    private static final String SIGNED_LINE =
            "([+-]\\d+(?:\\.\\d+)?)";

    /*
     * Wisła Kraków wygra z handicapem (-1.5)
     * Maroko wygra mecz z handicapem (-1.5)
     * Anglia wygra (handicap -1,5)
     * Hiszpania wygra (handicap -3)
     */
    private static final Pattern WINNER_SIGNED =
            Pattern.compile(
                    "^(.+?)\\s+wygra"
                            + "(?:\\s+mecz)?"
                            + "\\s+(?:z\\s+)?"
                            + "handicap(?:em)?"
                            + "\\s+"
                            + SIGNED_LINE
                            + "$"
            );

    /*
     * PSG wygra mecz z handicapem 0:1
     */
    private static final Pattern WINNER_COLON_0_1 =
            Pattern.compile(
                    "^(.+?)\\s+wygra"
                            + "(?:\\s+mecz)?"
                            + "\\s+(?:z\\s+)?"
                            + "handicap(?:em)?"
                            + "\\s+0:1$"
            );

    /*
     * handicap 0:1 - Brighton
     */
    private static final Pattern PREFIX_COLON_0_1 =
            Pattern.compile(
                    "^handicap\\s+0:1\\s*-\\s*(.+)$"
            );

    /*
     * Handicap 0:1
     *
     * Wiemy, że to handicap, ale z samego tip_title
     * nie wiemy bezpiecznie, która strona została wybrana.
     */
    private static final Pattern SUBJECTLESS_COLON_0_1 =
            Pattern.compile(
                    "^handicap\\s+0:1$"
            );

    /*
     * handicap -1.5 Manchester City
     * Handicap -2.5: Bayern
     */
    private static final Pattern PREFIX_LINE_TEAM =
            Pattern.compile(
                    "^handicap\\s*:?"
                            + "\\s*"
                            + SIGNED_LINE
                            + "\\s*:?"
                            + "\\s*(.+)$"
            );

    /*
     * handicap Chelsea -2.5
     * Handicap: Arsenal -2.5
     */
    private static final Pattern PREFIX_TEAM_LINE =
            Pattern.compile(
                    "^handicap\\s*:?"
                            + "\\s*(.+?)"
                            + "\\s+"
                            + SIGNED_LINE
                            + "$"
            );

    /*
     * Newcastle United +1.5 handicap
     * Walia +1.5 handicap
     */
    private static final Pattern TEAM_LINE_SUFFIX =
            Pattern.compile(
                    "^(.+?)\\s+"
                            + SIGNED_LINE
                            + "\\s+handicap$"
            );

    /*
     * Arsenal handicap (-1.5)
     */
    private static final Pattern TEAM_HANDICAP_LINE =
            Pattern.compile(
                    "^(.+?)\\s+handicap\\s+"
                            + SIGNED_LINE
                            + "$"
            );

    /*
     * Lokalne aliasy potrzebne wyłącznie tej rodzinie rynku.
     */
    private static final Map<String, String> LOCAL_SUBJECT_ALIASES =
            Map.ofEntries(
                    Map.entry(
                            "newcastle united",
                            "newcastle"
                    ),
                    Map.entry(
                            "czarni",
                            "czarni polaniec"
                    ),
                    Map.entry(
                            "wieczysta",
                            "wieczysta krakow"
                    )
            );

    private final FootballWinnerParser winnerParser;

    public FootballHandicapParser() {
        this.winnerParser =
                new FootballWinnerParser();
    }

    public ParseResult parse(
            String tipTitle,
            String homeTeam,
            String awayTeam
    ) {
        String text =
                normalize(
                        tipTitle
                );

        if (
                text.isBlank()
                        || !text.contains(
                        "handicap"
                )
        ) {
            return rejected(
                    Status.NOT_HANDICAP
            );
        }

        /*
         * Handicap dotyczący statystyk albo części meczu
         * nie jest handicapem wyniku FT.
         */
        if (
                containsNonScoreSignal(
                        text
                )
        ) {
            return rejected(
                    Status.UNSUPPORTED_NON_SCORE_HANDICAP
            );
        }

        /*
         * =====================================================
         * 0:1 — jawny subject przed handicapem
         * =====================================================
         */

        Matcher winnerColon =
                WINNER_COLON_0_1.matcher(
                        text
                );

        if (
                winnerColon.matches()
        ) {
            return parseColonSubject(
                    winnerColon.group(1),
                    homeTeam,
                    awayTeam,
                    Format.COLON_0_1_WINNER
            );
        }

        /*
         * =====================================================
         * 0:1 — jawny subject po separatorze
         * =====================================================
         */

        Matcher prefixColon =
                PREFIX_COLON_0_1.matcher(
                        text
                );

        if (
                prefixColon.matches()
        ) {
            return parseColonSubject(
                    prefixColon.group(1),
                    homeTeam,
                    awayTeam,
                    Format.COLON_0_1_PREFIX
            );
        }

        /*
         * =====================================================
         * 0:1 — brak subjectu
         * =====================================================
         */

        if (
                SUBJECTLESS_COLON_0_1
                        .matcher(
                                text
                        )
                        .matches()
        ) {
            return new ParseResult(
                    Status.SUBJECT_NOT_FOUND,
                    null,
                    null,
                    new BigDecimal("-1"),
                    Format.COLON_0_1_SUBJECTLESS
            );
        }

        /*
         * =====================================================
         * X wygra z handicapem -1.5
         * =====================================================
         */

        Matcher winnerSigned =
                WINNER_SIGNED.matcher(
                        text
                );

        if (
                winnerSigned.matches()
        ) {
            return parseNamedSubject(
                    winnerSigned.group(1),
                    decimal(
                            winnerSigned.group(2)
                    ),
                    homeTeam,
                    awayTeam,
                    Format.WINNER_SIGNED
            );
        }

        /*
         * =====================================================
         * handicap -1.5 TEAM
         * =====================================================
         */

        Matcher prefixLine =
                PREFIX_LINE_TEAM.matcher(
                        text
                );

        if (
                prefixLine.matches()
        ) {
            return parseNamedSubject(
                    prefixLine.group(2),
                    decimal(
                            prefixLine.group(1)
                    ),
                    homeTeam,
                    awayTeam,
                    Format.PREFIX_LINE_TEAM
            );
        }

        /*
         * =====================================================
         * handicap TEAM -1.5
         * =====================================================
         */

        Matcher prefixTeam =
                PREFIX_TEAM_LINE.matcher(
                        text
                );

        if (
                prefixTeam.matches()
        ) {
            return parseNamedSubject(
                    prefixTeam.group(1),
                    decimal(
                            prefixTeam.group(2)
                    ),
                    homeTeam,
                    awayTeam,
                    Format.PREFIX_TEAM_LINE
            );
        }

        /*
         * =====================================================
         * TEAM +1.5 handicap
         * =====================================================
         */

        Matcher suffix =
                TEAM_LINE_SUFFIX.matcher(
                        text
                );

        if (
                suffix.matches()
        ) {
            return parseNamedSubject(
                    suffix.group(1),
                    decimal(
                            suffix.group(2)
                    ),
                    homeTeam,
                    awayTeam,
                    Format.TEAM_LINE_SUFFIX
            );
        }

        /*
         * =====================================================
         * TEAM handicap -1.5
         * =====================================================
         */

        Matcher teamHandicap =
                TEAM_HANDICAP_LINE.matcher(
                        text
                );

        if (
                teamHandicap.matches()
        ) {
            return parseNamedSubject(
                    teamHandicap.group(1),
                    decimal(
                            teamHandicap.group(2)
                    ),
                    homeTeam,
                    awayTeam,
                    Format.TEAM_HANDICAP_LINE
            );
        }

        return rejected(
                Status.UNSUPPORTED_FORMAT
        );
    }

    public boolean looksLikeHandicap(
            String tipTitle
    ) {
        return normalize(
                tipTitle
        ).contains(
                "handicap"
        );
    }

    /*
     * =========================================================
     * COLON 0:1
     * =========================================================
     */

    private ParseResult parseColonSubject(
            String rawSubject,
            String homeTeam,
            String awayTeam,
            Format format
    ) {
        ParseResult result =
                parseNamedSubject(
                        rawSubject,
                        new BigDecimal("-1"),
                        homeTeam,
                        awayTeam,
                        format
                );

        if (
                !result.parsed()
        ) {
            return result;
        }

        /*
         * Zapis 0:1 oznacza w naszych aktualnych danych,
         * że jawnie wskazana drużyna gospodarzy startuje
         * z handicapem -1.
         *
         * Nie rozszerzamy tego automatycznie na przypadek,
         * gdzie subjectem byłaby drużyna AWAY.
         */
        if (
                result.selection()
                        != FootballWinnerParser.Selection.HOME
        ) {
            return new ParseResult(
                    Status.UNSUPPORTED_COLON_SIDE,
                    result.selection(),
                    result.subject(),
                    result.line(),
                    format
            );
        }

        return result;
    }

    /*
     * =========================================================
     * SUBJECT
     * =========================================================
     */

    private ParseResult parseNamedSubject(
            String rawSubject,
            BigDecimal line,
            String homeTeam,
            String awayTeam,
            Format format
    ) {
        if (
                line == null
        ) {
            return rejected(
                    Status.UNSUPPORTED_FORMAT
            );
        }

        String subject =
                normalizeSubject(
                        rawSubject
                );

        if (
                subject.isBlank()
        ) {
            return new ParseResult(
                    Status.SUBJECT_NOT_FOUND,
                    null,
                    subject,
                    line,
                    format
            );
        }

        FootballWinnerParser.ParseResult winner =
                winnerParser.parse(
                        subject + " wygra",
                        homeTeam,
                        awayTeam
                );

        if (
                winner.parsed()
        ) {
            return new ParseResult(
                    Status.PARSED,
                    winner.selection(),
                    subject,
                    line,
                    format
            );
        }

        return switch (
                winner.status()
                ) {
            case SUBJECT_MISMATCH ->
                    new ParseResult(
                            Status.SUBJECT_MISMATCH,
                            null,
                            subject,
                            line,
                            format
                    );

            case SUBJECT_AMBIGUOUS ->
                    new ParseResult(
                            Status.SUBJECT_AMBIGUOUS,
                            null,
                            subject,
                            line,
                            format
                    );

            case SUBJECT_NOT_FOUND,
                 NOT_WINNER,
                 UNSUPPORTED_COMPOSITE ->
                    new ParseResult(
                            Status.SUBJECT_NOT_FOUND,
                            null,
                            subject,
                            line,
                            format
                    );

            case PARSED ->
                    throw new IllegalStateException(
                            "Unexpected FootballWinnerParser state"
                    );
        };
    }

    /*
     * =========================================================
     * SAFETY
     * =========================================================
     */

    private static boolean containsNonScoreSignal(
            String text
    ) {
        return containsAny(
                text,

                /*
                 * Rzuty rożne.
                 *
                 * Po normalizacji:
                 *
                 * "rzutów rożnych"
                 * ->
                 * "rzutow roznych"
                 */
                "rzut rozn",
                "rzuty rozn",
                "rzutow rozn",
                "rozn",
                "corner",
                "korner",

                /*
                 * Kartki.
                 */
                "kart",
                "upomnien",

                /*
                 * Strzały.
                 */
                "strzal",
                "shots",

                /*
                 * Faule.
                 */
                "faul",

                /*
                 * Spalone.
                 */
                "spalon",
                "offside",

                /*
                 * Interwencje.
                 */
                "interwenc",
                "save",

                /*
                 * Asysty.
                 */
                "asyst",

                /*
                 * Inne statystyki.
                 */
                "posiadanie",
                "autow",

                /*
                 * Handicap części meczu.
                 */
                "1 polowa",
                "pierwsza polowa",
                "do przerwy",
                "half",

                /*
                 * Inne dyscypliny / jednostki.
                 */
                "sety",
                "gemy",
                "map",
                "rund"
        );
    }

    private static boolean containsAny(
            String text,
            String... signals
    ) {
        for (
                String signal :
                signals
        ) {
            if (
                    text.contains(
                            signal
                    )
            ) {
                return true;
            }
        }

        return false;
    }

    /*
     * =========================================================
     * NORMALIZATION
     * =========================================================
     */

    private static String normalizeSubject(
            String value
    ) {
        if (
                value == null
        ) {
            return "";
        }

        String subject =
                value
                        .replaceAll(
                                "^[-:]+",
                                ""
                        )
                        .replaceAll(
                                "[-:]+$",
                                ""
                        )
                        .trim();

        return LOCAL_SUBJECT_ALIASES.getOrDefault(
                subject,
                subject
        );
    }

    private static BigDecimal decimal(
            String raw
    ) {
        if (
                raw == null
                        || raw.isBlank()
        ) {
            return null;
        }

        try {
            return new BigDecimal(
                    raw
            );

        } catch (
                NumberFormatException exception
        ) {
            return null;
        }
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
                        )
                        .replace(
                                ',',
                                '.'
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
                        .replaceAll("");

        String lower =
                withoutMarks.toLowerCase(
                        Locale.ROOT
                );

        String cleaned =
                NON_ALLOWED
                        .matcher(
                                lower
                        )
                        .replaceAll(" ");

        return MULTIPLE_SPACES
                .matcher(
                        cleaned
                )
                .replaceAll(" ")
                .trim();
    }

    private static ParseResult rejected(
            Status status
    ) {
        return new ParseResult(
                status,
                null,
                null,
                null,
                null
        );
    }

    public enum Status {
        PARSED,
        NOT_HANDICAP,
        UNSUPPORTED_NON_SCORE_HANDICAP,
        UNSUPPORTED_FORMAT,
        UNSUPPORTED_COLON_SIDE,
        SUBJECT_NOT_FOUND,
        SUBJECT_MISMATCH,
        SUBJECT_AMBIGUOUS
    }

    public enum Format {
        PREFIX_LINE_TEAM,
        PREFIX_TEAM_LINE,
        TEAM_LINE_SUFFIX,
        TEAM_HANDICAP_LINE,
        WINNER_SIGNED,
        COLON_0_1_PREFIX,
        COLON_0_1_WINNER,
        COLON_0_1_SUBJECTLESS
    }

    public record ParseResult(
            Status status,
            FootballWinnerParser.Selection selection,
            String subject,
            BigDecimal line,
            Format format
    ) {

        public boolean parsed() {
            return status
                    == Status.PARSED;
        }
    }
}