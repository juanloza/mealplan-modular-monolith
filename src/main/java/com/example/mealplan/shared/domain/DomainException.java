package com.example.mealplan.shared.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A foreseen business case, carrying a code from the canonical list and translated into a concrete
 * 4xx response.
 *
 * <p>The distinction against the JDK unchecked exceptions is hard: if a condition can be triggered
 * by what a client sends, it is a {@code DomainException}; if it can only happen because of a bug,
 * it is an {@link IllegalArgumentException}, an {@link IllegalStateException} or a
 * {@link NullPointerException}, which nothing catches and which end up as a 500 with no detail.
 *
 * <p>The message is in English, ends up in the {@code detail} field of the response, and never
 * contains passwords, hashes or tokens.
 */
public class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;
    private final Map<String, Object> details;

    public DomainException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    /**
     * @param code    the canonical error code
     * @param message the English text that becomes the {@code detail} of the response
     * @param details JSON-ready values only: strings, numbers, booleans, lists and maps of these.
     *                Never domain objects, which would serialise into something the API does not
     *                promise. The caller converts, because the caller is who knows the data.
     */
    public DomainException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.details = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(details, "details must not be null")));
    }

    public ErrorCode code() {
        return code;
    }

    /**
     * @return the extra properties of the response; immutable, and empty when none were given
     */
    public Map<String, Object> details() {
        return details;
    }
}
