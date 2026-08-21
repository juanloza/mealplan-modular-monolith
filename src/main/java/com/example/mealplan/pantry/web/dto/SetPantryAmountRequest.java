package com.example.mealplan.pantry.web.dto;

import com.example.mealplan.shared.web.QuantityDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * The amount to set, in any unit of the dimension of the ingredient. Zero is a valid value and
 * means "I have run out", which is not the same as having no row at all.
 */
public record SetPantryAmountRequest(@NotNull @Valid QuantityDto quantity) {
}
