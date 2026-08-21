package com.example.mealplan.catalog.api;

/**
 * A recipe without its lines. It is all the other modules need: what they show is the title, and
 * what they decide on is the status.
 */
public record RecipeSummary(RecipeId id, String title, int servings, RecipeStatus status) {
}
