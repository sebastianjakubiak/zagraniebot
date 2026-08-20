package pl.zagranietyper.parser;

import pl.zagranietyper.model.FootballMarket;

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

public final class FootballMarketParser {

    private static final Pattern NON_ALPHANUMERIC =
            Pattern.compile(
                    "[^\\p{L}\\p{N}.,+\\-/]+"
            );

    private static final Pattern MULTIPLE_SPACES =
            Pattern.compile(
                    "\\s+"
            );

    /*
     * Zwykłe totale meczu:
     *
     * powyżej 2.5 gola
     * poniżej 3,5 gola
     * over 1.5
     * under 4.5
     * więcej niż 2.5 gola
     * mniej niż 3.5 gola
     *
     * Pattern może wystąpić w dowolnym miejscu tip_title.
     *
     * Team total jest odcinany wcześniej przez
     * looksLikeTeamTotal().
     */
    private static final Pattern TOTAL_PATTERN =
            Pattern.compile(
                    "\\b"
                            + "(powyzej|ponizej|over|under|wiecej\\s+niz|mniej\\s+niz)"
                            + "\\s*"
                            + "([0-9]+(?:[.,][0-9]+)?)"
                            + "\\s*"
                            + "(?:gola|goli|gol|bramki|bramek|bramka)?"
                            + "\\b"
            );

    /*
     * Narrow Polish MATCH TOTAL operator:
     *
     * Ponad 2,5 bramki
     * Espanyol vs Villarreal - ponad 1.5 gola w meczu
     *
     * Shape validation is performed separately so team totals and
     * unresolved composites cannot be partially parsed.
     */
    private static final Pattern PONAD_TOTAL_PATTERN =
            Pattern.compile(
                    "\\bponad\\s*"
                            + "([0-9]+(?:[.,][0-9]+)?)"
                            + "\\s*"
                            + "(?:gola|goli|gol|bramki|bramek|bramka)"
                            + "\\b"
            );

    private static final Pattern PURE_MATCH_GOAL_RANGE_PATTERN =
            Pattern.compile(
                    "^przedzial\\s+goli\\s+w\\s+meczu\\s+"
                            + "([0-9]+)\\s*-\\s*([0-9]+)$"
            );

    private static final Pattern GOAL_RANGE_LIKE_PATTERN =
            Pattern.compile(
                    "\\b(?:przedzial\\s+goli|suma\\s+goli\\s*:)"
            );

    private static final Pattern MINIMUM_TOTAL_PATTERN =
            Pattern.compile(
                    "\\b(?:co najmniej|minimum)\\s+"
                            + "(jeden|jedna|jedno|dwa|dwie|trzy|cztery|piec|szesc|siedem|osiem|dziewiec|dziesiec|[0-9]+)"
                            + "\\s+"
                            + "(?:gol|gole|gola|goli|bramka|bramki|bramek|trafienie|trafienia|trafien)"
                            + "\\b"
            );

    /* Także odrzucone warianty, np. minimum 2,5 gola. */
    private static final Pattern MINIMUM_TOTAL_LIKE_PATTERN =
            Pattern.compile(
                    "\\b(?:co najmniej|minimum)\\s+"
                            + "(?:[0-9]+(?:[.,][0-9]+)?|[\\p{L}]+)"
                            + "\\s*"
                            + "(?:gol|gole|gola|goli|bramka|bramki|bramek|trafienie|trafienia|trafien)"
                            + "\\b"
            );

    /*
     * Skrót:
     *
     * +2.5 goli -> OVER 2.5
     * -3.5 goli -> UNDER 3.5
     *
     * np.
     *
     * 1/+2.5 goli
     * X2/-3.5 goli
     */
    private static final Pattern SIGNED_TOTAL_PATTERN =
            Pattern.compile(
                    "(?:^|[\\s/,])"
                            + "([+-])\\s*"
                            + "([0-9]+(?:[.,][0-9]+)?)\\s*"
                            + "(?:gola|goli|gol|bramki|bramek|bramka)"
                            + "\\b"
            );

    /*
     * X/Miedź
     */
    private static final Pattern X_SLASH_TEAM_PATTERN =
            Pattern.compile(
                    "(?:^|[\\s,:])"
                            + "x\\s*/\\s*"
                            + "([\\p{L}\\p{N}]+)"
            );

