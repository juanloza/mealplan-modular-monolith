package com.example.mealplan.planning.application;

import com.example.mealplan.catalog.api.RecipeId;
import com.example.mealplan.planning.api.PlanEntryStatus;
import com.example.mealplan.planning.api.PlanEntryId;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A plan entry with the title of its recipe resolved.
 *
 * <p>The title is not stored with the entry: it is asked of the catalogue on every read, without
 * checking the status of the recipe, so that an entry whose recipe was archived afterwards can
 * still be seen and cancelled.
 */
public record PlanEntryView(PlanEntryId id, RecipeId recipeId, String recipeTitle,
                            LocalDate plannedFor, int servings, PlanEntryStatus status,
                            Instant createdAt, Instant updatedAt,
                            Instant cookedAt, Instant cancelledAt) {
}
