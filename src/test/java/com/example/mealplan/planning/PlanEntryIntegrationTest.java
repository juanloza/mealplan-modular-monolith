package com.example.mealplan.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mealplan.shared.domain.Dimension;
import com.example.mealplan.shared.domain.Unit;
import com.example.mealplan.support.AbstractIntegrationTest;
import com.example.mealplan.support.FixedClockConfig;
import com.example.mealplan.support.TestFixtures;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The plan, and cooking, against a clock frozen at a known instant so that the date window does not
 * depend on the day the suite runs.
 *
 * <p>Every request here carries a token from a real login. That it works at all is the proof that
 * the token validator receives the same frozen clock as the issuer: were it reading the wall clock,
 * every one of these tokens would be born expired and every assertion below would be a 401.
 */
@Import(FixedClockConfig.class)
class PlanEntryIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate TODAY = LocalDate.ofInstant(FixedClockConfig.FIXED_INSTANT, ZoneOffset.UTC);

    @Autowired
    private JdbcTemplate jdbc;

    private String chef;
    private UUID flour;
    private UUID milk;
    private UUID recipe;

    @BeforeEach
    void createCatalogueAndStock() throws Exception {
        chef = fixtures.registerAndLogin("chef@example.com");
        flour = fixtures.createIngredient(chef, "Flour", Dimension.MASS);
        milk = fixtures.createIngredient(chef, "Milk", Dimension.VOLUME);

        recipe = fixtures.createPublishedRecipe(chef, "Pancakes", 4, List.of(
                new TestFixtures.Line(flour, "350", Unit.GRAM),
                new TestFixtures.Line(milk, "0.500", Unit.LITER)));

        fixtures.setPantryAmount(chef, flour, "1", Unit.KILOGRAM);
        fixtures.setPantryAmount(chef, milk, "1", Unit.LITER);
    }

    @Test
    @DisplayName("cooking 3 servings of a recipe written for 4 subtracts exactly the scaled amounts")
    void cooksAndSubtractsTheScaledAmounts() throws Exception {
        UUID entry = fixtures.createPlanEntry(chef, recipe, TODAY, 3);

        mockMvc.perform(authenticated(post("/api/plan-entries/" + entry + "/cook"), chef))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry.status").value("COOKED"))
                .andExpect(jsonPath("$.entry.cookedAt").isNotEmpty())
                .andExpect(jsonPath("$.entry.recipeTitle").value("Pancakes"))
                .andExpect(jsonPath("$.consumed[0].quantity.amount").value("262.500"))
                .andExpect(jsonPath("$.consumed[0].quantity.unit").value("GRAM"))
                .andExpect(jsonPath("$.consumed[1].quantity.amount").value("375.000"));

        assertThat(amountOf(flour)).isEqualTo(737_500L);
        assertThat(amountOf(milk)).isEqualTo(625_000L);
    }

    @Test
    void refusesToCookTheSameEntryTwice() throws Exception {
        UUID entry = fixtures.createPlanEntry(chef, recipe, TODAY, 3);
        mockMvc.perform(authenticated(post("/api/plan-entries/" + entry + "/cook"), chef))
                .andExpect(status().isOk());

        mockMvc.perform(authenticated(post("/api/plan-entries/" + entry + "/cook"), chef))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_ENTRY_NOT_PLANNED"));

        assertThat(amountOf(flour)).isEqualTo(737_500L);
    }

    @Test
    @DisplayName("without enough stock nothing is subtracted, not even what there was enough of")
    void reportsEveryShortfallAndSubtractsNothing() throws Exception {
        fixtures.setPantryAmount(chef, milk, "10", Unit.LITER);
        UUID entry = fixtures.createPlanEntry(chef, recipe, TODAY, 50);

        mockMvc.perform(authenticated(post("/api/plan-entries/" + entry + "/cook"), chef))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.shortfalls.length()").value(1))
                .andExpect(jsonPath("$.shortfalls[0].ingredientName").value("Flour"))
                .andExpect(jsonPath("$.shortfalls[0].required").value("4375.000"))
                .andExpect(jsonPath("$.shortfalls[0].available").value("1000.000"))
                .andExpect(jsonPath("$.shortfalls[0].unit").value("GRAM"))
                .andExpect(jsonPath("$.shortfalls[0].ingredientId").isNotEmpty());

        assertThat(amountOf(flour)).isEqualTo(1_000_000L);
        assertThat(amountOf(milk)).isEqualTo(10_000_000L);

        mockMvc.perform(authenticated(get("/api/plan-entries/" + entry), chef))
                .andExpect(jsonPath("$.status").value("PLANNED"));
    }

    @Test
    void reportsBothShortfallsWhenBothAreMissing() throws Exception {
        UUID entry = fixtures.createPlanEntry(chef, recipe, TODAY, 50);

        mockMvc.perform(authenticated(post("/api/plan-entries/" + entry + "/cook"), chef))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.shortfalls.length()").value(2));
    }

    @Test
    @DisplayName("a recipe archived after planning can still be seen and cancelled, but not cooked")
    void keepsAnEntryUsableAfterItsRecipeIsArchived() throws Exception {
        UUID entry = fixtures.createPlanEntry(chef, recipe, TODAY, 2);
        mockMvc.perform(authenticated(post("/api/recipes/" + recipe + "/archive"), chef))
                .andExpect(status().isOk());

        mockMvc.perform(authenticated(post("/api/plan-entries/" + entry + "/cook"), chef))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECIPE_NOT_PLANNABLE"));

        mockMvc.perform(authenticated(get("/api/plan-entries/" + entry), chef))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipeTitle").value("Pancakes"));

        mockMvc.perform(authenticated(post("/api/plan-entries/" + entry + "/cancel"), chef))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void refusesToPlanARecipeThatIsNotPublished() throws Exception {
        UUID draft = fixtures.createRecipe(chef, "Draft", 2, List.of(
                new TestFixtures.Line(flour, "1", Unit.GRAM)));

        mockMvc.perform(authenticated(post("/api/plan-entries"), chef)
                        .content("""
                                {"recipeId": "%s", "plannedFor": "%s", "servings": 2}
                                """.formatted(draft, TODAY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECIPE_NOT_PLANNABLE"));
    }

    @Test
    @DisplayName("the date window is a year either way, measured from the frozen clock")
    void refusesADateOutsideTheWindow() throws Exception {
        mockMvc.perform(authenticated(post("/api/plan-entries"), chef)
                        .content("""
                                {"recipeId": "%s", "plannedFor": "%s", "servings": 2}
                                """.formatted(recipe, TODAY.plusDays(400))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAN_DATE_OUT_OF_RANGE"));

        mockMvc.perform(authenticated(post("/api/plan-entries"), chef)
                        .content("""
                                {"recipeId": "%s", "plannedFor": "%s", "servings": 2}
                                """.formatted(recipe, TODAY.plusDays(300))))
                .andExpect(status().isCreated());

        // The past is allowed on purpose: it is how you record what you already cooked.
        mockMvc.perform(authenticated(post("/api/plan-entries"), chef)
                        .content("""
                                {"recipeId": "%s", "plannedFor": "%s", "servings": 2}
                                """.formatted(recipe, TODAY.minusDays(300))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a patch with both fields null changes nothing, version included")
    void ignoresAPatchThatSaysNothing() throws Exception {
        UUID entry = fixtures.createPlanEntry(chef, recipe, TODAY, 2);
        Map<String, Object> before = entryRow(entry);

        mockMvc.perform(authenticated(patch("/api/plan-entries/" + entry), chef)
                        .content("""
                                {"plannedFor": null, "servings": null}
                                """))
                .andExpect(status().isOk());

        assertThat(entryRow(entry)).isEqualTo(before);
    }

    @Test
    void reschedulesTheDateAndTheServings() throws Exception {
        UUID entry = fixtures.createPlanEntry(chef, recipe, TODAY, 2);

        mockMvc.perform(authenticated(patch("/api/plan-entries/" + entry), chef)
                        .content("""
                                {"plannedFor": "%s", "servings": 5}
                                """.formatted(TODAY.plusDays(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedFor").value(TODAY.plusDays(1).toString()))
                .andExpect(jsonPath("$.servings").value(5));

        assertThat(entryRow(entry)).containsEntry("version", 1L);
    }

    @Test
    void deletesByStatus() throws Exception {
        UUID cooked = fixtures.createPlanEntry(chef, recipe, TODAY, 1);
        mockMvc.perform(authenticated(post("/api/plan-entries/" + cooked + "/cook"), chef))
                .andExpect(status().isOk());
        mockMvc.perform(authenticated(delete("/api/plan-entries/" + cooked), chef))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_ENTRY_NOT_DELETABLE"));

        UUID cancelled = fixtures.createPlanEntry(chef, recipe, TODAY, 1);
        mockMvc.perform(authenticated(post("/api/plan-entries/" + cancelled + "/cancel"), chef))
                .andExpect(status().isOk());
        mockMvc.perform(authenticated(delete("/api/plan-entries/" + cancelled), chef))
                .andExpect(status().isNoContent());
    }

    @Test
    void listsByDateAndFiltersTheRange() throws Exception {
        fixtures.createPlanEntry(chef, recipe, TODAY.plusDays(2), 1);
        fixtures.createPlanEntry(chef, recipe, TODAY, 1);
        fixtures.createPlanEntry(chef, recipe, TODAY.plusDays(1), 1);

        mockMvc.perform(authenticated(get("/api/plan-entries"), chef))
                .andExpect(jsonPath("$[0].plannedFor").value(TODAY.toString()))
                .andExpect(jsonPath("$[1].plannedFor").value(TODAY.plusDays(1).toString()))
                .andExpect(jsonPath("$[2].plannedFor").value(TODAY.plusDays(2).toString()));

        mockMvc.perform(authenticated(get("/api/plan-entries?from=" + TODAY + "&to=" + TODAY), chef))
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(authenticated(get("/api/plan-entries?from=nope"), chef))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    private Long amountOf(UUID ingredientId) {
        return jdbc.queryForObject(
                "select amount_milli from pantry_item where ingredient_id = ?", Long.class, ingredientId);
    }

    private Map<String, Object> entryRow(UUID entryId) {
        return jdbc.queryForMap(
                "select version, updated_at, planned_for, servings from plan_entry where id = ?", entryId);
    }
}
