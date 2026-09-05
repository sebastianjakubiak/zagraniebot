package pl.zagranietyper.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllowedAuthorsTest {

    @Test
    void onlyConfiguredAuthorsAreAllowed() {
        AllowedAuthors allowed =
                new AllowedAuthors(
                        Set.of(
                                8033L
                        )
                );

        assertTrue(
                allowed.isAllowed(
                        8033L
                )
        );

        assertFalse(
                allowed.isAllowed(
                        52L
                )
        );
    }
}
