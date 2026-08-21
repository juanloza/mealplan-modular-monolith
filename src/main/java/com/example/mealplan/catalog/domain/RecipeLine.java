package com.example.mealplan.catalog.domain;

import com.example.mealplan.catalog.api.IngredientId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One ingredient of a recipe, in the amount the recipe calls for at its own serving count.
 *
 * <p>Part of the recipe aggregate and with no behaviour of its own: only the accessors the service
 * needs to build views and consumption. It does not store the dimension either, and takes it from
 * the ingredient when needed: one more lookup, in exchange for not holding a copy that can drift.
 *
 * <p>The reference to the ingredient is an identifier and not an association. Ingredients are a
 * separate aggregate, and the rule that references crossing aggregates travel as identifiers is
 * what keeps the object graph from turning the whole schema into one inseparable lump.
 */
@Entity
@Table(name = "recipe_line")
public class RecipeLine {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false, updatable = false)
    private Recipe recipe;

    /** Called {@code line_index} in SQL, because {@code position} is a standard SQL function. */
    @Column(name = "line_index", nullable = false)
    private int position;

    @Column(name = "ingredient_id", nullable = false)
    private UUID ingredientId;

    @Column(name = "amount_milli", nullable = false)
    private long amountMilli;

    protected RecipeLine() {
        // Required by JPA.
    }

    RecipeLine(Recipe recipe, int position, IngredientId ingredientId, long amountMilli) {
        this.id = UUID.randomUUID();
        this.recipe = recipe;
        this.position = position;
        this.ingredientId = ingredientId.value();
        this.amountMilli = amountMilli;
    }

    public IngredientId ingredientId() {
        return new IngredientId(ingredientId);
    }

    public int position() {
        return position;
    }

    public long amountMilli() {
        return amountMilli;
    }
}
