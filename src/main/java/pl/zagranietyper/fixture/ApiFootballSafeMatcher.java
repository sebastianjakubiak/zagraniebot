package pl.zagranietyper.fixture;

import pl.zagranietyper.model.ApiFootballFixture;
import pl.zagranietyper.model.ApiFootballMatch;
import pl.zagranietyper.model.ApiFootballResolutionCandidate;
import pl.zagranietyper.model.ResolutionConfidence;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Safety layer for ApiFootballMatcher.
 *
 * If context_heading contains a structural matchup "A - B", only fixtures
 * compatible with that pair are allowed to reach the existing matcher.
 * This prevents context contamination from previousText/postTitle from creating
 * a different event.
 */
public final class ApiFootballSafeMatcher {

    private static final Pattern MATCHUP_SEPARATOR = Pattern.compile(
            "\\s+(?:[-–—]|vs\\.?|v\\.?)\\s+",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern LEFT_PREFIX = Pattern.compile(
            "^(?:typy|mecz|spotkanie|analiza|zapowiedz)\\s*[:\\-–—]\\s*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern RIGHT_SUFFIX = Pattern.compile(
            "\\s*(?:[,;|:]|\\btypy\\b|\\bkursy\\b|\\bzapowiedz\\b|"
                    + "\\banaliza\\b|\\btransmisja\\b).*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern DIACRITIC_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");
    private static final Pattern YOUTH = Pattern.compile(".*\\bu(?:17|18|19|20|21|22|23)\\b.*");
    private static final Pattern RESERVE = Pattern.compile(".*\\b(?:ii|iii|reserves?|reserve)\\b.*");

    private static final Set<String> GENERIC_TEAM_TOKENS = Set.of(
            "fc", "cf", "fk", "afc", "sc", "ks", "mks", "rks", "lks",
            "ssa", "sv", "cp", "ac", "as", "club"
    );

    /*
     * A one-token subset with one of these values is too weak to identify a
     * longer team name. Examples: Polonia Bydgoszcz != Polonia Sroda,
     * Dinamo Zagreb != Dinamo Minsk.
     */
    private static final Set<String> WEAK_SINGLE_TOKENS = Set.of(
            "polonia", "dinamo", "dynamo", "real", "racing", "union"
    );

    private static final Set<String> HEADING_NOISE = Set.of(
            "typy", "kursy", "zapowiedz", "analiza", "transmisja"
    );

    private static final Map<String, List<String>> TEAM_ALIASES = createTeamAliases();

    private final ApiFootballMatcher delegate;

    public ApiFootballSafeMatcher(ApiFootballMatcher delegate) {
        this.delegate = delegate;
    }

    public ApiFootballMatch match(
            ApiFootballResolutionCandidate candidate,
            List<ApiFootballFixture> fixtures
    ) {
        if (candidate == null || fixtures == null || fixtures.isEmpty()) {
            return null;
        }

        HeadingMatchup heading = parseHeadingMatchup(candidate.heading());
        if (heading == null) {
            return delegate.match(candidate, fixtures);
        }

        List<ApiFootballFixture> compatible = new ArrayList<>();
        for (ApiFootballFixture fixture : fixtures) {
            if (fixtureMatchesHeading(fixture, heading)) {
                compatible.add(fixture);
            }
        }

        /* Structural heading is a veto: no fallback to the bag of words. */
        if (compatible.isEmpty()) {
            return null;
        }

        ApiFootballMatch delegated = delegate.match(candidate, List.copyOf(compatible));
        if (delegated != null) {
            return delegated;
        }

        /*
         * A unique structural pair is enough even when the legacy scorer is too
         * conservative for a short club name, e.g. Legia -> Legia Warszawa or
         * Betis -> Real Betis. With 2+ compatible fixtures we do not guess.
         */
        if (compatible.size() != 1) {
            return null;
        }

        ApiFootballFixture fixture = compatible.getFirst();
        String evidence = "mode=STRUCTURAL_HEADING"
                + "; score=1.200"
                + "; gap=1.200"
                + "; requiredGap=STRUCTURAL"
                + "; headingMatchup=true"
                + "; fixtureId=" + fixture.fixtureId()
                + "; fixture=" + fixture.homeTeamName() + " vs " + fixture.awayTeamName()
                + "; date=" + fixture.fixtureDate();

        return new ApiFootballMatch(
                fixture,
                1.20,
                ResolutionConfidence.HIGH,
                evidence
        );
    }

    private static HeadingMatchup parseHeadingMatchup(String rawHeading) {
        if (rawHeading == null || rawHeading.isBlank()) {
            return null;
        }

        Matcher separator = MATCHUP_SEPARATOR.matcher(rawHeading);
        if (!separator.find()) {
            return null;
        }

        String left = rawHeading.substring(0, separator.start()).trim();
        String right = rawHeading.substring(separator.end()).trim();

        left = LEFT_PREFIX.matcher(left).replaceFirst("").trim();
        right = RIGHT_SUFFIX.matcher(right).replaceFirst("").trim();

        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);

        if (!looksLikeTeamSide(normalizedLeft) || !looksLikeTeamSide(normalizedRight)) {
            return null;
        }

        return new HeadingMatchup(normalizedLeft, normalizedRight);
    }

