package com.example.mealplan.pantry.web;

import com.example.mealplan.catalog.api.IngredientId;
import com.example.mealplan.pantry.application.PantryService;
import com.example.mealplan.pantry.web.dto.PantryItemResponse;
import com.example.mealplan.pantry.web.dto.SetPantryAmountRequest;
import com.example.mealplan.shared.domain.UserId;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The pantry is addressed by ingredient, not by a row identifier of its own: there is exactly one
 * row per ingredient, so the ingredient is the natural key of the resource.
 *
 * <p>Both writing endpoints may answer 409 {@code CONCURRENT_MODIFICATION}, because the row carries
 * a version. The delete counts too: Hibernate deletes {@code WHERE id = ? AND version = ?}, so a
 * removal can also lose the race against a concurrent change.
 */
@RestController
@RequestMapping("/api/pantry")
public class PantryController {

    private final PantryService pantry;

    public PantryController(PantryService pantry) {
        this.pantry = pantry;
    }

    /** Sorted by ingredient name ascending. */
    @GetMapping
    public List<PantryItemResponse> list(UserId owner) {
        return pantry.list(owner).stream().map(PantryItemResponse::of).toList();
    }

    /**
     * Answers 200 whether it created the row or updated it. Telling a 201 from a 200 would make the
     * client treat two cases differently that are the same case to it: setting how much there is.
     */
    @PutMapping("/{ingredientId}")
    public PantryItemResponse setAmount(UserId owner, @PathVariable UUID ingredientId,
                                        @Valid @RequestBody SetPantryAmountRequest request) {
        return PantryItemResponse.of(pantry.setAmount(owner, new IngredientId(ingredientId),
                request.quantity().amountAsDecimal(), request.quantity().unit()));
    }

    @DeleteMapping("/{ingredientId}")
    public ResponseEntity<Void> remove(UserId owner, @PathVariable UUID ingredientId) {
        pantry.remove(owner, new IngredientId(ingredientId));
        return ResponseEntity.noContent().build();
    }
}
