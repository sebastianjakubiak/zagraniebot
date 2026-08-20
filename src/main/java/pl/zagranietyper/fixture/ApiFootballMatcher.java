package pl.zagranietyper.fixture;

import pl.zagranietyper.model.ApiFootballFixture;
import pl.zagranietyper.model.ApiFootballMatch;
import pl.zagranietyper.model.ApiFootballResolutionCandidate;
import pl.zagranietyper.model.BetType;
import pl.zagranietyper.model.ResolutionConfidence;
import pl.zagranietyper.model.ResolvedSport;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class ApiFootballMatcher {

    private static final ZoneId WARSAW =
            ZoneId.of("Europe/Warsaw");

    private static final double MIN_TEAM_SCORE =
            0.75;

    private static final double MIN_BOTH_SCORE =
            0.84;

    private static final double MIN_NEW_RESOLUTION_GAP =
            0.10;

    private static final double MIN_LOCAL_CONFIRMATION_GAP =
            0.06;

    private static final double CURRENT_EVENT_PAIR_BONUS =
            0.35;

    private static final int TIP_SUBJECT_SCAN_TOKENS =
            4;

    private static final Set<String> GENERIC =
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
                    "as"
            );

    private static final Set<String> AMBIGUOUS_TEAM_TOKENS =
            Set.of(
                    "united",
                    "city",
                    "manchester"
            );

    private static final Set<String> TIP_PREFIXES =
            Set.of(
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

    private static final Pattern YOUTH_SUFFIX =
            Pattern.compile(
                    ".*\\bu(?:17|18|19|20|21|22|23)\\b.*"
            );

    private static final Pattern RESERVE_SUFFIX =
            Pattern.compile(
                    ".*\\b(?:ii|iii|reserves?|reserve)\\b.*"
            );

    private static final Pattern SENTENCE_SPLIT =
            Pattern.compile(
                    "(?<=[.!?])\\s+"
            );

    private static final Pattern DIACRITIC_MARKS =
            Pattern.compile(
                    "\\p{M}+"
            );

    private static final Pattern NON_ALPHANUMERIC =
            Pattern.compile(
                    "[^\\p{L}\\p{N}]+"
            );

    private static final Pattern MULTIPLE_SPACES =
            Pattern.compile(
                    "\\s+"
            );

    private static final Map<String, TeamView> TEAM_CACHE =
            new ConcurrentHashMap<>();

    private static final Map<String, List<String>> TEAM_ALIASES =
            createTeamAliases();

    public ApiFootballMatch match(
            ApiFootballResolutionCandidate candidate,
            List<ApiFootballFixture> fixtures
    ) {
        if (
                candidate.publishedAt() == null
                        || fixtures == null
                        || fixtures.isEmpty()
        ) {
            return null;
        }

        if (
                candidate.sport() != ResolvedSport.FOOTBALL
                        && candidate.sport() != ResolvedSport.UNKNOWN
        ) {
            return null;
        }

        EvidenceView localEvidence =
                EvidenceView.of(
                        join(
                                candidate.tipTitle(),
                                candidate.heading(),
                                candidate.previousText()
                        )
                );

        EvidenceView allEvidence =
                EvidenceView.of(
                        join(
                                candidate.postTitle(),
                                candidate.tipTitle(),
                                candidate.heading(),
                                candidate.previousText()
                        )
                );

        if (
                candidate.sport() == ResolvedSport.UNKNOWN
                        && containsStrongNonFootballSignal(
                        allEvidence
                )
        ) {
            return null;
        }

        EvidenceView matchupEvidence;

        if (
                candidate.betType() == BetType.SINGLE
                        && candidate.betLegCount() == 1
        ) {
            matchupEvidence =
                    allEvidence;
        } else {
            matchupEvidence =
                    localEvidence;
        }

        if (
                matchupEvidence.isBlank()
        ) {
            return null;
        }

        EvidenceView primaryEvidence =
                EvidenceView.of(
                        buildPrimaryEvidence(
                                candidate
                        )
                );

        EvidenceView tipEvidence =
                EvidenceView.of(
                        stripTipPrefixes(
                                candidate.tipTitle()
                        )
                );

        List<PreparedFixture> preparedFixtures =
                new ArrayList<>(
                        fixtures.size()
                );

        boolean hasTipSubjectAnchor =
                false;

        for (
                ApiFootballFixture fixture :
                fixtures
        ) {
            TeamView home =
                    teamView(
                            fixture.homeTeamName()
                    );

            TeamView away =
                    teamView(
                            fixture.awayTeamName()
                    );

            if (
                    !fixtureVariantCompatible(
                            home,
                            away,
                            matchupEvidence
                    )
            ) {
                continue;
            }

            boolean tipSubjectMatch =
                    fixtureMatchesTipSubject(
                            home,
                            away,
                            tipEvidence
                    );

            if (
                    tipSubjectMatch
            ) {
                hasTipSubjectAnchor =
                        true;
            }

            preparedFixtures.add(
                    new PreparedFixture(
                            fixture,
                            home,
                            away,
                            tipSubjectMatch
                    )
            );
        }

        if (
                preparedFixtures.isEmpty()
        ) {
            return null;
        }

        LocalDate publicationDate =
                candidate.publishedAt()
                        .atZone(
                                WARSAW
                        )
                        .toLocalDate();

        List<Scored> scored =
                new ArrayList<>();

        for (
                PreparedFixture prepared :
                preparedFixtures
        ) {
            if (
                    hasTipSubjectAnchor
                            && !prepared.tipSubjectMatch()
            ) {
                continue;
            }

            double homeScore =
                    teamScore(
                            prepared.home(),
                            matchupEvidence
                    );

            double awayScore =
                    teamScore(
                            prepared.away(),
                            matchupEvidence
                    );

            boolean both =
                    homeScore >= MIN_TEAM_SCORE
                            && awayScore >= MIN_TEAM_SCORE
                            && bothTeamsDistinctlySupported(
                            prepared.home(),
                            prepared.away(),
                            matchupEvidence
                    );

            if (
                    !both
            ) {
                continue;
            }

            double primaryHome =
                    teamScore(
                            prepared.home(),
                            primaryEvidence
                    );

            double primaryAway =
                    teamScore(
                            prepared.away(),
                            primaryEvidence
                    );

            boolean primaryBoth =
                    primaryHome >= MIN_TEAM_SCORE
                            && primaryAway >= MIN_TEAM_SCORE
                            && bothTeamsDistinctlySupported(
                            prepared.home(),
                            prepared.away(),
                            primaryEvidence
                    );

            double primaryBonus =
                    primaryBoth
                            ? CURRENT_EVENT_PAIR_BONUS
                            : 0.0;

            long days =
                    ChronoUnit.DAYS.between(
                            publicationDate,
                            prepared.fixture()
                                    .fixtureDate()
                    );

            double score =
                    (
                            homeScore
                                    + awayScore
                    ) / 2.0
                            + dateBonus(
                            days
                    )
                            + primaryBonus;

            scored.add(
                    new Scored(
                            prepared.fixture(),
                            primaryBoth,
                            primaryBonus,
                            score
                    )
            );
        }

        if (
                scored.isEmpty()
        ) {
            return null;
        }

        scored.sort(
                Comparator.comparingDouble(
                                Scored::score
                        )
                        .reversed()
        );

        Scored best =
                scored.getFirst();

        double second =
                scored.size() > 1
                        ? scored.get(1).score()
                        : 0.0;

        double gap =
                best.score()
                        - second;

        double requiredGap =
                candidate.hasLocalResolution()
                        ? MIN_LOCAL_CONFIRMATION_GAP
                        : MIN_NEW_RESOLUTION_GAP;

        if (
                best.score() >= MIN_BOTH_SCORE
                        && gap >= requiredGap
        ) {
            return result(
                    best,
                    gap,
                    requiredGap
            );
        }

        return null;
    }

    /*
     * =========================================================
     * TIP SUBJECT
     * =========================================================
     */

    private static boolean fixtureMatchesTipSubject(
            TeamView home,
            TeamView away,
            EvidenceView tipEvidence
    ) {
        return teamMatchesTipSubject(
                home,
                tipEvidence
        )
                || teamMatchesTipSubject(
                away,
                tipEvidence
        );
    }

    private static boolean teamMatchesTipSubject(
            TeamView team,
            EvidenceView tipEvidence
    ) {
        if (
                tipEvidence.isBlank()
                        || tipEvidence.tokens()
                        .isEmpty()
        ) {
            return false;
        }

        for (
                List<String> nameVariant :
                team.nameVariants()
        ) {
            if (
                    startsWithFuzzyTeamPhrase(
                            tipEvidence.tokens(),
                            nameVariant
                    )
            ) {
                return true;
            }
        }

        int limit =
                Math.min(
                        TIP_SUBJECT_SCAN_TOKENS,
                        tipEvidence.tokens()
                                .size()
                );

        for (
                int i = 0;
                i < limit;
                i++
        ) {
            String tipToken =
                    tipEvidence.tokens()
                            .get(
                                    i
                            );

            for (
                    String teamToken :
                    team.allDiscriminativeTokens()
            ) {
                if (
                        tokenEquivalent(
                                teamToken,
                                tipToken
                        )
                ) {
                    return true;
                }
            }
        }

        return false;
    }

    private static String stripTipPrefixes(
            String value
    ) {
        String result =
                normalize(
                        value
                );

        boolean changed;

        do {
            changed =
                    false;

            for (
                    String rawPrefix :
                    TIP_PREFIXES
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
                                            prefix.length()
                                                    + 1
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

    /*
     * =========================================================
     * PRIMARY / CURRENT EVENT
     * =========================================================
     */

    private static String buildPrimaryEvidence(
            ApiFootballResolutionCandidate candidate
    ) {
        StringBuilder result =
                new StringBuilder();

        append(
                result,
                candidate.tipTitle()
        );

        append(
                result,
                candidate.heading()
        );

        if (
                candidate.betType() == BetType.SINGLE
                        && candidate.betLegCount() == 1
        ) {
            append(
                    result,
                    candidate.postTitle()
            );
        }

        String previousText =
                candidate.previousText();

        if (
                previousText == null
                        || previousText.isBlank()
        ) {
            return result.toString();
        }

        String[] sentences =
                SENTENCE_SPLIT.split(
                        previousText.trim()
                );

        for (
                int i = 0;
                i < Math.min(
                        2,
                        sentences.length
                );
                i++
        ) {
            append(
                    result,
                    sentences[i]
            );
        }

        for (
                int i = 0;
                i < sentences.length;
                i++
        ) {
            String normalizedSentence =
                    normalize(
                            sentences[i]
                    );

            if (
                    !hasDecisionCue(
                            normalizedSentence
                    )
            ) {
                continue;
            }

            if (
                    i > 0
            ) {
                append(
                        result,
                        sentences[i - 1]
                );
            }

            append(
                    result,
                    sentences[i]
            );
        }

        return result.toString();
    }

    private static boolean hasDecisionCue(
            String sentence
    ) {
        if (
                sentence == null
                        || sentence.isBlank()
        ) {
            return false;
        }

        String padded =
                " " + sentence + " ";

        return padded.contains(
                " typuje "
        )
                || padded.contains(
                " obstawiam "
        )
                || padded.contains(
                " stawiam "
        )
                || padded.contains(
                " postawie "
        )
                || padded.contains(
                " wybieram "
        )
                || padded.contains(
                " wybralem "
        )
                || padded.contains(
                " moim typem "
        )
                || padded.contains(
                " moim wyborem "
        )
                || padded.contains(
                " zdecydowalem "
        )
                || padded.contains(
                " zdecyduje "
        )
                || padded.contains(
                " sklaniam sie "
        )
                || padded.contains(
                " wierze "
        )
                || padded.contains(
                " licze "
        )
                || padded.contains(
                " spodziewam sie "
        );
    }

    /*
     * =========================================================
     * BOTH TEAMS
     * =========================================================
     */

    private static boolean bothTeamsDistinctlySupported(
            TeamView home,
            TeamView away,
            EvidenceView evidence
    ) {
        if (
                evidence.isBlank()
        ) {
            return false;
        }

        if (
                containsFuzzyTeamPhrase(
                        evidence,
                        home
                )
                        && containsFuzzyTeamPhrase(
                        evidence,
                        away
                )
        ) {
            return true;
        }

        List<String> homeTokens =
                home.allDiscriminativeTokens();

        List<String> awayTokens =
                away.allDiscriminativeTokens();

        if (
                homeTokens.isEmpty()
                        || awayTokens.isEmpty()
        ) {
            return false;
        }

        Set<String> shared =
                new HashSet<>(
                        homeTokens
                );

        shared.retainAll(
                awayTokens
        );

        boolean homeSupported =
                false;

        for (
                String token :
                homeTokens
        ) {
            if (
                    shared.contains(
                            token
                    )
            ) {
                continue;
            }

            if (
                    containsTeamToken(
                            evidence,
                            token
                    )
            ) {
                homeSupported =
                        true;

                break;
            }
        }

        if (
                !homeSupported
        ) {
            return false;
        }

        for (
                String token :
                awayTokens
        ) {
            if (
                    shared.contains(
                            token
                    )
            ) {
                continue;
            }

            if (
                    containsTeamToken(
                            evidence,
                            token
                    )
            ) {
                return true;
            }
        }

        return false;
    }

    /*
     * =========================================================
     * NON FOOTBALL
     * =========================================================
     */

    private static boolean containsStrongNonFootballSignal(
            EvidenceView evidence
    ) {
        String text =
                evidence.text();

        if (
                containsAny(
                        text,

                        "handicap punktowy",

                        "siatkar",
                        "siatkow",
                        "volleyball",
                        "liga narodow",
                        "pluslig",

                        "koszyk",
                        "basketball",
                        "euroliga",
                        "euroleague",

                        "hokej",
                        "hockey",

                        "tenis",
                        "tennis",

                        "rzutki",
                        "checkout",

                        "zuzel",
                        "zuzlow",
                        "speedway",
                        "pge ekstraliga",
                        "metalkas",

                        "esport",
                        "counter strike",
                        "league of legends",

                        "pilka reczna",
                        "handball",
                        "orlen superliga",

                        "badminton",
                        "baseball",
                        "snooker"
                )
        ) {
            return true;
        }

        return containsAnyExactToken(
                evidence,
                "vnl",
                "nba",
                "wnba",
                "nhl",
                "atp",
                "wta",
                "ufc",
                "ksw",
                "mma",
                "cs2",
                "owal",
                "apator",
                "wlokniarz",
                "kpr"
        );
    }

    /*
     * =========================================================
     * VARIANTS
     * =========================================================
     */

    private static boolean fixtureVariantCompatible(
            TeamView home,
            TeamView away,
            EvidenceView evidence
    ) {
        return teamVariantCompatible(
                home,
                evidence
        )
                && teamVariantCompatible(
                away,
                evidence
        );
    }

    private static boolean teamVariantCompatible(
            TeamView team,
            EvidenceView evidence
    ) {
        if (
                team.normalizedName()
                        .isBlank()
        ) {
            return false;
        }

        if (
                team.women()
        ) {
            return containsAny(
                    evidence.text(),
                    "women",
                    "ladies",
                    "female",
                    "kobiet",
                    "kobieca",
                    "kobiece",
                    "zenska",
                    "zenskie",
                    "femen"
            );
        }

        if (
                team.youth()
                        || team.reserve()
                        || team.bTeam()
        ) {
            return containsFuzzyTeamPhrase(
                    evidence,
                    team
            );
        }

        return true;
    }

    /*
     * =========================================================
     * SCORE
     * =========================================================
     */

    private static double teamScore(
            TeamView team,
            EvidenceView evidence
    ) {
        if (
                evidence.isBlank()
                        || team.normalizedName()
                        .isBlank()
        ) {
            return 0.0;
        }

        if (
                containsFuzzyTeamPhrase(
                        evidence,
                        team
                )
        ) {
            return 1.0;
        }

        double best =
                0.0;

        for (
                List<String> tokens :
                team.nameVariants()
        ) {
            double variantScore =
                    tokenScore(
                            tokens,
                            evidence
                    );

            best =
                    Math.max(
                            best,
                            variantScore
                    );
        }

        return best;
    }

    private static double tokenScore(
            List<String> tokens,
            EvidenceView evidence
    ) {
        List<String> discriminative =
                discriminativeTokens(
                        tokens
                );

        if (
                discriminative.isEmpty()
        ) {
            return 0.0;
        }

        int matched =
                0;

        int strongMatched =
                0;

        for (
                String token :
                discriminative
        ) {
            if (
                    containsTeamToken(
                            evidence,
                            token
                    )
            ) {
                matched++;

                if (
                        token.length() >= 6
                ) {
                    strongMatched++;
                }
            }
        }

        if (
                matched == discriminative.size()
        ) {
            return 0.96;
        }

        double coverage =
                (double) matched
                        / discriminative.size();

        if (
                coverage >= 0.75
        ) {
            return 0.88;
        }

        if (
                strongMatched >= 1
                        && discriminative.size() <= 2
        ) {
            return 0.82;
        }

        return 0.0;
    }

    /*
     * =========================================================
     * TEAM CACHE / ALIASES
     * =========================================================
     */

    private static TeamView teamView(
            String teamName
    ) {
        String key =
                teamName == null
                        ? ""
                        : teamName;

        return TEAM_CACHE.computeIfAbsent(
                key,
                ApiFootballMatcher::createTeamView
        );
    }

    private static TeamView createTeamView(
            String teamName
    ) {
        String normalized =
                normalize(
                        teamName
                );

        List<String> primaryTokens =
                tokensFromNormalized(
                        normalized
                );

        List<List<String>> variants =
                new ArrayList<>();

        if (
                !primaryTokens.isEmpty()
        ) {
            variants.add(
                    List.copyOf(
                            primaryTokens
                    )
            );
        }

        List<String> aliases =
                TEAM_ALIASES.getOrDefault(
                        normalized,
                        List.of()
                );

        for (
                String alias :
                aliases
        ) {
            List<String> aliasTokens =
                    tokensFromNormalized(
                            normalize(
                                    alias
                            )
                    );

            if (
                    !aliasTokens.isEmpty()
            ) {
                variants.add(
                        List.copyOf(
                                aliasTokens
                        )
                );
            }
        }

        List<String> allDiscriminative =
                new ArrayList<>();

        for (
                List<String> variant :
                variants
        ) {
            for (
                    String token :
                    discriminativeTokens(
                            variant
                    )
            ) {
                if (
                        !allDiscriminative.contains(
                                token
                        )
                ) {
                    allDiscriminative.add(
                            token
                    );
                }
            }
        }

        boolean women =
                normalized.endsWith(
                        " w"
                )
                        || containsExactToken(
                        primaryTokens,
                        "women"
                )
                        || containsExactToken(
                        primaryTokens,
                        "ladies"
                )
                        || containsExactToken(
                        primaryTokens,
                        "femenino"
                )
                        || containsExactToken(
                        primaryTokens,
                        "femenina"
                );

        boolean youth =
                YOUTH_SUFFIX
                        .matcher(
                                normalized
                        )
                        .matches();

        boolean reserve =
                RESERVE_SUFFIX
                        .matcher(
                                normalized
                        )
                        .matches();

        boolean bTeam =
                normalized.endsWith(
                        " b"
                );

        return new TeamView(
                normalized,
                List.copyOf(
                        primaryTokens
                ),
                List.copyOf(
                        variants
                ),
                List.copyOf(
                        allDiscriminative
                ),
                women,
                youth,
                reserve,
                bTeam
        );
    }

    private static Map<String, List<String>>
    createTeamAliases() {

        Map<String, List<String>> result =
                new LinkedHashMap<>();

        /*
         * Europa
         */
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
                "Bulgaria",
                "Bulgaria"
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
                "Northern Ireland",
                "Irlandia Polnocna"
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
                "Bosnia and Herzegovina",
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

        /*
         * Ameryki
         */
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

        /*
         * Azja
         */
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
                "United Arab Emirates",
                "Zjednoczone Emiraty Arabskie",
                "ZEA"
        );

        /*
         * Afryka
         */
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
                "Côte d'Ivoire",
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
        result.put(
                normalize(
                        apiName
                ),
                List.of(
                        aliases
                )
        );
    }

    /*
     * =========================================================
     * TOKEN MATCHING
     * =========================================================
     */

    private static List<String> discriminativeTokens(
            List<String> source
    ) {
        List<String> result =
                new ArrayList<>();

        for (
                String token :
                source
        ) {
            if (
                    GENERIC.contains(
                            token
                    )
                            || AMBIGUOUS_TEAM_TOKENS.contains(
                            token
                    )
            ) {
                continue;
            }

            result.add(
                    token
            );
        }

        return result;
    }

    private static boolean containsTeamToken(
            EvidenceView evidence,
            String teamToken
    ) {
        if (
                teamToken == null
                        || teamToken.isBlank()
        ) {
            return false;
        }

        if (
                evidence.exactTokens()
                        .contains(
                                teamToken
                        )
        ) {
            return true;
        }

        for (
                String evidenceToken :
                evidence.tokens()
        ) {
            if (
                    tokenEquivalent(
                            teamToken,
                            evidenceToken
                    )
            ) {
                return true;
            }
        }

        return false;
    }

    private static boolean tokenEquivalent(
            String teamToken,
            String evidenceToken
    ) {
        if (
                teamToken == null
                        || evidenceToken == null
        ) {
            return false;
        }

        if (
                teamToken.equals(
                        evidenceToken
                )
        ) {
            return true;
        }

        if (
                teamToken.length() < 5
                        || evidenceToken.length() < 4
        ) {
            return false;
        }

        if (
                evidenceToken.startsWith(
                        teamToken
                )
                        && evidenceToken.length()
                        <= teamToken.length() + 7
        ) {
            return true;
        }

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
                    evidenceToken.startsWith(
                            stem
                    )
                            && evidenceToken.length()
                            <= teamToken.length() + 5
            ) {
                return true;
            }
        }

        return false;
    }

    private static boolean containsFuzzyTeamPhrase(
            EvidenceView evidence,
            TeamView team
    ) {
        for (
                List<String> variant :
                team.nameVariants()
        ) {
            if (
                    containsFuzzyPhrase(
                            evidence.tokens(),
                            variant
                    )
            ) {
                return true;
            }
        }

        return false;
    }

    private static boolean containsFuzzyPhrase(
            List<String> evidenceTokens,
            List<String> teamTokens
    ) {
        if (
                evidenceTokens.isEmpty()
                        || teamTokens.isEmpty()
                        || evidenceTokens.size()
                        < teamTokens.size()
        ) {
            return false;
        }

        for (
                int start = 0;
                start <= evidenceTokens.size()
                        - teamTokens.size();
                start++
        ) {
            boolean matches =
                    true;

            for (
                    int i = 0;
                    i < teamTokens.size();
                    i++
            ) {
                if (
                        !tokenEquivalent(
                                teamTokens.get(
                                        i
                                ),
                                evidenceTokens.get(
                                        start + i
                                )
                        )
                ) {
                    matches =
                            false;

                    break;
                }
            }

            if (
                    matches
            ) {
                return true;
            }
        }

        return false;
    }

    private static boolean startsWithFuzzyTeamPhrase(
            List<String> tipTokens,
            List<String> teamTokens
    ) {
        if (
                tipTokens.size()
                        < teamTokens.size()
        ) {
            return false;
        }

        for (
                int i = 0;
                i < teamTokens.size();
                i++
        ) {
            if (
                    !tokenEquivalent(
                            teamTokens.get(
                                    i
                            ),
                            tipTokens.get(
                                    i
                            )
                    )
            ) {
                return false;
            }
        }

        return true;
    }

    /*
     * =========================================================
     * DATE
     * =========================================================
     */

    private static double dateBonus(
            long days
    ) {
        if (
                days == 0
        ) {
            return 0.10;
        }

        if (
                days == 1
        ) {
            return 0.12;
        }

        if (
                days == 2
        ) {
            return 0.08;
        }

        if (
                days == 3
        ) {
            return 0.04;
        }

        if (
                days == -1
        ) {
            return 0.02;
        }

        return 0.0;
    }

    /*
     * =========================================================
     * RESULT
     * =========================================================
     */

    private static ApiFootballMatch result(
            Scored scored,
            double gap,
            double requiredGap
    ) {
        ApiFootballFixture fixture =
                scored.fixture();

        String evidence =
                "mode=BOTH_TEAMS"
                        + "; score="
                        + format(
                        scored.score()
                )
                        + "; gap="
                        + format(
                        gap
                )
                        + "; requiredGap="
                        + format(
                        requiredGap
                )
                        + "; primaryBoth="
                        + scored.primaryBoth()
                        + "; primaryBonus="
                        + format(
                        scored.primaryBonus()
                )
                        + "; fixtureId="
                        + fixture.fixtureId()
                        + "; fixture="
                        + fixture.homeTeamName()
                        + " vs "
                        + fixture.awayTeamName()
                        + "; date="
                        + fixture.fixtureDate();

        return new ApiFootballMatch(
                fixture,
                scored.score(),
                ResolutionConfidence.HIGH,
                evidence
        );
    }

    /*
     * =========================================================
     * EVIDENCE / NORMALIZATION
     * =========================================================
     */

    private static List<String> tokensFromNormalized(
            String normalized
    ) {
        List<String> result =
                new ArrayList<>();

        if (
                normalized == null
                        || normalized.isBlank()
        ) {
            return result;
        }

        for (
                String token :
                normalized.split(
                        " "
                )
        ) {
            if (
                    token == null
                            || token.isBlank()
                            || GENERIC.contains(
                            token
                    )
            ) {
                continue;
            }

            if (
                    token.chars()
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

        return result;
    }

    private static boolean containsAnyExactToken(
            EvidenceView evidence,
            String... tokens
    ) {
        for (
                String token :
                tokens
        ) {
            if (
                    evidence.exactTokens()
                            .contains(
                                    normalize(
                                            token
                                    )
                            )
            ) {
                return true;
            }
        }

        return false;
    }

    private static boolean containsExactToken(
            List<String> tokens,
            String token
    ) {
        return tokens.contains(
                token
        );
    }

    private static boolean containsAny(
            String text,
            String... values
    ) {
        if (
                text == null
                        || text.isBlank()
        ) {
            return false;
        }

        for (
                String value :
                values
        ) {
            String normalized =
                    normalize(
                            value
                    );

            if (
                    !normalized.isBlank()
                            && text.contains(
                            normalized
                    )
            ) {
                return true;
            }
        }

        return false;
    }

    private static String join(
            String... values
    ) {
        StringBuilder result =
                new StringBuilder();

        for (
                String value :
                values
        ) {
            append(
                    result,
                    value
            );
        }

        return result.toString();
    }

    private static void append(
            StringBuilder builder,
            String value
    ) {
        if (
                value == null
                        || value.isBlank()
        ) {
            return;
        }

        if (
                !builder.isEmpty()
        ) {
            builder.append(
                    ' '
            );
        }

        builder.append(
                value
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

    private static String format(
            double value
    ) {
        return String.format(
                Locale.ROOT,
                "%.3f",
                value
        );
    }

    /*
     * =========================================================
     * INTERNAL VIEWS
     * =========================================================
     */

    private record EvidenceView(
            String text,
            List<String> tokens,
            Set<String> exactTokens
    ) {

        static EvidenceView of(
                String raw
        ) {
            String normalized =
                    normalize(
                            raw
                    );

            List<String> tokens =
                    tokensFromNormalized(
                            normalized
                    );

            return new EvidenceView(
                    normalized,
                    List.copyOf(
                            tokens
                    ),
                    Set.copyOf(
                            tokens
                    )
            );
        }

        boolean isBlank() {
            return text == null
                    || text.isBlank();
        }
    }

    private record TeamView(
            String normalizedName,
            List<String> primaryTokens,
            List<List<String>> nameVariants,
            List<String> allDiscriminativeTokens,
            boolean women,
            boolean youth,
            boolean reserve,
            boolean bTeam
    ) {
    }

    private record PreparedFixture(
            ApiFootballFixture fixture,
            TeamView home,
            TeamView away,
            boolean tipSubjectMatch
    ) {
    }

    private record Scored(
            ApiFootballFixture fixture,
            boolean primaryBoth,
            double primaryBonus,
            double score
    ) {
    }
}