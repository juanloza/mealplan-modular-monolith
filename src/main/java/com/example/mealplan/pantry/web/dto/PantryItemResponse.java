package com.example.mealplan.pantry.web.dto;

import com.example.mealplan.pantry.application.PantryItemView;
import com.example.mealplan.shared.web.QuantityDto;
import java.time.Instant;
import java.util.UUID;

/** The version of the row is never part of it: it is a persistence concern, not a contract. */
public record PantryItemResponse(UUID ingredientId, String ingredientName,
                                 QuantityDto quantity, Instant updatedAt) {

    public static PantryItemResponse of(PantryItemView view) {
        return new PantryItemResponse(view.ingredientId().value(), view.ingredientName(),
                QuantityDto.canonicalOf(view.amount()), view.updatedAt());
    }
}
