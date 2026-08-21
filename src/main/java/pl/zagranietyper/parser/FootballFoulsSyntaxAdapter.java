package pl.zagranietyper.parser;

import pl.zagranietyper.model.FootballFixtureStatisticCondition;
import pl.zagranietyper.model.FootballFixtureStatisticType;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Whole-market adapter for deterministic full-time team and match fouls markets. */
public final class FootballFoulsSyntaxAdapter {
    private static final String DIRECTION = "(powyzej|ponizej|wiecej niz|mniej niz|over|under)";
    private static final String NUMBER = "([0-9]+(?:[.,][0-9]+)?)";
    private static final String FOULS = "(?:faul(?:i|e|ow)?|foul(?:s)?)";
    private static final Pattern MATCH_TOTAL = Pattern.compile("^" + DIRECTION + " " + NUMBER + " " + FOULS + "(?: w meczu)?$");
    private static final Pattern MATCH_MINIMUM = Pattern.compile("^(?:w meczu obejrzymy )?(?:min[.]?|minimum|co najmniej) ([0-9]+) " + FOULS + "(?: w meczu)?$");
    private static final Pattern TEAM_TOTAL = Pattern.compile("^(.+?) " + DIRECTION + " " + NUMBER + " " + FOULS + "(?: w meczu)?$");
    private static final Pattern TEAM_MINIMUM = Pattern.compile("^(.+?) (?:odnotuje|popelni|zanotuje) (?:min[.]?|minimum|co najmniej) ([0-9]+) " + FOULS + "$");
    private static final Pattern TEAM_RANGE = Pattern.compile("^(.+?) (?:przedzial|liczba) " + FOULS + " ([0-9]+) ?- ?([0-9]+)$");
    private static final Pattern MATCH_RANGE = Pattern.compile("^(?:przedzial|liczba) " + FOULS + " ([0-9]+) ?- ?([0-9]+)$");
    private static final Pattern SIGNED_TEAM_OVER = Pattern.compile("^(.+?) \\+ ?([0-9]+[.,]5) " + FOULS + "$");
    private static final Pattern RAW_SIGNED = Pattern.compile("(?:^|[^0-9])[+-]\\s*[0-9]");
    private static final Pattern PERIOD = Pattern.compile("\\b(?:1|2|pierwszej|drugiej) polow|\\bpolow|do przerwy|half|w kazdej polowie|w obu polowach");
    private static final Pattern PLAYER = Pattern.compile("^[a-z][.]\\s+[^ ]+ [+-] ?[0-9].* " + FOULS + "$");
    private static final Pattern HANDICAP_OR_COMPARISON = Pattern.compile("(?:^| )(?:handicap|hcp)(?: |$)|wiecej " + FOULS + "|mniej " + FOULS + "|liczba " + FOULS + " [12][.]?druzyna");
    private static final Pattern OTHER_MARKET = Pattern.compile("\\b(?:gol|gola|gole|goli|bramk|strzeli|btts|kart|rozn|corner|strzal|wygra|wygrana|zwyciestwo|zwyciezy|remis|awans)\\w*");

    public ParseResult parse(String tipTitle, String homeTeam, String awayTeam) {
        if (tipTitle == null || tipTitle.isBlank()) return rejected(Status.NOT_FOULS_LIKE);
        String text = normalize(tipTitle);
        if (!text.matches(".*(?:faul|foul).*$")) return rejected(Status.NOT_FOULS_LIKE);
        if (PERIOD.matcher(text).find()) return rejected(Status.UNSUPPORTED_PERIOD);
        if (OTHER_MARKET.matcher(text).find() || text.startsWith("obie druzyny ")
                || text.startsWith("oba zespoly ") || text.startsWith("kazda druzyna ")) {
            return rejected(Status.UNSUPPORTED_COMPOSITE);
        }
        if (PLAYER.matcher(text).matches()) return rejected(Status.UNSUPPORTED_PLAYER);
        if (HANDICAP_OR_COMPARISON.matcher(text).find()) {
            return rejected(Status.UNSUPPORTED_HANDICAP_OR_COMPARISON);
        }

        Matcher matcher = SIGNED_TEAM_OVER.matcher(text);
        if (matcher.matches()) {
            Resolution resolution = resolve(matcher.group(1), homeTeam, awayTeam);
            if (!resolution.parsed()) return rejected(resolution.status(), SyntaxFamily.SIGNED_TEAM_OVER);
            return parsed(Category.TEAM_TOTAL, FootballFixtureStatisticCondition.threshold(
                    FootballFixtureStatisticType.FOULS, resolution.subject(),
                    FootballFixtureStatisticCondition.Comparison.OVER, decimal(matcher.group(2))),
                    SyntaxFamily.SIGNED_TEAM_OVER);
        }
        if (RAW_SIGNED.matcher(tipTitle).find()) return rejected(Status.UNSUPPORTED_SIGNED_NOTATION);

        matcher = MATCH_TOTAL.matcher(text);
        if (matcher.matches()) return total(Category.MATCH_TOTAL,
                FootballFixtureStatisticCondition.Subject.MATCH, matcher.group(1), matcher.group(2));
        matcher = MATCH_MINIMUM.matcher(text);
        if (matcher.matches()) return parsed(Category.MATCH_TOTAL,
                FootballFixtureStatisticCondition.threshold(FootballFixtureStatisticType.FOULS,
                        FootballFixtureStatisticCondition.Subject.MATCH,
                        FootballFixtureStatisticCondition.Comparison.MINIMUM,
                        new BigDecimal(matcher.group(1))));
        matcher = MATCH_RANGE.matcher(text);
        if (matcher.matches()) return range(Category.MATCH_TOTAL,
                FootballFixtureStatisticCondition.Subject.MATCH, matcher.group(1), matcher.group(2));
        matcher = TEAM_TOTAL.matcher(text);
        if (matcher.matches()) return teamTotal(matcher.group(1), matcher.group(2), matcher.group(3), homeTeam, awayTeam);
        matcher = TEAM_MINIMUM.matcher(text);
        if (matcher.matches()) return teamMinimum(matcher.group(1), matcher.group(2), homeTeam, awayTeam);
        matcher = TEAM_RANGE.matcher(text);
        if (matcher.matches()) {
            Resolution resolution = resolve(matcher.group(1), homeTeam, awayTeam);
            if (!resolution.parsed()) return rejected(resolution.status());
            return range(Category.TEAM_RANGE, resolution.subject(), matcher.group(2), matcher.group(3));
        }
        return rejected(Status.UNSUPPORTED_GRAMMAR);
    }

