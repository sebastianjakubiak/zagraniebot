package pl.zagranietyper.service;

import pl.zagranietyper.repository.FootballSettlementRepository.Candidate;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class FootballManualReviewClassifierTest {
    private final FootballManualReviewClassifier c=new FootballManualReviewClassifier();
    private static Candidate x(String t){return new Candidate(1,2,3,"SINGLE",t,4,LocalDate.now(),"FT","Home","Away",1,0,0,0);}
    @Test void classifiesStableManualFamilies(){assertEquals("PLAYER_PROP",c.classify(x("Kane powyżej 1,5 strzałów")).family());assertEquals("GENERIC_CARDS",c.classify(x("Powyżej 3,5 kartek")).family());assertEquals("COMPOSITE",c.classify(x("Home wygra i powyżej 2,5 gola")).family());}
}
