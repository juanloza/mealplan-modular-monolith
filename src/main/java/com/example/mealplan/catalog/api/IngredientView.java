package com.example.mealplan.catalog.api;

import com.example.mealplan.shared.domain.Dimension;
import java.time.Instant;

/** What anyone outside this module may learn about an ingredient. The owner is not part of it. */
public record IngredientView(IngredientId id, String name, Dimension dimension, Instant createdAt) {
}
