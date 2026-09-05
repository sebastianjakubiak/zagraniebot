package pl.zagranietyper;

import org.junit.jupiter.api.Test;
import pl.zagranietyper.repository.FootballSettlementRepository;
import pl.zagranietyper.service.FootballManualDecisionImporter;
import pl.zagranietyper.service.FootballManualReviewClassifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FootballManualSettlementImportMainTest {
    @Test void missingFixtureStatusIsAllowedForManualDecision(){assertTrue(FootballManualSettlementImportMain.fixtureStatusAllowed(null,false));}
    @Test void nonfinalKnownStatusesRequireExplicitOverride(){assertFalse(FootballManualSettlementImportMain.fixtureStatusAllowed("NS",false));assertFalse(FootballManualSettlementImportMain.fixtureStatusAllowed("PST",false));assertTrue(FootballManualSettlementImportMain.fixtureStatusAllowed("NS",true));assertTrue(FootballManualSettlementImportMain.fixtureStatusAllowed("FT",false));}

    @Test void classifiesSkipPendingUpdateAndAlreadyTargetWhileCheckingEveryFingerprint() throws Exception {
        var rows=List.of(row(1,"SKIP"),row(2,"W"),row(3,"L"));
        var candidates=List.of(candidate(1,"PENDING","NONE"),candidate(2,"PENDING","NONE"),candidate(3,"L","MANUAL"));

        var result=FootballManualSettlementImportMain.preflight(rows,candidates,false);

        assertEquals(3,result.found());
        assertEquals(3,result.matching());
        assertEquals(0,result.mismatch());
        assertEquals(0,result.errors().size());
        assertEquals(1,result.skipped());
        assertEquals(1,result.would());
        assertEquals(1,result.already());
        assertEquals(java.util.Set.of(2L),result.updateIds());
    }

    @Test void differentExistingDecisionIsAConflict() throws Exception {
        var result=FootballManualSettlementImportMain.preflight(List.of(row(4,"W")),List.of(candidate(4,"L","MANUAL")),false);
        assertEquals(1,result.matching());
        assertEquals(1,result.errors().size());
        assertTrue(result.errors().getFirst().startsWith("CONFLICT LegId=4"));
    }

    @Test void sameDecisionWithDifferentManualNoteIsNotAlreadyInTargetState() throws Exception {
        var c=candidate(5,"W","MANUAL");
        c=new FootballSettlementRepository.ManualCandidate(c.legId(),c.betId(),c.postId(),c.postAuthor(),c.title(),c.fixtureId(),c.fixtureDate(),c.statusShort(),c.homeTeam(),c.awayTeam(),c.settlementStatus(),c.settlementSource(),"different",c.sourceFingerprint());
        var result=FootballManualSettlementImportMain.preflight(List.of(row(5,"W")),List.of(c),false);
        assertEquals(0,result.already());
        assertEquals(List.of("manual note mismatch LegId=5"),result.errors());
    }

    private static FootballManualDecisionImporter.ReviewRow row(long id,String decision) throws Exception {
        return new FootballManualDecisionImporter.ReviewRow(id,100+id,200+id,52,null,"","","","","title "+id,"","","",decision,"note",FootballManualReviewClassifier.fingerprint(id,100+id,"title "+id));
    }

    private static FootballSettlementRepository.ManualCandidate candidate(long id,String status,String source){
        return new FootballSettlementRepository.ManualCandidate(id,100+id,200+id,52,"title "+id,null,null,null,null,null,status,source,"MANUAL".equals(source)?"note":null,null);
    }
}
