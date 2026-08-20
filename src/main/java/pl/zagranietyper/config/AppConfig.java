package pl.zagranietyper.config;

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

    private static int envInt(String key, int defaultValue) {
        return Integer.parseInt(env(key, Integer.toString(defaultValue)));
    }

    private static long envLong(String key, long defaultValue) {
        return Long.parseLong(env(key, Long.toString(defaultValue)));
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
