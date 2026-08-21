package com.example.mealplan.catalog.api;

import com.example.mealplan.shared.domain.DomainException;
import com.example.mealplan.shared.domain.UserId;
import java.util.Collection;
import java.util.Map;

/** What other modules may ask this one about ingredients. */
public interface IngredientDirectory {

    /**
     * @throws DomainException {@code INGREDIENT_NOT_FOUND} if it does not exist or does not belong
     *                         to {@code owner}, which are deliberately the same answer
     */
    IngredientView require(UserId owner, IngredientId id);

    /**
     * Bulk resolution. Returns only the ones that exist and belong to {@code owner}, and never
     * throws: the caller decides what a missing one means.
     */
    Map<IngredientId, IngredientView> findAllById(UserId owner, Collection<IngredientId> ids);
}
