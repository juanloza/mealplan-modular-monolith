package com.example.mealplan.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

class IngredientIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private String chef;
    private String sam;

    @BeforeEach
    void createAccounts() throws Exception {
        chef = fixtures.registerAndLogin("chef@example.com");
        sam = fixtures.registerAndLogin("sam@example.com");
    }

    @Test
    void createsReadsRenamesAndDeletes() throws Exception {
        UUID id = fixtures.createIngredient(chef, "Flour", Dimension.MASS);

        mockMvc.perform(authenticated(get("/api/ingredients/" + id), chef))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Flour"))
                .andExpect(jsonPath("$.dimension").value("MASS"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.ownerId").doesNotExist());

        mockMvc.perform(authenticated(patch("/api/ingredients/" + id), chef)
                        .content("""
                                {"name": "Plain flour"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Plain flour"));

        mockMvc.perform(authenticated(delete("/api/ingredients/" + id), chef))
                .andExpect(status().isNoContent());

        mockMvc.perform(authenticated(get("/api/ingredients/" + id), chef))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"));
    }

    @Test
    void createsWithALocationHeader() throws Exception {
        mockMvc.perform(authenticated(post("/api/ingredients"), chef)
                        .content("""
                                {"name": "Flour", "dimension": "MASS"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION));
    }

    @Test
    void listsByNameIgnoringCase() throws Exception {
        fixtures.createIngredient(chef, "milk", Dimension.VOLUME);
        fixtures.createIngredient(chef, "Almond", Dimension.COUNT);
        fixtures.createIngredient(chef, "Flour", Dimension.MASS);

        mockMvc.perform(authenticated(get("/api/ingredients"), chef))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Almond"))
                .andExpect(jsonPath("$[1].name").value("Flour"))
                .andExpect(jsonPath("$[2].name").value("milk"));
    }

    @Test
    @DisplayName("two names that differ only in case belong to the same owner only once")
    void refusesADuplicateNameIgnoringCase() throws Exception {
        fixtures.createIngredient(chef, "Harina", Dimension.MASS);

        mockMvc.perform(authenticated(post("/api/ingredients"), chef)
                        .content("""
                                {"name": "harina", "dimension": "MASS"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NAME_TAKEN"));
    }

    @Test
    void letsAnotherOwnerUseTheSameName() throws Exception {
        fixtures.createIngredient(chef, "Harina", Dimension.MASS);

        mockMvc.perform(authenticated(post("/api/ingredients"), sam)
                        .content("""
                                {"name": "harina", "dimension": "MASS"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("renaming to the name it already has is not a conflict")
    void acceptsARenameToTheSameName() throws Exception {
        UUID id = fixtures.createIngredient(chef, "Flour", Dimension.MASS);

        mockMvc.perform(authenticated(patch("/api/ingredients/" + id), chef)
                        .content("""
                                {"name": "Flour"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Flour"));
    }

    @Test
    @DisplayName("an ingredient of another owner is a 404, identical to one that does not exist")
    void hidesTheIngredientsOfOtherOwners() throws Exception {
        UUID id = fixtures.createIngredient(chef, "Flour", Dimension.MASS);

        String ofAnotherOwner = mockMvc.perform(authenticated(get("/api/ingredients/" + id), sam))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        String neverExisted = mockMvc.perform(authenticated(get("/api/ingredients/" + UUID.randomUUID()), sam))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // Only the instance differs, and it cannot not differ: it is the path that was asked for.
        assertThat(withoutInstance(ofAnotherOwner))
                .isEqualTo(withoutInstance(neverExisted));
    }

    @Test
    void refusesToDeleteAnIngredientUsedByARecipe() throws Exception {
        UUID flour = fixtures.createIngredient(chef, "Flour", Dimension.MASS);
        fixtures.createRecipe(chef, "Pancakes", 4, List.of(new TestFixtures.Line(flour, "350", Unit.GRAM)));

        mockMvc.perform(authenticated(delete("/api/ingredients/" + flour), chef))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INGREDIENT_IN_USE"))
                .andExpect(jsonPath("$.recipeCount").value(1));
    }

    @Test
    @DisplayName("deleting an ingredient takes its stock with it, because the catalogue cannot ask the pantry")
    void deletingAnIngredientTakesItsStockWithIt() throws Exception {
        UUID flour = fixtures.createIngredient(chef, "Flour", Dimension.MASS);
        fixtures.setPantryAmount(chef, flour, "1", Unit.KILOGRAM);

        mockMvc.perform(authenticated(delete("/api/ingredients/" + flour), chef))
                .andExpect(status().isNoContent());

        Long rows = jdbc.queryForObject(
                "select count(*) from pantry_item where ingredient_id = ?", Long.class, flour);
        assertThat(rows).isZero();
    }

    @Test
    void refusesANameThatIsBlank() throws Exception {
        mockMvc.perform(authenticated(post("/api/ingredients"), chef)
                        .content("""
                                {"name": "   ", "dimension": "MASS"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

}
