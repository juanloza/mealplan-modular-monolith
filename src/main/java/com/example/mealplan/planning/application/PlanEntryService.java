package com.example.mealplan.planning.application;

import com.example.mealplan.catalog.api.RecipeCatalog;
import com.example.mealplan.catalog.api.RecipeId;
import com.example.mealplan.catalog.api.RecipeSummary;
import com.example.mealplan.planning.api.PlanEntryId;
import com.example.mealplan.planning.domain.PlanEntry;
import com.example.mealplan.planning.infrastructure.PlanEntryJpaRepository;
import com.example.mealplan.shared.domain.DomainException;
import com.example.mealplan.shared.domain.ErrorCode;
import com.example.mealplan.shared.domain.UserId;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everything a plan entry can do except cook, which lives on its own because it is the only
 * operation that orchestrates two modules.
 */
@Service
@Transactional
public class PlanEntryService {

    /** A year either way. Wider than anyone plans, narrow enough to catch a mistyped year. */
    private static final int WINDOW_DAYS = 365;

    /**
     * Open ends for the date filter, both representable in a {@code date} column. Substituting them
     * for a missing bound keeps the query free of nullable parameters.
     */
    private static final LocalDate BEGINNING_OF_TIME = LocalDate.of(1, 1, 1);
    private static final LocalDate END_OF_TIME = LocalDate.of(9999, 12, 31);

    private final PlanEntryJpaRepository entries;
    private final RecipeCatalog recipeCatalog;
    private final Clock clock;

    public PlanEntryService(PlanEntryJpaRepository entries,
                            RecipeCatalog recipeCatalog,
                            Clock clock) {
        this.entries = entries;
        this.recipeCatalog = recipeCatalog;
        this.clock = clock;
    }

    /**
     * The recipe is checked here as a courtesy, so that the user finds out now rather than a week
     * later. The check that actually decides is the one in cooking, because the recipe may be
     * archived in between: this one being permissive would break nothing, that one being permissive
     * would.
     *
     * @throws DomainException {@code RECIPE_NOT_FOUND}, {@code RECIPE_NOT_PLANNABLE},
     *                         {@code PLAN_DATE_OUT_OF_RANGE}
     */
    public PlanEntryView create(UserId owner, RecipeId recipeId, LocalDate plannedFor, int servings) {
        RecipeSummary recipe = recipeCatalog.requirePlannable(owner, recipeId);
        requireWithinWindow(plannedFor);

        PlanEntry entry = entries.save(
                new PlanEntry(owner, recipeId, plannedFor, servings, clock.instant()));

        // The title comes from the summary already loaded: asking the catalogue again would be a
        // second query for something this method is holding.
        return toView(entry, recipe.title());
    }

    /**
     * @param from null for no lower bound
     * @param to   null for no upper bound
     */
    @Transactional(readOnly = true)
    public List<PlanEntryView> list(UserId owner, LocalDate from, LocalDate to) {
        List<PlanEntry> found = entries.findByOwnerIdAndPlannedForBetweenOrderByPlannedForAscCreatedAtAsc(
                owner.value(),
                from == null ? BEGINNING_OF_TIME : from,
                to == null ? END_OF_TIME : to);

        Map<RecipeId, RecipeSummary> titles = titlesOf(owner, found);
        return found.stream().map(entry -> toView(entry, titles)).toList();
    }

    /**
     * @throws DomainException {@code PLAN_ENTRY_NOT_FOUND}, which is also the answer for an entry
     *                         of somebody else
     */
    @Transactional(readOnly = true)
    public PlanEntryView get(UserId owner, PlanEntryId id) {
        PlanEntry entry = require(owner, id);
        return toView(entry, titlesOf(owner, List.of(entry)));
    }

    /**
     * @throws DomainException {@code PLAN_ENTRY_NOT_FOUND}, {@code PLAN_ENTRY_NOT_PLANNED},
     *                         {@code PLAN_DATE_OUT_OF_RANGE}
     */
    public PlanEntryView update(UserId owner, PlanEntryId id, LocalDate plannedFor, Integer servings) {
        PlanEntry entry = require(owner, id);
        if (plannedFor != null) {
            requireWithinWindow(plannedFor);
        }
        entry.reschedule(plannedFor, servings, clock.instant());
        return toView(entry, titlesOf(owner, List.of(entry)));
    }

    /**
     * @throws DomainException {@code PLAN_ENTRY_NOT_FOUND}, {@code PLAN_ENTRY_NOT_PLANNED}
     */
    public PlanEntryView cancel(UserId owner, PlanEntryId id) {
        PlanEntry entry = require(owner, id);
        entry.cancel(clock.instant());
        return toView(entry, titlesOf(owner, List.of(entry)));
    }

    /**
     * @throws DomainException {@code PLAN_ENTRY_NOT_FOUND}, {@code PLAN_ENTRY_NOT_DELETABLE}
     */
    public void delete(UserId owner, PlanEntryId id) {
        PlanEntry entry = require(owner, id);
        entry.requireDeletable();
        entries.delete(entry);
    }

    private PlanEntry require(UserId owner, PlanEntryId id) {
        return entries.findByIdAndOwnerId(id.value(), owner.value())
                .orElseThrow(() -> new DomainException(ErrorCode.PLAN_ENTRY_NOT_FOUND,
                        "No such plan entry."));
    }

    /**
     * Planning in the past is allowed on purpose: it is how you record what you already cooked. The
     * window is there to catch absurd dates, which are nearly always a typo.
     *
     * <p>Today is read from the injected clock, which is UTC. For someone in a distant time zone
     * the edge of the window may fall a day either side of what their calendar says; for a window
     * of a year that is not worth dragging a user time zone through the whole model.
     */
    private void requireWithinWindow(LocalDate plannedFor) {
        LocalDate today = LocalDate.now(clock);
        if (plannedFor.isBefore(today.minusDays(WINDOW_DAYS)) || plannedFor.isAfter(today.plusDays(WINDOW_DAYS))) {
            throw new DomainException(ErrorCode.PLAN_DATE_OUT_OF_RANGE,
                    "A plan entry must fall within a year of today.");
        }
    }

    /**
     * Resolves every title in one call, and deliberately without checking the status of the recipe:
     * an entry may point at a recipe archived after it was planned, and requiring it to be
     * plannable here would answer 409 to merely looking at the plan, leaving the user unable to see
     * what to cancel.
     */
    private Map<RecipeId, RecipeSummary> titlesOf(UserId owner, List<PlanEntry> found) {
        return recipeCatalog.findAllById(owner, found.stream().map(PlanEntry::recipeId).toList());
    }

    private static PlanEntryView toView(PlanEntry entry, Map<RecipeId, RecipeSummary> titles) {
        RecipeSummary recipe = titles.get(entry.recipeId());
        if (recipe == null) {
            throw new IllegalStateException(
                    "Plan entry refers to a recipe that is not in the catalogue: " + entry.recipeId());
        }
        return toView(entry, recipe.title());
    }

    private static PlanEntryView toView(PlanEntry entry, String recipeTitle) {
        return new PlanEntryView(entry.id(), entry.recipeId(), recipeTitle,
                entry.plannedFor(), entry.servings(), entry.status(),
                entry.createdAt(), entry.updatedAt(), entry.cookedAt(), entry.cancelledAt());
    }
}
