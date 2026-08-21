package com.example.mealplan.catalog.domain;

import com.example.mealplan.catalog.api.IngredientId;
import com.example.mealplan.catalog.api.IngredientView;
import com.example.mealplan.shared.domain.Dimension;
import com.example.mealplan.shared.domain.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Something a recipe can call for and a pantry can hold.
 *
 * <p>The dimension is fixed on creation and no operation changes it. Everything else depends on
 * that: two amounts of the same ingredient are comparable without revalidating anything. Changing
 * it would mean migrating every recipe line and every pantry row of the ingredient, which is why
 * the operation does not exist.
 *
 * <p>There is no {@code @Version} either: no operation on an ingredient has a result that depends
 * on it not having been modified meanwhile. Two simultaneous renames leave the last name, which is
 * exactly what anyone would expect.
 */
@Entity
@Table(name = "ingredient")
public class Ingredient {

    private static final int MAX_NAME_LENGTH = 80;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "name", nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "dimension", nullable = false, length = 16, updatable = false)
    private Dimension dimension;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Ingredient() {
        // Required by JPA.
    }

    /**
     * @param name  trimmed here, and rejected outside 1 to 80 characters. That is not a duplicated
     *              business rule: the request DTO already validates it and answers
     *              {@code VALIDATION_FAILED}, so arriving here with an invalid name is a bug
     * @param now   read from the injected clock by the service; the entity never reads the time
     */
    public Ingredient(UserId owner, String name, Dimension dimension, Instant now) {
        this.id = UUID.randomUUID();
        this.ownerId = Objects.requireNonNull(owner, "owner must not be null").value();
        this.name = requireValidName(name);
        this.dimension = Objects.requireNonNull(dimension, "dimension must not be null");
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
    }

    /** Uniqueness per owner is not checked here: it needs the rest of the table, so the service owns it. */
    public void rename(String newName) {
        this.name = requireValidName(newName);
    }

    private static String requireValidName(String candidate) {
        Objects.requireNonNull(candidate, "name must not be null");
        String trimmed = candidate.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Ingredient name must be between 1 and " + MAX_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    public IngredientId id() {
        return new IngredientId(id);
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String name() {
        return name;
    }

    public Dimension dimension() {
        return dimension;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public IngredientView toView() {
        return new IngredientView(id(), name, dimension, createdAt);
    }
}
