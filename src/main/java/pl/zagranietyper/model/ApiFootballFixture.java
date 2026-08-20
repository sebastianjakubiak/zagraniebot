package pl.zagranietyper.model;

import java.time.Instant;
import java.time.LocalDate;

public record ApiFootballFixture(
        long fixtureId,

        Instant kickoffAt,
        LocalDate fixtureDate,

        Long leagueId,
        String leagueName,
        String leagueCountry,

        Integer season,
        String round,

        Long homeTeamId,
        String homeTeamName,

        Long awayTeamId,
        String awayTeamName,

        Integer goalsHome,
        Integer goalsAway,

        String statusShort,
        String statusLong,

        String rawJson
) {
}