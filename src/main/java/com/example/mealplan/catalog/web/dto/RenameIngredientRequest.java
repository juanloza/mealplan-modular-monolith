package com.example.mealplan.catalog.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The only field of an ingredient that can be changed. */
public record RenameIngredientRequest(@NotBlank @Size(max = 80) String name) {
}
