package pl.zagranietyper.parser;

import pl.zagranietyper.model.FootballTeamMarket;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FootballTeamMarketParser {

    /*
     * Śląsk poniżej 1.5 goli
     * Torino powyżej 0.5 gola
     * Barcelona over 1.5 gola
     */
    private static final Pattern DIRECT_TOTAL_PATTERN =
            Pattern.compile(
                    "^(.+?)\\s+"
                            + "(powyzej|ponizej|over|under)\\s+"
                            + "([0-9]+(?:[.,][0-9]+)?)\\s*"
                            + "(?:gola|gole|goli|gol|bramki|bramek|bramka)?$"
            );

    /*
     * Barcelona strzeli więcej niż 1.5 goli
     * Rennes zdobędzie powyżej 1.5 gola
     */
    private static final Pattern SCORING_TOTAL_PATTERN =
            Pattern.compile(
                    "^(.+?)\\s+"
                            + "(?:strzeli|zdobedzie)\\s+"
                            + "(wiecej\\s+niz|mniej\\s+niz|powyzej|ponizej|over|under)\\s+"
                            + "([0-9]+(?:[.,][0-9]+)?)\\s*"
                            + "(?:gola|gole|goli|gol|bramki|bramek|bramka)?$"
            );

    /*
     * Middlesbrough strzeli gola
     * Middlesbrough strzeli gola TAK
     * Crawley strzeli gola NIE
     */
    private static final Pattern TO_SCORE_PATTERN =
            Pattern.compile(
                    "^(.+?)\\s+"
                            + "(?:strzeli|zdobedzie)\\s+"
                            + "(?:gola|bramke)"
                            + "(?:\\s+(tak|nie))?$"
            );

    /*
     * Arsenal nie strzeli gola
     */
    private static final Pattern NOT_TO_SCORE_PATTERN =
            Pattern.compile(
                    "^(.+?)\\s+"
                            + "nie\\s+"
                            + "(?:strzeli|zdobedzie)\\s+"
                            + "(?:gola|bramke)$"
            );

    /*
     * Odrzucamy niejednoznaczne zapisy:
     *
     * Panathinaikos -1.5 goli
     *
     * Mogą oznaczać handicap.
     */
    private static final Pattern AMBIGUOUS_SIGNED_NUMBER =
            Pattern.compile(
                    "(?:^|\\s)-\\s*[0-9]+(?:[.,][0-9]+)?"
            );

    private static final Pattern NON_ALPHANUMERIC =
            Pattern.compile(
                    "[^\\p{L}\\p{N}.,]+"
            );

    private static final Pattern MULTIPLE_SPACES =
            Pattern.compile(
                    "\\s+"
            );

    /*
     * Jeżeli występuje którykolwiek z tych sygnałów,
     * nie próbujemy interpretować zakładu jako team total.
     *
     * To chroni m.in.:
     *
     * Girona - liczba rzutów rożnych powyżej 3.5
     */
    private static final Set<String> UNSUPPORTED_MARKET_SIGNALS =
            Set.of(
                    "kart",
                    "upomnien",
                    "rzut rozn",
                    "rzuty rozn",
                    "rozn",
                    "korner",
                    "corner",
                    "faul",
                    "strzal",
                    "strzaly",
                    "shots",
                    "spalony",
                    "offside",
                    "asyst",
                    "posiadanie",
                    "rzutow z autu",
                    "autow",
                    "w kazdej polowie",
                    "w obu polowach"
            );

    /*
     * Team parser obsługuje jeden konkretny rynek.
     *
     * Nie wolno mu wycinać fragmentu z MyCombi / BetBuildera.
     *
     * Manchester City wygra i Haaland strzeli gola
     *
     * nie jest "Manchester City strzeli gola".
     */
    private static final Set<String> COMPOSITE_MARKET_SIGNALS =
            Set.of(
                    "wygra",
                    "wygrana",
                    "zwyciestwo",
                    "zwyciezy",
                    "nie przegra",
                    "wygra lub",
                    "lub wygra",
                    "remis lub",
                    "lub remis",
                    "btts",
                    "obie druzyny",
                    "gole z obu stron",
                    "bramki z obu stron",
                    "awans"
            );

    private static final Set<String> GENERIC_TEAM_TOKENS =
            Set.of(
                    "fc",
                    "cf",
                    "ks",
                    "mks",
                    "rks",
                    "lks",
                    "gks",
                    "sc",
                    "afc",
                    "club",
                    "united",
                    "city"
            );

    /*
     * Słowa, które mogą poprzedzać nazwę drużyny
     * i nadal opisują team total.
     *
     * "Liczba goli Empoli: poniżej 0.5"
     */
    private static final Set<String> MARKET_LABEL_TOKENS =
            Set.of(
                    "liczba",
                    "goli",
                    "gola",
                    "gol",
                    "bramek",
                    "bramki",
                    "bramka"
            );

    private static final Map<String, List<String>> TEAM_ALIASES =
            createTeamAliases();

    private static final FootballParticipantResolver PARTICIPANT_RESOLVER =
            new FootballParticipantResolver();

    public Optional<FootballTeamMarket> parse(
            String tipTitle,
            String homeTeam,
            String awayTeam
    ) {
        if (
                tipTitle == null
                        || tipTitle.isBlank()
        ) {
            return Optional.empty();
        }

        /*
         * Ten check MUSI być przed normalize(),
         * bo normalize usuwa minus.
         */
        if (
                AMBIGUOUS_SIGNED_NUMBER
                        .matcher(
                                tipTitle
                        )
                        .find()
        ) {
            return Optional.empty();
        }

        String text =
                normalize(
                        tipTitle
                );

        if (
                text.isBlank()
        ) {
            return Optional.empty();
        }

        if (
                containsUnsupportedMarketSignal(
                        text
                )
        ) {
            return Optional.empty();
        }

        if (
                containsCompositeMarketSignal(
                        text
                )
        ) {
            return Optional.empty();
        }

        Optional<FootballTeamMarket> directTotal =
                parseDirectTotal(
                        text,
                        homeTeam,
                        awayTeam
                );

        if (
                directTotal.isPresent()
        ) {
            return directTotal;
        }

        Optional<FootballTeamMarket> scoringTotal =
                parseScoringTotal(
                        text,
                        homeTeam,
                        awayTeam
                );

        if (
                scoringTotal.isPresent()
        ) {
            return scoringTotal;
        }

        Optional<FootballTeamMarket> notToScore =
                parseNotToScore(
                        text,
                        homeTeam,
                        awayTeam
                );

        if (
                notToScore.isPresent()
        ) {
            return notToScore;
        }

        return parseToScore(
                text,
                homeTeam,
                awayTeam
        );
    }

    private static Optional<FootballTeamMarket> parseDirectTotal(
            String text,
            String homeTeam,
            String awayTeam
    ) {
        Matcher matcher =
                DIRECT_TOTAL_PATTERN.matcher(
                        text
                );

        if (
                !matcher.matches()
        ) {
            return Optional.empty();
        }

        Optional<FootballTeamMarket.TeamSide> side =
                resolveTeamSubject(
                        matcher.group(
                                1
                        ),
                        homeTeam,
                        awayTeam
                );

        if (
                side.isEmpty()
        ) {
            return Optional.empty();
        }

        FootballTeamMarket.Direction direction =
                parseDirection(
                        matcher.group(
                                2
                        )
                );

        BigDecimal line =
                decimal(
                        matcher.group(
                                3
                        )
                );

        if (
                direction == null
                        || line == null
        ) {
            return Optional.empty();
        }

        return Optional.of(
                new FootballTeamMarket.TeamTotalGoals(
                        side.get(),
                        direction,
                        line
                )
        );
    }

    private static Optional<FootballTeamMarket> parseScoringTotal(
            String text,
            String homeTeam,
            String awayTeam
    ) {
        Matcher matcher =
                SCORING_TOTAL_PATTERN.matcher(
                        text
                );

        if (
                !matcher.matches()
        ) {
            return Optional.empty();
        }

        Optional<FootballTeamMarket.TeamSide> side =
                resolveTeamSubject(
                        matcher.group(
                                1
                        ),
                        homeTeam,
                        awayTeam
                );

        if (
                side.isEmpty()
        ) {
            return Optional.empty();
        }

        FootballTeamMarket.Direction direction =
                parseDirection(
                        matcher.group(
                                2
                        )
                );

        BigDecimal line =
                decimal(
                        matcher.group(
                                3
                        )
                );

        if (
                direction == null
                        || line == null
        ) {
            return Optional.empty();
        }

        return Optional.of(
                new FootballTeamMarket.TeamTotalGoals(
                        side.get(),
                        direction,
                        line
                )
        );
    }

    private static Optional<FootballTeamMarket> parseToScore(
            String text,
            String homeTeam,
            String awayTeam
    ) {
        Matcher matcher =
                TO_SCORE_PATTERN.matcher(
                        text
                );

        if (
                !matcher.matches()
        ) {
            return Optional.empty();
        }

        Optional<FootballTeamMarket.TeamSide> side =
                resolveTeamSubject(
                        matcher.group(
                                1
                        ),
                        homeTeam,
                        awayTeam
                );

        if (
                side.isEmpty()
        ) {
            return Optional.empty();
        }

        String yesNo =
                matcher.group(
                        2
                );

        boolean yes =
                yesNo == null
                        || !"nie".equals(
                        yesNo
                );

        return Optional.of(
                new FootballTeamMarket.TeamToScore(
                        side.get(),
                        yes
                )
        );
    }

    private static Optional<FootballTeamMarket> parseNotToScore(
            String text,
            String homeTeam,
            String awayTeam
    ) {
        Matcher matcher =
                NOT_TO_SCORE_PATTERN.matcher(
                        text
                );

        if (
                !matcher.matches()
        ) {
            return Optional.empty();
        }

        Optional<FootballTeamMarket.TeamSide> side =
                resolveTeamSubject(
                        matcher.group(
                                1
                        ),
                        homeTeam,
                        awayTeam
                );

        if (
                side.isEmpty()
        ) {
            return Optional.empty();
        }

        return Optional.of(
                new FootballTeamMarket.TeamToScore(
                        side.get(),
                        false
                )
        );
    }

    private static FootballTeamMarket.Direction parseDirection(
            String raw
    ) {
        if (
                raw == null
        ) {
            return null;
        }

        return switch (
                raw
                ) {
            case "powyzej",
                 "over",
                 "wiecej niz" ->
                    FootballTeamMarket.Direction.OVER;

            case "ponizej",
                 "under",
                 "mniej niz" ->
                    FootballTeamMarket.Direction.UNDER;

            default ->
                    null;
        };
    }

    /*
     * =========================================================
     * STRICT TEAM SUBJECT RESOLUTION
     * =========================================================
     */

    private static Optional<FootballTeamMarket.TeamSide>
    resolveTeamSubject(
            String rawSubject,
            String homeTeam,
            String awayTeam
    ) {
        String subject =
                normalizeSubject(
                        rawSubject
                );

        if (
                subject.isBlank()
        ) {
            return Optional.empty();
        }

        return switch (resolveParticipant(subject, homeTeam, awayTeam)) {
            case HOME -> Optional.of(FootballTeamMarket.TeamSide.HOME);
            case AWAY -> Optional.of(FootballTeamMarket.TeamSide.AWAY);
            case UNRESOLVED, AMBIGUOUS -> Optional.empty();
        };
    }

    static FootballParticipantResolver.Resolution resolveParticipant(
            String rawSubject,
            String homeTeam,
            String awayTeam
    ) {
        String subject = normalizeSubject(rawSubject);
        if (subject.isBlank()) {
            return FootballParticipantResolver.Resolution.UNRESOLVED;
        }
        return PARTICIPANT_RESOLVER.resolve(
                subject,
                homeTeam,
                awayTeam,
                FootballParticipantResolver.MatchingPolicy.STRICT_SCORED_SUBSET,
                TEAM_ALIASES
        );
    }

    private static String normalizeSubject(
            String rawSubject
    ) {
        String subject =
                normalize(
                        rawSubject
                );

        if (
                subject.isBlank()
        ) {
            return "";
        }

        List<String> result =
                new ArrayList<>();

        for (
                String token :
                subject.split(
                        "\\s+"
                )
        ) {
            if (
                    MARKET_LABEL_TOKENS.contains(
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

    private static int strictTeamMatchScore(
            String subject,
            String team
    ) {
        if (
                subject == null
                        || subject.isBlank()
                        || team == null
                        || team.isBlank()
        ) {
            return 0;
        }

        if (
                subject.equals(
                        team
                )
        ) {
            return 100;
        }

        for (
                String alias :
                TEAM_ALIASES.getOrDefault(
                        team,
                        List.of()
                )
        ) {
            if (
                    subject.equals(
                            normalize(
                                    alias
                            )
                    )
            ) {
                return 95;
            }
        }

        List<String> subjectTokens =
                meaningfulTokens(
                        subject
                );

        if (
                subjectTokens.isEmpty()
        ) {
            return 0;
        }

        int best =
                strictTokenMatchScore(
                        subjectTokens,
                        meaningfulTokens(
                                team
                        )
                );

        for (
                String alias :
                TEAM_ALIASES.getOrDefault(
                        team,
                        List.of()
                )
        ) {
            best =
                    Math.max(
                            best,
                            strictTokenMatchScore(
                                    subjectTokens,
                                    meaningfulTokens(
                                            normalize(
                                                    alias
                                            )
                                    )
                            )
                    );
        }

        return best;
    }

    /*
     * KAŻDY istotny token subjectu musi pasować
     * do nazwy drużyny.
     *
     * Dzięki temu:
     *
     * "Manchester City Haaland"
     *
     * nie zostanie uznane za Manchester City,
     * bo "Haaland" nie należy do nazwy zespołu.
     */
    private static int strictTokenMatchScore(
            List<String> subjectTokens,
            List<String> teamTokens
    ) {
        if (
                subjectTokens.isEmpty()
                        || teamTokens.isEmpty()
        ) {
            return 0;
        }

        int matches =
                0;

        Set<Integer> usedTeamTokens =
                new HashSet<>();

        for (
                String subjectToken :
                subjectTokens
        ) {
            boolean found =
                    false;

            for (
                    int i = 0;
                    i < teamTokens.size();
                    i++
            ) {
                if (
                        usedTeamTokens.contains(
                                i
                        )
                ) {
                    continue;
                }

                if (
                        tokenEquivalent(
                                subjectToken,
                                teamTokens.get(
                                        i
                                )
                        )
                ) {
                    usedTeamTokens.add(
                            i
                    );

                    matches++;

                    found =
                            true;

                    break;
                }
            }

            if (
                    !found
            ) {
                return 0;
            }
        }

        return 10 + matches * 10;
    }

    private static boolean tokenEquivalent(
            String left,
            String right
    ) {
        if (
                left.equals(
                        right
                )
        ) {
            return true;
        }

        if (
                left.length() < 4
                        || right.length() < 4
        ) {
            return false;
        }

        if (
                left.startsWith(
                        right
                )
                        && left.length()
                        <= right.length() + 7
        ) {
            return true;
        }

        if (
                right.startsWith(
                        left
                )
                        && right.length()
                        <= left.length() + 7
        ) {
            return true;
        }

        if (
                sameStemWithoutFinalA(
                        left,
                        right
                )
        ) {
            return true;
        }

        return sameStemWithoutFinalA(
                right,
                left
        );
    }

    private static boolean sameStemWithoutFinalA(
            String base,
            String inflected
    ) {
        if (
                !base.endsWith(
                        "a"
                )
                        || base.length() < 5
        ) {
            return false;
        }

        String stem =
                base.substring(
                        0,
                        base.length() - 1
                );

        return inflected.startsWith(
                stem
        )
                && inflected.length()
                <= base.length() + 6;
    }

    private static List<String> meaningfulTokens(
            String value
    ) {
        if (
                value == null
                        || value.isBlank()
        ) {
            return List.of();
        }

        List<String> result =
                new ArrayList<>();

        for (
                String token :
                value.split(
                        "\\s+"
                )
        ) {
            if (
                    token.length() < 3
                            || GENERIC_TEAM_TOKENS.contains(
                            token
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

    /*
     * =========================================================
     * SAFETY GUARDS
     * =========================================================
     */

    private static boolean containsUnsupportedMarketSignal(
            String text
    ) {
        for (
                String signal :
                UNSUPPORTED_MARKET_SIGNALS
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

    private static boolean containsCompositeMarketSignal(
            String text
    ) {
        for (
                String signal :
                COMPOSITE_MARKET_SIGNALS
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
     * HELPERS
     * =========================================================
     */

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
                    raw.replace(
                            ',',
                            '.'
                    )
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
                        );

        String decomposed =
                Normalizer.normalize(
                        transliterated,
                        Normalizer.Form.NFD
                );

        String withoutMarks =
                decomposed.replaceAll(
                        "\\p{M}+",
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

    private static Map<String, List<String>> createTeamAliases() {
        Map<String, List<String>> result =
                new HashMap<>();

        alias(
                result,
                "Athletic Club",
                "Bilbao",
                "Athletic Bilbao"
        );

        alias(
                result,
                "Borussia Dortmund",
                "BVB"
        );

        alias(
                result,
                "Sporting CP",
                "Sporting"
        );

        alias(
                result,
                "Lyon",
                "Olympique Lyon"
        );

        return Map.copyOf(
                result
        );
    }

    private static void alias(
            Map<String, List<String>> map,
            String team,
            String... aliases
    ) {
        map.put(
                normalize(
                        team
                ),
                List.of(
                        aliases
                )
        );
    }
}
