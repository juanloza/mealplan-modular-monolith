package com.example.mealplan.pantry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mealplan.shared.domain.Dimension;
import com.example.mealplan.shared.domain.Unit;
import com.example.mealplan.support.AbstractIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class PantryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private String chef;
    private String sam;
    private UUID flour;

    @BeforeEach
    void createCatalogue() throws Exception {
        chef = fixtures.registerAndLogin("chef@example.com");
        sam = fixtures.registerAndLogin("sam@example.com");
        flour = fixtures.createIngredient(chef, "Flour", Dimension.MASS);
    }

    @Test
    @DisplayName("setting the same amount twice leaves no trace: same timestamp, same version")
    void isIdempotentDownToTheVersion() throws Exception {
        String first = setAmount("2.500", Unit.KILOGRAM)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity.amount").value("2500.000"))
                .andExpect(jsonPath("$.quantity.unit").value("GRAM"))
                .andReturn().getResponse().getContentAsString();

        String second = setAmount("2.500", Unit.KILOGRAM)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(row()).containsEntry("amount_milli", 2_500_000L).containsEntry("version", 0L);
    }

    @Test
    void movesTheVersionOnlyWhenSomethingChanges() throws Exception {
        setAmount("2.500", Unit.KILOGRAM);
        setAmount("3", Unit.KILOGRAM).andExpect(status().isOk());

        assertThat(row()).containsEntry("amount_milli", 3_000_000L).containsEntry("version", 1L);
    }

    @Test
    @DisplayName("a row holding zero is not the same thing as no row at all")
    void keepsARowThatHoldsZero() throws Exception {
        setAmount("0", Unit.GRAM).andExpect(status().isOk());

        mockMvc.perform(authenticated(get("/api/pantry"), chef))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].quantity.amount").value("0.000"));
    }

    @Test
    void listsByIngredientNameIgnoringCase() throws Exception {
        UUID milk = fixtures.createIngredient(chef, "milk", Dimension.VOLUME);
        UUID almond = fixtures.createIngredient(chef, "Almond", Dimension.COUNT);
        fixtures.setPantryAmount(chef, milk, "1", Unit.LITER);
        fixtures.setPantryAmount(chef, almond, "12", Unit.PIECE);
        fixtures.setPantryAmount(chef, flour, "1", Unit.KILOGRAM);

        mockMvc.perform(authenticated(get("/api/pantry"), chef))
                .andExpect(jsonPath("$[0].ingredientName").value("Almond"))
                .andExpect(jsonPath("$[1].ingredientName").value("Flour"))
                .andExpect(jsonPath("$[2].ingredientName").value("milk"));
    }

    @Test
    void refusesAnIngredientOfAnotherOwner() throws Exception {
        mockMvc.perform(authenticated(put("/api/pantry/" + flour), sam)
                        .content("""
                                {"quantity": {"amount": "1", "unit": "GRAM"}}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"));
    }

    @Test
    void refusesAUnitOfAnotherDimension() throws Exception {
        setAmount("1", Unit.LITER)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNIT_DIMENSION_MISMATCH"))
                .andExpect(jsonPath("$.expectedDimension").value("MASS"));
    }

    @Test
    void refusesAnAmountOverTheStorageCeiling() throws Exception {
        setAmount("1001", Unit.KILOGRAM)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AMOUNT_OUT_OF_RANGE"));
    }

    @Test
    @DisplayName("deleting tells apart an ingredient that is not yours from one you do not track")
    void answersTwoDifferentNotFounds() throws Exception {
        mockMvc.perform(authenticated(delete("/api/pantry/" + flour), chef))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PANTRY_ITEM_NOT_FOUND"));

        mockMvc.perform(authenticated(delete("/api/pantry/" + flour), sam))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INGREDIENT_NOT_FOUND"));

        fixtures.setPantryAmount(chef, flour, "1", Unit.KILOGRAM);
        mockMvc.perform(authenticated(delete("/api/pantry/" + flour), chef))
                .andExpect(status().isNoContent());
    }

    private org.springframework.test.web.servlet.ResultActions setAmount(String amount, Unit unit) throws Exception {
        return mockMvc.perform(authenticated(put("/api/pantry/" + flour), chef)
                .content("""
                        {"quantity": {"amount": "%s", "unit": "%s"}}
                        """.formatted(amount, unit)));
    }

    private Map<String, Object> row() {
        return jdbc.queryForMap("select amount_milli, version from pantry_item where ingredient_id = ?", flour);
    }
}
