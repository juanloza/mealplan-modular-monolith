package com.example.mealplan.planning.domain;

import com.example.mealplan.catalog.api.RecipeId;
import com.example.mealplan.planning.api.PlanEntryId;
import com.example.mealplan.planning.api.PlanEntryStatus;
import com.example.mealplan.shared.domain.DomainException;
import com.example.mealplan.shared.domain.ErrorCode;
import com.example.mealplan.shared.domain.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * One recipe planned for one day, in a number of servings of its own.
 *
 * <p>It does not copy the recipe, it holds its identifier and asks the catalogue when it needs
 * anything. What protects it from the recipe changing underneath is that a published recipe is
 * immutable, not a snapshot taken here.
 *
 * <p>The version guards the race of cooking the same entry twice at once. With {@code READ
 * COMMITTED} both requests read {@code PLANNED} and both go ahead; the {@code UPDATE ... WHERE id =
 * ? AND version = ?} of the second affects no rows, and its whole transaction rolls back, taking
 * its pantry subtraction with it. The case that makes this guard indispensable is cooking against
 * cancelling: without it both could commit and leave a cancelled entry with the stock already
 * gone.
 *
 * <p>The date window is not checked here: it needs the clock, and this entity never receives one.
 */
@Entity
@Table(name = "plan_entry")
public class PlanEntry {

    private static final int MAX_SERVINGS = 50;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "recipe_id", nullable = false, updatable = false)
    private UUID recipeId;

    @Column(name = "planned_for", nullable = false)
    private LocalDate plannedFor;

    @Column(name = "servings", nullable = false)
    private int servings;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PlanEntryStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "cooked_at")
    private Instant cookedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected PlanEntry() {
        // Required by JPA.
    }

    public PlanEntry(UserId owner, RecipeId recipeId, LocalDate plannedFor, int servings, Instant now) {
        this.id = UUID.randomUUID();
        this.ownerId = Objects.requireNonNull(owner, "owner must not be null").value();
        this.recipeId = Objects.requireNonNull(recipeId, "recipeId must not be null").value();
        this.plannedFor = Objects.requireNonNull(plannedFor, "plannedFor must not be null");
        this.servings = requireValidServings(servings);
        this.status = PlanEntryStatus.PLANNED;
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    /**
     * Moves the entry, changes how many servings it is for, or both. A null argument means "leave
     * this alone", and arguments that match what is already there leave no trace at all:
     * {@code updatedAt} is untouched and, with no dirty field, the version does not move either.
     *
     * @throws DomainException {@code PLAN_ENTRY_NOT_PLANNED} from any state but {@code PLANNED}
     */
    public void reschedule(LocalDate plannedFor, Integer servings, Instant now) {
        requirePlanned();

        boolean changed = false;
        if (plannedFor != null && !plannedFor.equals(this.plannedFor)) {
            this.plannedFor = plannedFor;
            changed = true;
        }
        if (servings != null && servings != this.servings) {
            this.servings = requireValidServings(servings);
            changed = true;
        }
        if (changed) {
            this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        }
    }

    /**
     * @throws DomainException {@code PLAN_ENTRY_NOT_PLANNED}
     */
    public void markCooked(Instant now) {
        requirePlanned();
        this.status = PlanEntryStatus.COOKED;
        this.cookedAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    /**
     * @throws DomainException {@code PLAN_ENTRY_NOT_PLANNED}
     */
    public void cancel(Instant now) {
        requirePlanned();
        this.status = PlanEntryStatus.CANCELLED;
        this.cancelledAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    /**
     * A cancelled entry subtracted nothing and can be thrown away; a cooked one cannot, and there
     * is no way to uncook it either, because giving the stock back would mean knowing that nobody
     * has touched it since.
     *
     * @throws DomainException {@code PLAN_ENTRY_NOT_DELETABLE} if the entry was cooked
     */
    public void requireDeletable() {
        if (status == PlanEntryStatus.COOKED) {
            throw new DomainException(ErrorCode.PLAN_ENTRY_NOT_DELETABLE,
                    "A cooked plan entry cannot be deleted.");
        }
    }

    /**
     * The same guard the transitions use, exposed because cooking has to check it <em>before</em>
     * touching the catalogue and the pantry. Without an early check, cooking an entry that is
     * already cooked would do all the work and fail at the last step, and could answer
     * {@code INSUFFICIENT_STOCK} for something that was never a stock problem.
     *
     * @throws DomainException {@code PLAN_ENTRY_NOT_PLANNED}
     */
    public void requirePlanned() {
        if (status != PlanEntryStatus.PLANNED) {
            throw new DomainException(ErrorCode.PLAN_ENTRY_NOT_PLANNED,
                    "This plan entry is " + status + " and can no longer change.");
        }
    }

    private static int requireValidServings(int candidate) {
        if (candidate < 1 || candidate > MAX_SERVINGS) {
            throw new IllegalArgumentException(
                    "Servings must be between 1 and " + MAX_SERVINGS + ", was " + candidate);
        }
        return candidate;
    }

    public PlanEntryId id() {
        return new PlanEntryId(id);
    }

    public UUID ownerId() {
        return ownerId;
    }

    public RecipeId recipeId() {
        return new RecipeId(recipeId);
    }

    public LocalDate plannedFor() {
        return plannedFor;
    }

    public int servings() {
        return servings;
    }

    public PlanEntryStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /** @return null unless the entry was cooked */
    public Instant cookedAt() {
        return cookedAt;
    }

    /** @return null unless the entry was cancelled */
    public Instant cancelledAt() {
        return cancelledAt;
    }

    public Long version() {
        return version;
    }
}
