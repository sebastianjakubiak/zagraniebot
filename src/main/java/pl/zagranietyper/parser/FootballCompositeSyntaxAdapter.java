package pl.zagranietyper.parser;

import pl.zagranietyper.model.*;
import pl.zagranietyper.service.FootballMarketUnifiedAdapter;
import pl.zagranietyper.service.FootballTeamMarketUnifiedAdapter;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Conservative whole-title adapter for conjunctions of existing football primitives. */
public final class FootballCompositeSyntaxAdapter {
    private static final Pattern CONNECTOR = Pattern.compile("\\s+(?:i|oraz|a)\\s+|\\s+\\+\\s+|,\\s+(?=\\p{L})");
    private static final Pattern PERIOD = Pattern.compile("(?:^| )(?:(?:1|2)[.]? )?polow|do przerwy|half|w kazdej polowie");
    private static final Pattern PLAYER = Pattern.compile("(?:^| )(?:[a-z]{1,3}[.] [a-z-]+|lewy|[a-z-]+ [a-z-]+) (?:strzeli|odda|minimum [0-9]+ celn|powyzej [0-9.,]+ celn|otrzyma)");
    private static final Pattern CARD = Pattern.compile("kart|booking|upomnien");
    private static final Pattern COMPARISON = Pattern.compile("handicap|wiecej (?:rzutow rozn|rozn|strzal).* (?:od|niz)|mniej .* (?:od|niz)");
    private static final Pattern TIMELINE = Pattern.compile("pierwsz(?:y|a) (?:gol|bram)|1[.]? gol|otworzy wynik|ostatni gol");
    private static final Pattern OUTRIGHT = Pattern.compile("awans|mistrz|wygra (?:lige|turniej|grupe)|final");
    private static final Pattern WHOLE_MATCH_TOTAL = Pattern.compile(
            "^(?:(?:powyzej|ponizej|ponad|over|under|wiecej niz|mniej niz) [0-9]+(?:[.,][0-9]+)?|[+-][0-9]+(?:[.,][0-9]+)?) (?:gol|gola|goli|gole|bramka|bramki|bramek)(?: w meczu)?$");

    private final FootballMarketParser matchParser = new FootballMarketParser();
    private final FootballTeamMarketParser teamParser = new FootballTeamMarketParser();
    private final FootballDoubleChanceParser doubleChanceParser = new FootballDoubleChanceParser();
    private final FootballCornersSyntaxAdapter corners = new FootballCornersSyntaxAdapter();
    private final FootballShotsSyntaxAdapter shots = new FootballShotsSyntaxAdapter();
    private final FootballFoulsSyntaxAdapter fouls = new FootballFoulsSyntaxAdapter();
    private final FootballMarketUnifiedAdapter matchAdapter = new FootballMarketUnifiedAdapter();
    private final FootballTeamMarketUnifiedAdapter teamAdapter = new FootballTeamMarketUnifiedAdapter();

    public ParseResult parse(String title, String home, String away) {
        if (title == null || title.isBlank()) return rejected(Status.NOT_COMPOSITE, List.of());
        String normalized = stripPrefix(normalize(title));
        List<String> pieces = Arrays.stream(CONNECTOR.split(normalized)).map(String::trim)
                .filter(s -> !s.isBlank()).toList();
        if (pieces.size() < 2 || primitivePieceCount(pieces) < 2) return rejected(Status.NOT_COMPOSITE, pieces);
        if (PERIOD.matcher(normalized).find()) return rejected(Status.PERIOD_BRANCH, pieces);
        if (CARD.matcher(normalized).find()) return rejected(Status.CARD_SEMANTIC_UNKNOWN, pieces);
        if (TIMELINE.matcher(normalized).find()) return rejected(Status.PLAYER_BRANCH, pieces);
        if (OUTRIGHT.matcher(normalized).find()) return rejected(Status.UNSUPPORTED_BRANCH, pieces);
        if (COMPARISON.matcher(normalized).find()) return rejected(Status.COMPARISON_OR_HANDICAP, pieces);
        if (PLAYER.matcher(normalized).find()) return rejected(Status.PLAYER_BRANCH, pieces);

        List<FootballCompositeCondition.Branch> branches = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (String piece : pieces) {
            BranchParse branch = parseBranch(piece, home, away);
            if (branch.branch() == null) return rejected(branch.status(), pieces);
            String key = branch.branch().toString();
            if (!unique.add(key)) return rejected(Status.AMBIGUOUS_GRAMMAR, pieces);
            branches.add(branch.branch());
        }
        return new ParseResult(Status.PARSED, pieces, new FootballCompositeCondition(branches));
    }

    public boolean looksLikeComposite(String title) {
        if (title == null) return false;
        String text = stripPrefix(normalize(title));
        List<String> pieces = Arrays.stream(CONNECTOR.split(text)).map(String::trim).toList();
        return pieces.size() >= 2 && primitivePieceCount(pieces) >= 2;
    }

