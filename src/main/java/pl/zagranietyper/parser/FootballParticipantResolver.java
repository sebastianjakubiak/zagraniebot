package pl.zagranietyper.parser;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Conservative, deterministic resolver for fixture participants. */
public final class FootballParticipantResolver {

    private static final Pattern DIACRITIC_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");

    private static final Set<String> COMMON_TEAM_NOISE = Set.of(
            "fc", "cf", "fk", "afc", "sc", "ks", "mks", "rks",
            "lks", "ssa", "sv", "cp", "ac", "as", "club"
    );
    private static final Set<String> SIMPLE_INFLECTION_SUFFIXES = Set.of(
            "u", "a", "em", "ie", "i", "y", "owi", "om", "ow"
    );
    private static final Set<String> A_STEM_SUFFIXES = Set.of(
            "a", "y", "i", "ie", "ii", "e"
    );

    public Resolution resolve(
            String subject,
            String homeTeam,
            String awayTeam,
            MatchingPolicy policy,
            Map<String, List<String>> aliases
    ) {
        Resolution exact = resolveExactNormalized(subject, homeTeam, awayTeam);
        if (exact != Resolution.UNRESOLVED) return exact;

        String normalizedSubject = normalize(subject);
        var aliasTarget = FootballParticipantAliasRegistry.canonicalTarget(normalizedSubject);
        if (aliasTarget.isPresent()) {
            return resolveExactNormalized(aliasTarget.get(), homeTeam, awayTeam);
        }

        int homeScore = matchScore(subject, homeTeam, policy, aliases);
        int awayScore = matchScore(subject, awayTeam, policy, aliases);

        if (homeScore <= 0 && awayScore <= 0) return Resolution.UNRESOLVED;
        if (homeScore == awayScore) return Resolution.AMBIGUOUS;
        if (homeScore > awayScore) return Resolution.HOME;
        if (awayScore > homeScore) return Resolution.AWAY;
        return Resolution.UNRESOLVED;
    }

    private static Resolution resolveExactNormalized(
            String subject,
            String homeTeam,
            String awayTeam
    ) {
        String normalizedSubject = normalize(subject);
        boolean homeMatches = normalizedSubject.equals(normalize(homeTeam));
        boolean awayMatches = normalizedSubject.equals(normalize(awayTeam));
        if (homeMatches && awayMatches) return Resolution.AMBIGUOUS;
        if (homeMatches) return Resolution.HOME;
        if (awayMatches) return Resolution.AWAY;
        return Resolution.UNRESOLVED;
    }

    private static int matchScore(
            String subject,
            String apiTeam,
            MatchingPolicy policy,
            Map<String, List<String>> aliases
    ) {
        List<String> subjectTokens = meaningfulTokens(subject, policy);
        String normalizedSubject = normalize(subject);
        String normalizedTeam = normalize(apiTeam);
        if (subjectTokens.isEmpty()) {
            if (policy == MatchingPolicy.STRICT_SCORED_SUBSET
                    && !normalizedSubject.isBlank()
                    && !normalizedSubject.contains(" ")
                    && List.of(normalizedTeam.split("\\s+")).contains(normalizedSubject)) {
                return 5;
            }
            return 0;
        }
        if (policy == MatchingPolicy.STRICT_SCORED_SUBSET
                && normalizedSubject.equals(normalizedTeam)) return 100;

        int best = 0;

        for (String variant : teamVariants(apiTeam, aliases)) {
            List<String> teamTokens = meaningfulTokens(variant, policy);
            if (teamTokens.isEmpty()) continue;

            int score = switch (policy) {
                case EXACT_ORDERED -> exactOrdered(teamTokens, subjectTokens);
                case SUBJECT_TOKENS_IN_TEAM -> subjectTokensInTeam(subjectTokens, teamTokens);
                case STRICT_SCORED_SUBSET -> strictSubsetScore(subjectTokens, teamTokens);
            };
            best = Math.max(best, score);
        }
        return best;
    }

    private static int exactOrdered(List<String> team, List<String> subject) {
        if (team.size() != subject.size()) return 0;
        for (int i = 0; i < team.size(); i++) {
            if (!tokenEquivalent(team.get(i), subject.get(i))) return 0;
        }
        return 1;
    }

