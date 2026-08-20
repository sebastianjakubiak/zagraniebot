package pl.zagranietyper.fixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.zagranietyper.config.ApiFootballConfig;
import pl.zagranietyper.model.ApiFootballFixture;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public final class ApiFootballClient {

    private static final ZoneId WARSAW =
            ZoneId.of(
                    "Europe/Warsaw"
            );

    private static final int MAX_ATTEMPTS =
            4;

    private final ApiFootballConfig config;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private long lastRequestAt;

    public ApiFootballClient(
            ApiFootballConfig config,
            ObjectMapper objectMapper
    ) {
        this.config = config;
        this.objectMapper = objectMapper;

        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(20)
                        )
                        .followRedirects(
                                HttpClient.Redirect.NORMAL
                        )
                        .build();
    }

    public FetchResult fetchDate(
            LocalDate date
    ) {
        URI uri =
                URI.create(
                        config.baseUrl()
                                + "/fixtures"
                                + "?date="
                                + date
                                + "&timezone=Europe%2FWarsaw"
                );

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                uri
                        )
                        .timeout(
                                Duration.ofSeconds(60)
                        )
                        .header(
                                "x-apisports-key",
                                config.apiKey()
                        )
                        .header(
                                "Accept",
                                "application/json"
                        )
                        .header(
                                "User-Agent",
                                "ZagranieTyper/0.1"
                        )
                        .GET()
                        .build();

        HttpResponse<String> response =
                sendWithRetry(
                        request,
                        date
                );

        if (
                response.statusCode() != 200
        ) {
            throw new IllegalStateException(
                    "API-Football HTTP "
                            + response.statusCode()
                            + " dla "
                            + date
            );
        }

        ParsedResponse parsed =
                parse(
                        response.body(),
                        date
                );

        return new FetchResult(
                parsed.fixtures(),

                integerHeader(
                        response,
                        "x-ratelimit-requests-limit"
                ),

                integerHeader(
                        response,
                        "x-ratelimit-requests-remaining"
                ),

                integerHeader(
                        response,
                        "x-ratelimit-limit"
                ),

                integerHeader(
                        response,
                        "x-ratelimit-remaining"
                )
        );
    }

    private HttpResponse<String> sendWithRetry(
            HttpRequest request,
            LocalDate date
    ) {
        int attempt =
                0;

        while (
                attempt < MAX_ATTEMPTS
        ) {
            attempt++;

            throttle();

            try {
                HttpResponse<String> response =
                        httpClient.send(
                                request,
                                HttpResponse.BodyHandlers.ofString()
                        );

                int status =
                        response.statusCode();

                if (
                        status == 429
                                && attempt < MAX_ATTEMPTS
                ) {
                    long wait =
                            retryDelayMillis(
                                    response,
                                    attempt
                            );

                    System.out.println(
                            "RATE LIMIT"
                                    + " date="
                                    + date
                                    + " attempt="
                                    + attempt
                                    + " sleepMs="
                                    + wait
                    );

                    sleep(
                            wait
                    );

                    continue;
                }

                if (
                        status >= 500
                                && status <= 599
                                && attempt < MAX_ATTEMPTS
                ) {
                    long wait =
                            attempt * 1000L;

                    System.out.println(
                            "API-Football "
                                    + status
                                    + " date="
                                    + date
                                    + " attempt="
                                    + attempt
                                    + " sleepMs="
                                    + wait
                    );

                    sleep(
                            wait
                    );

                    continue;
                }

                return response;

            } catch (
                    IOException e
            ) {
                if (
                        attempt >= MAX_ATTEMPTS
                ) {
                    throw new IllegalStateException(
                            "Błąd połączenia z API-Football dla "
                                    + date,
                            e
                    );
                }

                sleep(
                        attempt * 1000L
                );

            } catch (
                    InterruptedException e
            ) {
                Thread.currentThread()
                        .interrupt();

                throw new IllegalStateException(
                        "Przerwano request API-Football",
                        e
                );
            }
        }

        throw new IllegalStateException(
                "Nie udało się pobrać API-Football dla "
                        + date
        );
    }

    private ParsedResponse parse(
            String body,
            LocalDate requestedDate
    ) {
        try {
            JsonNode root =
                    objectMapper.readTree(
                            body
                    );

            JsonNode errors =
                    root.get(
                            "errors"
                    );

            if (
                    hasErrors(
                            errors
                    )
            ) {
                throw new IllegalStateException(
                        "API-Football errors dla "
                                + requestedDate
                                + ": "
                                + errors
                );
            }

            JsonNode paging =
                    root.get(
                            "paging"
                    );

            if (
                    paging != null
                            && paging.has(
                            "total"
                    )
                            && paging.get(
                            "total"
                    ).asInt(1) > 1
            ) {
                throw new IllegalStateException(
                        "API-Football zwróciło więcej niż jedną "
                                + "stronę dla fixtures?date="
                                + requestedDate
                                + ". Nie zapisuję niepełnego dnia."
                );
            }

            JsonNode response =
                    root.get(
                            "response"
                    );

            if (
                    response == null
                            || !response.isArray()
            ) {
                return new ParsedResponse(
                        List.of()
                );
            }

            List<ApiFootballFixture> fixtures =
                    new ArrayList<>();

            for (
                    JsonNode item :
                    response
            ) {
                ApiFootballFixture fixture =
                        parseFixture(
                                item
                        );

                if (
                        fixture != null
                ) {
                    fixtures.add(
                            fixture
                    );
                }
            }

            return new ParsedResponse(
                    List.copyOf(
                            fixtures
                    )
            );

        } catch (
                IOException e
        ) {
            throw new IllegalStateException(
                    "Nie udało się sparsować odpowiedzi "
                            + "API-Football dla "
                            + requestedDate,
                    e
            );
        }
    }

    private ApiFootballFixture parseFixture(
            JsonNode item
    ) throws IOException {

        JsonNode fixture =
                item.path(
                        "fixture"
                );

        JsonNode league =
                item.path(
                        "league"
                );

        JsonNode teams =
                item.path(
                        "teams"
                );

        JsonNode goals =
                item.path(
                        "goals"
                );

        long fixtureId =
                fixture.path(
                        "id"
                ).asLong(0L);

        if (
                fixtureId == 0L
        ) {
            return null;
        }

        String dateRaw =
                text(
                        fixture,
                        "date"
                );

        if (
                dateRaw == null
        ) {
            return null;
        }

        Instant kickoff =
                OffsetDateTime
                        .parse(
                                dateRaw
                        )
                        .toInstant();

        LocalDate fixtureDate =
                kickoff
                        .atZone(
                                WARSAW
                        )
                        .toLocalDate();

        JsonNode status =
                fixture.path(
                        "status"
                );

        JsonNode home =
                teams.path(
                        "home"
                );

        JsonNode away =
                teams.path(
                        "away"
                );

        String homeName =
                text(
                        home,
                        "name"
                );

        String awayName =
                text(
                        away,
                        "name"
                );

        if (
                homeName == null
                        || awayName == null
        ) {
            return null;
        }

        return new ApiFootballFixture(
                fixtureId,

                kickoff,
                fixtureDate,

                nullableLong(
                        league,
                        "id"
                ),

                text(
                        league,
                        "name"
                ),

                text(
                        league,
                        "country"
                ),

                nullableInteger(
                        league,
                        "season"
                ),

                text(
                        league,
                        "round"
                ),

                nullableLong(
                        home,
                        "id"
                ),

                homeName,

                nullableLong(
                        away,
                        "id"
                ),

                awayName,

                nullableInteger(
                        goals,
                        "home"
                ),

                nullableInteger(
                        goals,
                        "away"
                ),

                text(
                        status,
                        "short"
                ),

                text(
                        status,
                        "long"
                ),

                objectMapper.writeValueAsString(
                        item
                )
        );
    }

    private synchronized void throttle() {

        long minimumInterval =
                config.minimumRequestIntervalMillis();

        if (
                minimumInterval <= 0
        ) {
            return;
        }

        long now =
                System.currentTimeMillis();

        long elapsed =
                now - lastRequestAt;

        long wait =
                minimumInterval
                        - elapsed;

        if (
                wait > 0
        ) {
            sleep(
                    wait
            );
        }

        lastRequestAt =
                System.currentTimeMillis();
    }

    private static long retryDelayMillis(
            HttpResponse<?> response,
            int attempt
    ) {
        String retryAfter =
                response.headers()
                        .firstValue(
                                "Retry-After"
                        )
                        .orElse(
                                null
                        );

        if (
                retryAfter != null
        ) {
            try {
                long seconds =
                        Long.parseLong(
                                retryAfter.trim()
                        );

                return Math.max(
                        1000L,
                        seconds * 1000L
                );

            } catch (
                    NumberFormatException ignored
            ) {
            }
        }

        return Math.max(
                1000L,
                attempt * 1500L
        );
    }

    private static boolean hasErrors(
            JsonNode node
    ) {
        if (
                node == null
                        || node.isNull()
        ) {
            return false;
        }

        if (
                node.isArray()
        ) {
            return !node.isEmpty();
        }

        if (
                node.isObject()
        ) {
            return node.size() > 0;
        }

        return false;
    }

    private static Integer integerHeader(
            HttpResponse<?> response,
            String name
    ) {
        return response.headers()
                .firstValue(
                        name
                )
                .map(
                        value -> {
                            try {
                                return Integer.parseInt(
                                        value
                                );

                            } catch (
                                    NumberFormatException e
                            ) {
                                return null;
                            }
                        }
                )
                .orElse(
                        null
                );
    }

    private static String text(
            JsonNode node,
            String field
    ) {
        JsonNode value =
                node.get(
                        field
                );

        if (
                value == null
                        || value.isNull()
        ) {
            return null;
        }

        String text =
                value.asText();

        if (
                text == null
                        || text.isBlank()
        ) {
            return null;
        }

        return text.trim();
    }

    private static Integer nullableInteger(
            JsonNode node,
            String field
    ) {
        JsonNode value =
                node.get(
                        field
                );

        if (
                value == null
                        || value.isNull()
        ) {
            return null;
        }

        return value.asInt();
    }

    private static Long nullableLong(
            JsonNode node,
            String field
    ) {
        JsonNode value =
                node.get(
                        field
                );

        if (
                value == null
                        || value.isNull()
        ) {
            return null;
        }

        return value.asLong();
    }

    private static void sleep(
            long millis
    ) {
        try {
            Thread.sleep(
                    millis
            );

        } catch (
                InterruptedException e
        ) {
            Thread.currentThread()
                    .interrupt();

            throw new IllegalStateException(
                    "Przerwano oczekiwanie",
                    e
            );
        }
    }

    public record FetchResult(
            List<ApiFootballFixture> fixtures,

            Integer dailyLimit,
            Integer dailyRemaining,

            Integer minuteLimit,
            Integer minuteRemaining
    ) {
    }

    private record ParsedResponse(
            List<ApiFootballFixture> fixtures
    ) {
    }
}