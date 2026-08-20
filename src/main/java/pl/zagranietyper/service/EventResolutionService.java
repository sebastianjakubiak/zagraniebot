package pl.zagranietyper.service;

import pl.zagranietyper.model.EventResolutionCandidate;
import pl.zagranietyper.model.ResolutionSource;
import pl.zagranietyper.model.ResolvedEvent;
import pl.zagranietyper.repository.EventResolutionRepository;
import pl.zagranietyper.resolver.EventResolver;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class EventResolutionService {

    private final EventResolutionRepository repository;
    private final EventResolver resolver;

    public EventResolutionService(
            EventResolutionRepository repository,
            EventResolver resolver
    ) {
        this.repository = repository;
        this.resolver = resolver;
    }

    public ResolutionResult run() {

        List<EventResolutionCandidate> candidates =
                repository.findActiveLegs();

        int resolved = 0;
        int unresolved = 0;

        Map<ResolutionSource, Integer> bySource =
                new EnumMap<>(
                        ResolutionSource.class
                );

        for (
                EventResolutionCandidate candidate :
                candidates
        ) {
            ResolvedEvent event =
                    resolver.resolve(
                            candidate
                    );

            repository.saveResolution(
                    candidate.legId(),
                    event
            );

            bySource.merge(
                    event.source(),
                    1,
                    Integer::sum
            );

            if (
                    event.resolved()
            ) {
                resolved++;

            } else {
                unresolved++;
            }
        }

        return new ResolutionResult(
                candidates.size(),
                resolved,
                unresolved,
                Map.copyOf(
                        bySource
                )
        );
    }

    public record ResolutionResult(
            int total,
            int resolved,
            int unresolved,
            Map<ResolutionSource, Integer> bySource
    ) {
    }
}