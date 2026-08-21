package com.example.mealplan.planning.api;

import java.util.Objects;
import java.util.UUID;

/** How a plan entry is named. */
public record PlanEntryId(UUID value) {

    public PlanEntryId {
        Objects.requireNonNull(value, "value must not be null");
    }

    /**
     * @param raw the identifier as text
     * @return the parsed identifier
     * @throws IllegalArgumentException if {@code raw} is not a valid UUID
     */
    public static PlanEntryId of(String raw) {
        return new PlanEntryId(UUID.fromString(raw));
    }
}
