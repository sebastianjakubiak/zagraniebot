package pl.zagranietyper.model;

import java.math.BigDecimal;
import java.util.Objects;

/** A parser-independent full-match condition over one canonical fixture statistic. */
public record FootballFixtureStatisticCondition(
        FootballFixtureStatisticType type,
        Subject subject,
        Comparison comparison,
        BigDecimal threshold,
        BigDecimal rangeMaximum
) {
    public FootballFixtureStatisticCondition {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(comparison, "comparison");
        Objects.requireNonNull(threshold, "threshold");
        if (threshold.signum() < 0) throw new IllegalArgumentException("threshold cannot be negative");
        if (comparison == Comparison.INCLUSIVE_RANGE) {
            if (rangeMaximum == null || rangeMaximum.compareTo(threshold) < 0) {
                throw new IllegalArgumentException("invalid inclusive range");
            }
        } else if (rangeMaximum != null) {
            throw new IllegalArgumentException("rangeMaximum is only valid for an inclusive range");
        }
    }

    public static FootballFixtureStatisticCondition threshold(
            FootballFixtureStatisticType type, Subject subject, Comparison comparison,
            BigDecimal threshold) {
        return new FootballFixtureStatisticCondition(type, subject, comparison, threshold, null);
    }

    public static FootballFixtureStatisticCondition range(
            FootballFixtureStatisticType type, Subject subject,
            BigDecimal minimum, BigDecimal maximum) {
        return new FootballFixtureStatisticCondition(
                type, subject, Comparison.INCLUSIVE_RANGE, minimum, maximum);
    }

    public enum Subject { MATCH, HOME, AWAY }
    public enum Comparison { OVER, UNDER, MINIMUM, INCLUSIVE_RANGE }
}
