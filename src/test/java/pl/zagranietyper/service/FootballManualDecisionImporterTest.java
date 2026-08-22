package pl.zagranietyper.service;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FootballManualDecisionImporterTest {
    private final FootballManualDecisionImporter importer=new FootballManualDecisionImporter();
    @Test void parsesAllowedDecisionsAndEscapesQuotes(){var r=importer.parse(List.of("LegId,Title,ManualDecision,ManualComment","7,\"A, \"\"quoted\"\" title\",W,ok","8,title,SKIP,"));assertEquals(List.of("W","SKIP"),r.stream().map(FootballManualDecisionImporter.Decision::decision).toList());}
    @Test void rejectsDuplicateAndInvalidAndNonPending(){assertThrows(IllegalArgumentException.class,()->importer.parse(List.of("h","7,x,W,","7,y,L,")));assertThrows(IllegalArgumentException.class,()->importer.parse(List.of("h","7,x,X,")));assertThrows(IllegalArgumentException.class,()->importer.validateCurrent(List.of(new FootballManualDecisionImporter.Decision(7,"W","")),Set.of(),Map.of()));}
}
