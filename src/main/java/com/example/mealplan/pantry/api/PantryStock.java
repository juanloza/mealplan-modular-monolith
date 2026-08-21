package com.example.mealplan.pantry.api;

import com.example.mealplan.catalog.api.IngredientAmount;
import com.example.mealplan.shared.domain.DomainException;
import com.example.mealplan.shared.domain.UserId;
import java.util.List;

/**
 * The only thing this module exposes, because it is the only thing anyone else needs.
 *
 * <p>There is no HTTP endpoint behind it: it is called from cooking, inside the transaction of that
 * operation.
 */
public interface PantryStock {

    /**
     * Subtracts every amount given, or none of them.
     *
     * <p>An ingredient with no pantry row counts as zero available, which is the same as a row that
     * holds zero: one means "I am not keeping track of this" and the other "I have run out", and
     * for cooking they come to the same thing.
     *
     * @throws DomainException {@code INSUFFICIENT_STOCK}, carrying <em>every</em> shortfall in
     *                         {@code details.shortfalls} and not only the first one found. Whoever
     *                         is about to cook wants the whole shopping list at once, not to
     *                         discover it one ingredient per attempt
     */
    void consume(UserId owner, List<IngredientAmount> amounts);
}
