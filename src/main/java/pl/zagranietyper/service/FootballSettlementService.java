package pl.zagranietyper.service;

import pl.zagranietyper.model.FootballMarket;
import pl.zagranietyper.model.FootballScore;
import pl.zagranietyper.model.SettlementDecision;
import pl.zagranietyper.parser.FootballMarketParser;
import pl.zagranietyper.repository.FootballSettlementRepository;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FootballSettlementService {

    private static final int EXAMPLE_LIMIT =
            20;

    private final FootballSettlementRepository repository;

    private final FootballMarketParser parser;

    private final FootballMarketSettlementEngine engine;

    public FootballSettlementService(
            FootballSettlementRepository repository,
            FootballMarketParser parser,
            FootballMarketSettlementEngine engine
    ) {
        this.repository =
                repository;

        this.parser =
                parser;

        this.engine =
                engine;
    }

    public Result run(
            boolean apply
    ) {
        List<FootballSettlementRepository.Candidate> candidates =
                repository.findPendingApiFootballCandidates();

        int total =
                candidates.size();

        int eligibleFixture =
                0;

        int skippedFixture =
                0;

        int missingFulltime =
                0;

        int parsed =
                0;

        int unsupported =
                0;

        Map<SettlementDecision, Integer> counts =
                new EnumMap<>(
                        SettlementDecision.class
                );

        Map<SettlementDecision, List<String>> examples =
                new EnumMap<>(
                        SettlementDecision.class
                );

        for (
                SettlementDecision decision :
                SettlementDecision.values()
        ) {
            counts.put(
                    decision,
                    0
            );

            examples.put(
                    decision,
                    new ArrayList<>()
            );
        }

        List<String> skippedExamples =
                new ArrayList<>();

        List<String> missingExamples =
                new ArrayList<>();

        List<FootballSettlementRepository.SettlementUpdate> updates =
                new ArrayList<>();

        for (
                FootballSettlementRepository.Candidate candidate :
                candidates
        ) {
            String base =
                    describe(
                            candidate
                    );

            if (
                    !isSettlementEligibleStatus(
                            candidate.statusShort()
                    )
            ) {
                skippedFixture++;

                addExample(
                        skippedExamples,
                        base
                );

                continue;
            }

            eligibleFixture++;

            if (
                    candidate.fulltimeHome() == null
                            || candidate.fulltimeAway() == null
            ) {
                missingFulltime++;

                addExample(
                        missingExamples,
                        base
                );

                continue;
            }

            Optional<FootballMarket> market =
                    parser.parse(
                            candidate.tipTitle(),
                            candidate.homeTeam(),
                            candidate.awayTeam()
                    );

            if (
                    market.isEmpty()
            ) {
                unsupported++;

                increment(
                        counts,
                        SettlementDecision.UNSUPPORTED
                );

                addExample(
                        examples.get(
                                SettlementDecision.UNSUPPORTED
                        ),
                        base
                );

                continue;
            }

            parsed++;

            FootballScore score =
                    new FootballScore(
                            candidate.fulltimeHome(),
                            candidate.fulltimeAway()
                    );

            SettlementDecision decision =
                    engine.settle(
                            market.get(),
                            score
                    );

            if (
                    decision == SettlementDecision.UNSUPPORTED
            ) {
                unsupported++;
            } else {
                updates.add(
                        new FootballSettlementRepository.SettlementUpdate(
                                candidate.legId(),
                                candidate.betId(),
                                decision
                        )
                );
            }

            increment(
                    counts,
                    decision
            );

            addExample(
                    examples.get(
                            decision
                    ),
                    base
                            + System.lineSeparator()
                            + "  parsed="
                            + market.get()
            );
        }

        FootballSettlementRepository.ApplyResult applyResult =
                apply
                        ? repository.apply(
                        updates
                )
                        : FootballSettlementRepository.ApplyResult.empty();

        return new Result(
                apply,

                total,
                eligibleFixture,
                skippedFixture,
                missingFulltime,
                parsed,
                unsupported,

                counts.getOrDefault(
                        SettlementDecision.W,
                        0
                ),

                counts.getOrDefault(
                        SettlementDecision.L,
                        0
                ),

                counts.getOrDefault(
                        SettlementDecision.V,
                        0
                ),

                updates.size(),

                immutableExamples(
                        examples
                ),

                List.copyOf(
                        skippedExamples
                ),

                List.copyOf(
                        missingExamples
                ),

                applyResult
        );
    }

    private static boolean isSettlementEligibleStatus(
            String status
    ) {
        if (
                status == null
        ) {
            return false;
        }

        return switch (
                status
                ) {
            case "FT",
                 "AET",
                 "PEN" ->
                    true;

            default ->
                    false;
        };
    }

    private static void increment(
            Map<SettlementDecision, Integer> counts,
            SettlementDecision decision
    ) {
        counts.compute(
                decision,
                (
                        ignored,
                        previous
                ) ->
                        previous == null
                                ? 1
                                : previous + 1
        );
    }

    private static void addExample(
            List<String> target,
            String value
    ) {
        if (
                target.size()
                        >= EXAMPLE_LIMIT
        ) {
            return;
        }

        target.add(
                value
        );
    }

    private static String describe(
            FootballSettlementRepository.Candidate candidate
    ) {
        String score =
                candidate.fulltimeHome() == null
                        || candidate.fulltimeAway() == null
                        ? "?-?"
                        : candidate.fulltimeHome()
                        + "-"
                        + candidate.fulltimeAway();

        return "leg="
                + candidate.legId()
                + " | bet="
                + candidate.betId()
                + " | wp="
                + candidate.wpPostId()
                + " | fixture="
                + candidate.fixtureId()
                + " | status="
                + candidate.statusShort()
                + " | "
                + candidate.homeTeam()
                + " "
                + score
                + " "
                + candidate.awayTeam()
                + " | tip="
                + candidate.tipTitle();
    }

    private static Map<SettlementDecision, List<String>>
    immutableExamples(
            Map<SettlementDecision, List<String>> source
    ) {
        Map<SettlementDecision, List<String>> result =
                new EnumMap<>(
                        SettlementDecision.class
                );

        for (
                Map.Entry<SettlementDecision, List<String>> entry :
                source.entrySet()
        ) {
            result.put(
                    entry.getKey(),
                    List.copyOf(
                            entry.getValue()
                    )
            );
        }

        return Map.copyOf(
                result
        );
    }

    public record Result(
            boolean applied,

            int candidates,
            int eligibleFixture,
            int skippedFixture,
            int missingFulltime,
            int parsed,
            int unsupported,

            int wins,
            int losses,
            int voids,

            int autoSettleable,

            Map<SettlementDecision, List<String>> examples,

            List<String> skippedExamples,
            List<String> missingExamples,

            FootballSettlementRepository.ApplyResult applyResult
    ) {
    }
}