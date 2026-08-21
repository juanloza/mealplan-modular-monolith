package com.example.mealplan.planning.web.dto;

import com.example.mealplan.catalog.api.IngredientAmount;
import com.example.mealplan.planning.application.CookResult;
import com.example.mealplan.shared.web.QuantityDto;
import java.util.List;
import java.util.UUID;

/**
 * Cooking answers with the entry and with what it cost, because those amounts are computed nowhere
 * else and the caller has no other way to learn what its stock went on.
 */
public record CookResponse(PlanEntryResponse entry, List<ConsumedIngredient> consumed) {

    public static CookResponse of(CookResult result) {
        return new CookResponse(PlanEntryResponse.of(result.entry()),
                result.consumed().stream().map(ConsumedIngredient::of).toList());
    }

    public record ConsumedIngredient(UUID ingredientId, String ingredientName, QuantityDto quantity) {

        static ConsumedIngredient of(IngredientAmount amount) {
            return new ConsumedIngredient(amount.ingredientId().value(), amount.ingredientName(),
                    QuantityDto.canonicalOf(amount.quantity()));
        }
    }
}