    private BranchParse parseBranch(String text, String home, String away) {
        var match = matchParser.parse(text, home, away);
        if (match.isPresent()) {
            boolean hasMatchTotal = match.get().conditions().stream()
                    .anyMatch(FootballMarket.TotalGoals.class::isInstance);
            if (!hasMatchTotal || WHOLE_MATCH_TOTAL.matcher(text).matches())
                return parsed(new FootballCompositeCondition.ScoreBranch(matchAdapter.adapt(match.get())));
        }
        var team = teamParser.parse(text, home, away);
        if (team.isPresent()) return parsed(new FootballCompositeCondition.ScoreBranch(teamAdapter.adapt(team.get())));
        var corner = corners.parse(text, home, away);
        if (corner.parsed()) return parsed(new FootballCompositeCondition.StatisticBranch(corner.condition()));
        var shot = shots.parse(text, home, away);
        if (shot.parsed()) return parsed(new FootballCompositeCondition.StatisticBranch(shot.condition()));
        var foul = fouls.parse(text, home, away);
        if (foul.parsed()) return parsed(new FootballCompositeCondition.StatisticBranch(foul.condition()));
        var doubleChance = doubleChanceParser.parse(text, home, away);
        if (doubleChance.status() == FootballDoubleChanceParser.Status.SUBJECT_NOT_FOUND
                || doubleChance.status() == FootballDoubleChanceParser.Status.SUBJECT_MISMATCH
                || doubleChance.status() == FootballDoubleChanceParser.Status.SUBJECT_AMBIGUOUS)
            return new BranchParse(null, Status.PARTICIPANT_UNRESOLVED);
        if (corner.status() == FootballCornersSyntaxAdapter.Status.AMBIGUOUS_PARTICIPANT
                || shot.status() == FootballShotsSyntaxAdapter.Status.AMBIGUOUS_PARTICIPANT
                || foul.status() == FootballFoulsSyntaxAdapter.Status.AMBIGUOUS_PARTICIPANT)
            return new BranchParse(null, Status.PARTICIPANT_UNRESOLVED);
        if (corner.status() == FootballCornersSyntaxAdapter.Status.UNSUPPORTED_HANDICAP
                || shot.status() == FootballShotsSyntaxAdapter.Status.UNSUPPORTED_HANDICAP_OR_COMPARISON
                || foul.status() == FootballFoulsSyntaxAdapter.Status.UNSUPPORTED_HANDICAP_OR_COMPARISON)
            return new BranchParse(null, Status.COMPARISON_OR_HANDICAP);
        if (text.matches(".*(?:strzeli|celn.*strzal|strzal.*celn).*") && !containsTeam(text, home, away))
            return new BranchParse(null, Status.PLAYER_BRANCH);
        return new BranchParse(null, Status.UNSUPPORTED_BRANCH);
    }

    private static boolean containsTeam(String text, String home, String away) {
        String h = normalize(home), a = normalize(away);
        return (!h.isBlank() && text.contains(h)) || (!a.isBlank() && text.contains(a));
    }
    private static int primitiveDomains(String text) {
        int n = 0;
        if (text.matches(".*(?:gol|bram|btts|obie druzyny|obie strzela).*$")) n++;
        if (text.matches(".*(?:wygra|nie przegra|remis|^[12x]/|/[12x]).*$")) n++;
        if (text.matches(".*(?:rozn|corner).*$")) n++;
        if (text.matches(".*(?:strzal|shots?).*$")) n++;
        if (text.matches(".*faul.*")) n++;
        if (text.matches(".*(?:kart|booking).*$")) n++;
        return n;
    }
    private static int primitivePieceCount(List<String> pieces) {
        return (int) pieces.stream().filter(piece -> primitiveDomains(piece) > 0).count();
    }
    private static String stripPrefix(String text) { return text.replaceFirst("^(?:betbuilder|superbets?|betarchitekt|mycombi) ?[: -]? ?", ""); }
    private static String normalize(String value) { return Normalizer.normalize(value.replace('ł','l').replace('Ł','L'), Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replaceAll("[✅❌⏳]", "").replaceAll("\\s+", " ").trim(); }
    private static BranchParse parsed(FootballCompositeCondition.Branch branch) { return new BranchParse(branch, null); }
    private static ParseResult rejected(Status status, List<String> pieces) { return new ParseResult(status, List.copyOf(pieces), null); }
    private record BranchParse(FootballCompositeCondition.Branch branch, Status status) {}
    public enum Status { PARSED, NOT_COMPOSITE, UNSUPPORTED_BRANCH, AMBIGUOUS_GRAMMAR, PARTICIPANT_UNRESOLVED, PLAYER_BRANCH, PERIOD_BRANCH, CARD_SEMANTIC_UNKNOWN, COMPARISON_OR_HANDICAP }
    public record ParseResult(Status status, List<String> normalizedBranches, FootballCompositeCondition condition) { public boolean parsed() { return status == Status.PARSED; } }
}
