package pl.zagranietyper.parser;

import pl.zagranietyper.model.FootballFixtureStatisticCondition;
import pl.zagranietyper.model.FootballFixtureStatisticType;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Whole-market adapter for deterministic full-time corners thresholds. */
public final class FootballCornersSyntaxAdapter {
    private static final String DIRECTION = "(powyzej|ponizej|wiecej niz|mniej niz|over|under)";
    private static final String NUMBER = "([0-9]+(?:[.,][0-9]+)?)";
    private static final String CORNERS = "(?:rzut(?:u|ow|y)? rozn(?:ego|ych|e)?|rz[.]? ?rozn(?:ych|ego)?|rozn(?:ego|ych|e)?|rozncyh|corner(?:s)?)";

    private static final Pattern MATCH_TOTAL = Pattern.compile(
            "^" + DIRECTION + " " + NUMBER + " " + CORNERS
                    + "(?: w meczu| w tym spotkaniu)?$");
    private static final Pattern MATCH_LABEL_TOTAL = Pattern.compile(
            "^liczba " + CORNERS + " " + DIRECTION + " " + NUMBER + "$");
    private static final Pattern TEAM_TOTAL = Pattern.compile(
            "^(.+?) " + DIRECTION + " " + NUMBER
                    + " " + CORNERS + "(?: w meczu)?$");
    private static final Pattern TEAM_ACTION_TOTAL = Pattern.compile(
            "^(.+?) (?:wykona|wykonaja|zanotuje|zdobedzie) " + DIRECTION + " " + NUMBER
                    + " " + CORNERS + "(?: w meczu)?$");
    private static final Pattern TEAM_LABEL_TOTAL = Pattern.compile(
            "^(.+?) liczba " + CORNERS + " " + DIRECTION + " " + NUMBER + "$");
    private static final Pattern TOTAL_TEAM_LAST = Pattern.compile(
            "^" + DIRECTION + " " + NUMBER + " " + CORNERS + " (.+?)(?: w meczu)?$");
    private static final Pattern TEAM_MINIMUM = Pattern.compile(
            "^(.+?) (?:wykona|wykonaja|zdobedzie) (?:min[.]?|minimum|co najmniej) "
                    + "([0-9]+) " + CORNERS + "$");
    private static final Pattern TEAM_MINIMUM_SHORT = Pattern.compile(
            "^(.+?) (?:min[.]?|minimum|co najmniej) ([0-9]+) " + CORNERS + "$");
    private static final Pattern MATCH_MINIMUM = Pattern.compile(
            "^(?:w meczu obejrzymy )?(?:min[.]?|minimum|co najmniej) ([0-9]+) " + CORNERS + "(?: w meczu)?$");
    private static final Pattern TEAM_RANGE = Pattern.compile(
            "^(.+?) (?:przedzial|liczba) " + CORNERS + " ([0-9]+) ?- ?([0-9]+)$");
    private static final Pattern PERIOD = Pattern.compile(
            "\\b(?:1|2|pierwszej|drugiej) polow|\\bpolow|do przerwy|half|w kazdej polowie|w obu polowach");
    private static final Pattern SIGNED_SUBJECTLESS = Pattern.compile(
            "^([+-]) ?([0-9]+[.,]5) " + CORNERS + "$");
    private static final Pattern SIGNED_TEAM_OVER = Pattern.compile(
            "^(.+?) \\+ ?([0-9]+[.,]5) " + CORNERS + "$");
    private static final Pattern EXPLICIT_HANDICAP_OR_COMPARISON = Pattern.compile(
            "(?:^| )(?:handicap|hcp)(?: |$)|wiecej " + CORNERS + "|mniej " + CORNERS);
    private static final Pattern RAW_SIGNED = Pattern.compile("(?:^|[^0-9])[+-]\\s*[0-9]");
    private static final Pattern UNRELATED_BRANCH = Pattern.compile(
            "\\b(?:gol|gola|gole|goli|bramk|strzeli|btts|obie strzela|kart|faul|strzal|celnych strzal|"
                    + "wygra|wygrana|zwyciestwo|zwyciezy|nie przegra|remis|awans|prowadzil)\\w*");

