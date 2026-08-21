package com.example.mealplan.catalog.api;

import com.example.mealplan.shared.domain.Quantity;

/**
 * A concrete amount of a concrete ingredient.
 *
 * <p>The name travels inside so that whoever consumes it can build an error message without asking
 * the catalogue again.
 */
public record IngredientAmount(IngredientId ingredientId, String ingredientName, Quantity quantity) {
}
