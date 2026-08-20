package pl.zagranietyper.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record EventMetadata(
        String externalId,
        String home,
        String away,
        String competition,
        Instant startAt,
        String startRaw,
        Map<String, String> attributes
) {
    public EventMetadata {
        attributes = attributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    public static EventMetadata empty() {
        return new EventMetadata(null, null, null, null, null, null, Map.of());
    }
}
