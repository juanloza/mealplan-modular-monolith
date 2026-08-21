package com.example.mealplan.catalog.domain;

import com.example.mealplan.catalog.api.IngredientId;
import com.example.mealplan.shared.domain.Quantity;

/**
 * The vocabulary the service speaks to {@link Recipe} in: the amount already resolved to a
 * {@link Quantity} of the dimension of its ingredient.
 *
 * <p>It lives in the domain and not in the application layer because putting it there would make
 * the domain depend on the layer above it.
 */
public record LineSpec(IngredientId ingredientId, Quantity quantity) {
}
