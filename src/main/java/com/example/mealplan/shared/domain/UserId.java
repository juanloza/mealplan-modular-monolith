package com.example.mealplan.shared.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * The owner of every resource in every module.
 *
 * <p>It lives here and not in the authentication module so that no module has to depend on that
 * module just to name the owner of its own rows.
 */
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "value must not be null");
    }

    /**
     * @param raw the identifier as text
     * @return the parsed identifier
     * @throws IllegalArgumentException if {@code raw} is not a valid UUID
     */
    public static UserId of(String raw) {
        return new UserId(UUID.fromString(raw));
    }
}
