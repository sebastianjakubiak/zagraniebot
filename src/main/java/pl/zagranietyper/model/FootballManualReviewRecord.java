package pl.zagranietyper.model;

import java.time.LocalDate;

public record FootballManualReviewRecord(
        long legId, long betId, long postId, long fixtureId, LocalDate fixtureDate,
        String home, String away, String score, String title, String family,
        String blocker, String evidence, String stateFingerprint) {}
