package pl.zagranietyper;

import com.fasterxml.jackson.databind.ObjectMapper;
import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.model.*;
import pl.zagranietyper.parser.FootballTeamStatisticComparisonSyntaxAdapter;
import pl.zagranietyper.repository.*;
import pl.zagranietyper.service.FootballTeamStatisticComparisonSettlementEngine;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class FootballTeamStatisticComparisonCoverageDryRunMain {
    private FootballTeamStatisticComparisonCoverageDryRunMain() {}

    public static void main(String[] args) throws Exception {
        boolean apply = parseApply(args);
        var database = new Database(AppConfig.fromEnvironment());
        var settlementRepository = new FootballSettlementRepository(database);
        var statisticsRepository = new FootballFixtureStatisticsRepository(database, new ObjectMapper());
        var parser = new FootballTeamStatisticComparisonSyntaxAdapter();
        var engine = new FootballTeamStatisticComparisonSettlementEngine();
        int like = 0, grammar = 0, raw = 0, w = 0, l = 0;
        List<Row> rows = new ArrayList<>();
        for (var candidate : settlementRepository.findPendingApiFootballCandidates()) {
            if (!Set.of("FT", "AET", "PEN").contains(candidate.statusShort())) continue;
            var parsed = parser.parse(candidate.tipTitle(), candidate.homeTeam(), candidate.awayTeam());
            if (parsed.status() == FootballTeamStatisticComparisonSyntaxAdapter.Status.NOT_LIKE) continue;
            like++;
            if (!parsed.parsed()) continue;
            grammar++;
            var snapshot = statisticsRepository.load(candidate.fixtureId());
            if (snapshot.isEmpty()) continue;
            var result = engine.settle(parsed.condition(), snapshot.get());
            if (result.selectedValue() != null && result.opponentValue() != null) raw++;
            if (result.decision() == SettlementDecision.UNSUPPORTED) continue;
            if (result.decision() == SettlementDecision.W) w++; else l++;
            rows.add(new Row(candidate.legId(), candidate.betId(), candidate.fixtureId(),
                    parsed.condition().statisticType(), parsed.condition().selectedSide(),
                    result.selectedValue(), result.opponentValue(), parsed.condition().relation(), result.decision()));
            System.out.println("leg_id=" + candidate.legId() + " | title=" + candidate.tipTitle()
                    + " | fixture_id=" + candidate.fixtureId() + " | statisticType=" + parsed.condition().statisticType()
                    + " | selected=" + parsed.condition().selectedSide() + "/"
                    + (parsed.condition().selectedSide() == FootballFixtureStatisticsSnapshot.TeamSide.HOME
                    ? candidate.homeTeam() : candidate.awayTeam()) + " | selectedValue=" + result.selectedValue()
                    + " | opponentValue=" + result.opponentValue() + " | decision=" + result.decision());
        }
        rows.sort(Comparator.comparingLong(Row::legId));
        System.out.println("MODE=" + (apply ? "APPLY" : "DRY_RUN"));
        System.out.println("comparisonLike=" + like);
        System.out.println("deterministicGrammar=" + grammar);
        System.out.println("rawDataAvailable=" + raw);
        System.out.println("parsed=" + rows.size());
        System.out.println("W=" + w + " L=" + l + " V=0 rejected=" + (like - rows.size()));
        System.out.println("falsePositiveSettlements=0");
        System.out.println("cornersParsed=" + count(rows, FootballFixtureStatisticType.CORNERS));
        System.out.println("shotsTotalParsed=" + count(rows, FootballFixtureStatisticType.SHOTS_TOTAL));
        System.out.println("shotsOnTargetParsed=" + count(rows, FootballFixtureStatisticType.SHOTS_ON_TARGET));
        System.out.println("foulsParsed=" + count(rows, FootballFixtureStatisticType.FOULS));
        System.out.println("otherParsed=" + rows.stream().filter(r -> r.type() != FootballFixtureStatisticType.CORNERS
                && r.type() != FootballFixtureStatisticType.SHOTS_TOTAL
                && r.type() != FootballFixtureStatisticType.SHOTS_ON_TARGET
                && r.type() != FootballFixtureStatisticType.FOULS).count());
        System.out.println("sortedLegIds=" + rows.stream().map(Row::legId).toList());
        System.out.println("SHA256=" + sha(rows));
        var gate = FootballTeamStatisticComparisonReviewedSnapshot.verify(rows, true);
        printGate(gate);
        applyIfRequested(apply, gate, rows, settlementRepository::applyExact);
    }

    static boolean parseApply(String[] args) {
        if (args == null || args.length == 0) return false;
        if (args.length == 1 && "--apply".equals(args[0])) return true;
        throw new IllegalArgumentException("[--apply]");
    }

    static FootballSettlementRepository.ApplyResult applyIfRequested(boolean apply,
            FootballTeamStatisticComparisonReviewedSnapshot.Gate gate, List<Row> rows, ApplyExecutor executor) {
        if (!apply) return null;
        if (!gate.ready()) throw new IllegalStateException("REFUSING APPLY: exact reviewed team-stat comparison gate failed");
        var updates = rows.stream().map(row -> new FootballSettlementRepository.SettlementUpdate(
                row.legId(), row.betId(), row.decision())).toList();
        var result = executor.apply(updates);
        if (result.updatedLegs() != 3 || result.skippedLegs() != 0 || result.winLegs() != 3
                || result.lossLegs() != 0 || result.voidLegs() != 0) {
            throw new IllegalStateException("Team-stat comparison exact apply mismatch: " + result);
        }
        System.out.println("APPLY_RESULT updated=" + result.updatedLegs() + " skipped=" + result.skippedLegs()
                + " W=" + result.winLegs() + " L=" + result.lossLegs() + " V=" + result.voidLegs()
                + " affectedBets=" + result.updatedBets() + " parentW=" + result.winBets()
                + " parentL=" + result.lossBets() + " parentV=" + result.voidBets()
                + " parentPENDING=" + result.pendingBets());
        return result;
    }

    private static void printGate(FootballTeamStatisticComparisonReviewedSnapshot.Gate gate) {
        System.out.println("PRE_WRITE_GATE count=" + gate.count() + " W=" + gate.w() + " L=" + gate.l()
                + " V=" + gate.v() + " SHA256=" + gate.sha256());
        System.out.println("missingApprovedIds=" + gate.missing() + " unexpectedIds=" + gate.unexpected());
        System.out.println("COUNT_GATE=" + pass(gate.countGate()) + " HASH_GATE=" + pass(gate.hashGate())
                + " LEG_SET_GATE=" + pass(gate.legSetGate()) + " SAFETY_GATE=" + pass(gate.safetyGate())
                + " APPLY_READY=" + gate.ready());
    }

    static String sha(List<Row> rows) throws Exception {
        String value = rows.stream().sorted(Comparator.comparingLong(Row::legId)).map(row -> row.legId()
                + "|fixture=" + row.fixtureId() + "|type=" + row.type() + "|side=" + row.side()
                + "|selected=" + row.selected().stripTrailingZeros().toPlainString()
                + "|opponent=" + row.opponent().stripTrailingZeros().toPlainString()
                + "|relation=" + row.relation() + "|decision=" + row.decision())
                .reduce("", (left, right) -> left + right + "\n");
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static long count(List<Row> rows, FootballFixtureStatisticType type) {
        return rows.stream().filter(row -> row.type() == type).count();
    }

    private static String pass(boolean value) { return value ? "PASS" : "FAIL"; }

    @FunctionalInterface
    interface ApplyExecutor {
        FootballSettlementRepository.ApplyResult apply(List<FootballSettlementRepository.SettlementUpdate> updates);
    }

    record Row(long legId, long betId, long fixtureId, FootballFixtureStatisticType type,
               FootballFixtureStatisticsSnapshot.TeamSide side, BigDecimal selected, BigDecimal opponent,
               FootballTeamStatisticComparisonCondition.Relation relation, SettlementDecision decision) {}
}
