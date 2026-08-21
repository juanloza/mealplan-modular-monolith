package com.example.mealplan.planning.application;

import com.example.mealplan.catalog.api.IngredientAmount;
import java.util.List;

/**
 * What cooking produced: the entry as it now stands, and exactly what was taken out of the pantry
 * for it. The second half is worth returning because it is the only place those numbers are ever
 * computed, and the caller has no other way to know what its stock was spent on.
 */
public record CookResult(PlanEntryView entry, List<IngredientAmount> consumed) {
}
