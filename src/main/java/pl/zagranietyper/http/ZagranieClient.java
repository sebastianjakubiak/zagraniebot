package pl.zagranietyper.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.wp.WpPost;
import pl.zagranietyper.wp.WpPostPage;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class ZagranieClient {

    private static final Logger LOG =
            Logger.getLogger(
                    ZagranieClient.class.getName()
            );

    /*
     * Lista postów jest teraz LEKKA:
     * nie pobieramy content.rendered.
     *
     * Test na Zagranie:
     * 50 postów -> ~22 KB i stabilne HTTP 200.
     */
    private static final int INDEX_PER_PAGE =
            50;

    private static final long EMPTY_BODY_FINAL_COOLDOWN_MS =
            30_000L;

    private static final DateTimeFormatter WP_DATE =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final AppConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ZagranieClient(
            AppConfig config,
            ObjectMapper objectMapper
    ) {
        this.config =
                config;

        this.objectMapper =
                objectMapper;

        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(
                                        config.httpTimeoutSeconds()
                                )
                        )
                        .followRedirects(
                                HttpClient.Redirect.NORMAL
                        )
                        .build();
    }

    /*
     * Lekki indeks WordPressa.
     *
     * CELOWO bez "content".
     */
    public WpPostPage fetchPostsPage(
            Instant fromInclusive,
            Instant toExclusive,
            int page
    ) {
        Map<String, String> query =
                new LinkedHashMap<>();

        query.put(
                "page",
                Integer.toString(
                        page
                )
        );

        query.put(
                "per_page",
                Integer.toString(
                        INDEX_PER_PAGE
                )
        );

        query.put(
                "after",
                WP_DATE.format(
                        fromInclusive.atOffset(
                                ZoneOffset.UTC
                        )
                )
        );

        query.put(
                "before",
                WP_DATE.format(
                        toExclusive.atOffset(
                                ZoneOffset.UTC
                        )
                )
        );

        query.put(
                "orderby",
                "date"
        );

        query.put(
                "order",
                "asc"
        );

        query.put(
                "_fields",
                "id,author,link,slug,date,date_gmt,"
                        + "modified,modified_gmt,title"
        );

        URI uri =
                URI.create(
                        config.baseUrl()
                                + config.wpPostsPath()
                                + "?"
                                + encodeQuery(
                                query
                        )
                );

        LOG.info(
                "WP INDEX GET: "
                        + uri
        );

        HttpResponse<String> response =
                getWithRetry(
                        uri
                );

        String body =
                response.body();

        if (
                body == null
                        || body.isBlank()
        ) {
            throw new IllegalStateException(
                    "WordPress zwrócił pustą odpowiedź indeksową."
                            + " status="
                            + response.statusCode()
                            + ", uri="
                            + uri
            );
        }

        try {
            List<WpPost> posts =
                    objectMapper.readValue(
                            body,
                            new TypeReference<>() {
                            }
                    );

            int totalPages =
                    response.headers()
                            .firstValue(
                                    "X-WP-TotalPages"
                            )
                            .map(
                                    Integer::parseInt
                            )
                            .orElse(
                                    posts.size()
                                            < INDEX_PER_PAGE
                                            ? page
                                            : page + 1
                            );

            int totalPosts =
                    response.headers()
                            .firstValue(
                                    "X-WP-Total"
                            )
                            .map(
                                    Integer::parseInt
                            )
                            .orElse(
                                    -1
                            );

            LOG.info(
                    "WP INDEX response:"
                            + " status="
                            + response.statusCode()
                            + ", bytes="
                            + body.getBytes(
                            StandardCharsets.UTF_8
                    ).length
                            + ", posts="
                            + posts.size()
                            + ", total="
                            + totalPosts
                            + ", totalPages="
                            + totalPages
            );

            return new WpPostPage(
                    posts,
                    page,
                    totalPages,
                    totalPosts
            );

        } catch (
                IOException e
        ) {
            throw new IllegalStateException(
                    "Nie udało się zdekodować "
                            + "indeksu WordPress dla "
                            + uri
                            + ". Początek body: "
                            + abbreviate(
                            body,
                            500
                    ),
                    e
            );
        }
    }

    /*
     * Lekki indeks WordPressa po dacie modyfikacji.
     * Używany przez live polling.
     */
    public WpPostPage fetchModifiedPostsPage(
            long authorId,
            Instant modifiedAfter,
            Instant modifiedBefore,
            int page
    ) {
        Map<String, String> query =
                new LinkedHashMap<>();

        query.put(
                "page",
                Integer.toString(
                        page
                )
        );

        query.put(
                "per_page",
                Integer.toString(
                        INDEX_PER_PAGE
                )
        );

        query.put(
                "author",
                Long.toString(
                        authorId
                )
        );

        query.put(
                "modified_after",
                WP_DATE.format(
                        modifiedAfter.atOffset(
                                ZoneOffset.UTC
                        )
                )
        );

        query.put(
                "modified_before",
                WP_DATE.format(
                        modifiedBefore.atOffset(
                                ZoneOffset.UTC
                        )
                )
        );

        query.put(
                "orderby",
                "modified"
        );

        query.put(
                "order",
                "asc"
        );

        query.put(
                "_fields",
                "id,author,link,slug,date,date_gmt,"
                        + "modified,modified_gmt,title"
        );

        URI uri =
                URI.create(
                        config.baseUrl()
                                + config.wpPostsPath()
                                + "?"
                                + encodeQuery(
                                query
                        )
                );

        LOG.info(
                "WP MODIFIED INDEX GET: author="
                        + authorId
                        + ", page="
                        + page
                        + ", from="
                        + modifiedAfter
                        + ", to="
                        + modifiedBefore
        );

        HttpResponse<String> response =
                getWithRetry(
                        uri
                );

        String body =
                response.body();

        if (
                body == null
                        || body.isBlank()
        ) {
            throw new IllegalStateException(
                    "WordPress zwrócił pustą odpowiedź indeksową po modified."
                            + " status="
                            + response.statusCode()
                            + ", uri="
                            + uri
            );
        }

        try {
            List<WpPost> posts =
                    objectMapper.readValue(
                            body,
                            new TypeReference<>() {
                            }
                    );

            int totalPages =
                    response.headers()
                            .firstValue(
                                    "X-WP-TotalPages"
                            )
                            .map(
                                    Integer::parseInt
                            )
                            .orElse(
                                    posts.size()
                                            < INDEX_PER_PAGE
                                            ? page
                                            : page + 1
                            );

            int totalPosts =
                    response.headers()
                            .firstValue(
                                    "X-WP-Total"
                            )
                            .map(
                                    Integer::parseInt
                            )
                            .orElse(
                                    -1
                            );

            return new WpPostPage(
                    posts,
                    page,
                    totalPages,
                    totalPosts
            );

        } catch (
                IOException e
        ) {
            throw new IllegalStateException(
                    "Nie udało się zdekodować indeksu WordPress po modified dla "
                            + uri
                            + ". Początek body: "
                            + abbreviate(
                            body,
                            500
                    ),
                    e
            );
        }
    }

    /*
     * Lekki metadata GET dla pojedynczego posta.
     *
     * Używane m.in. przez repair-posts:
     * pobieramy strukturę WpPost bez ciężkiego content.rendered,
     * a content pobieramy osobnym requestem.
     */
    public WpPost fetchPost(
            long postId
    ) {
        Map<String, String> query =
                new LinkedHashMap<>();

        query.put(
                "_fields",
                "id,author,link,slug,date,date_gmt,"
                        + "modified,modified_gmt,title"
        );

        URI uri =
                URI.create(
                        config.baseUrl()
                                + config.wpPostsPath()
                                + "/"
                                + postId
                                + "?"
                                + encodeQuery(
                                query
                        )
                );

        LOG.info(
                "WP POST GET: post="
                        + postId
        );

        HttpResponse<String> response =
                getWithRetry(
                        uri
                );

        String body =
                response.body();

        if (
                body == null
                        || body.isBlank()
        ) {
            throw new IllegalStateException(
                    "Pusta odpowiedź metadata dla WP post "
                            + postId
            );
        }

        try {
            return objectMapper.readValue(
                    body,
                    WpPost.class
            );

        } catch (
                IOException e
        ) {
            throw new IllegalStateException(
                    "Nie udało się zdekodować metadata "
                            + "dla WP post "
                            + postId
                            + ". Początek body: "
                            + abbreviate(
                            body,
                            500
                    ),
                    e
            );
        }
    }

    /*
     * Ciężki content pobieramy osobno,
     * dokładnie dla jednego posta.
     */
    public String fetchPostRenderedContent(
            long postId
    ) {
        Map<String, String> query =
                new LinkedHashMap<>();

        query.put(
                "_fields",
                "id,content"
        );

        URI uri =
                URI.create(
                        config.baseUrl()
                                + config.wpPostsPath()
                                + "/"
                                + postId
                                + "?"
                                + encodeQuery(
                                query
                        )
                );

        LOG.info(
                "WP CONTENT GET: post="
                        + postId
        );

        HttpResponse<String> response =
                getWithRetry(
                        uri
                );

        String body =
                response.body();

        if (
                body == null
                        || body.isBlank()
        ) {
            throw new IllegalStateException(
                    "Pusta odpowiedź content dla WP post "
                            + postId
            );
        }

        try {
            JsonNode root =
                    objectMapper.readTree(
                            body
                    );

            JsonNode rendered =
                    root.path(
                            "content"
                    ).path(
                            "rendered"
                    );

            if (
                    rendered.isMissingNode()
                            || rendered.isNull()
            ) {
                return null;
            }

            String html =
                    rendered.asText();

            if (
                    html == null
                            || html.isBlank()
            ) {
                return null;
            }

            return html;

        } catch (
                IOException e
        ) {
            throw new IllegalStateException(
                    "Nie udało się zdekodować content "
                            + "dla WP post "
                            + postId
                            + ". Początek body: "
                            + abbreviate(
                            body,
                            500
                    ),
                    e
            );
        }
    }

    public String fetchArticleHtml(
            String url
    ) {
        HttpResponse<String> response =
                getWithRetry(
                        URI.create(
                                url
                        )
                );

        String body =
                response.body();

        if (
                body == null
                        || body.isBlank()
        ) {
            throw new IllegalStateException(
                    "Pusty HTML artykułu: "
                            + url
            );
        }

        return body;
    }

    private HttpResponse<String> getWithRetry(
            URI uri
    ) {
        RuntimeException lastError =
                null;

        int maxRetries =
                Math.max(
                        1,
                        config.httpMaxRetries()
                );

        for (
                int attempt = 1;
                attempt <= maxRetries;
                attempt++
        ) {
            try {
                HttpResponse<String> response =
                        sendRequest(
                                uri
                        );

                logResponse(
                        uri,
                        response
                );

                int status =
                        response.statusCode();

                String body =
                        response.body();

                if (
                        status == 200
                                && body != null
                                && !body.isBlank()
                ) {
                    sleep(
                            config.httpDelayMs()
                    );

                    return response;
                }

                if (
                        status == 200
                                && (
                                body == null
                                        || body.isBlank()
                        )
                ) {
                    lastError =
                            new EmptyBodyException(
                                    "HTTP 200, ale puste body dla "
                                            + uri
                            );

                    if (
                            attempt >= maxRetries
                    ) {
                        break;
                    }

                    long backoff =
                            calculateBackoff(
                                    attempt
                            );

                    LOG.warning(
                            "Puste body. Retry "
                                    + attempt
                                    + "/"
                                    + maxRetries
                                    + " za "
                                    + backoff
                                    + " ms"
                    );

                    sleep(
                            backoff
                    );

                    continue;
                }

                if (
                        status == 429
                                || status >= 500
                ) {
                    lastError =
                            new IllegalStateException(
                                    "HTTP "
                                            + status
                                            + " dla "
                                            + uri
                            );

                    if (
                            attempt >= maxRetries
                    ) {
                        break;
                    }

                    long backoff =
                            calculateBackoff(
                                    attempt
                            );

                    LOG.warning(
                            "HTTP "
                                    + status
                                    + ". Retry "
                                    + attempt
                                    + "/"
                                    + maxRetries
                                    + " za "
                                    + backoff
                                    + " ms"
                    );

                    sleep(
                            backoff
                    );

                    continue;
                }

                throw new IllegalStateException(
                        "HTTP "
                                + status
                                + " dla "
                                + uri
                                + ": "
                                + abbreviate(
                                body,
                                500
                        )
                );

            } catch (
                    IOException e
            ) {
                lastError =
                        new IllegalStateException(
                                "Błąd I/O dla "
                                        + uri,
                                e
                        );

                if (
                        attempt >= maxRetries
                ) {
                    break;
                }

                long backoff =
                        calculateBackoff(
                                attempt
                        );

                LOG.warning(
                        "Błąd I/O. Retry "
                                + attempt
                                + "/"
                                + maxRetries
                                + " za "
                                + backoff
                                + " ms"
                );

                sleep(
                        backoff
                );

            } catch (
                    InterruptedException e
            ) {
                Thread.currentThread()
                        .interrupt();

                throw new IllegalStateException(
                        "Przerwano request do "
                                + uri,
                        e
                );
            }
        }

        if (
                lastError instanceof EmptyBodyException
        ) {
            LOG.warning(
                    "Wyczerpano zwykłe retry dla pustego body."
                            + " Cooldown "
                            + EMPTY_BODY_FINAL_COOLDOWN_MS
                            + " ms, potem finalna próba."
                            + " URI="
                            + uri
            );

            sleep(
                    EMPTY_BODY_FINAL_COOLDOWN_MS
            );

            return finalEmptyBodyAttempt(
                    uri
            );
        }

        throw lastError != null
                ? lastError
                : new IllegalStateException(
                "Nie udało się pobrać "
                        + uri
        );
    }

    private HttpResponse<String> finalEmptyBodyAttempt(
            URI uri
    ) {
        try {
            HttpResponse<String> response =
                    sendRequest(
                            uri
                    );

            logResponse(
                    uri,
                    response
            );

            int status =
                    response.statusCode();

            String body =
                    response.body();

            if (
                    status == 200
                            && body != null
                            && !body.isBlank()
            ) {
                LOG.info(
                        "Finalna próba po cooldownie udana: "
                                + uri
                );

                sleep(
                        config.httpDelayMs()
                );

                return response;
            }

            if (
                    status == 200
                            && (
                            body == null
                                    || body.isBlank()
                    )
            ) {
                throw new IllegalStateException(
                        "HTTP 200, ale nadal puste body "
                                + "po finalnym cooldownie dla "
                                + uri
                );
            }

            throw new IllegalStateException(
                    "Finalna próba po cooldownie zwróciła HTTP "
                            + status
                            + " dla "
                            + uri
                            + ": "
                            + abbreviate(
                            body,
                            500
                    )
            );

        } catch (
                IOException e
        ) {
            throw new IllegalStateException(
                    "Błąd I/O podczas finalnej próby dla "
                            + uri,
                    e
            );

        } catch (
                InterruptedException e
        ) {
            Thread.currentThread()
                    .interrupt();

            throw new IllegalStateException(
                    "Przerwano finalny request do "
                            + uri,
                    e
            );
        }
    }

    private HttpResponse<String> sendRequest(
            URI uri
    ) throws IOException, InterruptedException {

        HttpRequest request =
                HttpRequest.newBuilder(
                                uri
                        )
                        .timeout(
                                Duration.ofSeconds(
                                        config.httpTimeoutSeconds()
                                )
                        )
                        .header(
                                "Accept",
                                "application/json"
                        )
                        .header(
                                "User-Agent",
                                "Mozilla/5.0 "
                                        + "(Macintosh; Intel Mac OS X) "
                                        + "ZagranieTyper/0.1"
                        )
                        .header(
                                "Cache-Control",
                                "no-cache"
                        )
                        .GET()
                        .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8
                )
        );
    }

    private static void logResponse(
            URI uri,
            HttpResponse<String> response
    ) {
        String body =
                response.body();

        int bodyLength =
                body == null
                        ? 0
                        : body.getBytes(
                        StandardCharsets.UTF_8
                ).length;

        LOG.info(
                "HTTP "
                        + response.statusCode()
                        + " | bytes="
                        + bodyLength
                        + " | "
                        + uri
        );
    }

    private static long calculateBackoff(
            int attempt
    ) {
        return Math.min(
                10_000L,
                500L
                        * (
                        1L
                                << Math.min(
                                attempt - 1,
                                4
                        )
                )
        );
    }

    private static String encodeQuery(
            Map<String, String> query
    ) {
        List<String> parts =
                new ArrayList<>();

        query.forEach(
                (key, value) ->
                        parts.add(
                                encode(
                                        key
                                )
                                        + "="
                                        + encode(
                                        value
                                )
                        )
        );

        return String.join(
                "&",
                parts
        );
    }

    private static String encode(
            String value
    ) {
        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }

    private static void sleep(
            long millis
    ) {
        if (
                millis <= 0
        ) {
            return;
        }

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
                    "Przerwano oczekiwanie HTTP",
                    e
            );
        }
    }

    private static String abbreviate(
            String value,
            int maxLength
    ) {
        if (
                value == null
        ) {
            return "<null>";
        }

        if (
                value.length()
                        <= maxLength
        ) {
            return value;
        }

        return value.substring(
                0,
                maxLength
        ) + "...";
    }

    private static final class EmptyBodyException
            extends IllegalStateException {

        private EmptyBodyException(
                String message
        ) {
            super(
                    message
            );
        }
    }
}