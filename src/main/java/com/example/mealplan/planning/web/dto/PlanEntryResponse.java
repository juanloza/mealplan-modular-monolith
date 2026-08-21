package com.example.mealplan.planning.web.dto;

import com.example.mealplan.planning.api.PlanEntryStatus;
import com.example.mealplan.planning.application.PlanEntryView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Neither the owner nor the version travels: one is already known, the other is not a contract. */
public record PlanEntryResponse(UUID id, UUID recipeId, String recipeTitle,
                                LocalDate plannedFor, int servings, PlanEntryStatus status,
                                Instant createdAt, Instant updatedAt,
                                Instant cookedAt, Instant cancelledAt) {

    public static PlanEntryResponse of(PlanEntryView view) {
        return new PlanEntryResponse(view.id().value(), view.recipeId().value(), view.recipeTitle(),
                view.plannedFor(), view.servings(), view.status(),
                view.createdAt(), view.updatedAt(), view.cookedAt(), view.cancelledAt());
    }
}
