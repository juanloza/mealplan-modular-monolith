package com.example.mealplan.catalog.application;

import com.example.mealplan.catalog.api.IngredientAmount;
import com.example.mealplan.catalog.api.IngredientId;
import com.example.mealplan.catalog.api.RecipeCatalog;
import com.example.mealplan.catalog.api.RecipeId;
import com.example.mealplan.catalog.api.RecipeStatus;
import com.example.mealplan.catalog.api.RecipeSummary;
import com.example.mealplan.catalog.domain.Ingredient;
import com.example.mealplan.catalog.domain.LineSpec;
import com.example.mealplan.catalog.domain.Recipe;
import com.example.mealplan.catalog.domain.RecipeLine;
import com.example.mealplan.catalog.infrastructure.IngredientJpaRepository;
import com.example.mealplan.catalog.infrastructure.RecipeJpaRepository;
import com.example.mealplan.shared.domain.DomainException;
import com.example.mealplan.shared.domain.ErrorCode;
import com.example.mealplan.shared.domain.Quantity;
import com.example.mealplan.shared.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recipes: the operations of the API, and the contract the other modules see.
 *
 * <p>What this class decides is what needs the ingredient table: whether an ingredient exists and
 * belongs to the caller, and whether the unit sent is of its dimension. Everything that can be
 * decided by looking at the recipe alone, the state machine included, belongs to the entity.
 */
@Service
@Transactional
public class RecipeService implements RecipeCatalog {

    private final RecipeJpaRepository recipes;
    private final IngredientJpaRepository ingredients;
    private final Clock clock;

    public RecipeService(RecipeJpaRepository recipes,
                         IngredientJpaRepository ingredients,
                         Clock clock) {
        this.recipes = recipes;
        this.ingredients = ingredients;
        this.clock = clock;
    }

    /**
     * @throws DomainException {@code INGREDIENT_NOT_FOUND}, {@code UNIT_DIMENSION_MISMATCH},
     *                         {@code AMOUNT_NOT_POSITIVE}, {@code AMOUNT_OUT_OF_RANGE},
     *                         {@code DUPLICATE_RECIPE_LINE}
     */
    public RecipeDetail create(UserId owner, SaveRecipeCommand command) {
        Instant now = clock.instant();
        Map<IngredientId, Ingredient> byId = requireIngredients(owner, command);

        Recipe recipe = new Recipe(owner, command.title(), command.servings(), now);
        recipe.replaceContent(command.title(), command.servings(), toSpecs(command, byId), now);

        return toDetail(recipes.save(recipe), byId);
    }

    /**
     * @param statusFilter null for all three states
     */
    @Transactional(readOnly = true)
    public List<RecipeDetail> list(UserId owner, RecipeStatus statusFilter) {
        List<Recipe> found = statusFilter == null
                ? recipes.findAllWithLinesByOwner(owner.value())
                : recipes.findAllWithLinesByOwnerAndStatus(owner.value(), statusFilter);

        Map<IngredientId, Ingredient> byId = ingredientsOf(owner, found);
        return found.stream()
                .sorted(Comparator.comparing(Recipe::title, String.CASE_INSENSITIVE_ORDER))
                .map(recipe -> toDetail(recipe, byId))
                .toList();
    }

    /**
     * @throws DomainException {@code RECIPE_NOT_FOUND}, which is also the answer for a recipe of
     *                         somebody else
     */
    @Transactional(readOnly = true)
    public RecipeDetail get(UserId owner, RecipeId id) {
        Recipe recipe = requireWithLines(owner, id);
        return toDetail(recipe, ingredientsOf(owner, List.of(recipe)));
    }

    /**
     * A complete replacement: the lines that do not come in the body disappear.
     *
     * @throws DomainException {@code RECIPE_NOT_FOUND}, {@code RECIPE_NOT_EDITABLE}, and the same
     *                         line errors as {@link #create}
     */
    public RecipeDetail replace(UserId owner, RecipeId id, SaveRecipeCommand command) {
        Recipe recipe = requireWithLines(owner, id);
        Map<IngredientId, Ingredient> byId = requireIngredients(owner, command);

        recipe.replaceContent(command.title(), command.servings(), toSpecs(command, byId), clock.instant());
        return toDetail(recipe, byId);
    }

