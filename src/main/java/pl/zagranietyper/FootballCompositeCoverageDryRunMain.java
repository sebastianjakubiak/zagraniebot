package pl.zagranietyper;

import com.fasterxml.jackson.databind.ObjectMapper;
import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.model.*;
import pl.zagranietyper.parser.FootballCompositeSyntaxAdapter;
import pl.zagranietyper.repository.*;
import pl.zagranietyper.service.FootballCompositeSettlementEngine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Global read-only coverage for conjunctions of already-supported football primitives. */
public final class FootballCompositeCoverageDryRunMain {
    private FootballCompositeCoverageDryRunMain() {}

    public static void main(String[] args) throws Exception {
        boolean apply=parseApply(args);
        var database = new Database(AppConfig.fromEnvironment());
        var repository = new FootballSettlementRepository(database);
        var statisticsRepository = new FootballFixtureStatisticsRepository(database, new ObjectMapper());
        var parser = new FootballCompositeSyntaxAdapter();
        var engine = new FootballCompositeSettlementEngine();
        EnumMap<Reason,Integer> rejected = new EnumMap<>(Reason.class);
        EnumMap<SettlementDecision,Integer> decisions = new EnumMap<>(SettlementDecision.class);
        Map<String,Integer> combinations = new TreeMap<>();
        Map<String,List<String>> examples = new TreeMap<>();
        List<SnapshotRow> snapshot = new ArrayList<>();
        int totalEligible=0, compositeLike=0, allSupported=0;

        for (var candidate : repository.findPendingApiFootballCandidates()) {
            if (!Set.of("FT","AET","PEN").contains(candidate.statusShort())) continue;
            totalEligible++;
            if (!parser.looksLikeComposite(candidate.tipTitle())) continue;
            compositeLike++;
            var parsed = parser.parse(candidate.tipTitle(), candidate.homeTeam(), candidate.awayTeam());
            if (!parsed.parsed()) { rejected.merge(reason(parsed.status()),1,Integer::sum); continue; }
            allSupported++;
            if (candidate.fulltimeHome()==null || candidate.fulltimeAway()==null) {
                rejected.merge(Reason.MISSING_RAW_DATA,1,Integer::sum); continue;
            }
            FootballFixtureStatisticsSnapshot stats = null;
            boolean needsStats = parsed.condition().branches().stream()
                    .anyMatch(FootballCompositeCondition.StatisticBranch.class::isInstance);
            if (needsStats) stats = statisticsRepository.load(candidate.fixtureId()).orElse(null);
            var result = engine.settle(parsed.condition(),
                    FootballScoreSnapshot.fullTime(new FootballScore(candidate.fulltimeHome(),candidate.fulltimeAway())), stats);
            if (result.decision()==SettlementDecision.UNSUPPORTED) {
                rejected.merge(Reason.MISSING_RAW_DATA,1,Integer::sum); continue;
            }
            decisions.merge(result.decision(),1,Integer::sum);
            String combination=combination(parsed.condition()); combinations.merge(combination,1,Integer::sum);
            examples.computeIfAbsent(combination,k->new ArrayList<>());
            if(examples.get(combination).size()<2)examples.get(combination).add(candidate.tipTitle());
            SnapshotRow row=new SnapshotRow(candidate.legId(),candidate.betId(),parsed.normalizedBranches(),parsed.condition(),result.branchDecisions(),result.decision());
            snapshot.add(row);
            System.out.println("PARSED leg_id="+candidate.legId()+" title="+candidate.tipTitle()+" branches="+parsed.normalizedBranches()+" branchDecisions="+result.branchDecisions()+" decision="+result.decision());
        }
        int parsedCount=decisions.values().stream().mapToInt(Integer::intValue).sum();
        int rejectedCount=rejected.values().stream().mapToInt(Integer::intValue).sum();
        System.out.println("AUDIT COMBINATIONS");
        combinations.forEach((key,value)->System.out.println(key+"="+value+" examples="+examples.get(key)));
        System.out.println("totalEligible="+totalEligible);System.out.println("compositeLike="+compositeLike);
        System.out.println("allBranchesSupported="+allSupported);System.out.println("parsed="+parsedCount);
        System.out.println("W="+decisions.getOrDefault(SettlementDecision.W,0));System.out.println("L="+decisions.getOrDefault(SettlementDecision.L,0));System.out.println("V="+decisions.getOrDefault(SettlementDecision.V,0));
        System.out.println("rejected="+rejectedCount);for(var reason:Reason.values())System.out.println(reason.label+"="+rejected.getOrDefault(reason,0));
        if(compositeLike!=parsedCount+rejectedCount)throw new IllegalStateException("Counts do not reconcile");
        List<SnapshotRow> sorted=snapshot.stream().sorted(Comparator.comparingLong(SnapshotRow::legId)).toList();
        System.out.println("snapshotCount="+sorted.size());System.out.println("snapshotW="+decisions.getOrDefault(SettlementDecision.W,0));System.out.println("snapshotL="+decisions.getOrDefault(SettlementDecision.L,0));System.out.println("snapshotV="+decisions.getOrDefault(SettlementDecision.V,0));
        System.out.println("sortedLegIds="+sorted.stream().map(SnapshotRow::legId).toList());System.out.println("snapshotSHA256="+sha256(sorted));
        var gate=FootballCompositeReviewedSnapshot.verify(sorted);printGate(gate);
        applyIfRequested(apply,gate,sorted,updates->repository.applyExact(updates));
    }

