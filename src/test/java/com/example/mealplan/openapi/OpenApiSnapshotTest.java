package com.example.mealplan.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mealplan.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps the published contract from drifting away from the application without anyone noticing.
 *
 * <p>The document is fetched from the running application, serialised again with a mapper of our
 * own so that the ordering and the indentation do not depend on the version of springdoc, and
 * compared with the file in the repository. Regenerating it is a flag away, and needs no Maven
 * plugin that starts the application a second time.
 */
class OpenApiSnapshotTest extends AbstractIntegrationTest {

    private static final Path SNAPSHOT = Path.of("docs", "openapi.json");

    private static final String REGENERATE =
            "./mvnw -Dtest=OpenApiSnapshotTest -Dmealplan.openapi.write=true test";

    private static final TypeReference<Map<String, Object>> MAP_OF_ANYTHING = new TypeReference<>() { };

    /** Ordered by key and indented, so that a diff of this file reads as a diff of the API. */
    private static final JsonMapper SNAPSHOT_MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    @Test
    @DisplayName("the published document matches the application, or says how to regenerate it")
    void matchesTheDocumentInTheRepository() throws Exception {
        String published = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Read as maps and not as a tree: ORDER_MAP_ENTRIES_BY_KEYS sorts maps, and a JsonNode
        // keeps whatever order it was parsed in. Going through maps is what makes the ordering ours
        // rather than a detail of the springdoc version in use.
        Map<String, Object> document = SNAPSHOT_MAPPER.readValue(published, MAP_OF_ANYTHING);

        // Line endings are forced to LF on both sides of the comparison. Jackson indents with the
        // line separator of the platform, so on Windows the freshly generated text would carry CRLF
        // while the file in the repository is LF, and the test would fail on nothing but that.
        String formatted = (SNAPSHOT_MAPPER.writeValueAsString(document) + "\n").replace("\r\n", "\n");

        if (Boolean.getBoolean("mealplan.openapi.write")) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, formatted, StandardCharsets.UTF_8);
            return;
        }

        assertThat(SNAPSHOT)
                .withFailMessage("%s is out of date. Regenerate it with:%n  %s", SNAPSHOT, REGENERATE)
                .exists();

        assertThat(Files.readString(SNAPSHOT, StandardCharsets.UTF_8).replace("\r\n", "\n"))
                .withFailMessage("%s no longer matches the application. Regenerate it with:%n  %s",
                        SNAPSHOT, REGENERATE)
                .isEqualTo(formatted);
    }

    @Test
    @DisplayName("the two public routes are documented as needing no token")
    void documentsTheAuthenticationRoutesAsPublic() throws Exception {
        String published = mockMvc.perform(get("/v3/api-docs"))
                .andReturn().getResponse().getContentAsString();
        JsonNode paths = SNAPSHOT_MAPPER.readTree(published).get("paths");

        assertThat(paths.get("/api/auth/login").get("post").get("security")).isEmpty();
        assertThat(paths.get("/api/auth/register").get("post").get("security")).isEmpty();
        assertThat(paths.get("/api/recipes").get("get").get("security")).isNull();
    }

    @Test
    @DisplayName("Swagger UI is served even with the default resource handling switched off")
    void servesSwaggerUi() throws Exception {
        String redirect = mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(redirect).isNotNull();
        mockMvc.perform(get(redirect)).andExpect(status().isOk());
    }
}
