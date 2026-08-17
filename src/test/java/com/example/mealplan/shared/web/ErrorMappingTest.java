package com.example.mealplan.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mealplan.shared.domain.ErrorCode;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.ProblemDetail;

/**
 * Walks the canonical list itself, so adding a constant without mapping it fails the build rather
 * than surfacing as a 500 the first time that error is raised.
 */
class ErrorMappingTest {

    /** The codes nobody else writes a message for: those raised by the web layer or the filter chain. */
    private static final Set<ErrorCode> CANONICAL_TEXT_CODES = EnumSet.of(
            ErrorCode.VALIDATION_FAILED,
            ErrorCode.MALFORMED_REQUEST,
            ErrorCode.ROUTE_NOT_FOUND,
            ErrorCode.METHOD_NOT_ALLOWED,
            ErrorCode.UNSUPPORTED_MEDIA_TYPE,
            ErrorCode.CONCURRENT_MODIFICATION,
            ErrorCode.INTERNAL_ERROR,
            ErrorCode.UNAUTHENTICATED,
            ErrorCode.ACCESS_DENIED);

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void everyCodeHasAnHttpStatus(ErrorCode code) {
        assertThat(ErrorStatus.of(code)).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void typeAndTitleAreDerivedFromTheCode(ErrorCode code) {
        ProblemDetail problem = ProblemDetailFactory.of(code, "Something happened.", Map.of(), "/api/things");

        String expectedSlug = code.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        assertThat(problem.getType().toString()).isEqualTo("https://example.com/problems/" + expectedSlug);
        assertThat(problem.getTitle()).isNotNull();
        assertThat(problem.getTitle()).doesNotContain("_");
        assertThat(problem.getTitle().charAt(0)).isUpperCase();
        assertThat(problem.getStatus()).isEqualTo(ErrorStatus.of(code).value());
        assertThat(problem.getProperties()).containsEntry("code", code.name());
    }

    @Test
    void exactlyTheNineWebAndFilterCodesHaveACanonicalText() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(ProblemDetailFactory.hasCanonicalDetail(code))
                    .as("canonical text for %s", code)
                    .isEqualTo(CANONICAL_TEXT_CODES.contains(code));
        }
        assertThat(CANONICAL_TEXT_CODES).hasSize(9);
    }

    @Test
    void theShortFormRefusesCodesWithoutACanonicalText() {
        assertThatThrownBy(() -> ProblemDetailFactory.of(ErrorCode.INSUFFICIENT_STOCK, "/api/things"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void knownTitlesReadAsSentences() {
        assertThat(ProblemDetailFactory.of(ErrorCode.INSUFFICIENT_STOCK, "x", Map.of(), "/i").getTitle())
                .isEqualTo("Insufficient stock");
        assertThat(ProblemDetailFactory.of(ErrorCode.VALIDATION_FAILED, "/i").getTitle())
                .isEqualTo("Validation failed");
    }

    @Test
    void extraDetailsBecomeExtensionProperties() {
        ProblemDetail problem = ProblemDetailFactory.of(ErrorCode.UNIT_DIMENSION_MISMATCH,
                "Wrong dimension.", Map.of("expectedDimension", "MASS", "actualDimension", "VOLUME"), "/api/pantry");

        assertThat(problem.getProperties())
                .containsEntry("expectedDimension", "MASS")
                .containsEntry("actualDimension", "VOLUME")
                .containsEntry("code", "UNIT_DIMENSION_MISMATCH");
        assertThat(problem.getInstance().toString()).isEqualTo("/api/pantry");
    }
}
