package com.example.mealplan.catalog.web.dto;

import com.example.mealplan.shared.domain.Dimension;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param dimension fixed here for the whole life of the ingredient: there is no endpoint that
 *                  changes it, because changing it would invalidate every amount already recorded
 */
public record CreateIngredientRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull Dimension dimension) {
}