    /**
     * @throws DomainException {@code RECIPE_NOT_FOUND}, {@code INVALID_RECIPE_TRANSITION},
     *                         {@code RECIPE_HAS_NO_LINES}
     */
    public RecipeDetail publish(UserId owner, RecipeId id) {
        Recipe recipe = requireWithLines(owner, id);
        recipe.publish(clock.instant());
        return toDetail(recipe, ingredientsOf(owner, List.of(recipe)));
    }

    /**
     * Archiving blocks the pending plan entries that use the recipe: cooking them fails, while
     * seeing and cancelling them keeps working. Cancelling them automatically would mean this
     * module writing into planning, which would close a cycle between modules.
     *
     * @throws DomainException {@code RECIPE_NOT_FOUND}, {@code INVALID_RECIPE_TRANSITION}
     */
    public RecipeDetail archive(UserId owner, RecipeId id) {
        Recipe recipe = requireWithLines(owner, id);
        recipe.archive(clock.instant());
        return toDetail(recipe, ingredientsOf(owner, List.of(recipe)));
    }

    /**
     * @throws DomainException {@code RECIPE_NOT_FOUND}, {@code RECIPE_NOT_DELETABLE}
     */
    public void delete(UserId owner, RecipeId id) {
        Recipe recipe = requireWithLines(owner, id);
        recipe.requireDeletable();
        recipes.delete(recipe);
    }

