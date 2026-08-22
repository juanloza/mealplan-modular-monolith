package com.example.mealplan.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mealplan.shared.domain.Dimension;
import com.example.mealplan.shared.domain.Unit;
import com.example.mealplan.support.AbstractIntegrationTest;
import com.example.mealplan.support.TestFixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecipeIntegrationTest extends AbstractIntegrationTest {

    private String chef;
    private UUID flour;
    private UUID milk;

    @BeforeEach
    void createCatalogue() throws Exception {
        chef = fixtures.registerAndLogin("chef@example.com");
        flour = fixtures.createIngredient(chef, "Flour", Dimension.MASS);
        milk = fixtures.createIngredient(chef, "Milk", Dimension.VOLUME);
    }

    @Test
    @DisplayName("amounts come back in the canonical unit, whatever unit they went in as")
    void createsARecipeWithItsLines() throws Exception {
        mockMvc.perform(authenticated(post("/api/recipes"), chef).content(recipeBody("Pancakes")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.publishedAt").doesNotExist())
                .andExpect(jsonPath("$.lines[0].ingredientName").value("Flour"))
                .andExpect(jsonPath("$.lines[0].quantity.amount").value("350.000"))
                .andExpect(jsonPath("$.lines[0].quantity.unit").value("GRAM"))
                .andExpect(jsonPath("$.lines[1].quantity.amount").value("500.000"))
                .andExpect(jsonPath("$.lines[1].quantity.unit").value("MILLILITER"));
    }

    @Test
    void publishesAndThenArchives() throws Exception {
        UUID recipe = draft();

        mockMvc.perform(authenticated(post("/api/recipes/" + recipe + "/publish"), chef))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());

        mockMvc.perform(authenticated(post("/api/recipes/" + recipe + "/archive"), chef))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());
    }

    @Test
    void refusesToPublishARecipeWithNoLines() throws Exception {
        UUID empty = fixtures.createRecipe(chef, "Empty", 2, List.of());

        mockMvc.perform(authenticated(post("/api/recipes/" + empty + "/publish"), chef))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECIPE_HAS_NO_LINES"));

        mockMvc.perform(authenticated(get("/api/recipes/" + empty), chef))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void refusesEveryTransitionThatIsNotOnTheTable() throws Exception {
        UUID recipe = draft();

        // Archiving a draft.
        mockMvc.perform(authenticated(post("/api/recipes/" + recipe + "/archive"), chef))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RECIPE_TRANSITION"));

        mockMvc.perform(authenticated(post("/api/recipes/" + recipe + "/publish"), chef))
                .andExpect(status().isOk());

        // Publishing something already published.
        mockMvc.perform(authenticated(post("/api/recipes/" + recipe + "/publish"), chef))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RECIPE_TRANSITION"));

        mockMvc.perform(authenticated(post("/api/recipes/" + recipe + "/archive"), chef))
                .andExpect(status().isOk());

        // Anything at all on something archived.
        mockMvc.perform(authenticated(post("/api/recipes/" + recipe + "/archive"), chef))
                .andExpect(status().isConflict());
        mockMvc.perform(authenticated(post("/api/recipes/" + recipe + "/publish"), chef))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a published recipe is immutable, so a cooked entry can still be explained")
    void refusesToEditAPublishedRecipe() throws Exception {
        UUID recipe = draft();
        mockMvc.perform(authenticated(post("/api/recipes/" + recipe + "/publish"), chef));

        mockMvc.perform(authenticated(put("/api/recipes/" + recipe), chef).content(recipeBody("Something else")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECIPE_NOT_EDITABLE"));

        mockMvc.perform(authenticated(get("/api/recipes/" + recipe), chef))
                .andExpect(jsonPath("$.title").value("Pancakes"));
    }

    @Test
    void refusesToDeleteAnythingButADraft() throws Exception {
        UUID recipe = draft();

        mockMvc.perform(authenticated(post("/api/recipes/" + recipe + "/publish"), chef));
        mockMvc.perform(authenticated(delete("/api/recipes/" + recipe), chef))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECIPE_NOT_DELETABLE"));

        UUID draft = draft();
        mockMvc.perform(authenticated(delete("/api/recipes/" + draft), chef))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("replacing the lines reuses line index 0, which needs the deferred constraint")
    void replacesTheContentOfADraft() throws Exception {
        UUID recipe = draft();

        mockMvc.perform(authenticated(put("/api/recipes/" + recipe), chef)
                        .content("""
                                {"title": "Crepes", "servings": 2, "lines": [
                                  {"ingredientId": "%s", "quantity": {"amount": "0.750", "unit": "LITER"}},
                                  {"ingredientId": "%s", "quantity": {"amount": "400", "unit": "GRAM"}}
                                ]}
                                """.formatted(milk, flour)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Crepes"))
                .andExpect(jsonPath("$.lines[0].ingredientName").value("Milk"))
                .andExpect(jsonPath("$.lines[1].ingredientName").value("Flour"))
                .andExpect(jsonPath("$.lines.length()").value(2));
    }

    @Test
    void refusesTheSameIngredientTwice() throws Exception {
        mockMvc.perform(authenticated(post("/api/recipes"), chef)
                        .content("""
                                {"title": "Twice", "servings": 4, "lines": [
                                  {"ingredientId": "%s", "quantity": {"amount": "1", "unit": "GRAM"}},
                                  {"ingredientId": "%s", "quantity": {"amount": "2", "unit": "GRAM"}}
                                ]}
                                """.formatted(flour, flour)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RECIPE_LINE"));
    }

    @Test
    void refusesAUnitOfAnotherDimension() throws Exception {
        mockMvc.perform(authenticated(post("/api/recipes"), chef)
                        .content("""
                                {"title": "Wrong", "servings": 4, "lines": [
                                  {"ingredientId": "%s", "quantity": {"amount": "1", "unit": "LITER"}}
                                ]}
                                """.formatted(flour)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNIT_DIMENSION_MISMATCH"))
                .andExpect(jsonPath("$.expectedDimension").value("MASS"))
                .andExpect(jsonPath("$.actualDimension").value("VOLUME"));
    }

    @Test
    void refusesAnAmountOfZero() throws Exception {
        mockMvc.perform(authenticated(post("/api/recipes"), chef)
                        .content("""
                                {"title": "Zero", "servings": 4, "lines": [
                                  {"ingredientId": "%s", "quantity": {"amount": "0", "unit": "GRAM"}}
                                ]}
                                """.formatted(flour)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AMOUNT_NOT_POSITIVE"));
    }

    @Test
    @DisplayName("an amount sent as a number instead of a string is refused, not quietly coerced")
    void refusesANumericAmount() throws Exception {
        mockMvc.perform(authenticated(post("/api/recipes"), chef)
                        .content("""
                                {"title": "Coerced", "servings": 4, "lines": [
                                  {"ingredientId": "%s", "quantity": {"amount": 350, "unit": "GRAM"}}
                                ]}
                                """.formatted(flour)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("an invented ownerId in the body is refused rather than ignored")
    void refusesAnUnknownProperty() throws Exception {
        mockMvc.perform(authenticated(post("/api/recipes"), chef)
                        .content("""
                                {"title": "Sneaky", "servings": 4, "lines": [], "ownerId": "%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void filtersTheListByStatusAndRefusesAnythingElse() throws Exception {
        UUID published = draft();
        mockMvc.perform(authenticated(post("/api/recipes/" + published + "/publish"), chef));
        draft();

        mockMvc.perform(authenticated(get("/api/recipes?status=PUBLISHED"), chef))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"));

        mockMvc.perform(authenticated(get("/api/recipes?status=NOPE"), chef))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    private UUID draft() throws Exception {
        return fixtures.createRecipe(chef, "Pancakes", 4, List.of(
                new TestFixtures.Line(flour, "350", Unit.GRAM),
                new TestFixtures.Line(milk, "0.500", Unit.LITER)));
    }

    private String recipeBody(String title) {
        return """
                {"title": "%s", "servings": 4, "lines": [
                  {"ingredientId": "%s", "quantity": {"amount": "350", "unit": "GRAM"}},
                  {"ingredientId": "%s", "quantity": {"amount": "0.500", "unit": "LITER"}}
                ]}
                """.formatted(title, flour, milk);
    }
}
