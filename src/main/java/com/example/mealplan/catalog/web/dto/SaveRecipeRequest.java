package com.example.mealplan.catalog.web.dto;

import com.example.mealplan.catalog.api.IngredientId;
import com.example.mealplan.catalog.application.SaveRecipeCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The body of both creating and replacing a recipe: replacing is a complete replacement, so the two
 * take the same shape.
 *
 * <p>There is no {@code ownerId} field here or in any other request, and unknown properties are
 * refused by the mapper, so trying to slip one in answers {@code MALFORMED_REQUEST}.
 */
public record SaveRecipeRequest(
        @NotBlank @Size(max = 120) String title,
        @Min(1) @Max(50) int servings,
        @NotNull @Size(max = 50) @Valid List<RecipeLineDto> lines) {

    public SaveRecipeCommand toCommand() {
        List<SaveRecipeCommand.LineCommand> commands = lines.stream()
                .map(line -> new SaveRecipeCommand.LineCommand(
                        new IngredientId(line.ingredientId()),
                        line.quantity().amountAsDecimal(),
                        line.quantity().unit()))
                .toList();
        return new SaveRecipeCommand(title, servings, commands);
    }
}
