package pl.zagranietyper.config;

public record ApiFootballConfig(
        String apiKey,
        String baseUrl,
        int daysBeforePublication,
        int daysAfterPublication,
        long minimumRequestIntervalMillis
) {

    public static ApiFootballConfig fromEnvironment() {

        String apiKey =
                System.getenv(
                        "APISPORTS_FOOTBALL_KEY"
                );

        if (
                apiKey == null
                        || apiKey.isBlank()
        ) {
            throw new IllegalStateException(
                    "Brak zmiennej środowiskowej "
                            + "APISPORTS_FOOTBALL_KEY"
            );
        }

        return new ApiFootballConfig(
                apiKey.trim(),

                env(
                        "API_FOOTBALL_BASE_URL",
                        "https://v3.football.api-sports.io"
                ),

                integerEnv(
                        "API_FOOTBALL_DAYS_BEFORE",
                        1
                ),

                integerEnv(
                        "API_FOOTBALL_DAYS_AFTER",
                        3
                ),

                /*
                 * API-Football PRO:
                 *
                 * 5 requestów / sekundę
                 * 300 requestów / minutę
                 *
                 * 220 ms daje lekki margines bezpieczeństwa
                 * przy pojedynczym procesie.
                 */
                longEnv(
                        "API_FOOTBALL_MIN_INTERVAL_MS",
                        220L
                )
        );
    }

    private static String env(
            String name,
            String defaultValue
    ) {
        String value =
                System.getenv(
                        name
                );

        if (
                value == null
                        || value.isBlank()
        ) {
            return defaultValue;
        }

        return value.trim();
    }

    private static int integerEnv(
            String name,
            int defaultValue
    ) {
        String value =
                System.getenv(
                        name
                );

        if (
                value == null
                        || value.isBlank()
        ) {
            return defaultValue;
        }

        return Integer.parseInt(
                value.trim()
        );
    }

    private static long longEnv(
            String name,
            long defaultValue
    ) {
        String value =
                System.getenv(
                        name
                );

        if (
                value == null
                        || value.isBlank()
        ) {
            return defaultValue;
        }

        return Long.parseLong(
                value.trim()
        );
    }
}