    public ParseResult parse(String tipTitle, String homeTeam, String awayTeam) {
        if (tipTitle == null || tipTitle.isBlank()) return rejected(Status.NOT_CORNERS_LIKE);
        String text = normalize(tipTitle);
        if (!text.matches(".*(?:rozn|korner|corner).*$")) return rejected(Status.NOT_CORNERS_LIKE);
        if (PERIOD.matcher(text).find()) return rejected(Status.UNSUPPORTED_PERIOD);
        if (UNRELATED_BRANCH.matcher(text).find()) return rejected(Status.UNSUPPORTED_COMPOSITE);
        if (text.startsWith("obie druzyny ") || text.startsWith("oba zespoly ")
                || text.startsWith("kazda druzyna ")) return rejected(Status.UNSUPPORTED_COMPOSITE);
        if (text.startsWith("1 oraz ") || text.contains(" 1 oraz ")) {
            return rejected(Status.UNSUPPORTED_COMPOSITE);
        }
        if (EXPLICIT_HANDICAP_OR_COMPARISON.matcher(text).find()) {
            return rejected(Status.UNSUPPORTED_HANDICAP);
        }

        Matcher matcher = SIGNED_SUBJECTLESS.matcher(text);
        if (matcher.matches()) {
            SyntaxFamily family = "+".equals(matcher.group(1))
                    ? SyntaxFamily.SIGNED_SUBJECTLESS_OVER
                    : SyntaxFamily.SIGNED_SUBJECTLESS_UNDER;
            FootballFixtureStatisticCondition.Comparison comparison = "+".equals(matcher.group(1))
                    ? FootballFixtureStatisticCondition.Comparison.OVER
                    : FootballFixtureStatisticCondition.Comparison.UNDER;
            return parsed(Category.MATCH_TOTAL, FootballFixtureStatisticCondition.threshold(
                    FootballFixtureStatisticType.CORNERS,
                    FootballFixtureStatisticCondition.Subject.MATCH,
                    comparison, decimal(matcher.group(2))), family);
        }
        matcher = SIGNED_TEAM_OVER.matcher(text);
        if (matcher.matches()) {
            Resolution resolution = resolve(matcher.group(1), homeTeam, awayTeam);
            if (!resolution.parsed()) {
                return rejected(resolution.status(), SyntaxFamily.SIGNED_TEAM_OVER);
            }
            return parsed(Category.TEAM_TOTAL, FootballFixtureStatisticCondition.threshold(
                    FootballFixtureStatisticType.CORNERS, resolution.subject(),
                    FootballFixtureStatisticCondition.Comparison.OVER,
                    decimal(matcher.group(2))), SyntaxFamily.SIGNED_TEAM_OVER);
        }
        if (RAW_SIGNED.matcher(tipTitle).find()) return rejected(Status.UNSUPPORTED_HANDICAP);

        matcher = MATCH_TOTAL.matcher(text);
        if (matcher.matches()) {
            return total(Category.MATCH_TOTAL, FootballFixtureStatisticCondition.Subject.MATCH,
                    matcher.group(1), matcher.group(2));
        }
        matcher = MATCH_LABEL_TOTAL.matcher(text);
        if (matcher.matches()) {
            return total(Category.MATCH_TOTAL, FootballFixtureStatisticCondition.Subject.MATCH,
                    matcher.group(1), matcher.group(2));
        }
        matcher = MATCH_MINIMUM.matcher(text);
        if (matcher.matches()) {
            return parsed(Category.MATCH_TOTAL, FootballFixtureStatisticCondition.threshold(
                    FootballFixtureStatisticType.CORNERS,
                    FootballFixtureStatisticCondition.Subject.MATCH,
                    FootballFixtureStatisticCondition.Comparison.MINIMUM,
                    new BigDecimal(matcher.group(1))));
        }
        matcher = TEAM_ACTION_TOTAL.matcher(text);
        if (matcher.matches()) return teamTotal(matcher.group(1), matcher.group(2), matcher.group(3), homeTeam, awayTeam);
        matcher = TEAM_TOTAL.matcher(text);
        if (matcher.matches()) return teamTotal(matcher.group(1), matcher.group(2), matcher.group(3), homeTeam, awayTeam);
        matcher = TEAM_LABEL_TOTAL.matcher(text);
        if (matcher.matches()) return teamTotal(matcher.group(1), matcher.group(2), matcher.group(3), homeTeam, awayTeam);
        matcher = TOTAL_TEAM_LAST.matcher(text);
        if (matcher.matches()) return teamTotal(matcher.group(3), matcher.group(1), matcher.group(2), homeTeam, awayTeam);
        matcher = TEAM_MINIMUM.matcher(text);
        if (matcher.matches()) return teamMinimum(matcher.group(1), matcher.group(2), homeTeam, awayTeam);
        matcher = TEAM_MINIMUM_SHORT.matcher(text);
        if (matcher.matches()) return teamMinimum(matcher.group(1), matcher.group(2), homeTeam, awayTeam);
        matcher = TEAM_RANGE.matcher(text);
        if (matcher.matches()) return teamRange(matcher.group(1), matcher.group(2), matcher.group(3), homeTeam, awayTeam);
        return rejected(Status.UNSUPPORTED_GRAMMAR);
    }

    private static ParseResult teamTotal(String subject, String direction, String number,
                                         String home, String away) {
        Resolution resolution = resolve(subject, home, away);
        if (!resolution.parsed()) return rejected(resolution.status());
        return total(Category.TEAM_TOTAL, resolution.subject(), direction, number);
    }

