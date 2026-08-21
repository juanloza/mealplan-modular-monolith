package com.example.mealplan.catalog.api;

import java.util.Objects;
import java.util.UUID;

/** How an ingredient is named outside this module. */
public record IngredientId(UUID value) {

    public IngredientId {
        Objects.requireNonNull(value, "value must not be null");
    }

    /**
     * @param raw the identifier as text
     * @return the parsed identifier
     * @throws IllegalArgumentException if {@code raw} is not a valid UUID
     */
    public static IngredientId of(String raw) {
        return new IngredientId(UUID.fromString(raw));
    }
}
