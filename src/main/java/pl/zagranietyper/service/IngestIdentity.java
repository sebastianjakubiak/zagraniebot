package pl.zagranietyper.service;

import pl.zagranietyper.model.ParsedBet;
import pl.zagranietyper.model.ParsedLeg;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

public final class IngestIdentity {

    private IngestIdentity() {
    }

    public static String legKey(
            ParsedLeg leg
    ) {
        return legKey(
                leg.operator(),
                leg.tipTitle(),
                leg.event() == null
                        ? null
                        : leg.event().externalId(),
                leg.event() == null
                        ? null
                        : leg.event().home(),
                leg.event() == null
                        ? null
                        : leg.event().away(),
                leg.event() == null
                        ? null
                        : leg.event().startAt(),
                leg.event() == null
                        ? null
                        : leg.event().startRaw()
        );
    }

    public static String legKey(
            String operator,
            String tipTitle,
            String eventExternalId,
            String eventHome,
            String eventAway,
            Instant eventStartAt,
            String eventStartRaw
    ) {
        String eventKey;

        if (
                eventExternalId != null
                        && !eventExternalId.isBlank()
        ) {
            eventKey =
                    "id:"
                            + normalize(
                                    eventExternalId
                            );

        } else {
            eventKey =
                    "event:"
                            + normalize(
                                    eventHome
                            )
                            + ">"
                            + normalize(
                                    eventAway
                            )
                            + "@"
                            + (
                            eventStartAt != null
                                    ? eventStartAt.toString()
                                    : normalize(
                                            eventStartRaw
                                    )
                    );
        }

        return normalize(
                operator
        )
                + "|"
                + normalize(
                        tipTitle
                )
                + "|"
                + eventKey;
    }

    public static String betKey(
            ParsedBet bet
    ) {
        return betKey(
                bet.type().name(),
                bet.legs()
                        .stream()
                        .map(
                                IngestIdentity::legKey
                        )
                        .toList()
        );
    }

    public static String betKey(
            String betType,
            List<String> legKeys
    ) {
        List<String> sorted =
                new ArrayList<>(
                        legKeys == null
                                ? List.of()
                                : legKeys
                );

        sorted.sort(
                String::compareTo
        );

        return normalize(
                betType
        )
                + "|"
                + String.join(
                        "||",
                        sorted
                );
    }

    public static String fingerprint(
            long wpPostId,
            String scope,
            String semanticKey,
            int occurrence
    ) {
        if (
                occurrence <= 0
        ) {
            throw new IllegalArgumentException(
                    "occurrence musi być > 0"
            );
        }

        return sha256(
                wpPostId
                        + "|live|"
                        + normalize(
                                scope
                        )
                        + "|"
                        + semanticKey
                        + "|#"
                        + occurrence
        );
    }

    static String normalize(
            String value
    ) {
        if (
                value == null
        ) {
            return "";
        }

        return Normalizer.normalize(
                        value,
                        Normalizer.Form.NFKC
                )
                .toLowerCase(
                        Locale.ROOT
                )
                .trim()
                .replaceAll(
                        "\\s+",
                        " "
                );
    }

    private static String sha256(
            String value
    ) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            return HexFormat.of()
                    .formatHex(
                            digest.digest(
                                    value.getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            )
                    );

        } catch (
                NoSuchAlgorithmException e
        ) {
            throw new IllegalStateException(
                    "Brak SHA-256",
                    e
            );
        }
    }
}
