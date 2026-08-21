package com.example.mealplan.planning.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;

/**
 * Both fields are optional, and {@code servings} is an {@code Integer} and not an {@code int} so
 * that it can be: null means "leave this alone". A body with both nulls is valid and changes
 * nothing, which is why it answers 200 without moving {@code updatedAt}.
 */
public record UpdatePlanEntryRequest(
        LocalDate plannedFor,
        @Min(1) @Max(50) Integer servings) {
}
