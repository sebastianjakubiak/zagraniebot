package pl.zagranietyper.service;

import java.util.Set;

public final class AllowedAuthors {

    private final Set<Long> ids;

    public AllowedAuthors(
            Set<Long> ids
    ) {
        this.ids =
                ids == null
                        ? Set.of()
                        : Set.copyOf(
                                ids
                        );
    }

    public boolean isAllowed(
            long authorId
    ) {
        return ids.contains(
                authorId
        );
    }

    public Set<Long> ids() {
        return ids;
    }
}
