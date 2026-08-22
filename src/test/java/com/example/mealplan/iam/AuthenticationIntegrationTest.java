package com.example.mealplan.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mealplan.iam.application.JwtAccessTokenIssuer;
import com.example.mealplan.iam.config.SecurityProperties;
import com.example.mealplan.iam.domain.User;
import com.example.mealplan.support.AbstractIntegrationTest;
import com.example.mealplan.support.TestFixtures;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Registration, login and what the filter chain does with a token it does not like.
 *
 * <p>Every token here is produced the way the application produces them, or forged the way an
 * attacker would. Nothing is stubbed: that is the only way these assertions say anything about the
 * decoder that is actually wired.
 */
class AuthenticationIntegrationTest extends AbstractIntegrationTest {

    private static final String CHEF = "chef@example.com";

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private Clock clock;

    @Test
    void registersAndAnswersWithoutTheHash() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(CHEF, TestFixtures.PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(CHEF))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                // The one endpoint that creates a resource without a Location: there is no
                // endpoint to read an account back from, so there is nothing to point at.
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    @Test
    void refusesTheSameEmailWhateverTheCapitalisation() throws Exception {
        fixtures.registerAndLogin(CHEF);

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "Chef@Example.com", "password": "%s"}
                                """.formatted(TestFixtures.PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("an unknown email and a wrong password answer byte for byte the same")
    void bothCredentialFailuresAreIndistinguishable() throws Exception {
        fixtures.registerAndLogin(CHEF);

        String wrongPassword = login(CHEF, "wrong-password-entirely");
        String unknownEmail = login("nobody@example.com", "wrong-password-entirely");

        assertThat(wrongPassword).isEqualTo(unknownEmail);
        assertThat(wrongPassword).contains("INVALID_CREDENTIALS");
    }

    @Test
    void refusesAPasswordShorterThanTwelveCharacters() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "elevenchars"}
                                """.formatted(CHEF)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    @DisplayName("a password of 40 accented characters passes @Size and is still refused")
    void refusesAPasswordLongerThanSeventyTwoBytes() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(CHEF, "é".repeat(40))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void answersAProblemDetailAndNotAPageWhenThereIsNoToken() throws Exception {
        mockMvc.perform(get("/api/recipes"))
                .andExpect(status().isUnauthorized())
                .andExpect(content -> assertThat(content.getResponse().getContentType())
                        .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void acceptsATokenItIssuedItself() throws Exception {
        String token = fixtures.registerAndLogin(CHEF);

        mockMvc.perform(get("/api/recipes").header(HttpHeaders.AUTHORIZATION, TestFixtures.bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void refusesATokenSignedWithAnotherSecret() throws Exception {
        fixtures.registerAndLogin(CHEF);

        SecretKeySpec otherKey = new SecretKeySpec(
                "another-secret-that-is-long-enough!!".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder otherEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(otherKey));
        String forged = new JwtAccessTokenIssuer(otherEncoder, securityProperties, clock)
                .issue(someUser()).value();

        expectUnauthenticated(forged);
    }

    @Test
    @DisplayName("a token with alg none is refused, which is what pinning HS256 buys")
    void refusesAnUnsignedToken() throws Exception {
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        String header = base64.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = base64.encodeToString(("{\"sub\":\"" + UUID.randomUUID()
                + "\",\"iss\":\"mealplan-api\"}").getBytes(StandardCharsets.UTF_8));

        expectUnauthenticated(header + "." + payload + ".");
    }

    @Test
    @DisplayName("an expired token is refused, and the test does not wait an hour to find out")
    void refusesAnExpiredToken() throws Exception {
        fixtures.registerAndLogin(CHEF);

        Clock twoHoursAgo = Clock.offset(clock, Duration.ofHours(-2));
        String expired = new JwtAccessTokenIssuer(jwtEncoder, securityProperties, twoHoursAgo)
                .issue(someUser()).value();

        expectUnauthenticated(expired);
    }

    private void expectUnauthenticated(String token) throws Exception {
        mockMvc.perform(get("/api/recipes").header(HttpHeaders.AUTHORIZATION, TestFixtures.bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    /** No token forged here gets far enough for the account behind it to matter. */
    private static User someUser() {
        return new User(CHEF, "irrelevant-hash", Instant.EPOCH);
    }

    private String login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
    }
}
