package com.example.mealplan.shared.domain;

/**
 * The canonical list of error codes. No error code exists outside this enum.
 *
 * <p>It carries no HTTP information on purpose: the domain does not know that HTTP exists. The
 * mapping to a status code lives in the web layer, and a test walks {@code values()} to prove that
 * every constant is mapped.
 */
public enum ErrorCode {

    // Produced by the web layer while reading the request.
    VALIDATION_FAILED,
    MALFORMED_REQUEST,

    // Raised by the domain.
    AMOUNT_OUT_OF_RANGE,
    AMOUNT_NOT_POSITIVE,
    UNIT_DIMENSION_MISMATCH,
    PLAN_DATE_OUT_OF_RANGE,

    // Emitted by the security filter chain, before routing.
    UNAUTHENTICATED,
    ACCESS_DENIED,

    INVALID_CREDENTIALS,

    ROUTE_NOT_FOUND,
    METHOD_NOT_ALLOWED,

    INGREDIENT_NOT_FOUND,
    RECIPE_NOT_FOUND,
    PANTRY_ITEM_NOT_FOUND,
    PLAN_ENTRY_NOT_FOUND,

    EMAIL_ALREADY_REGISTERED,
    INGREDIENT_NAME_TAKEN,
    INGREDIENT_IN_USE,

    RECIPE_NOT_EDITABLE,
    RECIPE_NOT_DELETABLE,
    INVALID_RECIPE_TRANSITION,
    RECIPE_HAS_NO_LINES,
    DUPLICATE_RECIPE_LINE,
    RECIPE_NOT_PLANNABLE,

    PLAN_ENTRY_NOT_PLANNED,
    PLAN_ENTRY_NOT_DELETABLE,
    INSUFFICIENT_STOCK,

    CONCURRENT_MODIFICATION,
    UNSUPPORTED_MEDIA_TYPE,
    INTERNAL_ERROR
}
