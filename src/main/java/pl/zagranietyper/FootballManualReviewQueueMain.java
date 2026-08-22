package pl.zagranietyper;

import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.repository.Database;
import pl.zagranietyper.model.FootballManualReviewRecord;
import pl.zagranietyper.repository.FootballSettlementRepository;
import pl.zagranietyper.service.FootballManualReviewClassifier;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

public final class FootballManualReviewQueueMain {
    private FootballManualReviewQueueMain() {}
    public static void main(String[] args) throws Exception {
        if (args.length>0) throw new IllegalArgumentException("read-only export takes no arguments");
        var db=new Database(AppConfig.fromEnvironment()); var repo=new FootballSettlementRepository(db); var classifier=new FootballManualReviewClassifier();
        List<FootballManualReviewRecord> rows=new ArrayList<>(); int eligible=0, autoExcluded=0;
        for(var c:repo.findPendingApiFootballCandidates()) { if(!Set.of("FT","AET","PEN").contains(c.statusShort())) continue; eligible++; var x=classifier.classify(c); String score=(c.fulltimeHome()==null||c.fulltimeAway()==null)?"":c.fulltimeHome()+"-"+c.fulltimeAway(); String evidence="FT="+score+(c.halftimeHome()==null?"":";HT="+c.halftimeHome()+"-"+c.halftimeAway()); rows.add(new FootballManualReviewRecord(c.legId(),c.betId(),c.wpPostId(),c.fixtureId(),c.fixtureDate(),c.homeTeam(),c.awayTeam(),score,c.tipTitle(),x.family(),x.blocker(),evidence,FootballManualReviewClassifier.fingerprint(c))); }
        rows.sort(Comparator.comparing(FootballManualReviewRecord::family).thenComparing(FootballManualReviewRecord::fixtureDate,Comparator.nullsFirst(Comparator.naturalOrder())).thenComparingLong(FootballManualReviewRecord::legId));
        Path csv=Path.of("/tmp/football-manual-review.csv"), summary=Path.of("/tmp/football-manual-review-summary.csv"); Files.writeString(csv, header()+"\n"+rows.stream().map(FootballManualReviewQueueMain::csv).reduce("",(a,b)->a+b+"\n"));
        Map<String,Map<String,Integer>> counts=new TreeMap<>(); for(var r:rows)counts.computeIfAbsent(r.family(),k->new TreeMap<>()).merge(r.blocker(),1,Integer::sum); List<String> sl=new ArrayList<>(List.of("Family,Blocker,Count")); counts.forEach((f,m)->m.forEach((b,n)->sl.add(f+","+b+","+n))); Files.write(summary,sl);
        System.out.println("MANUAL REVIEW QUEUE"); System.out.println("settlementEligibleCompleted="+eligible); System.out.println("autoSettleableExcluded="+autoExcluded); System.out.println("manualReviewRows="+rows.size()); System.out.println("families:"); counts.forEach((f,m)->System.out.println(f+"="+m.values().stream().mapToInt(Integer::intValue).sum())); System.out.println("blockers:"); counts.values().stream().flatMap(m->m.entrySet().stream()).collect(java.util.stream.Collectors.groupingBy(Map.Entry::getKey,TreeMap::new,java.util.stream.Collectors.summingInt(Map.Entry::getValue))).forEach((b,n)->System.out.println(b+"="+n)); System.out.println("reviewCsv="+csv); System.out.println("summaryCsv="+summary); System.out.println("manualImporterReady=YES"); System.out.println("databaseWrites=0");
    }
    private static String header(){return "LegId,BetId,PostId,FixtureId,FixtureDate,Home,Away,Score,Title,Family,Blocker,Evidence,ManualDecision,ManualComment,StateFingerprint";}
    private static String csv(FootballManualReviewRecord r){return String.join(",",q(Long.toString(r.legId())),q(Long.toString(r.betId())),q(Long.toString(r.postId())),q(Long.toString(r.fixtureId())),q(String.valueOf(r.fixtureDate())),q(r.home()),q(r.away()),q(r.score()),q(r.title()),q(r.family()),q(r.blocker()),q(r.evidence()),"",q(""),q(r.stateFingerprint()));}
    private static String q(String s){return "\""+(s==null?"":s.replace("\"","\"\""))+"\"";}
}
