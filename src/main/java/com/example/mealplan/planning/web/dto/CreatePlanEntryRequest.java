package com.example.mealplan.planning.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param plannedFor an ISO date with no time and no zone. The past is allowed on purpose, to record
 *                   what was already cooked; the service refuses anything beyond a year either way
 * @param servings   independent of the servings of the recipe, which is what makes the scaling
 *                   worth having
 */
public record CreatePlanEntryRequest(
        @NotNull UUID recipeId,
        @NotNull LocalDate plannedFor,
        @Min(1) @Max(50) int servings) {
}
