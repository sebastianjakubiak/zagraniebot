package pl.zagranietyper.parser;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FootballWinnerParser {

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

    private static final Pattern WIN_VERB =
            Pattern.compile(
                    "\\bwygra(?:ja)?\\b"
            );

    private static final Pattern WILL_WIN =
            Pattern.compile(
                    "\\bzwyciezy\\b"
            );

    private static final Pattern VICTORY_NOUN =
            Pattern.compile(
                    "^zwyciestwo\\s+(.+)$"
            );

    private static final Pattern WIN_NOUN =
            Pattern.compile(
                    "^wygrana\\s+(.+)$"
            );

    /*
     * STRICT PURE-WINNER PARSER.
     *
     * Jeżeli tytuł zawiera jakikolwiek sygnał dodatkowego
     * warunku, nie próbujemy redukować go do zwykłego winnera.
     */
    private static final List<String> UNSUPPORTED_SIGNALS =
            List.of(
                    " btts",
                    "btts ",
                    "brak btts",
                    "obie druzyny",
                    "obie strzela",

                    "pierwszy gol",
                    "1 gol",

                    "co najmniej",
                    "minimum",
                    "maksymalnie",

                    "powyzej",
                    "ponizej",
                    " over ",
                    " under ",
                    "wiecej niz",
                    "mniej niz",

                    "strzeli",
                    "zdobedzie",

                    "do zera",
                    "bez straty gola",

                    "bram",
                    "golami",
                    " goli",
                    " gole",
                    "roznic",

                    "polow",
                    "do przerwy",
                    "przerw",
                    " half",

                    "kart",
                    "upomnien",

                    "rzut rozn",
                    "rzuty rozn",
                    "rzutow rozn",
                    "korner",
                    "corner",

                    "strzal",
                    "asyst",
                    "faul",
                    "spalony",
                    "offside",

                    "handicap",

                    "awans",
                    "trofeum"
            );

    private static final Set<String> SUBJECT_NOISE =
            Set.of(
                    "reprezentacja",
                    "reprezentacji",
                    "reprezentacje",
                    "druzyna",
                    "druzyny",
                    "zespol",
                    "zespolu",
                    "ekipa",
                    "ekipy"
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

    private static final Set<String> SIMPLE_INFLECTION_SUFFIXES =
            Set.of(
                    "u",
                    "a",
                    "em",
                    "ie",
                    "i",
                    "y",
                    "owi",
                    "om",
                    "ow",
                    "ia"
            );

    private static final Set<String> A_STEM_SUFFIXES =
            Set.of(
                    "a",
                    "y",
                    "i",
                    "ie",
                    "ii",
                    "e"
            );

    private static final Map<String, List<String>> TEAM_ALIASES =
            createTeamAliases();

    private static final FootballParticipantResolver PARTICIPANT_RESOLVER =
            new FootballParticipantResolver();

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
        ) {
            return rejected(
                    Status.NOT_WINNER,
                    null
            );
        }

        if (
                !containsWinnerCue(
                        text
                )
        ) {
            return rejected(
                    Status.NOT_WINNER,
                    null
            );
        }

        if (
                tipTitle != null
                        && tipTitle.contains(
                        "+"
                )
        ) {
            return rejected(
                    Status.UNSUPPORTED_COMPOSITE,
                    null
            );
        }

        if (
                containsUnsupportedSignal(
                        text
                )
        ) {
            return rejected(
                    Status.UNSUPPORTED_COMPOSITE,
                    null
            );
        }

        /*
         * Zwycięstwo A lub B = brak remisu,
         * a nie zwykły winner.
         */
        if (
                text.contains(
                        " lub "
                )
        ) {
            return rejected(
                    Status.UNSUPPORTED_COMPOSITE,
                    null
            );
        }

        /*
         * Jawne określenie strony.
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
            return parsed(
                    Selection.HOME,
                    "gospodarze"
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
            return parsed(
                    Selection.AWAY,
                    "goscie"
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
            return rejected(
                    Status.SUBJECT_NOT_FOUND,
                    null
            );
        }

        subject =
                cleanSubject(
                        subject
                );

        if (
                subject.isBlank()
        ) {
            return rejected(
                    Status.SUBJECT_NOT_FOUND,
                    null
            );
        }

        FootballParticipantResolver.Resolution resolution =
                PARTICIPANT_RESOLVER.resolve(
                        subject,
                        homeTeam,
                        awayTeam,
                        FootballParticipantResolver.MatchingPolicy.EXACT_ORDERED,
                        TEAM_ALIASES
                );

        if (
                resolution == FootballParticipantResolver.Resolution.HOME
        ) {
            return parsed(
                    Selection.HOME,
                    subject
            );
        }

        if (
                resolution == FootballParticipantResolver.Resolution.AWAY
        ) {
            return parsed(
                    Selection.AWAY,
                    subject
            );
        }

        if (
                resolution == FootballParticipantResolver.Resolution.AMBIGUOUS
        ) {
            return new ParseResult(
                    Status.SUBJECT_AMBIGUOUS,
                    null,
                    subject
            );
        }

        return new ParseResult(
                Status.SUBJECT_MISMATCH,
                null,
                subject
        );
    }

    public boolean looksWinnerLike(
            String tipTitle
    ) {
        return containsWinnerCue(
                normalize(
                        tipTitle
                )
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
        Matcher victory =
                VICTORY_NOUN.matcher(
                        text
                );

        if (
                victory.matches()
        ) {
            return trimSubject(
                    victory.group(
                            1
                    )
            );
        }

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

        Matcher win =
                WIN_VERB.matcher(
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
                return trimSubject(
                        before
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
                        before
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
            String value
    ) {
        if (
                value == null
                        || value.isBlank()
        ) {
            return "";
        }

        String result =
                value.trim();

        /*
         * Zwycięstwo Liverpoolu z Bournemouth
         * -> Liverpoolu
         *
         * Zwycięstwo Hiszpanii ze Szwajcarią
         * -> Hiszpanii
         */
        int withZ =
                result.indexOf(
                        " z "
                );

        int withZe =
                result.indexOf(
                        " ze "
                );

        int versus =
                firstNonNegative(
                        withZ,
                        withZe
                );

        if (
                versus >= 0
        ) {
            result =
                    result.substring(
                                    0,
                                    versus
                            )
                            .trim();
        }

        result =
                removeTrailingWord(
                        result,
                        "mecz"
                );

        result =
                removeTrailingWord(
                        result,
                        "spotkanie"
                );

        return result.trim();
    }

    private static int firstNonNegative(
            int first,
            int second
    ) {
        if (
                first < 0
        ) {
            return second;
        }

        if (
                second < 0
        ) {
            return first;
        }

        return Math.min(
                first,
                second
        );
    }

    private static String removeTrailingWord(
            String value,
            String word
    ) {
        String suffix =
                " " + word;

        if (
                value.endsWith(
                        suffix
                )
        ) {
            return value.substring(
                            0,
                            value.length()
                                    - suffix.length()
                    )
                    .trim();
        }

        return value;
    }

    private static String cleanSubject(
            String subject
    ) {
        List<String> result =
                new ArrayList<>();

        for (
                String token :
                tokens(
                        subject
                )
        ) {
            if (
                    SUBJECT_NOISE.contains(
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
                    phraseEquivalent(
                            teamTokens,
                            subjectTokens
                    )
            ) {
                return true;
            }
        }

        return false;
    }

    private static boolean phraseEquivalent(
            List<String> teamTokens,
            List<String> subjectTokens
    ) {
        if (
                teamTokens.size()
                        != subjectTokens.size()
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
                            subjectTokens.get(
                                    i
                            )
                    )
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
         * Arsenal    -> Arsenalu
         *
         * Celowo nie robimy dowolnego startsWith(),
         * dzięki czemu Bayer != Bayern.
         */
        if (
                subjectToken.startsWith(
                        teamToken
                )
                        && subjectToken.length()
                        > teamToken.length()
        ) {
            String suffix =
                    subjectToken.substring(
                            teamToken.length()
                    );

            if (
                    SIMPLE_INFLECTION_SUFFIXES.contains(
                            suffix
                    )
            ) {
                return true;
            }
        }

        /*
         * Hiszpania -> Hiszpanii
         * Jagiellonia -> Jagiellonii
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
            ) {
                String suffix =
                        subjectToken.substring(
                                stem.length()
                        );

                if (
                        A_STEM_SUFFIXES.contains(
                                suffix
                        )
                ) {
                    return true;
                }
            }
        }

        return false;
    }

    private static List<String> teamVariants(
            String apiTeam
    ) {
        String team =
                normalize(
                        apiTeam
                );

        if (
                team.isBlank()
        ) {
            return List.of();
        }

        List<String> result =
                new ArrayList<>();

        result.add(
                team
        );

        result.addAll(
                TEAM_ALIASES.getOrDefault(
                        team,
                        List.of()
                )
        );

        return List.copyOf(
                result
        );
    }

    private static List<String> meaningfulTokens(
            String value
    ) {
        List<String> result =
                new ArrayList<>();

        for (
                String token :
                tokens(
                        value
                )
        ) {
            if (
                    TEAM_NOISE.contains(
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

        /*
         * Reprezentacje.
         */
        alias(
                result,
                "Poland",
                "Polska"
        );

        alias(
                result,
                "Germany",
                "Niemcy",
                "Niemcy reprezentacja",
                "Niemiec",
                "Niemcow"
        );

        alias(
                result,
                "Spain",
                "Hiszpania"
        );

        alias(
                result,
                "Italy",
                "Wlochy",
                "Wloch"
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
                "Netherlands W",
                "Holandia K",
                "Holandia kobiet"
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
                "Czech Republic",
                "Czechy",
                "Czech",
                "Czechow"
        );

        alias(
                result,
                "Czechia",
                "Czechy",
                "Czech",
                "Czechow"
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
                "Wegry",
                "Wegrzy"
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
                "Türkiye",
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
                "Kosovo",
                "Kosowo"
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
                "Ecuador",
                "Ekwador"
        );

        alias(
                result,
                "Mexico",
                "Meksyk"
        );

        alias(
                result,
                "Japan",
                "Japonia"
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
                "Ivory Coast",
                "Wybrzeze Kosci Sloniowej"
        );

        alias(
                result,
                "Côte d'Ivoire",
                "Wybrzeze Kosci Sloniowej"
        );

        /*
         * Kluby / skróty Zagranie.
         */
        alias(
                result,
                "Paris Saint Germain",
                "PSG"
        );

        alias(
                result,
                "Nieciecza",
                "Termalica",
                "Bruk Bet Termalica",
                "Bruk Bet"
        );

        alias(
                result,
                "Tychy 71",
                "GKS",
                "GKS Tychy"
        );

        alias(
                result,
                "Jastrzębie",
                "GKS",
                "GKS Jastrzebie"
        );

        alias(
                result,
                "Manchester City",
                "Man City",
                "City"
        );

        alias(
                result,
                "Bayern München",
                "Bayern",
                "Bayern Monachium"
        );

        alias(
                result,
                "Stade Brestois 29",
                "Brest"
        );

        alias(
                result,
                "Stargard Szczeciński",
                "Blekitni",
                "Blekitni Stargard"
        );

        alias(
                result,
                "Rangers",
                "Glasgow",
                "Glasgow Rangers"
        );

        alias(
                result,
                "Athletic Club",
                "Athletic",
                "Atheltic",
                "Athletic Bilbao",
                "Bilbao"
        );

        alias(
                result,
                "1899 Hoffenheim",
                "TSG",
                "Hoffenheim"
        );

        alias(
                result,
                "Jagiellonia",
                "Jaga"
        );

        alias(
                result,
                "Shakhtar Donetsk",
                "Szachtar"
        );

        alias(
                result,
                "Marseille",
                "Marsylia"
        );

        alias(
                result,
                "RB Leipzig",
                "Lipsk",
                "RB Lipsk"
        );

        alias(
                result,
                "Al-Nassr",
                "Al Nassr"
        );

        /*
         * Jawne skrócone nazwy polskich klubów.
         *
         * Dodajemy je jako aliasy konkretnych nazw API,
         * zamiast luzować phraseEquivalent().
         *
         * Dzięki temu np. samo "Manchester" nadal NIE
         * zacznie pasować jednocześnie do City i United.
         */
        alias(
                result,
                "Widzew Łódź",
                "Widzew"
        );

        alias(
                result,
                "Legia Warszawa",
                "Legia"
        );

        alias(
                result,
                "Lech Poznan",
                "Lech"
        );

        return Map.copyOf(
                result
        );
    }

    private static void alias(
            Map<String, List<String>> map,
            String apiName,
            String... aliases
    ) {
        List<String> normalized =
                new ArrayList<>();

        for (
                String alias :
                aliases
        ) {
            normalized.add(
                    normalize(
                            alias
                    )
            );
        }

        map.put(
                normalize(
                        apiName
                ),
                List.copyOf(
                        normalized
                )
        );
    }

    /*
     * =========================================================
     * GENERAL HELPERS
     * =========================================================
     */

    private static boolean containsWinnerCue(
            String text
    ) {
        if (
                text == null
                        || text.isBlank()
        ) {
            return false;
        }

        return WIN_VERB
                .matcher(
                        text
                )
                .find()
                || WILL_WIN
                .matcher(
                        text
                )
                .find()
                || text.startsWith(
                "zwyciestwo "
        )
                || text.startsWith(
                "wygrana "
        );
    }

    private static boolean containsUnsupportedSignal(
            String text
    ) {
        String padded =
                " " + text + " ";

        for (
                String signal :
                UNSUPPORTED_SIGNALS
        ) {
            if (
                    padded.contains(
                            normalizeSignal(
                                    signal
                            )
                    )
            ) {
                return true;
            }
        }

        return false;
    }

    private static String normalizeSignal(
            String signal
    ) {
        String normalized =
                normalize(
                        signal
                );

        if (
                signal.startsWith(
                        " "
                )
        ) {
            normalized =
                    " " + normalized;
        }

        if (
                signal.endsWith(
                        " "
                )
        ) {
            normalized =
                    normalized + " ";
        }

        return normalized;
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
                            normalize(
                                    value
                            )
                    )
            ) {
                return true;
            }
        }

        return false;
    }

    private static List<String> tokens(
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

        return List.of(
                normalized.split(
                        " "
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

    private static ParseResult parsed(
            Selection selection,
            String subject
    ) {
        return new ParseResult(
                Status.PARSED,
                selection,
                subject
        );
    }

    private static ParseResult rejected(
            Status status,
            String subject
    ) {
        return new ParseResult(
                status,
                null,
                subject
        );
    }

    public enum Selection {
        HOME,
        AWAY
    }

    public enum Status {
        PARSED,
        NOT_WINNER,
        UNSUPPORTED_COMPOSITE,
        SUBJECT_NOT_FOUND,
        SUBJECT_MISMATCH,
        SUBJECT_AMBIGUOUS
    }

    public record ParseResult(
            Status status,
            Selection selection,
            String subject
    ) {

        public boolean parsed() {
            return status == Status.PARSED
                    && selection != null;
        }
    }
}
