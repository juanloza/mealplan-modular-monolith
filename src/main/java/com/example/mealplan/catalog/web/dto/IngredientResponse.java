package com.example.mealplan.catalog.web.dto;

import com.example.mealplan.catalog.api.IngredientView;
import com.example.mealplan.shared.domain.Dimension;
import java.time.Instant;
import java.util.UUID;

/** No owner travels in any response: it is already known by whoever asked. */
public record IngredientResponse(UUID id, String name, Dimension dimension, Instant createdAt) {

    public static IngredientResponse of(IngredientView view) {
        return new IngredientResponse(view.id().value(), view.name(), view.dimension(), view.createdAt());
    }
}