    @Override
    @Transactional(readOnly = true)
    public RecipeSummary requirePlannable(UserId owner, RecipeId recipeId) {
        Recipe recipe = recipes.findByIdAndOwnerId(recipeId.value(), owner.value())
                .orElseThrow(RecipeService::recipeNotFound);
        requirePublished(recipe);
        return recipe.toSummary();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<RecipeId, RecipeSummary> findAllById(UserId owner, Collection<RecipeId> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Set<UUID> rawIds = ids.stream().map(RecipeId::value).collect(Collectors.toSet());

        Map<RecipeId, RecipeSummary> found = new LinkedHashMap<>();
        for (Recipe recipe : recipes.findByIdInAndOwnerId(rawIds, owner.value())) {
            found.put(recipe.id(), recipe.toSummary());
        }
        return found;
    }

    /**
     * Deliberately not marked read only, although it writes nothing: the cook operation calls it
     * inside a write transaction with {@code REQUIRED} propagation, where the attribute would be
     * ignored anyway. Declaring it would advertise a guarantee its real use does not have.
     */
    @Override
    public List<IngredientAmount> consumptionFor(UserId owner, RecipeId recipeId, int servings) {
        Recipe recipe = requireWithLines(owner, recipeId);
        requirePublished(recipe);

        Map<IngredientId, Ingredient> byId = ingredientsOf(owner, List.of(recipe));
        List<IngredientAmount> consumption = new ArrayList<>();

        for (RecipeLine line : recipe.lines()) {
            Ingredient ingredient = requireLoaded(byId, line);

            // The only place where anything is scaled, and rounding happens once per line: the
            // rounded number is the very number that gets subtracted from a pantry row.
            Quantity amount = Quantity.ofMilli(line.amountMilli(), ingredient.dimension())
                    .scaledTo(recipe.servings(), servings);

            consumption.add(new IngredientAmount(line.ingredientId(), ingredient.name(), amount));
        }
        return consumption;
    }

    private Recipe requireWithLines(UserId owner, RecipeId id) {
        return recipes.findWithLinesByIdAndOwnerId(id.value(), owner.value())
                .orElseThrow(RecipeService::recipeNotFound);
    }

    /**
     * Resolves every ingredient of the command in a single query, and refuses the whole request if
     * any of them is missing or belongs to somebody else.
     */
    private Map<IngredientId, Ingredient> requireIngredients(UserId owner, SaveRecipeCommand command) {
        Set<UUID> rawIds = command.lines().stream()
                .map(line -> line.ingredientId().value())
                .collect(Collectors.toSet());

        Map<IngredientId, Ingredient> byId = new LinkedHashMap<>();
        if (!rawIds.isEmpty()) {
            for (Ingredient ingredient : ingredients.findByOwnerIdAndIdIn(owner.value(), rawIds)) {
                byId.put(ingredient.id(), ingredient);
            }
        }
        for (SaveRecipeCommand.LineCommand line : command.lines()) {
            if (!byId.containsKey(line.ingredientId())) {
                throw new DomainException(ErrorCode.INGREDIENT_NOT_FOUND,
                        "A line refers to an ingredient that does not exist.");
            }
        }
        return byId;
    }

    /** The ingredients of every line of the given recipes, in one query. */
    private Map<IngredientId, Ingredient> ingredientsOf(UserId owner, List<Recipe> found) {
        Set<UUID> rawIds = found.stream()
                .flatMap(recipe -> recipe.lines().stream())
                .map(line -> line.ingredientId().value())
                .collect(Collectors.toSet());

        if (rawIds.isEmpty()) {
            return Map.of();
        }
        Map<IngredientId, Ingredient> byId = new LinkedHashMap<>();
        for (Ingredient ingredient : ingredients.findByOwnerIdAndIdIn(owner.value(), rawIds)) {
            byId.put(ingredient.id(), ingredient);
        }
        return byId;
    }

    private static List<LineSpec> toSpecs(SaveRecipeCommand command, Map<IngredientId, Ingredient> byId) {
        List<LineSpec> specs = new ArrayList<>();
        for (SaveRecipeCommand.LineCommand line : command.lines()) {
            Ingredient ingredient = byId.get(line.ingredientId());

            if (line.unit().dimension() != ingredient.dimension()) {
                throw new DomainException(ErrorCode.UNIT_DIMENSION_MISMATCH,
                        "That unit does not measure what this ingredient is measured in.",
                        Map.of("expectedDimension", ingredient.dimension().name(),
                               "actualDimension", line.unit().dimension().name()));
            }
            specs.add(new LineSpec(line.ingredientId(), Quantity.of(line.amount(), line.unit())));
        }
        return specs;
    }

    private static RecipeDetail toDetail(Recipe recipe, Map<IngredientId, Ingredient> byId) {
        List<RecipeDetail.LineDetail> lines = new ArrayList<>();
        for (RecipeLine line : recipe.lines()) {
            Ingredient ingredient = requireLoaded(byId, line);
            lines.add(new RecipeDetail.LineDetail(line.ingredientId(), ingredient.name(),
                    Quantity.ofMilli(line.amountMilli(), ingredient.dimension())));
        }
        return new RecipeDetail(recipe.id(), recipe.title(), recipe.servings(), recipe.status(),
                lines, recipe.createdAt(), recipe.updatedAt(),
                recipe.publishedAt(), recipe.archivedAt());
    }

    /**
     * A line whose ingredient no longer exists is impossible while the foreign key is
     * {@code RESTRICT}, so it is treated as what it would be: a bug, and a 500 with no detail. No
     * business case is invented for something the database does not allow.
     */
    private static Ingredient requireLoaded(Map<IngredientId, Ingredient> byId, RecipeLine line) {
        Ingredient ingredient = byId.get(line.ingredientId());
        if (ingredient == null) {
            throw new IllegalStateException(
                    "Recipe line refers to an ingredient that is not in the catalogue: " + line.ingredientId());
        }
        return ingredient;
    }

    private static void requirePublished(Recipe recipe) {
        if (recipe.status() != RecipeStatus.PUBLISHED) {
            throw new DomainException(ErrorCode.RECIPE_NOT_PLANNABLE,
                    "Only a published recipe can be planned or cooked.");
        }
    }

    private static DomainException recipeNotFound() {
        return new DomainException(ErrorCode.RECIPE_NOT_FOUND, "No such recipe.");
    }
}
