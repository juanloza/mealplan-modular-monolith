package com.example.mealplan.iam.config;

import com.example.mealplan.shared.domain.ErrorCode;
import com.example.mealplan.shared.web.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import tools.jackson.databind.json.JsonMapper;

/**
 * The filter chain answers exactly one question: is anyone authenticated? Whether the caller owns a
 * given resource requires loading the aggregate, so that decision belongs to the service that
 * already loads it, and this application carries no method level security annotations at all.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtDecoder jwtDecoder,
                                           AuthenticationEntryPoint entryPoint,
                                           AccessDeniedHandler deniedHandler) throws Exception {
        return http
                // The API is stateless and only accepts credentials in the Authorization header.
                // CSRF exploits ambient credentials the browser attaches on its own; with none,
                // there is nothing to forge. Disabling it here is the correct configuration, not
                // a concession.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**",
                                         "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.decoder(jwtDecoder))
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .build();
    }

    /**
     * The timestamp validator receives the injected clock, and this is not a detail. With the
     * default validator it would read the wall clock while the issuer reads the bean, so under a
     * test with a fixed clock in the past every freshly issued token would be born expired and
     * every authenticated request would answer 401.
     *
     * <p>Declaring this bean also switches off the resource server autoconfiguration, which would
     * otherwise demand a JWK Set URI or an Issuer URI that this application has no use for.
     */
    @Bean
    public JwtDecoder jwtDecoder(SecurityProperties properties, Clock clock) {
        SecretKeySpec key = new SecretKeySpec(
                properties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        // Pinning the algorithm is what makes a token signed with something else, or not signed at
        // all, be rejected rather than accepted.
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setClock(clock);

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestamps,
                new JwtIssuerValidator(properties.jwt().issuer())));
        return decoder;
    }

    @Bean
    public JwtEncoder jwtEncoder(SecurityProperties properties) {
        SecretKeySpec key = new SecretKeySpec(
                properties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    /**
     * These two run inside the filter chain, before routing, so they never pass through the
     * controller advice and have to do by hand what it would do for them. Using the same factory is
     * what makes the promise true that every error response has the same shape.
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(JsonMapper jsonMapper) {
        return (request, response, ex) -> writeProblem(response, request, jsonMapper, ErrorCode.UNAUTHENTICATED);
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler(JsonMapper jsonMapper) {
        return (request, response, ex) -> writeProblem(response, request, jsonMapper, ErrorCode.ACCESS_DENIED);
    }

    private static void writeProblem(HttpServletResponse response,
                                     HttpServletRequest request,
                                     JsonMapper jsonMapper,
                                     ErrorCode code) throws IOException {
        ProblemDetail problem = ProblemDetailFactory.of(code, request.getRequestURI());
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }
}
