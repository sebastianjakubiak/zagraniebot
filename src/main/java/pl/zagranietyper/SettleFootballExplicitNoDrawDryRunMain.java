package pl.zagranietyper;

import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.model.SettlementDecision;
import pl.zagranietyper.parser.FootballNoDrawParser;
import pl.zagranietyper.repository.Database;
import pl.zagranietyper.repository.FootballSettlementRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Audited settlement for explicit two-team winner alternatives (no draw). */
public final class SettleFootballExplicitNoDrawDryRunMain {

    private static final int EXPECTED_NO_DRAW_LIKE = 3;
    private static final int EXPECTED_PARSED = 3;
    private static final int EXPECTED_WINS = 2;
    private static final int EXPECTED_LOSSES = 1;
    private static final int EXPECTED_REJECTED = 0;

    private static final Map<Long, SettlementDecision> EXPECTED_DECISIONS =
            Map.of(
                    7603L, SettlementDecision.L,
                    17150L, SettlementDecision.W,
                    18829L, SettlementDecision.W
            );

    private SettleFootballExplicitNoDrawDryRunMain() {
    }

    public static void main(String[] args) {
        boolean apply = parseApplyFlag(args);

        FootballSettlementRepository repository =
                new FootballSettlementRepository(
                        new Database(AppConfig.fromEnvironment())
                );
        FootballNoDrawParser parser = new FootballNoDrawParser();

        List<String> parsed = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        Map<Long, SettlementDecision> decisions = new LinkedHashMap<>();
        List<FootballSettlementRepository.SettlementUpdate> updates =
                new ArrayList<>();
        int noDrawLike = 0;
        int wins = 0;
        int losses = 0;
        int skippedFixture = 0;
        int missingFulltime = 0;

        for (FootballSettlementRepository.Candidate candidate :
                repository.findPendingApiFootballCandidates()) {
            if (!parser.looksExplicitTwoTeamWinnerAlternativeLike(
                    candidate.tipTitle())) {
                continue;
            }

            noDrawLike++;

            if (!eligibleStatus(candidate.statusShort())) {
                skippedFixture++;
                rejected.add(describe(candidate) + " | reason=fixture status");
                continue;
            }

            FootballNoDrawParser.ParseResult result =
                    parser.parse(
                            candidate.tipTitle(),
                            candidate.homeTeam(),
                            candidate.awayTeam()
                    );

            if (!result.parsed()) {
                rejected.add(
                        describe(candidate)
                                + " | reason=" + result.status()
                );
                continue;
            }

            if (candidate.fulltimeHome() == null
                    || candidate.fulltimeAway() == null) {
                missingFulltime++;
                rejected.add(describe(candidate) + " | reason=missing fulltime");
                continue;
            }

            SettlementDecision decision =
                    settle(
                            candidate.fulltimeHome(),
                            candidate.fulltimeAway()
                    );

            if (decision == SettlementDecision.W) {
                wins++;
            } else {
                losses++;
            }

            parsed.add(
                    describe(candidate)
                            + " | participantA=" + result.participantA()
                            + " | participantB=" + result.participantB()
                            + " | decision=" + decision
            );

            decisions.put(candidate.legId(), decision);
            updates.add(
                    new FootballSettlementRepository.SettlementUpdate(
                            candidate.legId(),
                            candidate.betId(),
                            decision
                    )
            );
        }

        System.out.println("Zagranie Typer — EXPLICIT TWO-TEAM NO DRAW");
        System.out.println("MODE=" + (apply ? "APPLY" : "DRY_RUN"));
        if (!apply) {
            System.out.println("NO DATABASE WRITES");
        }
        System.out.println();
        System.out.println("SUMMARY");
        System.out.println("noDrawLike=" + noDrawLike);
        System.out.println("parsed=" + parsed.size());
        System.out.println("W=" + wins);
        System.out.println("L=" + losses);
        System.out.println("rejected=" + rejected.size());
        System.out.println("skippedFixture=" + skippedFixture);
        System.out.println("missingFulltime=" + missingFulltime);
        System.out.println();
        print("PARSED RECORDS", parsed);
        print("REJECTED NO-DRAW-LIKE RECORDS", rejected);

        if (!apply) {
            return;
        }

        validateApplySafety(
                new AuditSnapshot(
                        noDrawLike,
                        parsed.size(),
                        wins,
                        losses,
                        rejected.size(),
                        skippedFixture,
                        missingFulltime,
                        decisions,
                        updates
                )
        );

        FootballSettlementRepository.ApplyResult result =
                repository.apply(List.copyOf(updates));

        printApplyResult(result);
    }

    static boolean parseApplyFlag(String[] args) {
        if (args == null || args.length == 0) {
            return false;
        }

        if (args.length == 1 && "--apply".equals(args[0])) {
            return true;
        }

        throw new IllegalArgumentException(
                "Usage: SettleFootballExplicitNoDrawDryRunMain [--apply]"
        );
    }

