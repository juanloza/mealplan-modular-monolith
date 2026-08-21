package com.example.mealplan.planning.api;

/**
 * Where a plan entry stands. Both {@code COOKED} and {@code CANCELLED} are terminal, but only one
 * of them blocks deletion: a cooked entry is the record of a subtraction that already happened, and
 * deleting it would leave the missing stock with nothing to explain it.
 */
public enum PlanEntryStatus {

    PLANNED,
    COOKED,
    CANCELLED
}
