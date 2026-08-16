package com.example.mealplan.shared.domain;

/**
 * A unit a client may express a quantity in.
 *
 * <p>{@link #milliPerUnit()} is <em>thousandths of the canonical unit</em> per unit of this
 * constant, not canonical units per unit. That is what makes converting an input amount a plain
 * multiplication, with no division anywhere.
 */
public enum Unit {

    GRAM(Dimension.MASS, 1_000L),
    KILOGRAM(Dimension.MASS, 1_000_000L),
    MILLILITER(Dimension.VOLUME, 1_000L),
    LITER(Dimension.VOLUME, 1_000_000L),
    PIECE(Dimension.COUNT, 1_000L);

    private final Dimension dimension;
    private final long milliPerUnit;

    Unit(Dimension dimension, long milliPerUnit) {
        this.dimension = dimension;
        this.milliPerUnit = milliPerUnit;
    }

    public Dimension dimension() {
        return dimension;
    }

    public long milliPerUnit() {
        return milliPerUnit;
    }
}
