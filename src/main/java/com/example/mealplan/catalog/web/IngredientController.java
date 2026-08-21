package com.example.mealplan.catalog.web;

import com.example.mealplan.catalog.api.IngredientId;
import com.example.mealplan.catalog.api.IngredientView;
import com.example.mealplan.catalog.application.IngredientService;
import com.example.mealplan.catalog.web.dto.CreateIngredientRequest;
import com.example.mealplan.catalog.web.dto.IngredientResponse;
import com.example.mealplan.catalog.web.dto.RenameIngredientRequest;
import com.example.mealplan.shared.domain.UserId;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller identity arrives as a {@link UserId} parameter, resolved from the token. Nothing in
 * this class imports anything from the authentication module: the type is shared, so the boundary
 * stays where it is.
 *
 * <p>There is no security annotation on any method either. Whether the caller owns a given
 * ingredient is decided inside the service, in the same query that loads it.
 */
@RestController
@RequestMapping("/api/ingredients")
public class IngredientController {

    private final IngredientService ingredients;

    public IngredientController(IngredientService ingredients) {
        this.ingredients = ingredients;
    }

    @PostMapping
    public ResponseEntity<IngredientResponse> create(UserId owner,
                                                     @Valid @RequestBody CreateIngredientRequest request) {
        IngredientView created = ingredients.create(owner, request.name(), request.dimension());
        return ResponseEntity
                .created(URI.create("/api/ingredients/" + created.id().value()))
                .body(IngredientResponse.of(created));
    }

    /** Sorted by name ascending, ignoring case. */
    @GetMapping
    public List<IngredientResponse> list(UserId owner) {
        return ingredients.list(owner).stream().map(IngredientResponse::of).toList();
    }

    @GetMapping("/{id}")
    public IngredientResponse get(UserId owner, @PathVariable UUID id) {
        return IngredientResponse.of(ingredients.require(owner, new IngredientId(id)));
    }

    @PatchMapping("/{id}")
    public IngredientResponse rename(UserId owner, @PathVariable UUID id,
                                     @Valid @RequestBody RenameIngredientRequest request) {
        return IngredientResponse.of(ingredients.rename(owner, new IngredientId(id), request.name()));
    }

    /** Answers 204 and takes the stock of the ingredient with it; recipe lines block it instead. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(UserId owner, @PathVariable UUID id) {
        ingredients.delete(owner, new IngredientId(id));
        return ResponseEntity.noContent().build();
    }
}