    static void validateApplySafety(AuditSnapshot snapshot) {
        requireExpected(
                "noDrawLike",
                EXPECTED_NO_DRAW_LIKE,
                snapshot.noDrawLike()
        );
        requireExpected("parsed", EXPECTED_PARSED, snapshot.parsed());
        requireExpected("W", EXPECTED_WINS, snapshot.wins());
        requireExpected("L", EXPECTED_LOSSES, snapshot.losses());
        requireExpected("rejected", EXPECTED_REJECTED, snapshot.rejected());
        requireExpected("skippedFixture", 0, snapshot.skippedFixture());
        requireExpected("missingFulltime", 0, snapshot.missingFulltime());

        if (!snapshot.decisions().equals(EXPECTED_DECISIONS)) {
            throw new IllegalStateException(
                    "REFUSING APPLY: audited leg decisions changed"
                            + ", expected=" + EXPECTED_DECISIONS
                            + ", actual=" + snapshot.decisions()
            );
        }

        Set<Long> updateLegIds = new HashSet<>();

        for (FootballSettlementRepository.SettlementUpdate update :
                snapshot.updates()) {
            if (update.decision() != SettlementDecision.W
                    && update.decision() != SettlementDecision.L) {
                throw new IllegalStateException(
                        "REFUSING APPLY: non-W/L decision for leg="
                                + update.legId()
                                + ", decision=" + update.decision()
                );
            }

            if (!updateLegIds.add(update.legId())) {
                throw new IllegalStateException(
                        "REFUSING APPLY: duplicate update leg=" + update.legId()
                );
            }

            if (snapshot.decisions().get(update.legId())
                    != update.decision()) {
                throw new IllegalStateException(
                        "REFUSING APPLY: update decision mismatch for leg="
                                + update.legId()
                );
            }
        }

        if (snapshot.updates().size() != EXPECTED_PARSED
                || !updateLegIds.equals(EXPECTED_DECISIONS.keySet())) {
            throw new IllegalStateException(
                    "REFUSING APPLY: update leg set changed"
                            + ", expected=" + EXPECTED_DECISIONS.keySet()
                            + ", actual=" + updateLegIds
            );
        }
    }

    private static void requireExpected(
            String name,
            int expected,
            int actual
    ) {
        if (actual != expected) {
            throw new IllegalStateException(
                    "REFUSING APPLY: expected " + name + "=" + expected
                            + ", actual=" + actual
            );
        }
    }

    private static void printApplyResult(
            FootballSettlementRepository.ApplyResult result
    ) {
        System.out.println("APPLY RESULT");
        System.out.println("updatedLegs=" + result.updatedLegs());
        System.out.println("skippedLegs=" + result.skippedLegs());
        System.out.println("W=" + result.winLegs());
        System.out.println("L=" + result.lossLegs());
        System.out.println("V=" + result.voidLegs());
        System.out.println("updatedBets=" + result.updatedBets());
        System.out.println("pendingBets=" + result.pendingBets());
        System.out.println(
                "multiUnverifiedBets=" + result.multiUnverifiedBets()
        );
    }

    static SettlementDecision settle(int homeGoals, int awayGoals) {
        return homeGoals == awayGoals
                ? SettlementDecision.L
                : SettlementDecision.W;
    }

    record AuditSnapshot(
            int noDrawLike,
            int parsed,
            int wins,
            int losses,
            int rejected,
            int skippedFixture,
            int missingFulltime,
            Map<Long, SettlementDecision> decisions,
            List<FootballSettlementRepository.SettlementUpdate> updates
    ) {
        AuditSnapshot {
            decisions = Map.copyOf(decisions);
            updates = List.copyOf(updates);
        }
    }

    private static boolean eligibleStatus(String status) {
        return "FT".equals(status)
                || "AET".equals(status)
                || "PEN".equals(status);
    }

    private static void print(String title, List<String> rows) {
        System.out.println(title + " (" + rows.size() + ")");
        for (int index = 0; index < rows.size(); index++) {
            System.out.println("[" + (index + 1) + "] " + rows.get(index));
        }
        System.out.println();
    }

    private static String describe(
            FootballSettlementRepository.Candidate candidate
    ) {
        String score =
                candidate.fulltimeHome() == null
                        || candidate.fulltimeAway() == null
                        ? "?-?"
                        : candidate.fulltimeHome()
                        + "-" + candidate.fulltimeAway();

        return "leg=" + candidate.legId()
                + " | bet=" + candidate.betId()
                + " | wp=" + candidate.wpPostId()
                + " | fixture=" + candidate.fixtureId()
                + " | status=" + candidate.statusShort()
                + " | " + candidate.homeTeam() + " " + score
                + " " + candidate.awayTeam()
                + " | tip=" + candidate.tipTitle();
    }
}
