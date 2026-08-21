package com.example.mealplan.catalog.api;

import java.util.Objects;
import java.util.UUID;

/** How a recipe is named outside this module. */
public record RecipeId(UUID value) {

    public RecipeId {
        Objects.requireNonNull(value, "value must not be null");
    }

    /**
     * @param raw the identifier as text
     * @return the parsed identifier
     * @throws IllegalArgumentException if {@code raw} is not a valid UUID
     */
    public static RecipeId of(String raw) {
        return new RecipeId(UUID.fromString(raw));
    }
}
