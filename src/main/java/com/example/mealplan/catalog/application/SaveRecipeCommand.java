package com.example.mealplan.catalog.application;

import com.example.mealplan.catalog.api.IngredientId;
import com.example.mealplan.shared.domain.Unit;
import java.math.BigDecimal;
import java.util.List;

/**
 * What the web layer asks for, still in the units the caller wrote. Turning those into canonical
 * quantities needs the dimension of each ingredient, so it happens in the service.
 */
public record SaveRecipeCommand(String title, int servings, List<LineCommand> lines) {

    public record LineCommand(IngredientId ingredientId, BigDecimal amount, Unit unit) {
    }
}
