package com.example.mealplan.pantry.domain;

import com.example.mealplan.catalog.api.IngredientId;
import com.example.mealplan.shared.domain.Dimension;
import com.example.mealplan.shared.domain.Quantity;
import com.example.mealplan.shared.domain.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * How much of one ingredient the owner has. One row per owner and ingredient, no batches, no expiry
 * dates and no locations.
 *
 * <p>The dimension is not stored here. It belongs to the ingredient, it is immutable there, and a
 * copy would only be able to drift; whoever needs it passes it in, because whoever needs it has
 * already loaded the ingredient.
 *
 * <p>The version is what holds the stock invariant under concurrency. On flush Hibernate writes
 * {@code UPDATE ... WHERE id = ? AND version = ?}, so a lost update shows up as zero rows affected
 * instead of silently overwriting, and turns into a 409 rather than into stock that never existed.
 * It is declared as {@code Long} and not {@code long} because Spring Data reads a null version as
 * "this entity is new" and skips the {@code SELECT} it would otherwise do before every insert.
 */
@Entity
@Table(name = "pantry_item")
public class PantryItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "ingredient_id", nullable = false, updatable = false)
    private UUID ingredientId;

    @Column(name = "amount_milli", nullable = false)
    private long amountMilli;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PantryItem() {
        // Required by JPA.
    }

    public PantryItem(UserId owner, IngredientId ingredientId, Quantity amount, Instant now) {
        this.id = UUID.randomUUID();
        this.ownerId = Objects.requireNonNull(owner, "owner must not be null").value();
        this.ingredientId = Objects.requireNonNull(ingredientId, "ingredientId must not be null").value();
        this.amountMilli = Objects.requireNonNull(amount, "amount must not be null").milli();
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /**
     * @param dimension the dimension of the ingredient this row is about, which the caller holds
     */
    public Quantity amount(Dimension dimension) {
        return Quantity.ofMilli(amountMilli, dimension);
    }

    /**
     * Setting the amount it already holds changes nothing at all: not the value, not
     * {@code updatedAt}, and therefore not the version either, because with no dirty field Hibernate
     * emits no {@code UPDATE}. That is what makes the idempotence of the endpoint observable from
     * outside instead of merely claimed.
     */
    public void setAmount(Quantity newAmount, Instant now) {
        Objects.requireNonNull(newAmount, "newAmount must not be null");
        if (newAmount.milli() == amountMilli) {
            return;
        }
        this.amountMilli = newAmount.milli();
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /**
     * @throws IllegalArgumentException if there is not enough. By the time this is called the
     *                                  service has already checked availability for every line, so
     *                                  arriving here short is a bug and not a business case. The
     *                                  business case is decided before anything is touched
     */
    public void consume(Quantity required, Instant now) {
        Objects.requireNonNull(required, "required must not be null");
        if (required.milli() > amountMilli) {
            throw new IllegalArgumentException(
                    "Cannot consume " + required.milli() + " from " + amountMilli);
        }
        this.amountMilli -= required.milli();
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public IngredientId ingredientId() {
        return new IngredientId(ingredientId);
    }

    public Long version() {
        return version;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
