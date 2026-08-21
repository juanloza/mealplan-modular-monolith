package com.example.mealplan.catalog.api;

import com.example.mealplan.shared.domain.DomainException;
import com.example.mealplan.shared.domain.UserId;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** What other modules may ask this one about recipes. */
public interface RecipeCatalog {

    /**
     * @throws DomainException {@code RECIPE_NOT_FOUND}, {@code RECIPE_NOT_PLANNABLE}
     */
    RecipeSummary requirePlannable(UserId owner, RecipeId recipeId);

    /**
     * Bulk resolution that deliberately does <em>not</em> check the status: it returns drafts and
     * archived recipes too, and omits only what does not exist or does not belong to {@code owner}.
     *
     * <p>Planning needs it to title an entry whose recipe was archived after it was planned, which
     * is exactly the entry that later fails to cook. Were this to validate the status, listing the
     * plan would answer 409 and the user could not even see what to cancel.
     */
    Map<RecipeId, RecipeSummary> findAllById(UserId owner, Collection<RecipeId> ids);

    /**
     * The ingredients consumed by cooking {@code servings} servings of the recipe, already scaled
     * from the servings of the recipe and rounded line by line. The order is that of the recipe
     * lines; whoever needs another order sorts it.
     *
     * @throws DomainException {@code RECIPE_NOT_FOUND}, {@code RECIPE_NOT_PLANNABLE}
     */
    List<IngredientAmount> consumptionFor(UserId owner, RecipeId recipeId, int servings);
}
