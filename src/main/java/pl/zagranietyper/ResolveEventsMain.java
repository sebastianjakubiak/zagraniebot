package pl.zagranietyper;

import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.repository.Database;
import pl.zagranietyper.repository.EventResolutionRepository;
import pl.zagranietyper.resolver.EventResolver;
import pl.zagranietyper.service.EventResolutionService;

public final class ResolveEventsMain {

    private ResolveEventsMain() {
    }

    public static void main(
            String[] args
    ) {
        AppConfig config =
                AppConfig.fromEnvironment();

        Database database =
                new Database(
                        config
                );

        EventResolutionRepository repository =
                new EventResolutionRepository(
                        database
                );

        EventResolver resolver =
                new EventResolver();

        EventResolutionService service =
                new EventResolutionService(
                        repository,
                        resolver
                );

        System.out.println(
                "Zagranie Typer — Event Resolution"
        );

        EventResolutionService.ResolutionResult result =
                service.run();

        System.out.println();
        System.out.println("DONE");

        System.out.println(
                "total="
                        + result.total()
        );

        System.out.println(
                "resolved="
                        + result.resolved()
        );

        System.out.println(
                "unresolved="
                        + result.unresolved()
        );

        System.out.println();

        result.bySource()
                .forEach(
                        (source, count) ->
                                System.out.println(
                                        source
                                                + "="
                                                + count
                                )
                );
    }
}