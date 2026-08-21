package com.example.mealplan.catalog.application;

import com.example.mealplan.catalog.api.IngredientId;
import com.example.mealplan.catalog.api.RecipeId;
import com.example.mealplan.catalog.api.RecipeStatus;
import com.example.mealplan.shared.domain.Quantity;
import java.time.Instant;
import java.util.List;

/**
 * A recipe with its lines and the name of each ingredient resolved.
 *
 * <p>It lives in the application layer and not in the public contract because no other module needs
 * it: outside this one, a recipe is a {@code RecipeSummary} and its consumption is a list of
 * amounts. The owner is not part of it, and neither is anything else no response may expose.
 */
public record RecipeDetail(RecipeId id, String title, int servings, RecipeStatus status,
                           List<LineDetail> lines, Instant createdAt, Instant updatedAt,
                           Instant publishedAt, Instant archivedAt) {

    public record LineDetail(IngredientId ingredientId, String ingredientName, Quantity quantity) {
    }
}
