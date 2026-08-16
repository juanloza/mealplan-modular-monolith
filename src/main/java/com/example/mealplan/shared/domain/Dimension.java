package com.example.mealplan.shared.domain;

/**
 * The kind of magnitude an ingredient is measured in. Fixed when the ingredient is created and never changed.
 *
 * <p>There is no conversion between dimensions: turning mass into volume would need the density of the
 * ingredient, which this model does not hold.
 */
public enum Dimension {

    MASS,
    VOLUME,
    COUNT;

    /**
     * The unit every quantity of this dimension is stored and reported in.
     *
     * <p>Resolved on each call rather than held in a field: {@link Unit} already refers back to
     * {@code Dimension}, and a field initialiser would make the two enums depend on each other's
     * class initialisation order.
     *
     * @return the canonical unit of this dimension
     */
    public Unit canonicalUnit() {
        return switch (this) {
            case MASS -> Unit.GRAM;
            case VOLUME -> Unit.MILLILITER;
            case COUNT -> Unit.PIECE;
        };
    }
}
