package pl.zagranietyper;

import pl.zagranietyper.model.FootballFixtureStatisticCondition;
import pl.zagranietyper.model.SettlementDecision;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable audit artifact for the manually reviewed CORNERS settlement batch. */
public final class FootballCornersReviewedSnapshot {
    public static final int EXPECTED_COUNT = 233;
    public static final String EXPECTED_SHA256 =
            "bc3115d866c0ffa808d773d1c97d96b29dadc3eb64348560711fef3e9a76458c";
    public static final int EXPECTED_W = 121;
    public static final int EXPECTED_L = 112;
    public static final int EXPECTED_V = 0;

    public static final Set<Long> APPROVED_LEG_IDS = Set.of(
            59L,97L,103L,182L,213L,217L,340L,371L,393L,455L,496L,513L,536L,577L,
            648L,686L,692L,792L,916L,987L,992L,1016L,1029L,1068L,1125L,1176L,
            1213L,1218L,1299L,1404L,1444L,1581L,1711L,1836L,1886L,1887L,1904L,
            2069L,2099L,2142L,2152L,2213L,2220L,2311L,2479L,2624L,2646L,2649L,
            2969L,2975L,3048L,3117L,3207L,3252L,3335L,3462L,3482L,3684L,7432L,
            7491L,7615L,7620L,7745L,7930L,8071L,8086L,8106L,8185L,8197L,8210L,
            8220L,8322L,8323L,8403L,8432L,8488L,8489L,8537L,8585L,8594L,8657L,
            8702L,8808L,8842L,8903L,8920L,8931L,8963L,8979L,9170L,9187L,9190L,
            9202L,9218L,9248L,9250L,9279L,9587L,9640L,9696L,9726L,9729L,9773L,
            9802L,9809L,9810L,9849L,9869L,9917L,10007L,10083L,10113L,10116L,
            10176L,10241L,10254L,10319L,10372L,10398L,10422L,10437L,10449L,10452L,
            10511L,10550L,10573L,10576L,10635L,10774L,10830L,10833L,10864L,10894L,
            10948L,10958L,11009L,11080L,11194L,11260L,11438L,11461L,11518L,11640L,
            11654L,11658L,11720L,11936L,12010L,12033L,13778L,14061L,14168L,14172L,
            14173L,14174L,14224L,14565L,14570L,14595L,14621L,14775L,14805L,14830L,
            14848L,14886L,14974L,14990L,14995L,15213L,15214L,15274L,15304L,15377L,
            15393L,15394L,15428L,15430L,15440L,15547L,15577L,15638L,15994L,16008L,
            16161L,16198L,16215L,16355L,16411L,16504L,16707L,16736L,16766L,16843L,
            16927L,17003L,17033L,17108L,17507L,17595L,17772L,17812L,17830L,17896L,
            17949L,17950L,17951L,18047L,18078L,18245L,18247L,18277L,18328L,18465L,
            18541L,18582L,18838L,18923L,18951L,18954L,19054L,19056L,19063L,19132L,
            19161L,19231L,19356L,19378L,19517L,19557L,19558L,19579L,19989L,20002L
    );

    private FootballCornersReviewedSnapshot() {}

    public static GateResult verify(List<Candidate> candidates) {
        return verify(candidates, new Boundary(EXPECTED_COUNT, EXPECTED_SHA256, APPROVED_LEG_IDS,
                EXPECTED_W, EXPECTED_L, EXPECTED_V));
    }

    static GateResult verify(List<Candidate> candidates, Boundary boundary) {
        List<Candidate> sorted = candidates.stream().sorted(java.util.Comparator.comparingLong(Candidate::legId)).toList();
        List<String> lines = sorted.stream().map(FootballCornersReviewedSnapshot::canonicalLine).toList();
        Set<Long> actualIds = new HashSet<>();
        boolean unique = sorted.stream().allMatch(candidate -> actualIds.add(candidate.legId()));
        Set<Long> missing = new java.util.TreeSet<>(boundary.approvedLegIds());
        missing.removeAll(actualIds);
        Set<Long> unexpected = new java.util.TreeSet<>(actualIds);
        unexpected.removeAll(boundary.approvedLegIds());
        String hash = sha256(lines);
        int wins = count(sorted, SettlementDecision.W);
        int losses = count(sorted, SettlementDecision.L);
        int voids = count(sorted, SettlementDecision.V);
        boolean countGate = sorted.size() == boundary.expectedCount();
        boolean hashGate = hash.equals(boundary.expectedSha256());
        boolean legSetGate = unique && actualIds.equals(boundary.approvedLegIds());
        boolean secondary = unique && wins == boundary.expectedW()
                && losses == boundary.expectedL() && voids == boundary.expectedV()
                && !actualIds.contains(14362L) && !actualIds.contains(15065L)
                && sorted.stream().allMatch(Candidate::secondarySafe);
        return new GateResult(sorted.size(), wins, losses, voids, hash, Set.copyOf(actualIds),
                Set.copyOf(missing), Set.copyOf(unexpected), countGate, hashGate, legSetGate, secondary);
    }

    static String hashCandidates(List<Candidate> candidates) {
        return sha256(candidates.stream().sorted(java.util.Comparator.comparingLong(Candidate::legId))
                .map(FootballCornersReviewedSnapshot::canonicalLine).toList());
    }

    public static String canonicalLine(Candidate candidate) {
        FootballFixtureStatisticCondition condition = candidate.condition();
        return candidate.legId() + "|type=" + condition.type()
                + "|subject=" + condition.subject()
                + "|comparison=" + condition.comparison()
                + "|threshold=" + plain(condition.threshold())
                + "|rangeMaximum=" + plain(condition.rangeMaximum())
                + "|decision=" + candidate.decision();
    }

    private static int count(List<Candidate> values, SettlementDecision decision) {
        return (int) values.stream().filter(value -> value.decision() == decision).count();
    }

    private static String plain(BigDecimal value) {
        return value == null ? "null" : value.stripTrailingZeros().toPlainString();
    }

    private static String sha256(List<String> lines) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = String.join("\n", lines) + "\n";
            return java.util.HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record Candidate(long legId, long betId, FootballFixtureStatisticCondition condition,
                            SettlementDecision decision, boolean secondarySafe) {}

    record Boundary(int expectedCount, String expectedSha256, Set<Long> approvedLegIds,
                    int expectedW, int expectedL, int expectedV) {
        Boundary {
            approvedLegIds = Set.copyOf(approvedLegIds);
        }
    }

    public record GateResult(int actualCount, int wins, int losses, int voids, String actualSha256,
                             Set<Long> actualLegIds, Set<Long> missingApprovedIds, Set<Long> unexpectedIds,
                             boolean countGate, boolean hashGate, boolean legSetGate,
                             boolean secondarySafetyGate) {
        public boolean applyReady() { return countGate && hashGate && legSetGate && secondarySafetyGate; }
    }
}
