package com.example.mealplan.planning.web;

import com.example.mealplan.catalog.api.RecipeId;
import com.example.mealplan.planning.api.PlanEntryId;
import com.example.mealplan.planning.application.CookPlanEntryService;
import com.example.mealplan.planning.application.PlanEntryService;
import com.example.mealplan.planning.application.PlanEntryView;
import com.example.mealplan.planning.web.dto.CookResponse;
import com.example.mealplan.planning.web.dto.CreatePlanEntryRequest;
import com.example.mealplan.planning.web.dto.PlanEntryResponse;
import com.example.mealplan.planning.web.dto.UpdatePlanEntryRequest;
import com.example.mealplan.shared.domain.UserId;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cooking has its own service, so this controller depends on two. That is the shape of the module:
 * every other operation is about one plan entry, and cooking is about a plan entry, a recipe and a
 * pantry at once.
 *
 * <p>Every write here can answer 409 {@code CONCURRENT_MODIFICATION}, because the entry carries a
 * version, and the delete counts too. Creating cannot: it inserts a new row, with no previous
 * version to race against.
 */
@RestController
@RequestMapping("/api/plan-entries")
public class PlanEntryController {

    private final PlanEntryService entries;
    private final CookPlanEntryService cooking;

    public PlanEntryController(PlanEntryService entries, CookPlanEntryService cooking) {
        this.entries = entries;
        this.cooking = cooking;
    }

    @PostMapping
    public ResponseEntity<PlanEntryResponse> create(UserId owner,
                                                    @Valid @RequestBody CreatePlanEntryRequest request) {
        PlanEntryView created = entries.create(owner, new RecipeId(request.recipeId()),
                request.plannedFor(), request.servings());
        return ResponseEntity
                .created(URI.create("/api/plan-entries/" + created.id().value()))
                .body(PlanEntryResponse.of(created));
    }

    /**
     * Sorted by the planned date ascending and, on a tie, by creation.
     *
     * @param from optional lower bound, inclusive; a date that is not ISO answers
     *             {@code MALFORMED_REQUEST}
     * @param to   optional upper bound, inclusive
     */
    @GetMapping
    public List<PlanEntryResponse> list(UserId owner,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return entries.list(owner, from, to).stream().map(PlanEntryResponse::of).toList();
    }

    @GetMapping("/{id}")
    public PlanEntryResponse get(UserId owner, @PathVariable UUID id) {
        return PlanEntryResponse.of(entries.get(owner, new PlanEntryId(id)));
    }

    /** A patch: the fields that do not come are left alone, and a body of two nulls changes nothing. */
    @PatchMapping("/{id}")
    public PlanEntryResponse update(UserId owner, @PathVariable UUID id,
                                    @Valid @RequestBody UpdatePlanEntryRequest request) {
        return PlanEntryResponse.of(entries.update(owner, new PlanEntryId(id),
                request.plannedFor(), request.servings()));
    }

    /** The central operation: scales the recipe, subtracts the result and marks the entry, or none of it. */
    @PostMapping("/{id}/cook")
    public CookResponse cook(UserId owner, @PathVariable UUID id) {
        return CookResponse.of(cooking.cook(owner, new PlanEntryId(id)));
    }

    @PostMapping("/{id}/cancel")
    public PlanEntryResponse cancel(UserId owner, @PathVariable UUID id) {
        return PlanEntryResponse.of(entries.cancel(owner, new PlanEntryId(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(UserId owner, @PathVariable UUID id) {
        entries.delete(owner, new PlanEntryId(id));
        return ResponseEntity.noContent().build();
    }
}