    private static ParseResult teamMinimum(String rawSubject, String number, String home, String away) {
        Resolution resolution = resolve(rawSubject, home, away);
        if (!resolution.parsed()) return rejected(resolution.status());
        return parsed(Category.TEAM_MINIMUM, FootballFixtureStatisticCondition.threshold(
                FootballFixtureStatisticType.CORNERS, resolution.subject(),
                FootballFixtureStatisticCondition.Comparison.MINIMUM, new BigDecimal(number)));
    }

    private static ParseResult teamRange(String rawSubject, String minimum, String maximum,
                                         String home, String away) {
        Resolution resolution = resolve(rawSubject, home, away);
        if (!resolution.parsed()) return rejected(resolution.status());
        BigDecimal min = new BigDecimal(minimum);
        BigDecimal max = new BigDecimal(maximum);
        if (max.compareTo(min) < 0) return rejected(Status.UNSUPPORTED_GRAMMAR);
        return parsed(Category.TEAM_RANGE, FootballFixtureStatisticCondition.range(
                FootballFixtureStatisticType.CORNERS, resolution.subject(), min, max));
    }

    private static ParseResult total(Category category, FootballFixtureStatisticCondition.Subject subject,
                                     String rawDirection, String rawNumber) {
        FootballFixtureStatisticCondition.Comparison comparison = switch (rawDirection) {
            case "powyzej", "wiecej niz", "over" -> FootballFixtureStatisticCondition.Comparison.OVER;
            case "ponizej", "mniej niz", "under" -> FootballFixtureStatisticCondition.Comparison.UNDER;
            default -> throw new IllegalArgumentException("Unsupported direction: " + rawDirection);
        };
        return parsed(category, FootballFixtureStatisticCondition.threshold(
                FootballFixtureStatisticType.CORNERS, subject, comparison,
                decimal(rawNumber)));
    }

    private static Resolution resolve(String rawSubject, String home, String away) {
        FootballParticipantResolver.Resolution resolution =
                FootballTeamMarketParser.resolveParticipant(rawSubject, home, away);
        return switch (resolution) {
            case HOME -> new Resolution(FootballFixtureStatisticCondition.Subject.HOME, null);
            case AWAY -> new Resolution(FootballFixtureStatisticCondition.Subject.AWAY, null);
            case AMBIGUOUS -> new Resolution(null, Status.AMBIGUOUS_PARTICIPANT);
            case UNRESOLVED -> new Resolution(null, Status.AMBIGUOUS_PARTICIPANT);
        };
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value.replace('ł', 'l').replace('Ł', 'L'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}.,+\\-]+", " ")
                .replaceAll("\\s+", " ").trim()
                .replaceAll("[.]$", "")
                .replaceAll("\\s*[—–]\\s*", " ")
                .replaceAll("\\s+-\\s+", " ");
    }

    private static ParseResult parsed(Category category, FootballFixtureStatisticCondition condition) {
        return parsed(category, condition, SyntaxFamily.UNSIGNED);
    }
    private static ParseResult parsed(Category category, FootballFixtureStatisticCondition condition,
                                      SyntaxFamily syntaxFamily) {
        return new ParseResult(Status.PARSED, category, condition, syntaxFamily);
    }
    private static ParseResult rejected(Status status) {
        return rejected(status, SyntaxFamily.UNSIGNED);
    }
    private static ParseResult rejected(Status status, SyntaxFamily syntaxFamily) {
        return new ParseResult(status, null, null, syntaxFamily);
    }
    private static BigDecimal decimal(String raw) { return new BigDecimal(raw.replace(',', '.')); }

    public enum Category { MATCH_TOTAL, TEAM_TOTAL, TEAM_MINIMUM, TEAM_RANGE }
    public enum SyntaxFamily {
        UNSIGNED, SIGNED_SUBJECTLESS_OVER, SIGNED_SUBJECTLESS_UNDER, SIGNED_TEAM_OVER;
        public boolean signed() { return this != UNSIGNED; }
    }
    public enum Status {
        PARSED, NOT_CORNERS_LIKE, AMBIGUOUS_PARTICIPANT, UNSUPPORTED_PERIOD,
        UNSUPPORTED_HANDICAP, UNSUPPORTED_COMPOSITE, UNSUPPORTED_GRAMMAR
    }
    public record ParseResult(Status status, Category category,
                              FootballFixtureStatisticCondition condition,
                              SyntaxFamily syntaxFamily) {
        public boolean parsed() { return status == Status.PARSED; }
    }
    private record Resolution(FootballFixtureStatisticCondition.Subject subject, Status status) {
        boolean parsed() { return subject != null; }
    }
}