    static boolean parseApply(String[] args){if(args==null||args.length==0)return false;if(args.length==1&&"--apply".equals(args[0]))return true;throw new IllegalArgumentException("Usage: FootballCompositeCoverageDryRunMain [--apply]");}
    static FootballSettlementRepository.ApplyResult applyIfRequested(boolean apply,FootballCompositeReviewedSnapshot.Gate gate,List<SnapshotRow> rows,ApplyExecutor executor){
        if(!apply)return null;if(!gate.ready())throw new IllegalStateException("REFUSING APPLY: exact reviewed composite snapshot gate failed");
        var updates=rows.stream().map(r->new FootballSettlementRepository.SettlementUpdate(r.legId(),r.betId(),r.decision())).toList();
        var result=executor.apply(updates);if(result.updatedLegs()!=29||result.skippedLegs()!=0||result.winLegs()!=13||result.lossLegs()!=16||result.voidLegs()!=0)throw new IllegalStateException("Composite exact apply mismatch: "+result);
        System.out.println("APPLY_RESULT updated="+result.updatedLegs()+" skipped="+result.skippedLegs()+" W="+result.winLegs()+" L="+result.lossLegs()+" V="+result.voidLegs()+" affectedBets="+result.updatedBets());return result;
    }
    private static void printGate(FootballCompositeReviewedSnapshot.Gate g){System.out.println("PRE_WRITE_GATE count="+g.count()+" W="+g.w()+" L="+g.l()+" V="+g.v()+" SHA256="+g.sha256());System.out.println("missingApprovedIds="+new TreeSet<>(g.missing()));System.out.println("unexpectedIds="+new TreeSet<>(g.unexpected()));System.out.println("COUNT_GATE="+pass(g.countGate())+" HASH_GATE="+pass(g.hashGate())+" LEG_SET_GATE="+pass(g.legSetGate())+" SAFETY_GATE="+pass(g.safetyGate())+" APPLY_READY="+g.ready());}
    private static String pass(boolean b){return b?"PASS":"FAIL";}
    @FunctionalInterface interface ApplyExecutor{FootballSettlementRepository.ApplyResult apply(List<FootballSettlementRepository.SettlementUpdate> updates);}

    static String canonical(SnapshotRow row) { return row.legId()+"|branches="+String.join(" && ",row.normalizedBranches())+"|conditions="+row.condition().branches()+"|decision="+row.decision(); }
    static String sha256(List<SnapshotRow> rows) throws Exception { String payload=rows.stream().map(FootballCompositeCoverageDryRunMain::canonical).reduce("",(a,b)->a+b+"\n");return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))); }
    private static String combination(FootballCompositeCondition condition){return condition.branches().stream().map(b->b instanceof FootballCompositeCondition.ScoreBranch?"SCORE":((FootballCompositeCondition.StatisticBranch)b).condition().type().name()).toList().toString();}
    private static Reason reason(FootballCompositeSyntaxAdapter.Status status){return switch(status){case UNSUPPORTED_BRANCH->Reason.UNSUPPORTED_BRANCH;case AMBIGUOUS_GRAMMAR->Reason.AMBIGUOUS_GRAMMAR;case PARTICIPANT_UNRESOLVED->Reason.PARTICIPANT_UNRESOLVED;case PLAYER_BRANCH->Reason.PLAYER_BRANCH;case PERIOD_BRANCH->Reason.PERIOD_BRANCH;case CARD_SEMANTIC_UNKNOWN->Reason.CARD_SEMANTIC_UNKNOWN;case COMPARISON_OR_HANDICAP->Reason.COMPARISON_OR_HANDICAP;case PARSED,NOT_COMPOSITE->Reason.OTHER;};}
    enum Reason {UNSUPPORTED_BRANCH("unsupportedBranch"),AMBIGUOUS_GRAMMAR("ambiguousGrammar"),PARTICIPANT_UNRESOLVED("participantUnresolved"),MISSING_RAW_DATA("missingRawData"),PLAYER_BRANCH("playerBranch"),PERIOD_BRANCH("periodBranch"),CARD_SEMANTIC_UNKNOWN("cardSemanticUnknown"),COMPARISON_OR_HANDICAP("comparisonOrHandicap"),OTHER("other");final String label;Reason(String label){this.label=label;}}
    record SnapshotRow(long legId,long betId,List<String> normalizedBranches,FootballCompositeCondition condition,List<SettlementDecision> branchDecisions,SettlementDecision decision){}
}
