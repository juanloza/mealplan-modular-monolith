package com.example.mealplan.iam.application;

import java.util.UUID;

/** What a caller may learn about an account. The hash is never part of it. */
public record UserView(UUID id, String email) {
}
