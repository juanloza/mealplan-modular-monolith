package com.example.mealplan.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Runs with no Spring context and no database: the arithmetic of the domain depends on nothing.
 */
class QuantityTest {

    @Nested
    @DisplayName("conversion from an input amount")
    class Conversion {

        @ParameterizedTest(name = "{0} {1} is {2} thousandths")
        @CsvSource({
            "2,     KILOGRAM,   2000000",
            "1,     GRAM,       1000",
            "0.001, GRAM,       1",
            "350,   GRAM,       350000",
            "0.500, LITER,      500000",
            "2,     PIECE,      2000",
            "0,     GRAM,       0",
        })
        void convertsByMultiplyingOnly(BigDecimal amount, Unit unit, long expectedMilli) {
            assertThat(Quantity.of(amount, unit).milli()).isEqualTo(expectedMilli);
        }

        @Test
        void takesTheDimensionFromTheUnit() {
            assertThat(Quantity.of(new BigDecimal("1"), Unit.LITER).dimension()).isEqualTo(Dimension.VOLUME);
            assertThat(Quantity.of(new BigDecimal("1"), Unit.KILOGRAM).dimension()).isEqualTo(Dimension.MASS);
            assertThat(Quantity.of(new BigDecimal("1"), Unit.PIECE).dimension()).isEqualTo(Dimension.COUNT);
        }

        @Test
        void acceptsExactlyTheStorageCeiling() {
            Quantity atCeiling = Quantity.of(new BigDecimal("1000"), Unit.KILOGRAM);

            assertThat(atCeiling.milli()).isEqualTo(Quantity.MAX_STORED_MILLI);
        }

        @Test
        void rejectsAnythingAboveTheStorageCeiling() {
            assertThatThrownBy(() -> Quantity.of(new BigDecimal("1001"), Unit.KILOGRAM))
                    .isInstanceOf(DomainException.class)
                    .extracting(e -> ((DomainException) e).code())
                    .isEqualTo(ErrorCode.AMOUNT_OUT_OF_RANGE);
        }

        @Test
        void doesNotApplyTheCeilingToComputedAmounts() {
            Quantity computed = Quantity.ofMilli(Quantity.MAX_STORED_MILLI + 1, Dimension.MASS);

            assertThat(computed.milli()).isEqualTo(Quantity.MAX_STORED_MILLI + 1);
        }
    }

    @Nested
    @DisplayName("scaling between serving counts")
    class Scaling {

        /** Every row of the reference table, HALF_UP and applied per line. */
        @ParameterizedTest(name = "{0} thousandths from {1} to {2} servings is {3}")
        @CsvSource({
            "350000, 4, 3, 262500",
            "100000, 3, 1, 33333",
            "1000,   2, 1, 500",
            "1000,   3, 2, 667",
            "5000,   2, 2, 5000",
        })
        void scalesWithHalfUpRounding(long milli, int from, int to, long expected) {
            Quantity scaled = Quantity.ofMilli(milli, Dimension.MASS).scaledTo(from, to);

            assertThat(scaled.milli()).isEqualTo(expected);
        }

        @Test
        void keepsTheDimension() {
            Quantity scaled = Quantity.ofMilli(1000, Dimension.COUNT).scaledTo(2, 1);

            assertThat(scaled.dimension()).isEqualTo(Dimension.COUNT);
        }

        @Test
        void rejectsNonPositiveServings() {
            Quantity oneGram = Quantity.ofMilli(1000, Dimension.MASS);

            assertThatThrownBy(() -> oneGram.scaledTo(0, 1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> oneGram.scaledTo(1, 0)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("canonical text form")
    class CanonicalText {

        @ParameterizedTest(name = "{0} thousandths renders as {1}")
        @CsvSource({
            "2000000, 2000.000",
            "262500,  262.500",
            "33333,   33.333",
            "500,     0.500",
            "1,       0.001",
            "0,       0.000",
        })
        void alwaysHasExactlyThreeDecimals(long milli, String expected) {
            assertThat(Quantity.ofMilli(milli, Dimension.MASS).toCanonicalString()).isEqualTo(expected);
        }

        @Test
        void reportsTwoKilogramsAsGrams() {
            Quantity twoKilos = Quantity.of(new BigDecimal("2"), Unit.KILOGRAM);

            assertThat(twoKilos.toCanonicalString()).isEqualTo("2000.000");
            assertThat(twoKilos.dimension().canonicalUnit()).isEqualTo(Unit.GRAM);
        }
    }

    @Nested
    @DisplayName("arithmetic and comparison")
    class Arithmetic {

        @Test
        void addsAndSubtractsWithinTheSameDimension() {
            Quantity threeHundred = Quantity.ofMilli(300, Dimension.MASS);
            Quantity twoHundred = Quantity.ofMilli(200, Dimension.MASS);

            assertThat(threeHundred.plus(twoHundred).milli()).isEqualTo(500);
            assertThat(threeHundred.minus(twoHundred).milli()).isEqualTo(100);
        }

        @Test
        void refusesToSubtractBelowZero() {
            Quantity one = Quantity.ofMilli(1, Dimension.MASS);
            Quantity two = Quantity.ofMilli(2, Dimension.MASS);

            assertThatThrownBy(() -> one.minus(two)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void refusesToMixDimensions() {
            Quantity mass = Quantity.ofMilli(1000, Dimension.MASS);
            Quantity volume = Quantity.ofMilli(1000, Dimension.VOLUME);

            assertThatThrownBy(() -> mass.plus(volume)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> mass.minus(volume)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> mass.compareTo(volume)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void comparesAndDetectsZero() {
            Quantity zero = Quantity.zero(Dimension.VOLUME);
            Quantity some = Quantity.ofMilli(1, Dimension.VOLUME);

            assertThat(zero.isZero()).isTrue();
            assertThat(some.isZero()).isFalse();
            assertThat(zero.isLessThan(some)).isTrue();
            assertThat(some.isLessThan(zero)).isFalse();
            assertThat(some.isLessThan(some)).isFalse();
        }
    }

    @Nested
    @DisplayName("the line between a business case and a bug")
    class FailureKind {

        /**
         * A negative amount can only arrive through a programming error, so it must not be a
         * {@link DomainException}: those become 4xx responses, and this one has to reach the
         * generic handler as a 500.
         */
        @Test
        void aNegativeAmountIsABugAndNotABusinessCase() {
            assertThatThrownBy(() -> Quantity.ofMilli(-1, Dimension.MASS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .isNotInstanceOf(DomainException.class);
        }

        @Test
        void aMissingDimensionIsABug() {
            assertThatThrownBy(() -> Quantity.ofMilli(1, null))
                    .isInstanceOf(NullPointerException.class)
                    .isNotInstanceOf(DomainException.class);
        }
    }

    @Nested
    @DisplayName("units and their dimensions")
    class Units {

        @Test
        void everyDimensionReportsItsCanonicalUnit() {
            assertThat(Dimension.MASS.canonicalUnit()).isEqualTo(Unit.GRAM);
            assertThat(Dimension.VOLUME.canonicalUnit()).isEqualTo(Unit.MILLILITER);
            assertThat(Dimension.COUNT.canonicalUnit()).isEqualTo(Unit.PIECE);
        }

        @Test
        void everyCanonicalUnitIsWorthOneThousandThousandths() {
            for (Dimension dimension : Dimension.values()) {
                assertThat(dimension.canonicalUnit().milliPerUnit()).isEqualTo(1_000L);
                assertThat(dimension.canonicalUnit().dimension()).isEqualTo(dimension);
            }
        }
    }
}
