package com.example.mealplan.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An amount of something, held as a whole number of thousandths of the canonical unit of its
 * dimension: 1 g is {@code 1_000}, 1 kg is {@code 1_000_000}, half a piece is {@code 500}.
 *
 * <p>No amount anywhere in this application is a {@code double} or a {@code float}. A binary
 * floating point number in the path would let stock drift by accumulated rounding error, and stock
 * that silently drifts is worse than input that gets refused.
 */
public record Quantity(long milli, Dimension dimension) implements Comparable<Quantity> {

    /** The largest amount that may be stored: 1,000,000 grams, millilitres or pieces. */
    public static final long MAX_STORED_MILLI = 1_000_000_000L;

    private static final int CANONICAL_SCALE = 3;

    /**
     * A negative amount or a missing dimension is a programming error, not a business case: the web
     * layer rejects both before anything reaches the domain.
     */
    public Quantity {
        Objects.requireNonNull(dimension, "dimension must not be null");
        if (milli < 0) {
            throw new IllegalArgumentException("Quantity must not be negative, was " + milli);
        }
    }

    /**
     * Converts an amount expressed in an arbitrary unit into canonical thousandths.
     *
     * <p>Because {@code milliPerUnit} is already expressed in thousandths of the canonical unit,
     * this is a multiplication and nothing else. There is no division by 1000 here, and adding one
     * is the easiest way to break this class.
     *
     * @throws DomainException {@code AMOUNT_OUT_OF_RANGE} if the result exceeds what may be stored
     */
    public static Quantity of(BigDecimal amount, Unit unit) {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(unit, "unit must not be null");

        // Both of these throw ArithmeticException, and neither can happen through the API: the web
        // layer caps the input at three decimals, so the product is always a whole number that
        // fits. If one ever fires, it is a bug and belongs in a 500, not in a business error.
        long milli = amount.multiply(BigDecimal.valueOf(unit.milliPerUnit()))
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();

        if (milli > MAX_STORED_MILLI) {
            throw new DomainException(ErrorCode.AMOUNT_OUT_OF_RANGE,
                    "Amount is larger than the maximum this application stores.");
        }
        return new Quantity(milli, unit.dimension());
    }

    /**
     * Builds a quantity from raw thousandths, without the storage ceiling of {@link #of}.
     *
     * <p>The ceiling bounds what is <em>stored</em>, not what is <em>computed</em>: the consumption
     * of a scaled up recipe may legitimately exceed it, and the only consequence is that there is
     * not enough stock.
     */
    public static Quantity ofMilli(long milli, Dimension dimension) {
        return new Quantity(milli, dimension);
    }

    public static Quantity zero(Dimension dimension) {
        return new Quantity(0L, dimension);
    }

    public Quantity plus(Quantity other) {
        requireSameDimension(other);
        return new Quantity(Math.addExact(milli, other.milli), dimension);
    }

    /**
     * @throws IllegalArgumentException if the result would be negative, which callers are expected
     *                                  to have ruled out before asking
     */
    public Quantity minus(Quantity other) {
        requireSameDimension(other);
        return new Quantity(Math.subtractExact(milli, other.milli), dimension);
    }

    public boolean isLessThan(Quantity other) {
        return compareTo(other) < 0;
    }

    public boolean isZero() {
        return milli == 0L;
    }

    /**
     * Scales this amount from {@code fromServings} to {@code toServings}, rounding HALF_UP.
     *
     * <p>Exact integer arithmetic, no floating point: doubling before dividing turns the half-up
     * rule into a plain truncating division. Rounding is applied once per line and never to a
     * total, because the rounded number has to be the very number subtracted from a pantry row.
     */
    public Quantity scaledTo(int fromServings, int toServings) {
        if (fromServings < 1 || toServings < 1) {
            throw new IllegalArgumentException(
                    "Servings must be at least 1, were " + fromServings + " and " + toServings);
        }
        long numerator = Math.multiplyExact(milli, (long) toServings);
        long scaled = (Math.multiplyExact(2L, numerator) + fromServings) / (2L * fromServings);
        return new Quantity(scaled, dimension);
    }

    /** @return the amount in the canonical unit, always with exactly three decimals: {@code "262.500"} */
    public String toCanonicalString() {
        return BigDecimal.valueOf(milli, CANONICAL_SCALE).toPlainString();
    }

    @Override
    public int compareTo(Quantity other) {
        requireSameDimension(other);
        return Long.compare(milli, other.milli);
    }

    private void requireSameDimension(Quantity other) {
        Objects.requireNonNull(other, "other must not be null");
        if (dimension != other.dimension) {
            throw new IllegalArgumentException(
                    "Cannot combine " + dimension + " with " + other.dimension);
        }
    }
}
