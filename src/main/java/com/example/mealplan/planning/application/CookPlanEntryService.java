package com.example.mealplan.planning.application;

import com.example.mealplan.catalog.api.IngredientAmount;
import com.example.mealplan.catalog.api.RecipeCatalog;
import com.example.mealplan.catalog.api.RecipeSummary;
import com.example.mealplan.pantry.api.PantryStock;
import com.example.mealplan.planning.api.PlanEntryId;
import com.example.mealplan.planning.domain.PlanEntry;
import com.example.mealplan.planning.infrastructure.PlanEntryJpaRepository;
import com.example.mealplan.shared.domain.DomainException;
import com.example.mealplan.shared.domain.ErrorCode;
import com.example.mealplan.shared.domain.UserId;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cooking a plan entry: scale the recipe to the planned servings, subtract the result from the
 * pantry, and mark the entry, all in one transaction.
 *
 * <p>It lives apart from the rest of the plan operations because it is the only one that
 * orchestrates two modules, and the only one whose transactional semantics are interesting. This
 * module is the leaf of the dependency graph, which is precisely why the orchestration can live
 * here and nowhere else.
 */
@Service
public class CookPlanEntryService {

    private final PlanEntryJpaRepository entries;
    private final RecipeCatalog recipeCatalog;
    private final PantryStock pantryStock;
    private final Clock clock;

    public CookPlanEntryService(PlanEntryJpaRepository entries,
                                RecipeCatalog recipeCatalog,
                                PantryStock pantryStock,
                                Clock clock) {
        this.entries = entries;
        this.recipeCatalog = recipeCatalog;
        this.pantryStock = pantryStock;
        this.clock = clock;
    }

    /**
     * The order of the steps is the design.
     *
     * <p>Checking the status before touching the catalogue avoids useless work, and marking the
     * entry last means the most likely failure, not enough stock, happens with nothing written yet.
     * Everything runs in one transaction: the pantry joins this one rather than opening its own, so
     * any failure undoes the whole thing and the state "subtracted but not cooked", or the reverse,
     * does not exist.
     *
     * <p>Two races end here, and each has its own guard. The same entry cooked twice at once, or
     * cooked and cancelled at once, is caught by the version of the entry. Two different entries
     * that share an ingredient are caught by the version of the pantry row, which is the only thing
     * standing between a shared ingredient and stock subtracted once for two meals. Both surface as
     * 409 and neither is retried.
     *
     * @throws DomainException {@code PLAN_ENTRY_NOT_FOUND}, {@code PLAN_ENTRY_NOT_PLANNED},
     *                         {@code RECIPE_NOT_FOUND}, {@code RECIPE_NOT_PLANNABLE},
     *                         {@code INSUFFICIENT_STOCK}
     */
    @Transactional
    public CookResult cook(UserId owner, PlanEntryId id) {
        PlanEntry entry = entries.findByIdAndOwnerId(id.value(), owner.value())
                .orElseThrow(() -> new DomainException(ErrorCode.PLAN_ENTRY_NOT_FOUND,
                        "No such plan entry."));

        entry.requirePlanned();

        // Validates the state of the recipe and brings the title the view needs, in one query.
        // consumptionFor validates it again on its own: that is its contract, and it must not
        // depend on somebody having called this first.
        RecipeSummary recipe = recipeCatalog.requirePlannable(owner, entry.recipeId());

        List<IngredientAmount> amounts =
                recipeCatalog.consumptionFor(owner, entry.recipeId(), entry.servings());

        pantryStock.consume(owner, amounts);
        entry.markCooked(clock.instant());

        return new CookResult(new PlanEntryView(entry.id(), entry.recipeId(), recipe.title(),
                entry.plannedFor(), entry.servings(), entry.status(),
                entry.createdAt(), entry.updatedAt(), entry.cookedAt(), entry.cancelledAt()),
                amounts);
    }
}
