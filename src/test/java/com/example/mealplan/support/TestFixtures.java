package com.example.mealplan.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.example.mealplan.shared.domain.Dimension;
import com.example.mealplan.shared.domain.Unit;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the data every integration test starts from, and builds it <em>through the HTTP API</em>
 * rather than by inserting rows. That way no test starts from a state the application would not
 * know how to produce, and a rule enforced by a service cannot be sidestepped by a fixture.
 *
 * <p>Tokens are obtained with a real login for the same reason, and it is why this project does not
 * use {@code spring-security-test} or {@code @WithMockUser}: those would short circuit the very
 * decoder that several acceptance criteria exist to check.
 *
 * <p>Every method declares {@code throws Exception} rather than wrapping, so that the failure a
 * test sees is the one that happened.
 */
public class TestFixtures {

    public static final String PASSWORD = "correct-horse-battery-staple";

    private final MockMvc mockMvc;
    private final JsonMapper json;

    public TestFixtures(MockMvc mockMvc, JsonMapper json) {
        this.mockMvc = mockMvc;
        this.json = json;
    }

    /** One line of a recipe on the way in. */
    public record Line(UUID ingredientId, String amount, Unit unit) {
    }

    /** Registers an account and returns the bearer token of a real login. */
    public String registerAndLogin(String email) throws Exception {
        perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, PASSWORD)), 201);

        MvcResult result = perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, PASSWORD)), 200);

        return readString(result, "accessToken");
    }

    public UUID createIngredient(String token, String name, Dimension dimension) throws Exception {
        MvcResult result = perform(authenticated(post("/api/ingredients"), token)
                .content("""
                        {"name": "%s", "dimension": "%s"}
                        """.formatted(name, dimension)), 201);
        return UUID.fromString(readString(result, "id"));
    }

    public UUID createRecipe(String token, String title, int servings, List<Line> lines) throws Exception {
        String body = """
                {"title": "%s", "servings": %d, "lines": [%s]}
                """.formatted(title, servings, lines.stream()
                        .map(line -> """
                                {"ingredientId": "%s", "quantity": {"amount": "%s", "unit": "%s"}}
                                """.formatted(line.ingredientId(), line.amount(), line.unit()))
                        .collect(Collectors.joining(",")));

        MvcResult result = perform(authenticated(post("/api/recipes"), token).content(body), 201);
        return UUID.fromString(readString(result, "id"));
    }

    /** Creates the recipe and publishes it, which is the only state a recipe can be planned in. */
    public UUID createPublishedRecipe(String token, String title, int servings, List<Line> lines) throws Exception {
        UUID recipeId = createRecipe(token, title, servings, lines);
        perform(authenticated(post("/api/recipes/" + recipeId + "/publish"), token), 200);
        return recipeId;
    }

    public void setPantryAmount(String token, UUID ingredientId, String amount, Unit unit) throws Exception {
        perform(authenticated(put("/api/pantry/" + ingredientId), token)
                .content("""
                        {"quantity": {"amount": "%s", "unit": "%s"}}
                        """.formatted(amount, unit)), 200);
    }

    public UUID createPlanEntry(String token, UUID recipeId, LocalDate plannedFor, int servings) throws Exception {
        MvcResult result = perform(authenticated(post("/api/plan-entries"), token)
                .content("""
                        {"recipeId": "%s", "plannedFor": "%s", "servings": %d}
                        """.formatted(recipeId, plannedFor, servings)), 201);
        return UUID.fromString(readString(result, "id"));
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    private static MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON);
    }

    /**
     * A fixture that does not get the status it expected fails here, where the cause is obvious,
     * instead of three assertions later in the test that was actually being written.
     */
    private MvcResult perform(MockHttpServletRequestBuilder builder, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(builder).andReturn();
        if (result.getResponse().getStatus() != expectedStatus) {
            throw new IllegalStateException("Fixture expected " + expectedStatus + " but got "
                    + result.getResponse().getStatus() + ": " + result.getResponse().getContentAsString());
        }
        return result;
    }

    private String readString(MvcResult result, String field) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get(field).asString();
    }
}