    private static ParseResult teamTotal(String rawSubject, String direction, String number, String home, String away) {
        Resolution resolution = resolve(rawSubject, home, away);
        return resolution.parsed() ? total(Category.TEAM_TOTAL, resolution.subject(), direction, number)
                : rejected(resolution.status());
    }

    private static ParseResult teamMinimum(String rawSubject, String number, String home, String away) {
        Resolution resolution = resolve(rawSubject, home, away);
        if (!resolution.parsed()) return rejected(resolution.status());
        return parsed(Category.TEAM_MINIMUM, FootballFixtureStatisticCondition.threshold(
                FootballFixtureStatisticType.FOULS, resolution.subject(),
                FootballFixtureStatisticCondition.Comparison.MINIMUM, new BigDecimal(number)));
    }

    private static ParseResult total(Category category, FootballFixtureStatisticCondition.Subject subject,
                                     String direction, String number) {
        var comparison = switch (direction) {
            case "powyzej", "wiecej niz", "over" -> FootballFixtureStatisticCondition.Comparison.OVER;
            case "ponizej", "mniej niz", "under" -> FootballFixtureStatisticCondition.Comparison.UNDER;
            default -> throw new IllegalArgumentException("Unsupported direction: " + direction);
        };
        return parsed(category, FootballFixtureStatisticCondition.threshold(
                FootballFixtureStatisticType.FOULS, subject, comparison, decimal(number)));
    }

    private static ParseResult range(Category category, FootballFixtureStatisticCondition.Subject subject,
                                     String minimum, String maximum) {
        BigDecimal min = decimal(minimum), max = decimal(maximum);
        if (max.compareTo(min) < 0) return rejected(Status.UNSUPPORTED_GRAMMAR);
        return parsed(category, FootballFixtureStatisticCondition.range(
                FootballFixtureStatisticType.FOULS, subject, min, max));
    }

    private static Resolution resolve(String rawSubject, String home, String away) {
        return switch (FootballTeamMarketParser.resolveParticipant(rawSubject, home, away)) {
            case HOME -> new Resolution(FootballFixtureStatisticCondition.Subject.HOME, null);
            case AWAY -> new Resolution(FootballFixtureStatisticCondition.Subject.AWAY, null);
            case AMBIGUOUS, UNRESOLVED -> new Resolution(null, Status.AMBIGUOUS_PARTICIPANT);
        };
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value.replace('ł', 'l').replace('Ł', 'L'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}.,+\\-]+", " ").replaceAll("\\s+", " ").trim()
                .replaceAll("[.]$", "").replaceAll("\\s*[—–]\\s*", " ").replaceAll("\\s+-\\s+", " ");
    }

    private static BigDecimal decimal(String raw) { return new BigDecimal(raw.replace(',', '.')); }
    private static ParseResult parsed(Category category, FootballFixtureStatisticCondition condition) {
        return parsed(category, condition, SyntaxFamily.UNSIGNED);
    }
    private static ParseResult parsed(Category category, FootballFixtureStatisticCondition condition, SyntaxFamily family) {
        return new ParseResult(Status.PARSED, category, condition, family);
    }
    private static ParseResult rejected(Status status) { return rejected(status, SyntaxFamily.UNSIGNED); }
    private static ParseResult rejected(Status status, SyntaxFamily family) { return new ParseResult(status, null, null, family); }

    public enum Category { MATCH_TOTAL, TEAM_TOTAL, TEAM_MINIMUM, TEAM_RANGE }
    public enum SyntaxFamily { UNSIGNED, SIGNED_TEAM_OVER }
    public enum Status {
        PARSED, NOT_FOULS_LIKE, AMBIGUOUS_PARTICIPANT, UNSUPPORTED_PERIOD,
        UNSUPPORTED_PLAYER, UNSUPPORTED_HANDICAP_OR_COMPARISON, UNSUPPORTED_COMPOSITE,
        UNSUPPORTED_SIGNED_NOTATION, UNSUPPORTED_GRAMMAR
    }
    public record ParseResult(Status status, Category category,
                              FootballFixtureStatisticCondition condition, SyntaxFamily syntaxFamily) {
        public boolean parsed() { return status == Status.PARSED; }
    }
    private record Resolution(FootballFixtureStatisticCondition.Subject subject, Status status) {
        boolean parsed() { return subject != null; }
    }
}
