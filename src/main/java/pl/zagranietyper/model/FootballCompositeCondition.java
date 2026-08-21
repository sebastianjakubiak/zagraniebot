package pl.zagranietyper.model;

import java.util.List;
import java.util.Objects;

/** An ordered, fully parsed conjunction of existing football primitives. */
public record FootballCompositeCondition(List<Branch> branches) {
    public FootballCompositeCondition {
        Objects.requireNonNull(branches, "branches");
        if (branches.size() < 2) throw new IllegalArgumentException("Composite requires at least two branches");
        branches = List.copyOf(branches);
    }

    public sealed interface Branch permits ScoreBranch, StatisticBranch {}

    public record ScoreBranch(UnifiedFootballMarket market) implements Branch {
        public ScoreBranch { Objects.requireNonNull(market, "market"); }
    }

    public record StatisticBranch(FootballFixtureStatisticCondition condition) implements Branch {
        public StatisticBranch { Objects.requireNonNull(condition, "condition"); }
    }
}
