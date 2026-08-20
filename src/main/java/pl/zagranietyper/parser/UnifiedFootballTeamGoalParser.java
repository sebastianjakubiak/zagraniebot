package pl.zagranietyper.parser;

import pl.zagranietyper.model.FootballScorePeriod;
import pl.zagranietyper.model.FootballTeamMarket;
import pl.zagranietyper.model.UnifiedFootballMarket;
import pl.zagranietyper.service.FootballTeamMarketUnifiedAdapter;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Whole-market syntax adapter for deterministic full-time team-goal markets. */
public final class UnifiedFootballTeamGoalParser {

    private static final String NUMBER =
            "(jeden|jedna|jedno|dwa|dwie|trzy|cztery|piec|szesc|siedem|osiem|dziewiec|dziesiec|[0-9]+)";
    private static final String GOAL_WORD =
            "(?:gol|gole|gola|goli|bramka|bramki|bramek)";

    private static final Pattern MINIMUM_AFTER = Pattern.compile(
            "^(.+?)\\s+strzeli\\s+co\\s+najmniej\\s+" + NUMBER + "\\s+" + GOAL_WORD + "$"
    );
    private static final Pattern MINIMUM_BEFORE = Pattern.compile(
            "^co\\s+najmniej\\s+" + NUMBER + "\\s+" + GOAL_WORD + "\\s+(.+)$"
    );
    private static final Pattern RANGE_TEAM_FIRST = Pattern.compile(
            "^(.+?)\\s+(?:przedzial\\s+goli|suma\\s+goli)\\s+([0-9]+)\\s*-\\s*([0-9]+)$"
    );
    private static final Pattern RANGE_LABEL_FIRST = Pattern.compile(
            "^przedzial\\s+goli\\s+(.+?)\\s+([0-9]+)\\s*-\\s*([0-9]+)$"
    );
    private static final List<Pattern> LEGACY_SUBJECT_SHAPES = List.of(
            Pattern.compile("^(.+?)\\s+(?:powyzej|ponizej|over|under)\\s+[0-9]+(?:[.,][0-9]+)?.*$"),
            Pattern.compile("^(.+?)\\s+(?:strzeli|zdobedzie)\\s+(?:wiecej\\s+niz|mniej\\s+niz|powyzej|ponizej|over|under).*$"),
            Pattern.compile("^(.+?)\\s+(?:nie\\s+)?(?:strzeli|zdobedzie)\\s+(?:gola|bramke).*$")
    );
    private static final Pattern SIGNED = Pattern.compile(
            "(?:^|\\s)(?:[+-]\\s*[0-9]|\\+\\s*ponad)"
    );
    private static final Pattern PLAYER_INITIAL = Pattern.compile(
            "(?:^|\\s)[A-ZŁŚŻŹĆŃÓĘĄ]\\.\\s*\\p{L}+"
    );
    private static final Pattern TEAM_GOAL_CUE = Pattern.compile(
            "(?i)(strzeli|zdobedzie|co\\s+najmniej|przedzial\\s+goli|suma\\s+goli|liczba\\s+goli|powyzej|ponizej|ponad|over|under)"
    );
    private static final Pattern COMPOSITE = Pattern.compile(
            "\\b(wygra|wygrana|zwyciestwo|zwyciezy|nie przegra|remis|btts|obie druzyny|awans)\\b"
    );

    private final FootballTeamMarketParser legacyParser = new FootballTeamMarketParser();
    private final FootballTeamMarketUnifiedAdapter legacyAdapter = new FootballTeamMarketUnifiedAdapter();

    public ParseResult parse(String tipTitle, String homeTeam, String awayTeam) {
        if (tipTitle == null || tipTitle.isBlank()) return rejected(Status.NOT_TEAM_GOAL_LIKE);
        String text = normalize(tipTitle);
        if (!looksTeamGoalLike(text)) return rejected(Status.NOT_TEAM_GOAL_LIKE);
        if (SIGNED.matcher(tipTitle).find()) return rejected(Status.UNSUPPORTED_SIGNED_FORMAT);
        if (COMPOSITE.matcher(text).find() || text.contains(" i ") || text.contains(" / ")) {
            return rejected(Status.UNSUPPORTED_COMPOSITE);
        }
        if (PLAYER_INITIAL.matcher(tipTitle).find()) return rejected(Status.UNSUPPORTED_FORMAT);

        Optional<FootballTeamMarket> legacy = legacyParser.parse(tipTitle, homeTeam, awayTeam);
        if (legacy.isPresent()) {
            Category category = legacy.get() instanceof FootballTeamMarket.TeamTotalGoals
                    ? Category.TEAM_TOTAL : Category.TEAM_TO_SCORE;
            return parsed(category, side(legacy.get().side()), legacyAdapter.adapt(legacy.get()), legacy.get());
        }

        Matcher minimumAfter = MINIMUM_AFTER.matcher(text);
        if (minimumAfter.matches()) {
            return minimum(minimumAfter.group(1), minimumAfter.group(2), homeTeam, awayTeam);
        }
        Matcher minimumBefore = MINIMUM_BEFORE.matcher(text);
        if (minimumBefore.matches()) {
            return minimum(minimumBefore.group(2), minimumBefore.group(1), homeTeam, awayTeam);
        }
        Matcher rangeFirst = RANGE_TEAM_FIRST.matcher(text);
        if (rangeFirst.matches()) {
            return range(rangeFirst.group(1), rangeFirst.group(2), rangeFirst.group(3), homeTeam, awayTeam);
        }
        Matcher rangeLabel = RANGE_LABEL_FIRST.matcher(text);
        if (rangeLabel.matches()) {
            return range(rangeLabel.group(1), rangeLabel.group(2), rangeLabel.group(3), homeTeam, awayTeam);
        }

        for (Pattern shape : LEGACY_SUBJECT_SHAPES) {
            Matcher matcher = shape.matcher(text);
            if (matcher.matches()) {
                FootballParticipantResolver.Resolution resolution =
                        FootballTeamMarketParser.resolveParticipant(matcher.group(1), homeTeam, awayTeam);
                if (resolution == FootballParticipantResolver.Resolution.AMBIGUOUS) {
                    return rejected(Status.AMBIGUOUS_PARTICIPANT);
                }
                if (resolution == FootballParticipantResolver.Resolution.UNRESOLVED) {
                    return rejected(Status.UNRESOLVED_PARTICIPANT);
                }
            }
        }

        return rejected(Status.UNSUPPORTED_FORMAT);
    }

