package pl.zagranietyper.model;

public record FootballScore(
        int home,
        int away
) {

    public FootballScore {
        if (
                home < 0
                        || away < 0
        ) {
            throw new IllegalArgumentException(
                    "Wynik meczu nie może być ujemny"
            );
        }
    }

    public int totalGoals() {
        return home + away;
    }

    public boolean homeWin() {
        return home > away;
    }

    public boolean awayWin() {
        return away > home;
    }

    public boolean draw() {
        return home == away;
    }

    public boolean bothTeamsScored() {
        return home > 0
                && away > 0;
    }
}