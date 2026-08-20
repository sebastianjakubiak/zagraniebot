package pl.zagranietyper.fixture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.zagranietyper.config.ApiFootballConfig;
import pl.zagranietyper.model.ApiFootballFixtureStatisticsResponse;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiFootballClientStatisticsTest {
    private final ApiFootballClient client = new ApiFootballClient(
            new ApiFootballConfig("test", "https://example.invalid", 1, 3, 0), new ObjectMapper());

    @Test
    void parsesTwoTeamStatisticsWithoutCoercingValues() {
        String json = """
                {"errors":[],"results":2,"response":[
                  {"team":{"id":10,"name":"Home"},"statistics":[
                    {"type":"Corner Kicks","value":0},
                    {"type":"Fouls","value":"12"},
                    {"type":"Offsides","value":null}]},
                  {"team":{"id":20,"name":"Away"},"statistics":[
                    {"type":"Corner Kicks","value":3}]}
                ]}
                """;
        var result = client.parseFixtureStatistics(json, 99, 200, Instant.EPOCH);
        assertEquals(ApiFootballFixtureStatisticsResponse.Status.SUCCESS, result.status());
        assertEquals(2, result.teams().size());
        assertTrue(result.teams().getFirst().statistics().getFirst().value().isInt());
        assertTrue(result.teams().getFirst().statistics().get(2).value().isNull());
    }

    @Test
    void distinguishesEmptyApiErrorAndMalformedJson() {
        assertEquals(ApiFootballFixtureStatisticsResponse.Status.EMPTY,
                client.parseFixtureStatistics("{\"errors\":[],\"response\":[]}",
                        1, 200, Instant.EPOCH).status());
        assertEquals(ApiFootballFixtureStatisticsResponse.Status.API_ERROR,
                client.parseFixtureStatistics("{\"errors\":{\"fixture\":\"bad\"},\"response\":[]}",
                        1, 200, Instant.EPOCH).status());
        assertEquals(ApiFootballFixtureStatisticsResponse.Status.PARSE_ERROR,
                client.parseFixtureStatistics("not-json", 1, 200, Instant.EPOCH).status());
    }
}
