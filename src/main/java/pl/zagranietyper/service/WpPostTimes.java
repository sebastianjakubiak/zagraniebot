package pl.zagranietyper.service;

import pl.zagranietyper.wp.WpPost;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

final class WpPostTimes {

    private static final ZoneId WARSAW =
            ZoneId.of(
                    "Europe/Warsaw"
            );

    private WpPostTimes() {
    }

    static Instant modifiedAt(
            WpPost post
    ) {
        return parse(
                post.modifiedGmt(),
                post.modified()
        );
    }

    private static Instant parse(
            String gmtValue,
            String localValue
    ) {
        if (
                gmtValue != null
                        && !gmtValue.isBlank()
        ) {
            try {
                return LocalDateTime
                        .parse(
                                gmtValue
                        )
                        .toInstant(
                                ZoneOffset.UTC
                        );

            } catch (
                    DateTimeParseException ignored
            ) {
            }
        }

        if (
                localValue != null
                        && !localValue.isBlank()
        ) {
            try {
                return LocalDateTime
                        .parse(
                                localValue
                        )
                        .atZone(
                                WARSAW
                        )
                        .toInstant();

            } catch (
                    DateTimeParseException ignored
            ) {
            }
        }

        return null;
    }
}
