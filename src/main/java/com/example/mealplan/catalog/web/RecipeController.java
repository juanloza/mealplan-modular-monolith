package com.example.mealplan.catalog.web;

import com.example.mealplan.catalog.api.RecipeId;
import com.example.mealplan.catalog.api.RecipeStatus;
import com.example.mealplan.catalog.application.RecipeDetail;
import com.example.mealplan.catalog.application.RecipeService;
import com.example.mealplan.catalog.web.dto.RecipeResponse;
import com.example.mealplan.catalog.web.dto.SaveRecipeRequest;
import com.example.mealplan.shared.domain.UserId;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two transitions are {@code POST} to a subresource and answer 200 with the updated recipe.
 * They are not idempotent: publishing something already published answers 409, which is deliberate
 * and the opposite of setting a pantry amount.
 */
@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipes;

    public RecipeController(RecipeService recipes) {
        this.recipes = recipes;
    }

    @PostMapping
    public ResponseEntity<RecipeResponse> create(UserId owner,
                                                 @Valid @RequestBody SaveRecipeRequest request) {
        RecipeDetail created = recipes.create(owner, request.toCommand());
        return ResponseEntity
                .created(URI.create("/api/recipes/" + created.id().value()))
                .body(RecipeResponse.of(created));
    }

    /**
     * Sorted by title ascending, ignoring case.
     *
     * @param status optional; a value that is not a constant of the enum answers
     *               {@code MALFORMED_REQUEST}
     */
    @GetMapping
    public List<RecipeResponse> list(UserId owner,
                                     @RequestParam(required = false) RecipeStatus status) {
        return recipes.list(owner, status).stream().map(RecipeResponse::of).toList();
    }

    @GetMapping("/{id}")
    public RecipeResponse get(UserId owner, @PathVariable UUID id) {
        return RecipeResponse.of(recipes.get(owner, new RecipeId(id)));
    }

    /** A complete replacement: the lines that do not come in the body disappear. */
    @PutMapping("/{id}")
    public RecipeResponse replace(UserId owner, @PathVariable UUID id,
                                  @Valid @RequestBody SaveRecipeRequest request) {
        return RecipeResponse.of(recipes.replace(owner, new RecipeId(id), request.toCommand()));
    }

    @PostMapping("/{id}/publish")
    public RecipeResponse publish(UserId owner, @PathVariable UUID id) {
        return RecipeResponse.of(recipes.publish(owner, new RecipeId(id)));
    }

    @PostMapping("/{id}/archive")
    public RecipeResponse archive(UserId owner, @PathVariable UUID id) {
        return RecipeResponse.of(recipes.archive(owner, new RecipeId(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(UserId owner, @PathVariable UUID id) {
        recipes.delete(owner, new RecipeId(id));
        return ResponseEntity.noContent().build();
    }
}
