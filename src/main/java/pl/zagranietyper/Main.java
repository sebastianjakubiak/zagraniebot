package pl.zagranietyper;

import com.fasterxml.jackson.databind.ObjectMapper;
import pl.zagranietyper.config.AppConfig;
import pl.zagranietyper.http.ZagranieClient;
import pl.zagranietyper.model.DiscoveredAuthor;
import pl.zagranietyper.notification.TelegramNotifier;
import pl.zagranietyper.notification.TelegramTipMessageFormatter;
import pl.zagranietyper.parser.AuthorIdentityParser;
import pl.zagranietyper.parser.EditorialTipDetector;
import pl.zagranietyper.parser.ZagraniePostParser;
import pl.zagranietyper.repository.AuthorRepository;
import pl.zagranietyper.repository.Database;
import pl.zagranietyper.repository.ImportRepository;
import pl.zagranietyper.service.AllAuthorsBackfillService;
import pl.zagranietyper.service.AllowedAuthors;
import pl.zagranietyper.service.AuthorDiscoveryService;
import pl.zagranietyper.service.HistoricalBackfillService;
import pl.zagranietyper.service.NewTipsPollingService;
import pl.zagranietyper.service.RepairPostsService;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Main {

    private static final ZoneId WARSAW =
            ZoneId.of(
                    "Europe/Warsaw"
            );

    private Main() {
    }

    public static void main(
            String[] args
    ) {
        if (
                args.length == 0
                        || "help".equalsIgnoreCase(
                        args[0]
                )
                        || "--help".equalsIgnoreCase(
                        args[0]
                )
        ) {
            printUsage();
            return;
        }

        String command =
                args[0];

        Map<String, String> options =
                parseOptions(
                        args
                );

        DateRange dateRange =
                resolveDateRange(
                        options
                );

        boolean initializeSchema =
                shouldInitializeSchema(
                        command,
                        options
                );

        AppConfig config =
                AppConfig.fromEnvironment();

        ObjectMapper objectMapper =
                new ObjectMapper();

        Database database =
                new Database(
                        config
                );

        if (
                initializeSchema
        ) {
            database.initializeSchema();
        }

        ZagranieClient client =
                new ZagranieClient(
                        config,
                        objectMapper
                );

        switch (
                command.toLowerCase()
        ) {
            case "backfill" ->
                    runBackfill(
                            options,
                            dateRange,
                            database,
                            objectMapper,
                            client
                    );

            case "discover-authors" ->
                    runAuthorDiscovery(
                            dateRange,
                            database,
                            client
                    );

            case "backfill-all" ->
                    runBackfillAll(
                            dateRange,
                            database,
                            objectMapper,
                            client
                    );

            case "sync-new-tips" ->
                    runSyncNewTips(
                            options,
                            config,
                            database,
                            objectMapper,
                            client
                    );

            case "repair-posts" ->
                    runRepairPosts(
                            options,
                            database,
                            objectMapper,
                            client
                    );

            default ->
                    throw new IllegalArgumentException(
                            "Nieznana komenda: "
                                    + command
                    );
        }
    }

    private static void runBackfill(
            Map<String, String> options,
            DateRange dateRange,
            Database database,
            ObjectMapper objectMapper,
            ZagranieClient client
    ) {
        long authorId =
                requiredLong(
                        options,
                        "author-id"
                );

        String authorName =
                options.getOrDefault(
                        "author-name",
                        "author-" + authorId
                );

        ImportRepository repository =
                new ImportRepository(
                        database,
                        objectMapper
                );

        ZagraniePostParser parser =
                new ZagraniePostParser();

        HistoricalBackfillService service =
                new HistoricalBackfillService(
                        client,
                        parser,
                        repository
                );

        System.out.println(
                "Zagranie Typer — backfill"
        );

        System.out.println(
                "authorId="
                        + authorId
                        + ", authorName="
                        + authorName
        );

        System.out.println(
                "from="
                        + dateRange.fromInclusive()
                        + ", toExclusive="
                        + dateRange.toExclusive()
        );

        HistoricalBackfillService.BackfillResult result =
                service.run(
                        authorId,
                        authorName,
                        dateRange.fromInclusive(),
                        dateRange.toExclusive()
                );

        System.out.println();
        System.out.println("DONE");

        System.out.println(
                "runId="
                        + result.runId()
        );

        System.out.println(
                "pages="
                        + result.pagesFetched()
        );

        System.out.println(
                "posts="
                        + result.postsSaved()
        );

        System.out.println(
                "bets="
                        + result.betsSaved()
        );

        System.out.println(
                "legs="
                        + result.legsSaved()
        );
    }

    private static void runAuthorDiscovery(
            DateRange dateRange,
            Database database,
            ZagranieClient client
    ) {
        EditorialTipDetector tipDetector =
                new EditorialTipDetector();

        AuthorIdentityParser identityParser =
                new AuthorIdentityParser();

        AuthorRepository authorRepository =
                new AuthorRepository(
                        database
                );

        AuthorDiscoveryService service =
                new AuthorDiscoveryService(
                        client,
                        tipDetector,
                        identityParser,
                        authorRepository
                );

        System.out.println(
                "Zagranie Typer — author discovery"
        );

        System.out.println(
                "from="
                        + dateRange.fromInclusive()
                        + ", toExclusive="
                        + dateRange.toExclusive()
        );

        AuthorDiscoveryService.DiscoveryResult result =
                service.discover(
                        dateRange.fromInclusive(),
                        dateRange.toExclusive()
                );

        System.out.println();
        System.out.println("DONE");

        System.out.println(
                "pages="
                        + result.pagesFetched()
        );

        System.out.println(
                "postsScanned="
                        + result.postsScanned()
        );

        System.out.println(
                "editorialPosts="
                        + result.editorialPosts()
        );

        System.out.println(
                "editorialLegs="
                        + result.editorialLegs()
        );

        System.out.println(
                "authors="
                        + result.authors().size()
        );

        System.out.println();

        for (
                DiscoveredAuthor author :
                result.authors()
        ) {
            System.out.println(
                    author.authorId()
                            + " | "
                            + author.displayName()
                            + " | "
                            + (
                            author.slug() == null
                                    ? "-"
                                    : author.slug()
                    )
                            + " | posts="
                            + author.editorialPosts()
                            + " | legs="
                            + author.editorialLegs()
            );
        }
    }

    private static void runBackfillAll(
            DateRange dateRange,
            Database database,
            ObjectMapper objectMapper,
            ZagranieClient client
    ) {
        ImportRepository importRepository =
                new ImportRepository(
                        database,
                        objectMapper
                );

        AuthorRepository authorRepository =
                new AuthorRepository(
                        database
                );

        ZagraniePostParser parser =
                new ZagraniePostParser();

        AllAuthorsBackfillService service =
                new AllAuthorsBackfillService(
                        client,
                        parser,
                        importRepository,
                        authorRepository
                );

        System.out.println(
                "Zagranie Typer — backfill ALL"
        );

        System.out.println(
                "from="
                        + dateRange.fromInclusive()
                        + ", toExclusive="
                        + dateRange.toExclusive()
        );

        AllAuthorsBackfillService.BackfillAllResult result =
                service.run(
                        dateRange.fromInclusive(),
                        dateRange.toExclusive()
                );

        System.out.println();
        System.out.println("DONE");

        System.out.println(
                "pages="
                        + result.pagesFetched()
        );

        System.out.println(
                "postsScanned="
                        + result.postsScanned()
        );

        System.out.println(
                "postsSaved="
                        + result.totalPostsSaved()
        );

        System.out.println(
                "betsSaved="
                        + result.totalBetsSaved()
        );

        System.out.println(
                "legsSaved="
                        + result.totalLegsSaved()
        );

        System.out.println();

        for (
                AllAuthorsBackfillService.AuthorBackfillResult author :
                result.authors()
        ) {
            System.out.println(
                    author.authorId()
                            + " | "
                            + author.displayName()
                            + " | posts="
                            + author.postsSaved()
                            + " | bets="
                            + author.betsSaved()
                            + " | legs="
                            + author.legsSaved()
            );
        }
    }

    private static void runSyncNewTips(
            Map<String, String> options,
            AppConfig config,
            Database database,
            ObjectMapper objectMapper,
            ZagranieClient client
    ) {
        boolean dryRun =
                optionalBoolean(
                        options,
                        "dry-run",
                        false
                );

        ImportRepository importRepository =
                new ImportRepository(
                        database,
                        objectMapper
                );

        ZagraniePostParser parser =
                new ZagraniePostParser();

        AllowedAuthors allowedAuthors =
                new AllowedAuthors(
                        config.allowedAuthorIds()
                );

        NewTipsPollingService service =
                new NewTipsPollingService(
                        client,
                        parser,
                        importRepository,
                        allowedAuthors,
                        Duration.ofHours(
                                config.pollBootstrapLookbackHours()
                        ),
                        Duration.ofHours(
                                config.pollRecentScanHours()
                        ),
                        Duration.ofSeconds(
                                config.pollOverlapSeconds()
                        )
                );

        System.out.println(
                "Zagranie Typer — sync new tips"
        );

        System.out.println(
                "mode="
                        + (
                        dryRun
                                ? "DRY_RUN"
                                : "LIVE"
                )
        );

        System.out.println(
                "allowedAuthors="
                        + allowedAuthors.ids()
        );

        NewTipsPollingService.SyncResult result =
                service.run(
                        dryRun
                );

        System.out.println();
        System.out.println("DONE");
        System.out.println(
                "authors="
                        + result.authorsScanned()
        );
        System.out.println(
                "pages="
                        + result.pagesFetched()
        );
        System.out.println(
                "postsSeen="
                        + result.postsSeen()
        );
        System.out.println(
                (
                        dryRun
                                ? "postsWouldProcess="
                                : "postsProcessed="
                )
                        + result.postsProcessed()
        );
        System.out.println(
                "postsSkippedUnchanged="
                        + result.postsSkippedUnchanged()
        );
        System.out.println(
                (
                        dryRun
                                ? "wouldCreateNewBets="
                                : "newBets="
                )
                        + result.newBets().size()
        );

        System.out.println(
                "DATABASE_WRITES="
                        + (
                        dryRun
                                ? 0
                                : "ENABLED"
                )
        );

        boolean telegramEnabled =
                config.telegramEnabled()
                        && !dryRun;

        System.out.println(
                "TELEGRAM="
                        + (
                        telegramEnabled
                                ? "ENABLED"
                                : "DISABLED"
                )
        );

        TelegramNotifier telegramNotifier =
                telegramEnabled
                        ? new TelegramNotifier(
                                config.telegramBotToken(),
                                config.telegramChatId(),
                                objectMapper,
                                config.httpTimeoutSeconds()
                        )
                        : null;

        for (
                NewTipsPollingService.DetectedBet detectedBet :
                result.newBets()
        ) {
            var bet =
                    detectedBet.bet();

            System.out.println();
            System.out.println(
                    (
                            dryRun
                                    ? "WOULD CREATE BET"
                                    : "NEW BET"
                    )
                            + " | post="
                            + detectedBet.wpPostId()
                            + " | ordinal="
                            + bet.ordinal()
                            + " | type="
                            + bet.type()
                            + " | odds="
                            + bet.displayedOdds()
                            + " | "
                            + detectedBet.articleTitle()
            );

            System.out.println(
                    detectedBet.articleUrl()
            );

            for (
                    var leg :
                    bet.legs()
            ) {
                System.out.println(
                        "  - "
                                + leg.tipTitle()
                                + " @"
                                + leg.tipOdds()
                                + " | operator="
                                + leg.operator()
                );
            }

            if (
                    telegramNotifier != null
                            && (
                            !detectedBet.bootstrap()
                                    || config.telegramNotifyBootstrap()
                    )
            ) {
                telegramNotifier.send(
                        TelegramTipMessageFormatter.format(
                                config.telegramTipsterName(),
                                detectedBet
                        )
                );

            } else if (
                    telegramNotifier != null
                            && detectedBet.bootstrap()
            ) {
                System.out.println(
                        "TELEGRAM SKIP BOOTSTRAP"
                                + " | post="
                                + detectedBet.wpPostId()
                                + " | ordinal="
                                + bet.ordinal()
                );
            }
        }
    }

    private static void runRepairPosts(
            Map<String, String> options,
            Database database,
            ObjectMapper objectMapper,
            ZagranieClient client
    ) {
        List<Long> postIds =
                requiredLongList(
                        options,
                        "ids"
                );

        ImportRepository importRepository =
                new ImportRepository(
                        database,
                        objectMapper
                );

        ZagraniePostParser parser =
                new ZagraniePostParser();

        RepairPostsService service =
                new RepairPostsService(
                        client,
                        parser,
                        importRepository
                );

        System.out.println(
                "Zagranie Typer — repair posts"
        );

        System.out.println(
                "ids="
                        + postIds
        );

        RepairPostsService.RepairResult result =
                service.repair(
                        postIds
                );

        System.out.println();
        System.out.println("DONE");

        System.out.println(
                "posts="
                        + result.postsSaved()
        );

        System.out.println(
                "bets="
                        + result.betsSaved()
        );

        System.out.println(
                "legs="
                        + result.legsSaved()
        );

        System.out.println();

        for (
                RepairPostsService.RepairedPost post :
                result.posts()
        ) {
            System.out.println(
                    post.wpPostId()
                            + " | bets="
                            + post.betsSaved()
                            + " | legs="
                            + post.legsSaved()
            );
        }
    }

    private static DateRange resolveDateRange(
            Map<String, String> options
    ) {
        LocalDate today =
                LocalDate.now(
                        WARSAW
                );

        if (
                options.containsKey(
                        "from"
                )
                        || options.containsKey(
                        "to"
                )
        ) {
            if (
                    !options.containsKey(
                            "from"
                    )
                            || !options.containsKey(
                            "to"
                    )
            ) {
                throw new IllegalArgumentException(
                        "Przy zakresie dat podaj jednocześnie "
                                + "--from i --to"
                );
            }

            LocalDate from =
                    LocalDate.parse(
                            options.get(
                                    "from"
                            )
                    );

            LocalDate toInclusive =
                    LocalDate.parse(
                            options.get(
                                    "to"
                            )
                    );

            if (
                    toInclusive.isBefore(
                            from
                    )
            ) {
                throw new IllegalArgumentException(
                        "--to nie może być przed --from"
                );
            }

            return new DateRange(
                    from
                            .atStartOfDay(
                                    WARSAW
                            )
                            .toInstant(),

                    toInclusive
                            .plusDays(1)
                            .atStartOfDay(
                                    WARSAW
                            )
                            .toInstant()
            );
        }

        int days =
                Integer.parseInt(
                        options.getOrDefault(
                                "days",
                                "730"
                        )
                );

        if (days <= 0) {
            throw new IllegalArgumentException(
                    "--days musi być > 0"
            );
        }

        return new DateRange(
                today
                        .minusDays(
                                days
                        )
                        .atStartOfDay(
                                WARSAW
                        )
                        .toInstant(),

                today
                        .plusDays(1)
                        .atStartOfDay(
                                WARSAW
                        )
                        .toInstant()
        );
    }

    private static Map<String, String> parseOptions(
            String[] args
    ) {
        Map<String, String> result =
                new HashMap<>();

        for (
                int i = 1;
                i < args.length;
                i++
        ) {
            String arg =
                    args[i];

            if (
                    !arg.startsWith(
                            "--"
                    )
                            || !arg.contains(
                            "="
                    )
            ) {
                throw new IllegalArgumentException(
                        "Opcja musi mieć format "
                                + "--key=value: "
                                + arg
                );
            }

            int equals =
                    arg.indexOf('=');

            result.put(
                    arg.substring(
                            2,
                            equals
                    ),
                    arg.substring(
                            equals + 1
                    )
            );
        }

        return result;
    }

    private static long requiredLong(
            Map<String, String> options,
            String key
    ) {
        String value =
                options.get(
                        key
                );

        if (
                value == null
                        || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Brak wymaganej opcji --"
                            + key
                            + "=..."
            );
        }

        return Long.parseLong(
                value
        );
    }

    static boolean shouldInitializeSchema(
            String command,
            Map<String, String> options
    ) {
        return !(
                "sync-new-tips".equalsIgnoreCase(
                        command
                )
                        && optionalBoolean(
                        options,
                        "dry-run",
                        false
                )
        );
    }

    private static boolean optionalBoolean(
            Map<String, String> options,
            String key,
            boolean defaultValue
    ) {
        String value =
                options.get(
                        key
                );

        if (
                value == null
                        || value.isBlank()
        ) {
            return defaultValue;
        }

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
                "Opcja --"
                        + key
                        + " musi mieć wartość true albo false"
        );
    }

    private static List<Long> requiredLongList(
            Map<String, String> options,
            String key
    ) {
        String value =
                options.get(
                        key
                );

        if (
                value == null
                        || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Brak wymaganej opcji --"
                            + key
                            + "=..."
            );
        }

        List<Long> result =
                new ArrayList<>();

        for (
                String part :
                value.split(",")
        ) {
            String trimmed =
                    part.trim();

            if (
                    trimmed.isBlank()
            ) {
                continue;
            }

            result.add(
                    Long.parseLong(
                            trimmed
                    )
            );
        }

        if (
                result.isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "Opcja --"
                            + key
                            + " nie zawiera żadnego ID"
            );
        }

        return List.copyOf(
                result
        );
    }

    private static void printUsage() {
        System.out.println("""
                Zagranie Typer

                Discovery typerów:
                  java -jar target/zagranie-typer-0.1.0-SNAPSHOT.jar discover-authors \\
                    --days=90

                Backfill wszystkich discovered typerów:
                  java -jar target/zagranie-typer-0.1.0-SNAPSHOT.jar backfill-all \\
                    --days=90

                Backfill pojedynczego autora:
                  java -jar target/zagranie-typer-0.1.0-SNAPSHOT.jar backfill \\
                    --author-id=8560 \\
                    --author-name="Patryk Domagala" \\
                    --days=730

                Bezpieczny dry-run live syncu, bez zapisów do DB:
                  java -jar target/zagranie-typer-0.1.0-SNAPSHOT.jar sync-new-tips \
                    --dry-run=true

                Jednorazowy live sync nowych/zmodyfikowanych typów z whitelisty:
                  java -jar target/zagranie-typer-0.1.0-SNAPSHOT.jar sync-new-tips

                Naprawa konkretnych postów bez pełnego backfillu:
                  java -cp target/zagranie-typer-0.1.0-SNAPSHOT.jar \\
                    pl.zagranietyper.Main repair-posts \\
                    --ids=563540,587058
                """);
    }

    private record DateRange(
            Instant fromInclusive,
            Instant toExclusive
    ) {
    }
}