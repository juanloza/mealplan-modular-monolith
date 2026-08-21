package com.example.mealplan.pantry.application;

import com.example.mealplan.catalog.api.IngredientId;
import com.example.mealplan.shared.domain.Quantity;
import java.time.Instant;

/**
 * A pantry row with the name of its ingredient resolved.
 *
 * <p>It lives in the application layer and not in the public contract: no other module reads the
 * pantry, it only consumes from it.
 */
public record PantryItemView(IngredientId ingredientId, String ingredientName,
                             Quantity amount, Instant updatedAt) {
}
