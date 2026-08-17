package com.example.mealplan.shared.web;

import com.example.mealplan.shared.domain.ErrorCode;
import java.net.URI;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * The only builder of error responses in the application.
 *
 * <p>It is used both by the exception handler and by the 401 and 403 handlers of the security
 * filter chain. Those run before routing and never reach the controller advice, so without a shared
 * factory they would produce a different shape and the promise that every error looks the same
 * would quietly stop being true.
 */
public final class ProblemDetailFactory {

    private static final String TYPE_PREFIX = "https://example.com/problems/";

    /** The canonical texts for the codes nobody else writes a message for. */
    private static final Map<ErrorCode, String> DEFAULT_DETAILS;

    static {
        Map<ErrorCode, String> details = new EnumMap<>(ErrorCode.class);
        details.put(ErrorCode.VALIDATION_FAILED, "The request body is not valid.");
        details.put(ErrorCode.MALFORMED_REQUEST, "The request body could not be read.");
        details.put(ErrorCode.ROUTE_NOT_FOUND, "No handler found for this path.");
        details.put(ErrorCode.METHOD_NOT_ALLOWED, "This method is not supported for this path.");
        details.put(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Only application/json is supported.");
        details.put(ErrorCode.CONCURRENT_MODIFICATION,
                "The resource was modified by another request. Re-read it and try again.");
        details.put(ErrorCode.INTERNAL_ERROR, "Unexpected error.");
        details.put(ErrorCode.UNAUTHENTICATED, "Authentication is required.");
        details.put(ErrorCode.ACCESS_DENIED, "Access is denied.");
        DEFAULT_DETAILS = Collections.unmodifiableMap(details);
    }

    private ProblemDetailFactory() {
    }

    /**
     * For codes raised by the web layer or the filter chain: uses the canonical text and carries no
     * extensions.
     *
     * @throws IllegalStateException if the code has no canonical text
     */
    public static ProblemDetail of(ErrorCode code, String instance) {
        String detail = DEFAULT_DETAILS.get(code);
        if (detail == null) {
            throw new IllegalStateException("No canonical detail text for error code " + code);
        }
        return of(code, detail, Map.of(), instance);
    }

    /** For everything else, where whoever raised the failure wrote the message. */
    public static ProblemDetail of(ErrorCode code, String detail, Map<String, Object> details, String instance) {
        HttpStatus status = ErrorStatus.of(code);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + slugOf(code)));
        problem.setTitle(titleOf(code));
        if (instance != null) {
            problem.setInstance(URI.create(instance));
        }

        // `code` is the client contract: it is what a caller should branch on, never the status
        // alone and never the prose.
        problem.setProperty("code", code.name());
        details.forEach(problem::setProperty);
        return problem;
    }

    /** Both of these derive from the constant, so they can never drift out of sync with it. */
    private static String slugOf(ErrorCode code) {
        return code.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String titleOf(ErrorCode code) {
        String words = code.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    static boolean hasCanonicalDetail(ErrorCode code) {
        return DEFAULT_DETAILS.containsKey(code);
    }
}
