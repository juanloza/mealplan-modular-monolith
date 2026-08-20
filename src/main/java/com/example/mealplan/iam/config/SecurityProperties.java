package com.example.mealplan.iam.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Validated at startup, so a missing or too short secret stops the application instead of turning
 * into a 500 on the first login.
 *
 * <p>Every constraint carries its own message. The default ones would be interpolated in the
 * language of the machine, because this validation runs before any request exists and therefore
 * outside the fixed locale that the web layer installs, and "size must be between 32 and
 * 2147483647" says nothing about what to configure anyway.
 */
@Validated
@ConfigurationProperties(prefix = "mealplan.security")
public record SecurityProperties(
        @Valid @NotNull(message = "is required") Jwt jwt,

        @Min(value = 4, message = "must be at least 4; only the test profile goes that low")
        @Max(value = 15, message = "must be at most 15, or a single login takes minutes")
        int bcryptStrength) {

    public record Jwt(
            @NotBlank(message = "is required")
            @Size(min = 32, message = "must be at least 32 characters, because HS256 needs a key of 256 bits")
            String secret,

            @Min(value = 60, message = "must be at least 60 seconds")
            @Max(value = 86_400, message = "must be at most 86400 seconds, which is a day")
            long ttlSeconds,

            @NotBlank(message = "is required") String issuer) {
    }
}
