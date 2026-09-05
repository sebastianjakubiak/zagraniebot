package pl.zagranietyper.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

public final class TelegramNotifier {

    private static final Logger LOG =
            Logger.getLogger(
                    TelegramNotifier.class.getName()
            );

    private static final int MAX_ATTEMPTS =
            3;

    private final String botToken;
    private final String chatId;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public TelegramNotifier(
            String botToken,
            String chatId,
            ObjectMapper objectMapper,
            int timeoutSeconds
    ) {
        if (
                botToken == null
                        || botToken.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "TELEGRAM_BOT_TOKEN jest wymagany, gdy TELEGRAM_ENABLED=true"
            );
        }

        if (
                chatId == null
                        || chatId.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "TELEGRAM_CHAT_ID jest wymagany, gdy TELEGRAM_ENABLED=true"
            );
        }

        this.botToken =
                botToken.trim();

        this.chatId =
                chatId.trim();

        this.objectMapper =
                objectMapper;

        this.requestTimeout =
                Duration.ofSeconds(
                        Math.max(
                                1,
                                timeoutSeconds
                        )
                );

        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(
                                requestTimeout
                        )
                        .build();
    }

    public void send(
            String text
    ) {
        String payload =
                payload(
                        text
                );

        URI uri =
                URI.create(
                        "https://api.telegram.org/bot"
                                + botToken
                                + "/sendMessage"
                );

        RuntimeException lastError =
                null;

        for (
                int attempt = 1;
                attempt <= MAX_ATTEMPTS;
                attempt++
        ) {
            try {
                HttpRequest request =
                        HttpRequest.newBuilder(
                                        uri
                                )
                                .timeout(
                                        requestTimeout
                                )
                                .header(
                                        "Content-Type",
                                        "application/json"
                                )
                                .POST(
                                        HttpRequest.BodyPublishers.ofString(
                                                payload
                                        )
                                )
                                .build();

                HttpResponse<String> response =
                        httpClient.send(
                                request,
                                HttpResponse.BodyHandlers.ofString()
                        );

                int status =
                        response.statusCode();

                if (
                        status >= 200
                                && status < 300
                ) {
                    LOG.info(
                            "Telegram message sent"
                    );

                    return;
                }

                String responseBody =
                        response.body();

                lastError =
                        new IllegalStateException(
                                "Telegram HTTP "
                                        + status
                                        + ": "
                                        + abbreviate(
                                        responseBody,
                                        500
                                )
                        );

                if (
                        status < 500
                                && status != 429
                ) {
                    throw lastError;
                }

            } catch (
                    IOException e
            ) {
                lastError =
                        new IllegalStateException(
                                "Błąd I/O podczas wysyłki Telegram",
                                e
                        );

            } catch (
                    InterruptedException e
            ) {
                Thread.currentThread()
                        .interrupt();

                throw new IllegalStateException(
                        "Przerwano wysyłkę Telegram",
                        e
                );
            }

            if (
                    attempt < MAX_ATTEMPTS
            ) {
                sleep(
                        500L
                                * attempt
                );
            }
        }

        throw lastError != null
                ? lastError
                : new IllegalStateException(
                "Nie udało się wysłać wiadomości Telegram"
        );
    }

    private String payload(
            String text
    ) {
        Map<String, Object> payload =
                new LinkedHashMap<>();

        payload.put(
                "chat_id",
                chatId
        );

        payload.put(
                "text",
                text
        );

        payload.put(
                "disable_web_page_preview",
                true
        );

        try {
            return objectMapper.writeValueAsString(
                    payload
            );

        } catch (
                JsonProcessingException e
        ) {
            throw new IllegalStateException(
                    "Nie udało się zbudować Telegram payload",
                    e
            );
        }
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
                    "Przerwano retry Telegram",
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
                        || value.length() <= maxLength
        ) {
            return value;
        }

        return value.substring(
                0,
                maxLength
        );
    }
}
