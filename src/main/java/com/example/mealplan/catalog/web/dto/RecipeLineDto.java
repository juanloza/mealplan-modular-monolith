package com.example.mealplan.catalog.web.dto;

import com.example.mealplan.shared.web.QuantityDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** One line of a recipe on the way in, in whatever unit of the right dimension the caller prefers. */
public record RecipeLineDto(
        @NotNull UUID ingredientId,
        @NotNull @Valid QuantityDto quantity) {
}