    private static boolean looksLikeTeamSide(String side) {
        List<String> values = tokens(side);
        if (values.isEmpty() || values.size() > 7) {
            return false;
        }
        for (String token : values) {
            if (HEADING_NOISE.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private static boolean fixtureMatchesHeading(
            ApiFootballFixture fixture,
            HeadingMatchup heading
    ) {
        String home = normalize(fixture.homeTeamName());
        String away = normalize(fixture.awayTeamName());

        boolean direct = sideMatchesTeam(heading.left(), home)
                && sideMatchesTeam(heading.right(), away)
                && variantsCompatible(heading.left(), home)
                && variantsCompatible(heading.right(), away);

        if (direct) {
            return true;
        }

        return sideMatchesTeam(heading.left(), away)
                && sideMatchesTeam(heading.right(), home)
                && variantsCompatible(heading.left(), away)
                && variantsCompatible(heading.right(), home);
    }

    private static boolean sideMatchesTeam(String side, String team) {
        if (side == null || side.isBlank() || team == null || team.isBlank()) {
            return false;
        }

        List<String> variants = new ArrayList<>();
        variants.add(team);
        variants.addAll(TEAM_ALIASES.getOrDefault(team, List.of()));

        for (String rawVariant : variants) {
            String variant = normalize(rawVariant);
            if (variant.isBlank()) {
                continue;
            }

            if (side.equals(variant)) {
                return true;
            }

            if (tokenSubsetMatch(tokens(side), tokens(variant))) {
                return true;
            }
        }

        return false;
    }

    /**
     * The shorter meaningful name may be a shorthand of the longer one, but
     * every token from the shorter side has to map to a distinct token of the
     * longer side. Equal-size names therefore need all tokens to agree.
     */
    private static boolean tokenSubsetMatch(
            List<String> left,
            List<String> right
    ) {
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }

        List<String> shorter = left.size() <= right.size() ? left : right;
        List<String> longer = left.size() <= right.size() ? right : left;

        if (shorter.size() == 1
                && longer.size() > 1
                && WEAK_SINGLE_TOKENS.contains(shorter.getFirst())) {
            return false;
        }

        Set<Integer> used = new HashSet<>();
        for (String shortToken : shorter) {
            boolean found = false;
            for (int i = 0; i < longer.size(); i++) {
                if (used.contains(i)) {
                    continue;
                }
                if (tokenEquivalent(longer.get(i), shortToken)) {
                    used.add(i);
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }

        return true;
    }

    private static boolean variantsCompatible(String headingSide, String apiTeam) {
        if (isWomenTeam(apiTeam)
                && !containsAny(
                headingSide,
                "women", "ladies", "kobiet", "kobieca", "kobiece",
                "zenska", "zenskie", "femen"
        )) {
            return false;
        }

        if (YOUTH.matcher(apiTeam).matches() && !YOUTH.matcher(headingSide).matches()) {
            return false;
        }

        if (RESERVE.matcher(apiTeam).matches() && !RESERVE.matcher(headingSide).matches()) {
            return false;
        }

        return !apiTeam.endsWith(" b") || headingSide.endsWith(" b");
    }

    private static boolean isWomenTeam(String team) {
        return team.endsWith(" w")
                || containsAny(team, "women", "ladies", "femenino", "femenina");
    }

    private static boolean tokenEquivalent(String teamToken, String sideToken) {
        if (teamToken == null || sideToken == null) {
            return false;
        }
        if (teamToken.equals(sideToken)) {
            return true;
        }
        if (teamToken.length() < 4 || sideToken.length() < 4) {
            return false;
        }
        if (sideToken.startsWith(teamToken)
                && sideToken.length() <= teamToken.length() + 7) {
            return true;
        }
        if (teamToken.startsWith(sideToken)
                && teamToken.length() <= sideToken.length() + 7) {
            return true;
        }
        if (teamToken.endsWith("a") && teamToken.length() >= 5) {
            String stem = teamToken.substring(0, teamToken.length() - 1);
            return sideToken.startsWith(stem)
                    && sideToken.length() <= teamToken.length() + 6;
        }
        return false;
    }

    private static List<String> tokens(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            if (token.isBlank()
                    || GENERIC_TEAM_TOKENS.contains(token)
                    || token.chars().allMatch(Character::isDigit)) {
                continue;
            }
            result.add(token);
        }
        return List.copyOf(result);
    }

    private static boolean containsAny(String text, String... values) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalizedText = normalize(text);
        for (String value : values) {
            String normalizedValue = normalize(value);
            if (!normalizedValue.isBlank() && normalizedText.contains(normalizedValue)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }

        String transliterated = value.replace('ł', 'l').replace('Ł', 'L');
        String decomposed = Normalizer.normalize(transliterated, Normalizer.Form.NFD);
        String withoutMarks = DIACRITIC_MARKS.matcher(decomposed).replaceAll("");
        String lower = withoutMarks.toLowerCase(Locale.ROOT);
        String cleaned = NON_ALPHANUMERIC.matcher(lower).replaceAll(" ");
        return MULTIPLE_SPACES.matcher(cleaned).replaceAll(" ").trim();
    }

    private static Map<String, List<String>> createTeamAliases() {
        Map<String, List<String>> result = new LinkedHashMap<>();

        alias(result, "Poland", "Polska");
        alias(result, "Germany", "Niemcy");
        alias(result, "Spain", "Hiszpania");
        alias(result, "Italy", "Wlochy");
        alias(result, "France", "Francja");
        alias(result, "England", "Anglia");
        alias(result, "Netherlands", "Holandia");
        alias(result, "Switzerland", "Szwajcaria");
        alias(result, "Sweden", "Szwecja");
        alias(result, "Denmark", "Dania");
        alias(result, "Norway", "Norwegia");
        alias(result, "Finland", "Finlandia");
        alias(result, "Belgium", "Belgia");
        alias(result, "Portugal", "Portugalia");
        alias(result, "Luxembourg", "Luksemburg");
        alias(result, "Czech Republic", "Czechy");
        alias(result, "Czechia", "Czechy");
        alias(result, "Slovakia", "Slowacja");
        alias(result, "Slovenia", "Slowenia");
        alias(result, "Croatia", "Chorwacja");
        alias(result, "Hungary", "Wegry");
        alias(result, "Romania", "Rumunia");
        alias(result, "Bulgaria", "Bulgaria");
        alias(result, "Greece", "Grecja");
        alias(result, "Turkey", "Turcja");
        alias(result, "Ukraine", "Ukraina");
        alias(result, "Scotland", "Szkocja");
        alias(result, "Wales", "Walia");
        alias(result, "Northern Ireland", "Irlandia Polnocna");
        alias(result, "Iceland", "Islandia");
        alias(result, "Israel", "Izrael");
        alias(result, "Georgia", "Gruzja");
        alias(result, "North Macedonia", "Macedonia Polnocna");
        alias(result, "Bosnia & Herzegovina", "Bosnia i Hercegowina");
        alias(result, "Bosnia and Herzegovina", "Bosnia i Hercegowina");
        alias(result, "Montenegro", "Czarnogora");
        alias(result, "Belarus", "Bialorus");
        alias(result, "Lithuania", "Litwa");
        alias(result, "Latvia", "Lotwa");
        alias(result, "Moldova", "Moldawia");

        alias(result, "Colombia", "Kolumbia");
        alias(result, "Brazil", "Brazylia");
        alias(result, "Argentina", "Argentyna");
        alias(result, "Uruguay", "Urugwaj");
        alias(result, "Paraguay", "Paragwaj");
        alias(result, "Ecuador", "Ekwador");
        alias(result, "Bolivia", "Boliwia");
        alias(result, "Venezuela", "Wenezuela");
        alias(result, "Mexico", "Meksyk");
        alias(result, "Canada", "Kanada");
        alias(result, "USA", "Stany Zjednoczone", "USA");
        alias(result, "United States", "Stany Zjednoczone", "USA");

        alias(result, "Japan", "Japonia");
        alias(result, "China", "Chiny");
        alias(result, "South Korea", "Korea Poludniowa");
        alias(result, "North Korea", "Korea Polnocna");
        alias(result, "New Zealand", "Nowa Zelandia");
        alias(result, "Saudi Arabia", "Arabia Saudyjska");
        alias(result, "Qatar", "Katar");
        alias(result, "United Arab Emirates", "Zjednoczone Emiraty Arabskie", "ZEA");

        alias(result, "Morocco", "Maroko");
        alias(result, "Egypt", "Egipt");
        alias(result, "Algeria", "Algieria");
        alias(result, "Tunisia", "Tunezja");
        alias(result, "Cameroon", "Kamerun");
        alias(result, "Ivory Coast", "Wybrzeze Kosci Sloniowej");
        alias(result, "Côte d'Ivoire", "Wybrzeze Kosci Sloniowej");
        alias(result, "South Africa", "RPA", "Republika Poludniowej Afryki");
        alias(result, "Congo DR", "DR Konga", "Demokratyczna Republika Konga");

        alias(result, "Real Betis", "Betis");
        alias(result, "Legia Warszawa", "Legia");
        alias(result, "Bayern München", "Bayern Monachium", "Bayern");
        alias(result, "Atletico Madrid", "Atletico Madryt");
        alias(result, "Real Madrid", "Real Madryt");
        alias(result, "Paris Saint Germain", "PSG");
        alias(result, "Athletic Club", "Athletic Bilbao", "Bilbao");
        alias(result, "Borussia Dortmund", "BVB");
        alias(result, "Sporting CP", "Sporting");
        alias(result, "Lyon", "Olympique Lyon");
        alias(result, "Olympique Lyonnais", "Lyon", "Olympique Lyon");

        return Map.copyOf(result);
    }

    private static void alias(
            Map<String, List<String>> result,
            String apiName,
            String... aliases
    ) {
        result.put(normalize(apiName), List.of(aliases));
    }

    private record HeadingMatchup(String left, String right) {
    }
}