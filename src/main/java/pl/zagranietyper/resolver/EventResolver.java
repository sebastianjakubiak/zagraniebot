package pl.zagranietyper.resolver;

import pl.zagranietyper.model.BetType;
import pl.zagranietyper.model.EventResolutionCandidate;
import pl.zagranietyper.model.ResolutionConfidence;
import pl.zagranietyper.model.ResolutionSource;
import pl.zagranietyper.model.ResolvedEvent;
import pl.zagranietyper.model.ResolvedSport;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EventResolver {

    private static final ZoneId WARSAW =
            ZoneId.of("Europe/Warsaw");

    /*
     * Jawna data wyciągnięta z tekstu artykułu jest
     * wiarygodna tylko wtedy, gdy leży w normalnym
     * oknie czasowym względem publikacji.
     *
     * Artykuły bardzo często wspominają historyczne
     * wyniki i daty poprzednich spotkań.
     *
     * Przykład realnego błędu:
     *
     * published = 2024-09-21
     * text      = ... 02.12.2021 ...
     *
     * Stara implementacja przypisywała 2021-12-02
     * jako datę aktualnego eventu.
     */
    private static final int EXPLICIT_DATE_DAYS_BEFORE =
            1;

    private static final int EXPLICIT_DATE_DAYS_AFTER =
            3;

    private static final Pattern MATCHUP_PATTERN =
            Pattern.compile(
                    "^\\s*(.{2,100}?)\\s+[–—-]\\s+(.{2,100}?)\\s*$"
            );

    /*
     * "Pojedynek A – B..."
     * "Mecz A – B..."
     */
    private static final Pattern NARRATIVE_DASH_PATTERN =
            Pattern.compile(
                    "(?iu)\\b(?:pojedynek|mecz|spotkanie|starcie)\\s+"
                            + "(.{2,100}?)\\s+[–—-]\\s+"
                            + "(.{2,100}?)"
                            + endLookahead()
            );

    /*
     * "Pojedynek Sparty Wrocław z Motorem Lublin zaplanowano..."
     */
    private static final Pattern NARRATIVE_WITH_PATTERN =
            Pattern.compile(
                    "(?iu)\\b(?:pojedynek|mecz|spotkanie|starcie)\\s+"
                            + "(.{2,100}?)\\s+z\\s+"
                            + "(.{2,100}?)"
                            + endLookahead()
            );

    /*
     * "Miejscowy PSŻ podejmie Wilki Krosno."
     */
    private static final Pattern HOST_PATTERN =
            Pattern.compile(
                    "(?iu)(?:^|[.!?]\\s+)"
                            + "(?:(?:miejscowy|miejscowa|miejscowi|gospodarze)\\s+)?"
                            + "(.{2,80}?)\\s+"
                            + "(?:podejmie|podejmą|podejma)\\s+"
                            + "(.{2,80}?)"
                            + "(?=[.!?]|$)"
            );

    /*
     * "A zmierzy się z B"
     */
    private static final Pattern FACE_PATTERN =
            Pattern.compile(
                    "(?iu)(?:^|[.!?]\\s+)"
                            + "(.{2,80}?)\\s+"
                            + "(?:zmierzy\\s+się\\s+z|zmierzy\\s+sie\\s+z)\\s+"
                            + "(.{2,80}?)"
                            + "(?=[.!?]|$)"
            );

    /*
     * "A zagra z B"
     */
    private static final Pattern PLAY_PATTERN =
            Pattern.compile(
                    "(?iu)(?:^|[.!?]\\s+)"
                            + "(.{2,80}?)\\s+"
                            + "(?:zagra|zagrają|zagraja)\\s+z\\s+"
                            + "(.{2,80}?)"
                            + "(?=[.!?]|$)"
            );

    private static final Pattern NUMERIC_DATE_PATTERN =
            Pattern.compile(
                    "(?<!\\d)"
                            + "(\\d{1,2})\\."
                            + "(\\d{1,2})\\."
                            + "(\\d{4})"
                            + "(?!\\d)"
            );

    private static final Pattern WORD_DATE_PATTERN =
            Pattern.compile(
                    "(?iu)(?<!\\d)"
                            + "(\\d{1,2})\\s+"
                            + "(stycznia|lutego|marca|kwietnia|maja|czerwca|"
                            + "lipca|sierpnia|września|wrzesnia|października|"
                            + "pazdziernika|listopada|grudnia)"
                            + "\\s+(\\d{4})"
            );

    private static final Pattern FOOTBALL_AKO_PATTERN =
            Pattern.compile(
                    "(?iu)\\b(?:LE|LK|LM)\\s+AKO\\b"
            );

    public ResolvedEvent resolve(
            EventResolutionCandidate candidate
    ) {
        ResolvedSport sport =
                resolveSport(
                        candidate
                );

        /*
         * 1. Source metadata zawsze wygrywa.
         *
         * sourceStartAt jest źródłem strukturalnym,
         * dlatego NIE ograniczamy go heurystycznym
         * oknem -1/+3.
         */
        if (
                notBlank(
                        candidate.sourceHome()
                )
                        && notBlank(
                        candidate.sourceAway()
                )
        ) {
            LocalDate date =
                    candidate.sourceStartAt() == null
                            ? explicitDate(candidate)
                            : candidate.sourceStartAt()
                            .atZone(WARSAW)
                            .toLocalDate();

            return resolved(
                    sport,
                    candidate.sourceHome(),
                    candidate.sourceAway(),
                    date,
                    ResolutionSource.SOURCE_METADATA,
                    ResolutionConfidence.HIGH,
                    "event_home/event_away"
            );
        }

        /*
         * 2. Jawny matchup w headingu jest leg-specific
         * i dlatego możemy go stosować również do AKO.
         */
        Matchup headingMatch =
                exactMatchup(
                        candidate.heading()
                );

        if (
                headingMatch != null
        ) {
            return resolved(
                    sport,
                    headingMatch.a(),
                    headingMatch.b(),
                    explicitDate(candidate),
                    ResolutionSource.CONTEXT_HEADING,
                    ResolutionConfidence.HIGH,
                    candidate.heading()
            );
        }

        /*
         * 3. Tytuł posta tylko dla SINGLE.
         */
        if (
                candidate.betType()
                        == BetType.SINGLE
                        && candidate.betLegCount() == 1
        ) {
            String titlePrefix =
                    titleBeforeColon(
                            candidate.postTitle()
                    );

            Matchup titleMatch =
                    exactMatchup(
                            titlePrefix
                    );

            if (
                    titleMatch != null
            ) {
                return resolved(
                        sport,
                        titleMatch.a(),
                        titleMatch.b(),
                        explicitDate(candidate),
                        ResolutionSource.POST_TITLE,
                        ResolutionConfidence.HIGH,
                        titlePrefix
                );
            }
        }

        /*
         * 4. Narracyjny tekst bezpośrednio przy danym legu.
         */
        Matchup previousTextMatch =
                narrativeMatchup(
                        candidate.previousText()
                );

        if (
                previousTextMatch != null
        ) {
            return resolved(
                    sport,
                    previousTextMatch.a(),
                    previousTextMatch.b(),
                    explicitDate(candidate),
                    ResolutionSource.PREVIOUS_TEXT,
                    ResolutionConfidence.MEDIUM,
                    candidate.previousText()
            );
        }

        return ResolvedEvent.unresolved(
                sport
        );
    }

    private static String endLookahead() {
        return "(?=\\s+(?:"
                + "to\\b"
                + "|czeka\\b"
                + "|czekają\\b"
                + "|czekaja\\b"
                + "|zaplanowano\\b"
                + "|odbędzie\\b"
                + "|odbedzie\\b"
                + "|jest\\b"
                + "|będzie\\b"
                + "|bedzie\\b"
                + "|w\\s+ramach\\b"
                + "|na\\s+\\d"
                + ")"
                + "|[.!?]"
                + "|$)";
    }

    private static Matchup narrativeMatchup(
            String text
    ) {
        if (
                !notBlank(
                        text
                )
        ) {
            return null;
        }

        String normalized =
                normalize(
                        text
                );

        Matchup matchup;

        matchup =
                match(
                        NARRATIVE_DASH_PATTERN,
                        normalized
                );

        if (
                matchup != null
        ) {
            return matchup;
        }

        matchup =
                match(
                        NARRATIVE_WITH_PATTERN,
                        normalized
                );

        if (
                matchup != null
        ) {
            return matchup;
        }

        matchup =
                match(
                        HOST_PATTERN,
                        normalized
                );

        if (
                matchup != null
        ) {
            return matchup;
        }

        matchup =
                match(
                        FACE_PATTERN,
                        normalized
                );

        if (
                matchup != null
        ) {
            return matchup;
        }

        return match(
                PLAY_PATTERN,
                normalized
        );
    }

    private static Matchup match(
            Pattern pattern,
            String text
    ) {
        Matcher matcher =
                pattern.matcher(
                        text
                );

        if (
                !matcher.find()
        ) {
            return null;
        }

        String a =
                cleanParticipant(
                        matcher.group(1)
                );

        String b =
                cleanParticipant(
                        matcher.group(2)
                );

        if (
                !validParticipant(a)
                        || !validParticipant(b)
        ) {
            return null;
        }

        return new Matchup(
                a,
                b
        );
    }

    private static ResolvedEvent resolved(
            ResolvedSport sport,
            String participantA,
            String participantB,
            LocalDate date,
            ResolutionSource source,
            ResolutionConfidence confidence,
            String evidence
    ) {
        String a =
                cleanParticipant(
                        participantA
                );

        String b =
                cleanParticipant(
                        participantB
                );

        return new ResolvedEvent(
                sport,
                a + " – " + b,
                a,
                b,
                date,
                source,
                confidence,
                truncate(
                        evidence,
                        1000
                )
        );
    }

    private static Matchup exactMatchup(
            String text
    ) {
        if (
                !notBlank(
                        text
                )
        ) {
            return null;
        }

        Matcher matcher =
                MATCHUP_PATTERN.matcher(
                        normalize(
                                text
                        )
                );

        if (
                !matcher.matches()
        ) {
            return null;
        }

        String a =
                cleanParticipant(
                        matcher.group(1)
                );

        String b =
                cleanParticipant(
                        matcher.group(2)
                );

        if (
                !validParticipant(a)
                        || !validParticipant(b)
        ) {
            return null;
        }

        return new Matchup(
                a,
                b
        );
    }

    private static String titleBeforeColon(
            String title
    ) {
        if (
                !notBlank(
                        title
                )
        ) {
            return null;
        }

        int colon =
                title.indexOf(':');

        if (
                colon < 0
        ) {
            return title;
        }

        return title.substring(
                0,
                colon
        );
    }

    /*
     * Szukamy jawnej daty kolejno:
     *
     * post title
     * heading
     * previous text
     *
     * ALE data zostaje zaakceptowana tylko wtedy,
     * gdy jest sensownie blisko daty publikacji.
     *
     * Jeżeli np. post title zawiera starą datę,
     * nie kończymy szukania - próbujemy heading,
     * a potem previous_text.
     */
    private static LocalDate explicitDate(
            EventResolutionCandidate candidate
    ) {
        LocalDate date;

        date =
                extractPlausibleDate(
                        candidate,
                        candidate.postTitle()
                );

        if (
                date != null
        ) {
            return date;
        }

        date =
                extractPlausibleDate(
                        candidate,
                        candidate.heading()
                );

        if (
                date != null
        ) {
            return date;
        }

        return extractPlausibleDate(
                candidate,
                candidate.previousText()
        );
    }

    private static LocalDate extractPlausibleDate(
            EventResolutionCandidate candidate,
            String text
    ) {
        LocalDate date =
                extractDate(
                        text
                );

        if (
                date == null
                        || candidate.publishedAt() == null
        ) {
            return null;
        }

        LocalDate publication =
                candidate.publishedAt()
                        .atZone(
                                WARSAW
                        )
                        .toLocalDate();

        LocalDate min =
                publication.minusDays(
                        EXPLICIT_DATE_DAYS_BEFORE
                );

        LocalDate max =
                publication.plusDays(
                        EXPLICIT_DATE_DAYS_AFTER
                );

        if (
                date.isBefore(
                        min
                )
                        || date.isAfter(
                        max
                )
        ) {
            return null;
        }

        return date;
    }

    private static LocalDate extractDate(
            String text
    ) {
        if (
                !notBlank(
                        text
                )
        ) {
            return null;
        }

        Matcher numeric =
                NUMERIC_DATE_PATTERN.matcher(
                        text
                );

        if (
                numeric.find()
        ) {
            try {
                return LocalDate.of(
                        Integer.parseInt(
                                numeric.group(3)
                        ),
                        Integer.parseInt(
                                numeric.group(2)
                        ),
                        Integer.parseInt(
                                numeric.group(1)
                        )
                );

            } catch (
                    RuntimeException ignored
            ) {
            }
        }

        Matcher word =
                WORD_DATE_PATTERN.matcher(
                        text
                );

        if (
                word.find()
        ) {
            try {
                int day =
                        Integer.parseInt(
                                word.group(1)
                        );

                int month =
                        monthNumber(
                                word.group(2)
                        );

                int year =
                        Integer.parseInt(
                                word.group(3)
                        );

                return LocalDate.of(
                        year,
                        month,
                        day
                );

            } catch (
                    RuntimeException ignored
            ) {
            }
        }

        return null;
    }

    private static int monthNumber(
            String month
    ) {
        String normalized =
                stripPolish(
                        month
                                .toLowerCase(
                                        Locale.ROOT
                                )
                );

        return switch (
                normalized
                ) {
            case "stycznia" -> 1;
            case "lutego" -> 2;
            case "marca" -> 3;
            case "kwietnia" -> 4;
            case "maja" -> 5;
            case "czerwca" -> 6;
            case "lipca" -> 7;
            case "sierpnia" -> 8;
            case "wrzesnia" -> 9;
            case "pazdziernika" -> 10;
            case "listopada" -> 11;
            case "grudnia" -> 12;

            default ->
                    throw new IllegalArgumentException(
                            "Nieznany miesiąc: "
                                    + month
                    );
        };
    }

    private static ResolvedSport resolveSport(
            EventResolutionCandidate candidate
    ) {
        String text =
                normalize(
                        join(
                                candidate.postTitle(),
                                candidate.tipTitle(),
                                candidate.heading(),
                                candidate.previousText(),
                                candidate.sourceCompetition()
                        )
                );

        String lower =
                text.toLowerCase(
                        Locale.ROOT
                );

        if (
                containsAny(
                        lower,
                        "esport",
                        "cs2",
                        "counter-strike",
                        "league of legends",
                        " blast ",
                        " iem "
                )
        ) {
            return ResolvedSport.ESPORT;
        }

        if (
                containsAny(
                        lower,
                        " ufc ",
                        "ufc ",
                        " ksw ",
                        "ksw ",
                        " mma "
                )
        ) {
            return ResolvedSport.MMA;
        }

        if (
                containsAny(
                        lower,
                        "darts",
                        "rzutki",
                        "checkout",
                        "world matchplay"
                )
        ) {
            return ResolvedSport.DARTS;
        }

        if (
                containsAny(
                        lower,
                        "żuż",
                        "zuz",
                        "pge ekstraliga",
                        "metalkas 2. ekstraligi",
                        "żużlu"
                )
        ) {
            return ResolvedSport.SPEEDWAY;
        }

        if (
                containsAny(
                        lower,
                        "tenis",
                        " atp ",
                        " wta ",
                        "świątek",
                        "swiatek",
                        "wimbled",
                        "roland garros",
                        "us open"
                )
        ) {
            return ResolvedSport.TENNIS;
        }

        if (
                containsAny(
                        lower,
                        "hokej",
                        "hokejowej",
                        " nhl "
                )
        ) {
            return ResolvedSport.HOCKEY;
        }

        if (
                containsAny(
                        lower,
                        " nba ",
                        "wnba",
                        "koszyk"
                )
        ) {
            return ResolvedSport.BASKETBALL;
        }

        if (
                FOOTBALL_AKO_PATTERN
                        .matcher(
                                text
                        )
                        .find()
                        || containsAny(
                        lower,
                        "uefa",
                        "ekstraklasa",
                        "liga konferencji",
                        "liga mistrzów",
                        "liga mistrzow",
                        "liga europy",
                        "champions league",
                        "europa league",
                        "conference league",
                        "copa libertadores",
                        "premier league",
                        "la liga",
                        "serie a",
                        "bundesliga"
                )
        ) {
            return ResolvedSport.FOOTBALL;
        }

        return ResolvedSport.UNKNOWN;
    }

    private static boolean containsAny(
            String text,
            String... needles
    ) {
        for (
                String needle :
                needles
        ) {
            if (
                    text.contains(
                            needle
                    )
            ) {
                return true;
            }
        }

        return false;
    }

    private static boolean validParticipant(
            String value
    ) {
        if (
                !notBlank(
                        value
                )
        ) {
            return false;
        }

        if (
                value.length() > 100
        ) {
            return false;
        }

        String lower =
                value.toLowerCase(
                        Locale.ROOT
                );

        return !containsAny(
                lower,
                "co obstawiać",
                "co obstawiac",
                "co typuję",
                "co typuje",
                "co zagramy",
                "typy i kursy",
                "propozycja kuponu",
                "gotowy kupon"
        );
    }

    private static String cleanParticipant(
            String value
    ) {
        if (
                value == null
        ) {
            return null;
        }

        return normalize(
                value
        )
                .replaceAll(
                        "^[|:;,\\-–—]+",
                        ""
                )
                .replaceAll(
                        "[|:;,\\-–—]+$",
                        ""
                )
                .trim();
    }

    private static String normalize(
            String value
    ) {
        if (
                value == null
        ) {
            return "";
        }

        return value
                .replace(
                        '\u00A0',
                        ' '
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    private static String stripPolish(
            String value
    ) {
        return value
                .replace("ą", "a")
                .replace("ć", "c")
                .replace("ę", "e")
                .replace("ł", "l")
                .replace("ń", "n")
                .replace("ó", "o")
                .replace("ś", "s")
                .replace("ź", "z")
                .replace("ż", "z");
    }

    private static boolean notBlank(
            String value
    ) {
        return value != null
                && !value.isBlank();
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
            if (
                    !notBlank(
                            value
                    )
            ) {
                continue;
            }

            if (
                    !result.isEmpty()
            ) {
                result.append(
                        ' '
                );
            }

            result.append(
                    value
            );
        }

        return result.toString();
    }

    private static String truncate(
            String value,
            int max
    ) {
        if (
                value == null
                        || value.length() <= max
        ) {
            return value;
        }

        return value.substring(
                0,
                max
        );
    }

    private record Matchup(
            String a,
            String b
    ) {
    }
}