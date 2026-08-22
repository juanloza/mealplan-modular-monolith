package com.example.mealplan.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * The base of every integration test: a real PostgreSQL, the real migrations and the real security
 * filter chain.
 *
 * <p>The container is declared as a bean rather than with {@code @Container}, so Spring Boot owns
 * it and, because the application context is cached, every test class that shares this
 * configuration reuses the same container instead of starting one of its own. The corollary is
 * worth knowing: each distinct set of beans is another cache entry and therefore another container,
 * which is why the fixed clock and the barrier are imported class by class instead of living here.
 *
 * <p>Nothing here skips when Docker is missing. A test that quietly skips is worse than no test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AbstractIntegrationTest.Containers.class)
public abstract class AbstractIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {

        /**
         * No generics: in Testcontainers 2 the class stopped being self referential.
         *
         * <p>{@code @ServiceConnection} wires {@code spring.datasource.*} and nothing else, so
         * Flyway applies the migrations on startup exactly as in production and the suite runs
         * against the schema that gets deployed, not one Hibernate invented.
         */
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:18-alpine");
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper json;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    protected TestFixtures fixtures;

    @BeforeEach
    void createFixtures() {
        fixtures = new TestFixtures(mockMvc, json);
    }

    /**
     * Isolation comes from truncating, not from a rollback: see {@link DatabaseCleaner} for why
     * that distinction is the whole point.
     */
    @AfterEach
    void cleanDatabase() {
        databaseCleaner.clean();
    }

    /** Adds the bearer token of a real login and the JSON content type. */
    protected static MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, TestFixtures.bearer(token))
                .contentType(MediaType.APPLICATION_JSON);
    }

    /**
     * Strips {@code instance} from an error body, which is the request path and therefore cannot be
     * equal for two requests to different paths. What has to be identical is everything else.
     */
    protected static String withoutInstance(String body) {
        return body.replaceAll("\"instance\":\"[^\"]*\",?", "");
    }
}
