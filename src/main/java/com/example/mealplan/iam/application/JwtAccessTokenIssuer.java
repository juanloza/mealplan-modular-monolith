package com.example.mealplan.iam.application;

import com.example.mealplan.iam.config.SecurityProperties;
import com.example.mealplan.iam.domain.User;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Issues the access token.
 *
 * <p>It is a component of the application layer rather than a configuration bean because it holds
 * logic worth testing on its own: building one with a clock shifted backwards is how a test
 * produces an already expired token without waiting an hour for it.
 */
@Component
public class JwtAccessTokenIssuer {

    private final JwtEncoder encoder;
    private final SecurityProperties properties;
    private final Clock clock;

    public JwtAccessTokenIssuer(JwtEncoder encoder, SecurityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public AccessToken issue(User user) {
        Instant issuedAt = clock.instant();
        long ttlSeconds = properties.jwt().ttlSeconds();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                // The identity is the subject, always. The email below is informational.
                .subject(user.id().value().toString())
                .claim("email", user.email())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(ttlSeconds))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, ttlSeconds);
    }
}
