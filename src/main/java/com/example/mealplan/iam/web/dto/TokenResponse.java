package com.example.mealplan.iam.web.dto;

import com.example.mealplan.iam.application.AccessToken;

/**
 * The token type is a constant and travels anyway, so the client can build the {@code
 * Authorization} header from the response alone instead of hardcoding the scheme.
 */
public record TokenResponse(String tokenType, String accessToken, long expiresInSeconds) {

    private static final String BEARER = "Bearer";

    public static TokenResponse of(AccessToken token) {
        return new TokenResponse(BEARER, token.value(), token.expiresInSeconds());
    }
}
