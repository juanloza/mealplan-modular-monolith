package com.example.mealplan.catalog.web.dto;

import com.example.mealplan.catalog.application.RecipeDetail;
import com.example.mealplan.shared.web.QuantityDto;
import java.util.UUID;

/**
 * On the way out the amount is always in the canonical unit of its dimension, whatever unit the
 * caller wrote it in.
 */
public record RecipeLineResponse(UUID ingredientId, String ingredientName, QuantityDto quantity) {

    public static RecipeLineResponse of(RecipeDetail.LineDetail line) {
        return new RecipeLineResponse(line.ingredientId().value(), line.ingredientName(),
                QuantityDto.canonicalOf(line.quantity()));
    }
}