    private static ParseResult minimum(String subject, String rawMinimum, String home, String away) {
        Integer minimum = integer(rawMinimum);
        if (minimum == null) return rejected(Status.UNSUPPORTED_FORMAT);
        return condition(Category.TEAM_MINIMUM, subject, home, away,
                side -> new UnifiedFootballMarket.MinimumGoals(side, FootballScorePeriod.FULL_TIME, minimum));
    }

    private static ParseResult range(String subject, String rawMin, String rawMax, String home, String away) {
        int minimum = Integer.parseInt(rawMin);
        int maximum = Integer.parseInt(rawMax);
        if (maximum < minimum) return rejected(Status.MALFORMED_RANGE);
        return condition(Category.TEAM_RANGE, subject, home, away,
                side -> new UnifiedFootballMarket.GoalRange(side, FootballScorePeriod.FULL_TIME, minimum, maximum));
    }

    private static ParseResult condition(Category category, String subject, String home, String away,
                                         java.util.function.Function<UnifiedFootballMarket.GoalSubject,
                                                 UnifiedFootballMarket.Condition> factory) {
        FootballParticipantResolver.Resolution resolution =
                FootballTeamMarketParser.resolveParticipant(subject, home, away);
        if (resolution == FootballParticipantResolver.Resolution.AMBIGUOUS) {
            return rejected(Status.AMBIGUOUS_PARTICIPANT);
        }
        if (resolution == FootballParticipantResolver.Resolution.UNRESOLVED) {
            return rejected(Status.UNRESOLVED_PARTICIPANT);
        }
        UnifiedFootballMarket.GoalSubject side = resolution == FootballParticipantResolver.Resolution.HOME
                ? UnifiedFootballMarket.GoalSubject.HOME : UnifiedFootballMarket.GoalSubject.AWAY;
        return parsed(category, side, new UnifiedFootballMarket(List.of(factory.apply(side))), null);
    }

    private static boolean looksTeamGoalLike(String text) {
        boolean signedGoals = text.matches(".*[+-]\\s*(?:[0-9]+(?:[.,][0-9]+)?)?\\s*(?:gol|gole|gola|goli|bramk.*).*" );
        if (!TEAM_GOAL_CUE.matcher(text).find() && !signedGoals) return false;
        if (text.matches("^(powyzej|ponizej|over|under)\\b.*")) return false;
        return text.contains("gol") || text.contains("bram") || text.contains("strzeli") || text.contains("zdobedzie");
    }

    private static Integer integer(String raw) {
        return switch (raw) {
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
            default -> raw.chars().allMatch(Character::isDigit) ? Integer.valueOf(raw) : null;
        };
    }

    private static UnifiedFootballMarket.GoalSubject side(FootballTeamMarket.TeamSide side) {
        return side == FootballTeamMarket.TeamSide.HOME
                ? UnifiedFootballMarket.GoalSubject.HOME : UnifiedFootballMarket.GoalSubject.AWAY;
    }

    private static String normalize(String value) {
        return java.text.Normalizer.normalize(value.replace('ł', 'l').replace('Ł', 'L'),
                        java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}.,+\\-/]+", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private static ParseResult parsed(Category category, UnifiedFootballMarket.GoalSubject subject,
                                      UnifiedFootballMarket market, FootballTeamMarket legacy) {
        return new ParseResult(Status.PARSED, category, subject, market, legacy);
    }
    private static ParseResult rejected(Status status) { return new ParseResult(status, null, null, null, null); }

    public enum Category { TEAM_TOTAL, TEAM_MINIMUM, TEAM_RANGE, TEAM_TO_SCORE }
    public enum Status {
        PARSED, NOT_TEAM_GOAL_LIKE, AMBIGUOUS_PARTICIPANT, UNRESOLVED_PARTICIPANT,
        UNSUPPORTED_SIGNED_FORMAT, UNSUPPORTED_COMPOSITE, MALFORMED_RANGE, UNSUPPORTED_FORMAT
    }
    public record ParseResult(Status status, Category category,
                              UnifiedFootballMarket.GoalSubject subject,
                              UnifiedFootballMarket market,
                              FootballTeamMarket legacyMarket) {
        public boolean parsed() { return status == Status.PARSED; }
    }
}
