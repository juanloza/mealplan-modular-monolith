package com.example.mealplan.iam.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param email    normalised by the service, not here: trimming and lowercasing belong with whoever
 *                 stores the canonical form
 * @param password the minimum of 12 characters buys more real entropy than composition rules, which
 *                 push towards predictable substitutions. The maximum of 72 characters is not
 *                 cosmetic: BCrypt only considers the first 72 bytes and the encoder rejects longer
 *                 input. The byte side of that limit cannot be expressed here and is checked by the
 *                 controller
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72) String password) {
}
