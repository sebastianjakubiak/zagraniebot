package pl.zagranietyper;

import com.fasterxml.jackson.databind.ObjectMapper;
import pl.zagranietyper.config.ApiFootballConfig;
import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.fixture.ApiFootballClient;
import pl.zagranietyper.fixture.ApiFootballMatcher;
import pl.zagranietyper.repository.ApiFootballRepository;
import pl.zagranietyper.repository.Database;
import pl.zagranietyper.service.ApiFootballResolutionService;

public final class ResolveApiFootballMain {

    private ResolveApiFootballMain() {
    }

    public static void main(
            String[] args
    ) {
        AppConfig appConfig =
                AppConfig.fromEnvironment();

        ApiFootballConfig apiConfig =
                ApiFootballConfig.fromEnvironment();

        Database database =
                new Database(
                        appConfig
                );

        ObjectMapper objectMapper =
                new ObjectMapper();

        ApiFootballClient client =
                new ApiFootballClient(
                        apiConfig,
                        objectMapper
                );

        ApiFootballRepository repository =
                new ApiFootballRepository(
                        database
                );

        ApiFootballMatcher matcher =
                new ApiFootballMatcher();

        ApiFootballResolutionService service =
                new ApiFootballResolutionService(
                        apiConfig,
                        client,
                        repository,
                        matcher
                );

        Long legId =
                parseLegId(
                        args
                );

        System.out.println(
                "Zagranie Typer — API-Football historical resolver"
        );

        System.out.println(
                "window=-"
                        + apiConfig.daysBeforePublication()
                        + "/+"
                        + apiConfig.daysAfterPublication()
                        + " days"
        );

        System.out.println(
                "requestInterval="
                        + apiConfig.minimumRequestIntervalMillis()
                        + " ms"
        );

        if (
                legId != null
        ) {
            System.out.println(
                    "legId="
                            + legId
            );
        }

        ApiFootballResolutionService.Result result =
                legId == null
                        ? service.run()
                        : service.run(
                        legId
                );

        System.out.println();
        System.out.println(
                "DONE"
        );

        System.out.println(
                "candidates="
                        + result.candidates()
        );

        System.out.println(
                "matched="
                        + result.matched()
        );

        System.out.println(
                "unmatched="
                        + result.unmatched()
        );

        System.out.println(
                "incompleteWindow="
                        + result.incompleteWindow()
        );

        System.out.println(
                "fetchedDays="
                        + result.fetchedDays()
        );

        System.out.println(
                "cachedDays="
                        + result.cachedDays()
        );

        System.out.println(
                "dailyRemaining="
                        + value(
                        result.dailyRemaining()
                )
        );

        System.out.println(
                "minuteRemaining="
                        + value(
                        result.minuteRemaining()
                )
        );
    }

    private static Long parseLegId(
            String[] args
    ) {
        if (
                args == null
                        || args.length == 0
        ) {
            return null;
        }

        for (
                String arg :
                args
        ) {
            if (
                    arg == null
                            || arg.isBlank()
            ) {
                continue;
            }

            if (
                    arg.startsWith(
                            "--leg-id="
                    )
            ) {
                String value =
                        arg.substring(
                                "--leg-id=".length()
                        );

                try {
                    return Long.parseLong(
                            value
                    );

                } catch (
                        NumberFormatException ex
                ) {
                    throw new IllegalArgumentException(
                            "Niepoprawne --leg-id: "
                                    + value,
                            ex
                    );
                }
            }
        }

        throw new IllegalArgumentException(
                "Nieznany argument. Użycie: --leg-id=<id>"
        );
    }

    private static String value(
            Integer value
    ) {
        return value == null
                ? "?"
                : value.toString();
    }
}