    private static int subjectTokensInTeam(List<String> subject, List<String> team) {
        for (String subjectToken : subject) {
            boolean found = false;
            for (String teamToken : team) {
                if (tokenEquivalent(teamToken, subjectToken)) {
                    found = true;
                    break;
                }
            }
            if (!found) return 0;
        }
        return 1;
    }

    private static int strictSubsetScore(List<String> subject, List<String> team) {
        boolean[] used = new boolean[team.size()];
        int matches = 0;
        for (String subjectToken : subject) {
            boolean found = false;
            for (int i = 0; i < team.size(); i++) {
                if (!used[i] && broadTokenEquivalent(subjectToken, team.get(i))) {
                    used[i] = true;
                    matches++;
                    found = true;
                    break;
                }
            }
            if (!found) return 0;
        }
        return 10 + matches * 10;
    }

    private static boolean tokenEquivalent(String teamToken, String subjectToken) {
        if (teamToken.equals(subjectToken)) return true;
        if (teamToken.length() < 4 || subjectToken.length() < 4) return false;

        if (subjectToken.startsWith(teamToken) && subjectToken.length() > teamToken.length()) {
            String suffix = subjectToken.substring(teamToken.length());
            if (SIMPLE_INFLECTION_SUFFIXES.contains(suffix)) return true;
        }

        if (teamToken.endsWith("a") && teamToken.length() >= 5) {
            String stem = teamToken.substring(0, teamToken.length() - 1);
            if (subjectToken.startsWith(stem)) {
                String suffix = subjectToken.substring(stem.length());
                if (A_STEM_SUFFIXES.contains(suffix)) return true;
            }
        }
        return false;
    }

    private static boolean broadTokenEquivalent(String left, String right) {
        if (left.equals(right)) return true;
        if (left.length() < 4 || right.length() < 4) return false;
        // right is the API participant token; only derive a subject inflection from that base.
        return tokenEquivalent(right, left) || safePolishInflection(right, left);
    }

    private static boolean safePolishInflection(String teamToken, String subjectToken) {
        String team = teamToken.replace("gn", "ni");
        String subject = subjectToken.replace("gn", "ni");
        if (team.length() < 5 || subject.length() < 5) return false;

        if (team.endsWith("a")) {
            String stem = team.substring(0, team.length() - 1);
            if (subject.startsWith(stem)
                    && A_STEM_SUFFIXES.contains(subject.substring(stem.length()))) return true;
        }
        if (team.endsWith("ie")) {
            String stem = team.substring(0, team.length() - 2);
            return subject.equals(stem + "ia");
        }
        return false;
    }

    private static List<String> teamVariants(
            String apiTeam,
            Map<String, List<String>> aliases
    ) {
        String team = normalize(apiTeam);
        if (team.isBlank()) return List.of();

        List<String> result = new ArrayList<>();
        result.add(team);
        result.addAll(aliases.getOrDefault(team, List.of()));
        return List.copyOf(result);
    }

    private static List<String> meaningfulTokens(String value, MatchingPolicy policy) {
        List<String> result = new ArrayList<>();
        for (String token : tokens(value)) {
            if (COMMON_TEAM_NOISE.contains(token)) continue;
            if (policy == MatchingPolicy.STRICT_SCORED_SUBSET
                    && Set.of("gks", "united", "city").contains(token)) continue;
            if (policy == MatchingPolicy.SUBJECT_TOKENS_IN_TEAM
                    && (token.length() == 1 || token.equals("town"))) continue;
            if (token.chars().allMatch(Character::isDigit)) continue;
            result.add(token);
        }
        return List.copyOf(result);
    }

    private static List<String> tokens(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? List.of() : List.of(normalized.split("\\s+"));
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String transliterated = value.replace('ł', 'l').replace('Ł', 'L');
        String withoutMarks = DIACRITIC_MARKS.matcher(
                Normalizer.normalize(transliterated, Normalizer.Form.NFD)
        ).replaceAll("");
        String cleaned = NON_ALPHANUMERIC.matcher(
                withoutMarks.toLowerCase(Locale.ROOT)
        ).replaceAll(" ");
        return MULTIPLE_SPACES.matcher(cleaned).replaceAll(" ").trim();
    }

    public enum Resolution { HOME, AWAY, UNRESOLVED, AMBIGUOUS }

    public enum MatchingPolicy { EXACT_ORDERED, SUBJECT_TOKENS_IN_TEAM, STRICT_SCORED_SUBSET }
}
