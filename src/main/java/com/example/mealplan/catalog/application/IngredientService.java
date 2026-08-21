package com.example.mealplan.catalog.application;

import com.example.mealplan.catalog.api.IngredientDirectory;
import com.example.mealplan.catalog.api.IngredientId;
import com.example.mealplan.catalog.api.IngredientView;
import com.example.mealplan.catalog.domain.Ingredient;
import com.example.mealplan.catalog.infrastructure.IngredientJpaRepository;
import com.example.mealplan.catalog.infrastructure.RecipeJpaRepository;
import com.example.mealplan.shared.domain.Dimension;
import com.example.mealplan.shared.domain.DomainException;
import com.example.mealplan.shared.domain.ErrorCode;
import com.example.mealplan.shared.domain.UserId;
import java.time.Clock;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingredients, and the authorisation that goes with them.
 *
 * <p>Ownership is never checked with a load followed by an {@code if}: every lookup carries the
 * owner into the query, so a resource of somebody else and a resource that does not exist come out
 * of the same branch and answer the same 404.
 *
 * <p>It depends on the recipe repository, of its own module and therefore across no boundary, for
 * one thing only: counting how many recipes use an ingredient that is about to be deleted.
 */
@Service
@Transactional
public class IngredientService implements IngredientDirectory {

    private final IngredientJpaRepository ingredients;
    private final RecipeJpaRepository recipes;
    private final Clock clock;

    public IngredientService(IngredientJpaRepository ingredients,
                             RecipeJpaRepository recipes,
                             Clock clock) {
        this.ingredients = ingredients;
        this.recipes = recipes;
        this.clock = clock;
    }

    /**
     * The pre-check answers cleanly in the ordinary case; the unique index is what actually
     * guarantees uniqueness, because two simultaneous creations both pass the pre-check. Together
     * with the email of an account, this is one of only two rules where the database decides and
     * the domain follows.
     *
     * @throws DomainException {@code INGREDIENT_NAME_TAKEN}
     */
    public IngredientView create(UserId owner, String name, Dimension dimension) {
        String trimmed = name.trim();
        if (ingredients.existsByOwnerIdAndNameIgnoreCase(owner.value(), trimmed)) {
            throw nameTaken();
        }
        try {
            Ingredient created = ingredients.saveAndFlush(
                    new Ingredient(owner, trimmed, dimension, clock.instant()));
            return created.toView();
        } catch (DataIntegrityViolationException ex) {
            throw nameTaken();
        }
    }

    /**
     * Sorted by name ignoring case, which is what the API promises. The final ordering is applied
     * here and not left to the database, so that it does not depend on the collation of the server.
     */
    @Transactional(readOnly = true)
    public List<IngredientView> list(UserId owner) {
        return ingredients.findByOwnerIdOrderByNameAsc(owner.value()).stream()
                .map(Ingredient::toView)
                .sorted(Comparator.comparing(IngredientView::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * @throws DomainException {@code INGREDIENT_NOT_FOUND}, {@code INGREDIENT_NAME_TAKEN} if the new
     *                         name clashes with <em>another</em> ingredient of the same owner
     */
    public IngredientView rename(UserId owner, IngredientId id, String newName) {
        Ingredient ingredient = requireEntity(owner, id);
        String trimmed = newName.trim();

        // Excluding the ingredient itself is what makes renaming it to what it is already called a
        // 200 and not a 409.
        if (ingredients.existsByOwnerIdAndNameIgnoreCaseAndIdNot(owner.value(), trimmed, id.value())) {
            throw nameTaken();
        }
        ingredient.rename(trimmed);
        try {
            ingredients.flush();
        } catch (DataIntegrityViolationException ex) {
            throw nameTaken();
        }
        return ingredient.toView();
    }

    /**
     * Deleting an ingredient takes its stock with it, through the cascade of the foreign key. The
     * check below covers recipe lines only, because this module cannot ask the pantry anything
     * without closing a cycle between modules. The consequence is reasonable: an ingredient that is
     * no longer in the catalogue cannot have any left in store.
     *
     * @throws DomainException {@code INGREDIENT_NOT_FOUND}, {@code INGREDIENT_IN_USE}
     */
    public void delete(UserId owner, IngredientId id) {
        Ingredient ingredient = requireEntity(owner, id);

        long recipeCount = recipes.countRecipesUsingIngredient(owner.value(), id.value());
        if (recipeCount > 0) {
            throw new DomainException(ErrorCode.INGREDIENT_IN_USE,
                    "This ingredient is used by a recipe and cannot be deleted.",
                    Map.of("recipeCount", recipeCount));
        }
        ingredients.delete(ingredient);
    }

    /**
     * There is no {@code get} beside this one on purpose: reading an ingredient and requiring it
     * are the same operation with the same signature and the same error, and two names for one
     * thing eventually drift apart.
     */
    @Override
    @Transactional(readOnly = true)
    public IngredientView require(UserId owner, IngredientId id) {
        return requireEntity(owner, id).toView();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<IngredientId, IngredientView> findAllById(UserId owner, Collection<IngredientId> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Set<UUID> rawIds = ids.stream().map(IngredientId::value).collect(Collectors.toSet());

        Map<IngredientId, IngredientView> found = new LinkedHashMap<>();
        for (Ingredient ingredient : ingredients.findByOwnerIdAndIdIn(owner.value(), rawIds)) {
            found.put(ingredient.id(), ingredient.toView());
        }
        return found;
    }

    private Ingredient requireEntity(UserId owner, IngredientId id) {
        return ingredients.findByIdAndOwnerId(id.value(), owner.value())
                .orElseThrow(() -> new DomainException(ErrorCode.INGREDIENT_NOT_FOUND,
                        "No such ingredient."));
    }

    private static DomainException nameTaken() {
        return new DomainException(ErrorCode.INGREDIENT_NAME_TAKEN,
                "An ingredient with that name already exists.");
    }
}
