package pl.zagranietyper.config;

import java.util.LinkedHashSet;
import java.util.Set;

public record AppConfig(
        String dbUrl,
        String dbUser,
        String dbPassword,
        String baseUrl,
        String wpPostsPath,
        long httpDelayMs,
        int httpMaxRetries,
        int httpTimeoutSeconds
) {
    public static AppConfig fromEnvironment() {
        return new AppConfig(
                env("DB_URL", "jdbc:postgresql://localhost:5432/zagranie_typer"),
                env("DB_USER", "postgres"),
                env("DB_PASSWORD", "postgres"),
                trimTrailingSlash(env("ZAGRANIE_BASE_URL", "https://zagranie.com")),
                normalizePath(env("ZAGRANIE_WP_POSTS_PATH", "/wp-json/wp/v2/posts")),
                envLong("HTTP_DELAY_MS", 300L),
                envInt("HTTP_MAX_RETRIES", 5),
                envInt("HTTP_TIMEOUT_SECONDS", 30)
        );
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static boolean envBoolean(
            String key,
            boolean defaultValue
    ) {
        String value =
                env(
                        key,
                        Boolean.toString(
                                defaultValue
                        )
                );

        if (
                "true".equalsIgnoreCase(
                        value
                )
        ) {
            return true;
        }

        if (
                "false".equalsIgnoreCase(
                        value
                )
        ) {
            return false;
        }

        throw new IllegalArgumentException(
                key
                        + " musi mieć wartość true albo false"
        );
    }

    private static int envInt(String key, int defaultValue) {
        return Integer.parseInt(env(key, Integer.toString(defaultValue)));
    }

    private static long envLong(String key, long defaultValue) {
        return Long.parseLong(env(key, Long.toString(defaultValue)));
    }

    public Set<Long> allowedAuthorIds() {
        return envLongSet(
                "ZAGRANIE_ALLOWED_AUTHOR_IDS",
                "8033"
        );
    }

    public long pollBootstrapLookbackHours() {
        return envLong(
                "ZAGRANIE_POLL_BOOTSTRAP_LOOKBACK_HOURS",
                720L
        );
    }

    public long pollRecentScanHours() {
        return envLong(
                "ZAGRANIE_POLL_RECENT_SCAN_HOURS",
                72L
        );
    }

    public boolean telegramEnabled() {
        return envBoolean(
                "TELEGRAM_ENABLED",
                false
        );
    }

    public String telegramBotToken() {
        return env(
                "TELEGRAM_BOT_TOKEN",
                ""
        );
    }

    public String telegramChatId() {
        return env(
                "TELEGRAM_CHAT_ID",
                ""
        );
    }

    public String telegramTipsterName() {
        return env(
                "TELEGRAM_TIPSTER_NAME",
                "Mateusz Domański"
        );
    }

    public long pollOverlapSeconds() {
        return envLong(
                "ZAGRANIE_POLL_OVERLAP_SECONDS",
                120L
        );
    }

    private static Set<Long> envLongSet(
            String key,
            String defaultValue
    ) {
        String raw =
                env(
                        key,
                        defaultValue
                );

        LinkedHashSet<Long> result =
                new LinkedHashSet<>();

        for (
                String part :
                raw.split(",")
        ) {
            String value =
                    part.trim();

            if (
                    value.isEmpty()
            ) {
                continue;
            }

            long id =
                    Long.parseLong(
                            value
                    );

            if (
                    id <= 0
            ) {
                throw new IllegalArgumentException(
                        key
                                + " zawiera niepoprawne ID autora: "
                                + value
                );
            }

            result.add(
                    id
            );
        }

        return Set.copyOf(
                result
        );
    }

    private static String trimTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String normalizePath(String value) {
        return value.startsWith("/") ? value : "/" + value;
    }
}
