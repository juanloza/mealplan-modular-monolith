package com.example.mealplan.catalog.web.dto;

import com.example.mealplan.catalog.api.RecipeStatus;
import com.example.mealplan.catalog.application.RecipeDetail;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Fields that may be absent are sent as null rather than omitted, so the shape of the response does
 * not change with the state of the recipe.
 */
public record RecipeResponse(UUID id, String title, int servings, RecipeStatus status,
                             List<RecipeLineResponse> lines,
                             Instant createdAt, Instant updatedAt,
                             Instant publishedAt, Instant archivedAt) {

    public static RecipeResponse of(RecipeDetail detail) {
        return new RecipeResponse(
                detail.id().value(), detail.title(), detail.servings(), detail.status(),
                detail.lines().stream().map(RecipeLineResponse::of).toList(),
                detail.createdAt(), detail.updatedAt(), detail.publishedAt(), detail.archivedAt());
    }
}
