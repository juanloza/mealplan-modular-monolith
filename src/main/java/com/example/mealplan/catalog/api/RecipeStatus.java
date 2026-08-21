package com.example.mealplan.catalog.api;

/**
 * Where a recipe stands in its lifecycle. Only {@link #PUBLISHED} recipes may be planned and
 * cooked; the transitions between these three are the state machine of the module.
 */
public enum RecipeStatus {

    DRAFT,
    PUBLISHED,
    ARCHIVED
}
