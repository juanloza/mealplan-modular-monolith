package com.example.mealplan.iam.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * No {@code @Email} and no minimum length on the password, unlike registration: rejecting a
 * malformed email here would answer something other than the single credentials error, and the
 * rules that applied when the account was created are not the client's business at login time. The
 * upper bounds stay, because they cap the work an unauthenticated caller can ask for.
 */
public record LoginRequest(
        @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(max = 72) String password) {
}
