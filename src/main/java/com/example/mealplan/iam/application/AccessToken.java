package com.example.mealplan.iam.application;

/** A freshly issued access token and how long it remains valid. */
public record AccessToken(String value, long expiresInSeconds) {
}
