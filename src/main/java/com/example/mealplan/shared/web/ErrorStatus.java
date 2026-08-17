package com.example.mealplan.shared.web;

import com.example.mealplan.shared.domain.ErrorCode;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * The one place that knows which HTTP status belongs to each error code.
 *
 * <p>It lives in the web layer because the domain does not know that HTTP exists. A test walks
 * {@code ErrorCode.values()} and fails if any constant is missing, which is what stops a new code
 * from slipping in without a status.
 */
public final class ErrorStatus {

    private static final Map<ErrorCode, HttpStatus> STATUSES;

    static {
        Map<ErrorCode, HttpStatus> statuses = new EnumMap<>(ErrorCode.class);

        statuses.put(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST);
        statuses.put(ErrorCode.MALFORMED_REQUEST, HttpStatus.BAD_REQUEST);
        statuses.put(ErrorCode.AMOUNT_OUT_OF_RANGE, HttpStatus.BAD_REQUEST);
        statuses.put(ErrorCode.AMOUNT_NOT_POSITIVE, HttpStatus.BAD_REQUEST);
        statuses.put(ErrorCode.UNIT_DIMENSION_MISMATCH, HttpStatus.BAD_REQUEST);
        statuses.put(ErrorCode.PLAN_DATE_OUT_OF_RANGE, HttpStatus.BAD_REQUEST);

        statuses.put(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED);
        statuses.put(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);

        statuses.put(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN);

        statuses.put(ErrorCode.ROUTE_NOT_FOUND, HttpStatus.NOT_FOUND);
        statuses.put(ErrorCode.INGREDIENT_NOT_FOUND, HttpStatus.NOT_FOUND);
        statuses.put(ErrorCode.RECIPE_NOT_FOUND, HttpStatus.NOT_FOUND);
        statuses.put(ErrorCode.PANTRY_ITEM_NOT_FOUND, HttpStatus.NOT_FOUND);
        statuses.put(ErrorCode.PLAN_ENTRY_NOT_FOUND, HttpStatus.NOT_FOUND);

        statuses.put(ErrorCode.METHOD_NOT_ALLOWED, HttpStatus.METHOD_NOT_ALLOWED);

        statuses.put(ErrorCode.EMAIL_ALREADY_REGISTERED, HttpStatus.CONFLICT);
        statuses.put(ErrorCode.INGREDIENT_NAME_TAKEN, HttpStatus.CONFLICT);
        statuses.put(ErrorCode.INGREDIENT_IN_USE, HttpStatus.CONFLICT);
        statuses.put(ErrorCode.RECIPE_NOT_EDITABLE, HttpStatus.CONFLICT);
        statuses.put(ErrorCode.RECIPE_NOT_DELETABLE, HttpStatus.CONFLICT);
        statuses.put(ErrorCode.INVALID_RECIPE_TRANSITION, HttpStatus.CONFLICT);
        statuses.put(ErrorCode.RECIPE_HAS_NO_LINES, HttpStatus.CONFLICT);
        statuses.put(ErrorCode.DUPLICATE_RECIPE_LINE, HttpStatus.CONFLICT);
        statuses.put(ErrorCode.RECIPE_NOT_PLANNABLE, HttpStatus.CONFLICT);
        statuses.put(ErrorCode.PLAN_ENTRY_NOT_PLANNED, HttpStatus.CONFLICT);
        statuses.put(ErrorCode.PLAN_ENTRY_NOT_DELETABLE, HttpStatus.CONFLICT);
        statuses.put(ErrorCode.INSUFFICIENT_STOCK, HttpStatus.CONFLICT);
        statuses.put(ErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT);

        statuses.put(ErrorCode.UNSUPPORTED_MEDIA_TYPE, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        statuses.put(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);

        STATUSES = Collections.unmodifiableMap(statuses);
    }

    private ErrorStatus() {
    }

    /**
     * @throws IllegalStateException if the code has no status, which can only mean a constant was
     *                               added to the canonical list without being mapped here
     */
    public static HttpStatus of(ErrorCode code) {
        HttpStatus status = STATUSES.get(code);
        if (status == null) {
            throw new IllegalStateException("No HTTP status mapped for error code " + code);
        }
        return status;
    }
}
