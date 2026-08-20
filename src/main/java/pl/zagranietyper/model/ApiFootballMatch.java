package pl.zagranietyper.model;

public record ApiFootballMatch(
        ApiFootballFixture fixture,
        double score,
        ResolutionConfidence confidence,
        String evidence
) {
}