    /*
     * Miedź/X
     */
    private static final Pattern TEAM_SLASH_X_PATTERN =
            Pattern.compile(
                    "(?:^|[\\s,:])"
                            + "([\\p{L}\\p{N}]+)"
                            + "\\s*/\\s*x"
                            + "(?:$|[\\s,+])"
            );

    private static final Set<String> UNSUPPORTED_SIGNALS =
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
                    "spalony",
                    "offside",
                    "asyst",
                    "shots",
                    "posiadanie",
                    "autow",
                    "rzutow z autu",

                    /*
                     * Rynki zależne od wyniku konkretnej połowy są poza
                     * zakresem tego parsera. Ten parser rozlicza wyłącznie
                     * rynki możliwe do ustalenia z wyniku całego meczu.
                     *
                     * normalize():
                     * połowa / połowie / połowę / połowach -> polow...
                     * przerwa / przerwy / przerwie           -> przerw...
                     */
                    "polow",
                    "przerw",
                    "half"
            );

    /*
     * To są tylko aliasy do zidentyfikowania strony
     * już poprawnie resolved fixture.
     */
    private static final Map<String, List<String>> TEAM_ALIASES =
            createTeamAliases();

    public Optional<FootballMarket> parse(
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
        ) {
            return Optional.empty();
        }

        if (
                containsUnsupportedSignal(
                        text
                )
        ) {
            return Optional.empty();
        }

        if (
                containsPlayerLikeScorerSignal(
                        text
                )
        ) {
            return Optional.empty();
        }

        /*
         * Na razie team total zostawiamy manualnie.
         *
         * Bardzo ważne, żeby np.
         *
         * Barcelona strzeli więcej niż 1.5 goli
         *
         * nie zmieniło się w:
         *
         * TOTAL MATCH OVER 1.5.
         */
        if (
                looksLikeTeamTotal(
                        text,
                        homeTeam,
                        awayTeam
                )
        ) {
            return Optional.empty();
        }

        List<FootballMarket.Condition> conditions =
                new ArrayList<>();

        parseBtts(
                text,
                conditions
        );

        parseTotalGoals(
                text,
                homeTeam,
                awayTeam,
                conditions
        );

        parseMatchGoalRange(
                text,
                conditions
        );

        parseDoubleChance(
                text,
                homeTeam,
                awayTeam,
                conditions
        );

        parseMatchResult(
                text,
                homeTeam,
                awayTeam,
                conditions
        );

        conditions =
                deduplicate(
                        conditions
                );

        if (
                conditions.isEmpty()
        ) {
            return Optional.empty();
        }

        /*
         * Parser musi rozumieć CAŁY deklarowany rynek.
         *
         * Jeśli tekst mówi:
         *
         * X/Miedź + under
         *
         * a my rozpoznaliśmy wyłącznie under,
         * nie wolno wykonać AUTO settlementu.
         */
        if (
                !isCompleteParse(
                        text,
                        conditions
                )
        ) {
            return Optional.empty();
        }

        return Optional.of(
                new FootballMarket(
                        conditions
                )
        );
    }

    /*
     * =========================================================
     * COMPLETENESS
     * =========================================================
     */

    private static boolean isCompleteParse(
            String text,
            List<FootballMarket.Condition> conditions
    ) {
        boolean hasMatchResult =
                containsCondition(
                        conditions,
                        FootballMarket.MatchResult.class
                );

        boolean hasDoubleChance =
                containsCondition(
                        conditions,
                        FootballMarket.DoubleChance.class
                );

        boolean hasTotal =
                containsCondition(
                        conditions,
                        FootballMarket.TotalGoals.class
                );

        boolean hasMinimumTotal =
                containsCondition(
                        conditions,
                        FootballMarket.MinimumTotalGoals.class
                );

        boolean hasMatchGoalRange =
                containsCondition(
                        conditions,
                        FootballMarket.MatchGoalRange.class
                );

        boolean hasBtts =
                containsCondition(
                        conditions,
                        FootballMarket.BothTeamsToScore.class
                );

        if (
                hasPureMatchResultCue(
                        text
                )
                        && !hasMatchResult
        ) {
            return false;
        }

        if (
                hasDoubleChanceCue(
                        text
                )
                        && !hasDoubleChance
        ) {
            return false;
        }

        if (
                hasTotalCue(
                        text
                )
                        && !hasTotal
        ) {
            return false;
        }

        if (
                hasMinimumTotalCue(
                        text
                )
                        && !hasMinimumTotal
        ) {
            return false;
        }

        if (
                hasGoalRangeCue(text)
                        && !hasMatchGoalRange
        ) {
            return false;
        }

        if (
                hasBttsCue(
                        text
                )
                        && !hasBtts
        ) {
            return false;
        }

        return true;
    }

    private static boolean containsCondition(
            List<FootballMarket.Condition> conditions,
            Class<?> type
    ) {
        for (
                FootballMarket.Condition condition :
                conditions
        ) {
            if (
                    type.isInstance(
                            condition
                    )
            ) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasPureMatchResultCue(
            String text
    ) {
        /*
         * X/Miedź NIE jest czystym remisem.
         */
        if (
                hasDoubleChanceCue(
                        text
                )
        ) {
            return false;
        }

        if (
                containsCompoundResultSelection(
                        text,
                        "1"
                )
                        || containsCompoundResultSelection(
                        text,
                        "2"
                )
                        || containsCompoundResultSelection(
                        text,
                        "x"
                )
        ) {
            return true;
        }

        return containsAny(
                text,
                "wygra",
                "wygrana",
                "zwyciestwo",
                "zwyciezy"
        );
    }

    private static boolean hasDoubleChanceCue(
            String text
    ) {
        return containsStandaloneToken(
                text,
                "1x"
        )
                || containsStandaloneToken(
                text,
                "x2"
        )
                || containsStandaloneToken(
                text,
                "12"
        )
                || X_SLASH_TEAM_PATTERN
                .matcher(
                        text
                )
                .find()
                || TEAM_SLASH_X_PATTERN
                .matcher(
                        text
                )
                .find()
                || text.contains(
                "nie przegra"
        )
                || text.contains(
                "remis lub"
        )
                || text.contains(
                "lub remis"
        )
                || text.contains(
                "wygra lub zremisuje"
        )
                || text.contains(
                "zremisuje lub wygra"
        );
    }

    private static boolean hasTotalCue(
            String text
    ) {
        if (
                TOTAL_PATTERN
                        .matcher(
                                text
                        )
                        .find()
        ) {
            return true;
        }

        if (
                PONAD_TOTAL_PATTERN
                        .matcher(
                                text
                        )
                        .find()
        ) {
            return true;
        }

        return SIGNED_TOTAL_PATTERN
                .matcher(
                        text
                )
                .find();
    }

    private static boolean hasMinimumTotalCue(
            String text
    ) {
        return MINIMUM_TOTAL_LIKE_PATTERN
                .matcher(
                        text
                )
                .find();
    }

    private static boolean hasGoalRangeCue(String text) {
        return GOAL_RANGE_LIKE_PATTERN.matcher(text).find();
    }

    private static boolean hasBttsCue(
            String text
    ) {
        return text.contains(
                "btts"
        )
                || text.contains(
                "obie druzyny strzela"
        )
                || text.contains(
                "gole z obu stron"
        )
                || text.contains(
                "bramki z obu stron"
        );
    }

    /*
     * =========================================================
     * BTTS
     * =========================================================
     */

    private static void parseBtts(
            String text,
            List<FootballMarket.Condition> result
    ) {
        if (
                containsAny(
                        text,
                        "btts - nie",
                        "btts nie",
                        "btts no",
                        "obie druzyny strzela gola nie",
                        "obie druzyny strzela bramke nie",
                        "obie druzyny strzela nie",
                        "gole z obu stron nie",
                        "bramki z obu stron nie"
                )
        ) {
            result.add(
                    new FootballMarket.BothTeamsToScore(
                            false
                    )
            );

            return;
        }

        if (
                hasBttsCue(
                        text
                )
        ) {
            result.add(
                    new FootballMarket.BothTeamsToScore(
                            true
                    )
            );
        }
    }

    /*
     * =========================================================
     * TOTAL GOALS
     * =========================================================
     */

    private static void parseTotalGoals(
            String text,
            String homeTeam,
            String awayTeam,
            List<FootballMarket.Condition> result
    ) {
        Matcher minimum =
                MINIMUM_TOTAL_PATTERN.matcher(
                        text
                );

        while (
                minimum.find()
        ) {
            String suffix =
                    text.substring(
                            minimum.end()
                    );

            if (
                    containsTeamReference(
                            suffix,
                            homeTeam
                    )
                            || containsTeamReference(
                            suffix,
                            awayTeam
                    )
            ) {
                continue;
            }

            Integer value =
                    wholeGoalNumber(
                            minimum.group(
                                    1
                            )
                    );

            if (
                    value != null
            ) {
                result.add(
                        new FootballMarket.MinimumTotalGoals(
                                value
                        )
                );
            }
        }

        Matcher ponad =
                PONAD_TOTAL_PATTERN.matcher(
                        text
                );

        while (
                ponad.find()
        ) {
            if (
                    !isPurePonadMatchTotal(
                            text,
                            homeTeam,
                            awayTeam,
                            ponad
                    )
            ) {
                continue;
            }

            BigDecimal line =
                    decimal(
                            ponad.group(
                                    1
                            )
                    );

            if (
                    line != null
            ) {
                result.add(
                        new FootballMarket.TotalGoals(
                                FootballMarket.TotalDirection.OVER,
                                line
                        )
                );
            }
        }

        Matcher explicit =
                TOTAL_PATTERN.matcher(
                        text
                );

        while (
                explicit.find()
        ) {
            String directionRaw =
                    explicit.group(
                            1
                    );

            BigDecimal line =
                    decimal(
                            explicit.group(
                                    2
                            )
                    );

            if (
                    line == null
            ) {
                continue;
            }

            FootballMarket.TotalDirection direction =
                    switch (
                            directionRaw
                            ) {
                        case "powyzej",
                             "over",
                             "wiecej niz" ->
                                FootballMarket.TotalDirection.OVER;

                        case "ponizej",
                             "under",
                             "mniej niz" ->
                                FootballMarket.TotalDirection.UNDER;

                        default ->
                                null;
                    };

            if (
                    direction != null
            ) {
                result.add(
                        new FootballMarket.TotalGoals(
                                direction,
                                line
                        )
                );
            }
        }

        Matcher signed =
                SIGNED_TOTAL_PATTERN.matcher(
                        text
                );

        while (
                signed.find()
        ) {
            BigDecimal line =
                    decimal(
                            signed.group(
                                    2
                            )
                    );

            if (
                    line == null
            ) {
                continue;
            }

            FootballMarket.TotalDirection direction =
                    "+".equals(
                            signed.group(
                                    1
                            )
                    )
                            ? FootballMarket.TotalDirection.OVER
                            : FootballMarket.TotalDirection.UNDER;

            result.add(
                    new FootballMarket.TotalGoals(
                            direction,
                            line
                    )
            );
        }
    }

    private static void parseMatchGoalRange(
            String text,
            List<FootballMarket.Condition> result
    ) {
        Matcher matcher =
                PURE_MATCH_GOAL_RANGE_PATTERN.matcher(text);

        if (!matcher.matches()) {
            return;
        }

        Integer minimum = wholeGoalNumber(matcher.group(1));
        Integer maximum = wholeGoalNumber(matcher.group(2));

        if (minimum == null || maximum == null || maximum < minimum) {
            return;
        }

        result.add(
                new FootballMarket.MatchGoalRange(
                        minimum,
                        maximum
                )
        );
    }

    private static boolean isPurePonadMatchTotal(
            String text,
            String homeTeam,
            String awayTeam,
            Matcher ponad
    ) {
        String prefix =
                text.substring(
                                0,
                                ponad.start()
                        )
                        .trim();

        String suffix =
                text.substring(
                                ponad.end()
                        )
                        .trim();

        if (
                !suffix.isBlank()
                        && !"w meczu".equals(
                        suffix
                )
        ) {
            return false;
        }

        if (
                prefix.isBlank()
        ) {
            return true;
        }

        String fixturePrefix =
                prefix.replaceFirst(
                                "\\s*-\\s*$",
                                ""
                        )
                        .trim();

        String homeVsAway =
                normalize(homeTeam)
                        + " vs "
                        + normalize(awayTeam);

        String awayVsHome =
                normalize(awayTeam)
                        + " vs "
                        + normalize(homeTeam);

        return suffix.equals(
                "w meczu"
        )
                && (
                fixturePrefix.equals(homeVsAway)
                        || fixturePrefix.equals(awayVsHome)
        );
    }

    private static Integer wholeGoalNumber(
            String raw
    ) {
        return switch (
                raw
                ) {
            case "jeden", "jedna", "jedno" -> 1;
            case "dwa", "dwie" -> 2;
            case "trzy" -> 3;
            case "cztery" -> 4;
            case "piec" -> 5;
            case "szesc" -> 6;
            case "siedem" -> 7;
            case "osiem" -> 8;
            case "dziewiec" -> 9;
            case "dziesiec" -> 10;
            default -> {
                try {
                    yield Integer.valueOf(
                            raw
                    );
                } catch (
                        NumberFormatException ignored
                ) {
                    yield null;
                }
            }
        };
    }

    /*
     * =========================================================
     * DOUBLE CHANCE
     * =========================================================
     */

    private static void parseDoubleChance(
            String text,
            String homeTeam,
            String awayTeam,
            List<FootballMarket.Condition> result
    ) {
        if (
                containsStandaloneToken(
                        text,
                        "1x"
                )
        ) {
            result.add(
                    new FootballMarket.DoubleChance(
                            FootballMarket.DoubleChanceSelection.HOME_OR_DRAW
                    )
            );
        }

        if (
                containsStandaloneToken(
                        text,
                        "x2"
                )
        ) {
            result.add(
                    new FootballMarket.DoubleChance(
                            FootballMarket.DoubleChanceSelection.AWAY_OR_DRAW
                    )
            );
        }

        if (
                containsStandaloneToken(
                        text,
                        "12"
                )
        ) {
            result.add(
                    new FootballMarket.DoubleChance(
                            FootballMarket.DoubleChanceSelection.HOME_OR_AWAY
                    )
            );
        }

        /*
         * X/Miedź
         * Miedź/X
         */
        TeamSide slashSubject =
                resolveSlashXSubject(
                        text,
                        homeTeam,
                        awayTeam
                );

        if (
                slashSubject == TeamSide.HOME
        ) {
            result.add(
                    new FootballMarket.DoubleChance(
                            FootballMarket.DoubleChanceSelection.HOME_OR_DRAW
                    )
            );
        }

        if (
                slashSubject == TeamSide.AWAY
        ) {
            result.add(
                    new FootballMarket.DoubleChance(
                            FootballMarket.DoubleChanceSelection.AWAY_OR_DRAW
                    )
            );
        }

        TeamSide subject =
                resolveSubjectTeam(
                        text,
                        homeTeam,
                        awayTeam
                );

        if (
                subject == TeamSide.NONE
        ) {
            return;
        }

        if (
                text.contains(
                        "nie przegra"
                )
                        || text.contains(
                        "remis lub"
                )
                        || text.contains(
                        "lub remis"
                )
                        || text.contains(
                        "wygra lub zremisuje"
                )
                        || text.contains(
                        "zremisuje lub wygra"
                )
        ) {
            result.add(
                    new FootballMarket.DoubleChance(
                            subject == TeamSide.HOME
                                    ? FootballMarket.DoubleChanceSelection.HOME_OR_DRAW
                                    : FootballMarket.DoubleChanceSelection.AWAY_OR_DRAW
                    )
            );
        }
    }

    private static TeamSide resolveSlashXSubject(
            String text,
            String homeTeam,
            String awayTeam
    ) {
        Set<TeamSide> found =
                new HashSet<>();

        Matcher xSlash =
                X_SLASH_TEAM_PATTERN.matcher(
                        text
                );

        while (
                xSlash.find()
        ) {
            TeamSide side =
                    resolveSingleTeamToken(
                            xSlash.group(
                                    1
                            ),
                            homeTeam,
                            awayTeam
                    );

            if (
                    side != TeamSide.NONE
            ) {
                found.add(
                        side
                );
            }
        }

        Matcher slashX =
                TEAM_SLASH_X_PATTERN.matcher(
                        text
                );

        while (
                slashX.find()
        ) {
            TeamSide side =
                    resolveSingleTeamToken(
                            slashX.group(
                                    1
                            ),
                            homeTeam,
                            awayTeam
                    );

            if (
                    side != TeamSide.NONE
            ) {
                found.add(
                        side
                );
            }
        }

        if (
                found.size() != 1
        ) {
            return TeamSide.NONE;
        }

        return found.iterator()
                .next();
    }

    private static TeamSide resolveSingleTeamToken(
            String rawToken,
            String homeTeam,
            String awayTeam
    ) {
        String token =
                normalize(
                        rawToken
                );

        boolean home =
                tokenBelongsToTeam(
                        token,
                        homeTeam
                );

        boolean away =
                tokenBelongsToTeam(
                        token,
                        awayTeam
                );

        if (
                home == away
        ) {
            return TeamSide.NONE;
        }

        return home
                ? TeamSide.HOME
                : TeamSide.AWAY;
    }

    private static boolean tokenBelongsToTeam(
            String token,
            String team
    ) {
        if (
                token == null
                        || token.isBlank()
        ) {
            return false;
        }

        String normalizedTeam =
                normalize(
                        team
                );

        for (
                String teamToken :
                tokens(
                        normalizedTeam
                )
        ) {
            if (
                    isGenericTeamToken(
                            teamToken
                    )
            ) {
                continue;
            }

            if (
                    tokenEquivalent(
                            teamToken,
                            token
                    )
            ) {
                return true;
            }
        }

        for (
                String alias :
                TEAM_ALIASES.getOrDefault(
                        normalizedTeam,
                        List.of()
                )
        ) {
            for (
                    String aliasToken :
                    tokens(
                            normalize(
                                    alias
                            )
                    )
            ) {
                if (
                        tokenEquivalent(
                                aliasToken,
                                token
                        )
                ) {
                    return true;
                }
            }
        }

        return false;
    }

    /*
     * =========================================================
     * MATCH RESULT
     * =========================================================
     */

    private static void parseMatchResult(
            String text,
            String homeTeam,
            String awayTeam,
            List<FootballMarket.Condition> result
    ) {
        /*
         * Bardzo ważne:
         *
         * X/Miedź
         *
         * nie może trafić tutaj jako zwykły remis.
         */
        if (
                hasDoubleChanceCue(
                        text
                )
        ) {
            return;
        }

        if (
                text.equals(
                        "1"
                )
                        || containsCompoundResultSelection(
                        text,
                        "1"
                )
        ) {
            result.add(
                    new FootballMarket.MatchResult(
                            FootballMarket.MatchResultSelection.HOME
                    )
            );

            return;
        }

        if (
                text.equals(
                        "x"
                )
                        || containsCompoundResultSelection(
                        text,
                        "x"
                )
        ) {
            result.add(
                    new FootballMarket.MatchResult(
                            FootballMarket.MatchResultSelection.DRAW
                    )
            );

            return;
        }

        if (
                text.equals(
                        "2"
                )
                        || containsCompoundResultSelection(
                        text,
                        "2"
                )
        ) {
            result.add(
                    new FootballMarket.MatchResult(
                            FootballMarket.MatchResultSelection.AWAY
                    )
            );

            return;
        }

        if (
                !containsAny(
                        text,
                        "wygra",
                        "wygrana",
                        "zwyciestwo",
                        "zwyciezy"
                )
        ) {
            return;
        }

        TeamSide subject =
                resolveSubjectTeam(
                        text,
                        homeTeam,
                        awayTeam
                );

        if (
                subject == TeamSide.HOME
        ) {
            result.add(
                    new FootballMarket.MatchResult(
                            FootballMarket.MatchResultSelection.HOME
                    )
            );
        }

        if (
                subject == TeamSide.AWAY
        ) {
            result.add(
                    new FootballMarket.MatchResult(
                            FootballMarket.MatchResultSelection.AWAY
                    )
            );
        }
    }

    /*
     * =========================================================
     * TEAM IDENTIFICATION
     * =========================================================
     */

    private static TeamSide resolveSubjectTeam(
            String text,
            String homeTeam,
            String awayTeam
    ) {
        boolean home =
                containsTeamReference(
                        text,
                        homeTeam
                );

        boolean away =
                containsTeamReference(
                        text,
                        awayTeam
                );

        if (
                home == away
        ) {
            return TeamSide.NONE;
        }

        return home
                ? TeamSide.HOME
                : TeamSide.AWAY;
    }

    private static boolean containsTeamReference(
            String text,
            String team
    ) {
        String normalizedTeam =
                normalize(
                        team
                );

        if (
                normalizedTeam.isBlank()
        ) {
            return false;
        }

        if (
                containsPhrase(
                        text,
                        normalizedTeam
                )
        ) {
            return true;
        }

        List<String> aliases =
                TEAM_ALIASES.getOrDefault(
                        normalizedTeam,
                        List.of()
                );

        for (
                String alias :
                aliases
        ) {
            if (
                    containsPhrase(
                            text,
                            normalize(
                                    alias
                            )
                    )
            ) {
                return true;
            }
        }

        List<String> textTokens =
                tokens(
                        text
                );

        List<String> teamTokens =
                tokens(
                        normalizedTeam
                );

        for (
                String teamToken :
                teamTokens
        ) {
            if (
                    isGenericTeamToken(
                            teamToken
                    )
            ) {
                continue;
            }

            for (
                    String textToken :
                    textTokens
            ) {
                if (
                        tokenEquivalent(
                                teamToken,
                                textToken
                        )
                ) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean tokenEquivalent(
            String teamToken,
            String textToken
    ) {
        if (
                teamToken == null
                        || textToken == null
        ) {
            return false;
        }

        if (
                teamToken.equals(
                        textToken
                )
        ) {
            return true;
        }

        if (
                teamToken.length() < 4
                        || textToken.length() < 4
        ) {
            return false;
        }

        String localizedTeamToken =
                teamToken.replace(
                        'y',
                        'i'
                );

        String localizedTextToken =
                textToken.replace(
                        'y',
                        'i'
                );

        if (
                localizedTextToken.startsWith(
                        localizedTeamToken
                )
                        && localizedTextToken.length()
                        <= localizedTeamToken.length() + 7
        ) {
            return true;
        }

        /*
         * Manchester -> Manchesteru
         */
        if (
                textToken.startsWith(
                        teamToken
                )
                        && textToken.length()
                        <= teamToken.length() + 7
        ) {
            return true;
        }

        /*
         * Sevilla -> Sevilli
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
                    textToken.startsWith(
                            stem
                    )
                            && textToken.length()
                            <= teamToken.length() + 5
            ) {
                return true;
            }
        }

        /*
         * Polish team-name inflection may replace only the final vowel:
         * Podbeskidzie -> Podbeskidzia. Localized Italian "gn" is written
         * as "ni", so the same rule also handles localized club names.
         */
        if (
                samePolishInflectionStem(
                        teamToken,
                        textToken
                )
        ) {
            return true;
        }

        return false;
    }

    private static boolean samePolishInflectionStem(
            String left,
            String right
    ) {
        String normalizedLeft =
                left.replace(
                        "gn",
                        "ni"
                );

        String normalizedRight =
                right.replace(
                        "gn",
                        "ni"
                );

        if (
                normalizedLeft.length() < 5
                        || normalizedRight.length() < 5
        ) {
            return false;
        }

        return normalizedLeft.substring(
                        0,
                        normalizedLeft.length() - 1
                )
                .equals(
                        normalizedRight.substring(
                                0,
                                normalizedRight.length() - 1
                        )
                );
    }

    /*
     * =========================================================
     * TEAM TOTAL GUARD
     * =========================================================
     */

    private static boolean looksLikeTeamTotal(
            String text,
            String homeTeam,
            String awayTeam
    ) {
        return minimumScoringHasTeamSubject(
                text,
                homeTeam,
                awayTeam
        )
                || teamBeforeTotal(
                text,
                normalize(
                        homeTeam
                )
        )
                || teamBeforeTotal(
                text,
                normalize(
                        awayTeam
                )
        );
    }

    private static boolean minimumScoringHasTeamSubject(
            String text,
            String homeTeam,
            String awayTeam
    ) {
        Pattern pattern =
                Pattern.compile(
                        "^(.+?)\\s+"
                                + "(?:strzeli|zdobedzie)\\b"
                                + ".{0,25}"
                                + "\\b(?:co najmniej|minimum)\\b"
                );

        Matcher matcher =
                pattern.matcher(
                        text
                );

        if (
                !matcher.find()
        ) {
            return false;
        }

        return resolveSubjectTeam(
                matcher.group(
                        1
                ),
                homeTeam,
                awayTeam
        ) != TeamSide.NONE;
    }

    private static boolean teamBeforeTotal(
            String text,
            String normalizedTeam
    ) {
        if (
                normalizedTeam.isBlank()
        ) {
            return false;
        }

        List<String> variants =
                new ArrayList<>();

        variants.add(
                normalizedTeam
        );

        variants.addAll(
                TEAM_ALIASES.getOrDefault(
                        normalizedTeam,
                        List.of()
                )
        );

        for (
                String rawVariant :
                variants
        ) {
            String variant =
                    normalize(
                            rawVariant
                    );

            for (
                    String token :
                    tokens(
                            variant
                    )
            ) {
                if (
                        isGenericTeamToken(
                                token
                        )
                ) {
                    continue;
                }

                /*
                 * Torino powyżej 0.5 gola
                 * Śląsk poniżej 1.5 gola
                 */
                Pattern direct =
                        Pattern.compile(
                                "\\b"
                                        + Pattern.quote(
                                        token
                                )
                                        + "\\b\\s+"
                                        + "(?:"
                                        + "powyzej"
                                        + "|ponizej"
                                        + "|over"
                                        + "|under"
                                        + "|wiecej\\s+niz"
                                        + "|mniej\\s+niz"
                                        + ")\\b"
                        );

                if (
                        direct.matcher(
                                        text
                                )
                                .find()
                ) {
                    return true;
                }

                /*
                 * Barcelona strzeli więcej niż 1.5 goli
                 */
                Pattern scoring =
                        Pattern.compile(
                                "\\b"
                                        + Pattern.quote(
                                        token
                                )
                                        + "\\b"
                                        + ".{0,30}"
                                        + "\\b(?:strzeli|zdobedzie)\\b"
                                        + ".{0,25}"
                                        + "\\b(?:"
                                        + "powyzej"
                                        + "|ponizej"
                                        + "|over"
                                        + "|under"
                                        + "|wiecej\\s+niz"
                                        + "|mniej\\s+niz"
                                        + ")\\b"
                        );

                if (
                        scoring.matcher(
                                        text
                                )
                                .find()
                ) {
                    return true;
                }

                /*
                 * Panathinaikos -1.5 goli
                 */
                Pattern signed =
                        Pattern.compile(
                                "\\b"
                                        + Pattern.quote(
                                        token
                                )
                                        + "\\b\\s+[+-]\\s*"
                                        + "[0-9]+(?:[.,][0-9]+)?\\s*"
                                        + "(?:gola|goli|gol|bramki|bramek|bramka)"
                                        + "\\b"
                        );

                if (
                        signed.matcher(
                                        text
                                )
                                .find()
                ) {
                    return true;
                }
            }
        }

        return false;
    }

    /*
     * =========================================================
     * OTHER GUARDS
     * =========================================================
     */

    private static boolean containsPlayerLikeScorerSignal(
            String text
    ) {
        return text.contains(
                "strzeli gola"
        )
                || text.contains(
                "strzeli bramke"
        )
                || text.contains(
                "zdobedzie gola"
        )
                || text.contains(
                "zdobedzie bramke"
        );
    }

    private static boolean containsUnsupportedSignal(
            String text
    ) {
        for (
                String signal :
                UNSUPPORTED_SIGNALS
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

    private static List<FootballMarket.Condition> deduplicate(
            List<FootballMarket.Condition> conditions
    ) {
        Set<FootballMarket.Condition> seen =
                new HashSet<>();

        List<FootballMarket.Condition> result =
                new ArrayList<>();

        for (
                FootballMarket.Condition condition :
                conditions
        ) {
            if (
                    seen.add(
                            condition
                    )
            ) {
                result.add(
                        condition
                );
            }
        }

        return result;
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

    private static boolean containsPhrase(
            String text,
            String phrase
    ) {
        if (
                text == null
                        || phrase == null
                        || phrase.isBlank()
        ) {
            return false;
        }

        return (
                " " + text + " "
        ).contains(
                " " + phrase + " "
        );
    }

    private static boolean containsStandaloneToken(
            String text,
            String token
    ) {
        return Pattern.compile(
                        "(?:^|[\\s+,/])"
                                + Pattern.quote(
                                token
                        )
                                + "(?:$|[\\s+,/])"
                )
                .matcher(
                        text
                )
                .find();
    }

    private static boolean containsCompoundResultSelection(
            String text,
            String selection
    ) {
        return Pattern.compile(
                        "(?:^|/)"
                                + Pattern.quote(
                                selection
                        )
                                + "(?:/|$)"
                )
                .matcher(
                        text
                )
                .find();
    }

    private static boolean isGenericTeamToken(
            String token
    ) {
        return switch (
                token
                ) {
            case "fc",
                 "cf",
                 "ks",
                 "mks",
                 "rks",
                 "sc",
                 "afc",
                 "united",
                 "city" ->
                    true;

            default ->
                    false;
        };
    }

    private static List<String> tokens(
            String value
    ) {
        if (
                value == null
                        || value.isBlank()
        ) {
            return List.of();
        }

        return List.of(
                value.split(
                        "\\s+"
                )
        );
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

    /*
     * =========================================================
     * ALIASES
     * =========================================================
     */

    private static Map<String, List<String>>
    createTeamAliases() {

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

    private enum TeamSide {
        HOME,
        AWAY,
        NONE
    }
}
