package com.example.mealplan.catalog.domain;

import com.example.mealplan.catalog.api.IngredientId;
import com.example.mealplan.catalog.api.RecipeId;
import com.example.mealplan.catalog.api.RecipeStatus;
import com.example.mealplan.catalog.api.RecipeSummary;
import com.example.mealplan.shared.domain.DomainException;
import com.example.mealplan.shared.domain.ErrorCode;
import com.example.mealplan.shared.domain.UserId;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A recipe and its state machine.
 *
 * <p>The transitions live here and not in the service, which is what lets them be covered by a
 * plain unit test with no Spring and no database.
 *
 * <p>A published recipe is immutable: neither its title, nor its servings, nor its lines can
 * change. A plan entry that has already been cooked recorded a consumption computed from those
 * lines, and if they changed afterwards that consumption could no longer be explained. Editing a
 * published recipe means archiving it and writing another one.
 *
 * <p>There is no {@code @Version}: none of these operations can produce an inconsistent state under
 * concurrency. Two simultaneous edits of a draft leave the last one, and two simultaneous publishes
 * leave the recipe published with one of them getting a 409 from the status check inside its own
 * transaction. A version here would only manufacture conflicts without protecting anything.
 */
@Entity
@Table(name = "recipe")
public class Recipe {

    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_SERVINGS = 50;
    private static final int MAX_LINES = 50;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "title", nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(name = "servings", nullable = false)
    private int servings;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RecipeStatus status;

    /**
     * The only JPA association in the application, because it is the only one that stays inside a
     * single aggregate. {@code orphanRemoval} is what makes a replacement delete the lines that no
     * longer appear, but Hibernate flushes the inserts of the new lines <em>before</em> those
     * deletes, so old and new rows coexist mid-transaction. That is why the two unique constraints
     * on the line table are deferred until commit; verified by replacing the content of a draft,
     * which reuses line index 0 and fails immediately without it.
     */
    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position asc")
    private List<RecipeLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Recipe() {
        // Required by JPA.
    }

    /** Born a draft, with no lines. */
    public Recipe(UserId owner, String title, int servings, Instant now) {
        this.id = UUID.randomUUID();
        this.ownerId = Objects.requireNonNull(owner, "owner must not be null").value();
        this.title = requireValidTitle(title);
        this.servings = requireValidServings(servings);
        this.status = RecipeStatus.DRAFT;
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    /**
     * Complete replacement of the content, allowed only from {@code DRAFT}.
     *
     * <p>The amounts arrive already resolved to the dimension of their ingredient: the service
     * checked that before calling, because it is the only one that can read the ingredient table.
     * What is checked here is what can be decided by looking at the lines alone.
     *
     * @throws DomainException {@code RECIPE_NOT_EDITABLE}, {@code DUPLICATE_RECIPE_LINE},
     *                         {@code AMOUNT_NOT_POSITIVE}
     */
    public void replaceContent(String title, int servings, List<LineSpec> specs, Instant now) {
        if (status != RecipeStatus.DRAFT) {
            throw new DomainException(ErrorCode.RECIPE_NOT_EDITABLE,
                    "Only a draft recipe can be edited.");
        }
        Objects.requireNonNull(specs, "specs must not be null");
        if (specs.size() > MAX_LINES) {
            throw new IllegalArgumentException("A recipe may have at most " + MAX_LINES + " lines");
        }
        requireNoDuplicates(specs);
        requireAllPositive(specs);

        this.title = requireValidTitle(title);
        this.servings = requireValidServings(servings);

        lines.clear();
        for (int position = 0; position < specs.size(); position++) {
            LineSpec spec = specs.get(position);
            lines.add(new RecipeLine(this, position, spec.ingredientId(), spec.quantity().milli()));
        }
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /**
     * @throws DomainException {@code INVALID_RECIPE_TRANSITION} from any state but {@code DRAFT},
     *                         {@code RECIPE_HAS_NO_LINES} if there is nothing to cook
     */
    public void publish(Instant now) {
        if (status != RecipeStatus.DRAFT) {
            throw invalidTransition("publish");
        }
        if (lines.isEmpty()) {
            throw new DomainException(ErrorCode.RECIPE_HAS_NO_LINES,
                    "A recipe needs at least one line before it can be published.");
        }
        this.status = RecipeStatus.PUBLISHED;
        this.publishedAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    /**
     * @throws DomainException {@code INVALID_RECIPE_TRANSITION} from any state but {@code PUBLISHED}
     */
    public void archive(Instant now) {
        if (status != RecipeStatus.PUBLISHED) {
            throw invalidTransition("archive");
        }
        this.status = RecipeStatus.ARCHIVED;
        this.archivedAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    /**
     * @throws DomainException {@code RECIPE_NOT_DELETABLE} unless the recipe is still a draft
     */
    public void requireDeletable() {
        if (status != RecipeStatus.DRAFT) {
            throw new DomainException(ErrorCode.RECIPE_NOT_DELETABLE,
                    "Only a draft recipe can be deleted.");
        }
    }

    private static void requireNoDuplicates(List<LineSpec> specs) {
        Set<IngredientId> seen = new HashSet<>();
        for (LineSpec spec : specs) {
            if (!seen.add(spec.ingredientId())) {
                throw new DomainException(ErrorCode.DUPLICATE_RECIPE_LINE,
                        "A recipe cannot list the same ingredient twice.");
            }
        }
    }

    private static void requireAllPositive(List<LineSpec> specs) {
        for (LineSpec spec : specs) {
            if (spec.quantity().isZero()) {
                throw new DomainException(ErrorCode.AMOUNT_NOT_POSITIVE,
                        "A recipe line must call for more than zero.");
            }
        }
    }

    /**
     * Repeating a transition is not idempotent on purpose, and this is where the 409 comes from.
     * Publishing something already published is almost always a client mistake, and finding out is
     * cheaper than a 200 that did nothing.
     */
    private DomainException invalidTransition(String operation) {
        return new DomainException(ErrorCode.INVALID_RECIPE_TRANSITION,
                "A recipe in " + status + " cannot " + operation + ".");
    }

    private static String requireValidTitle(String candidate) {
        Objects.requireNonNull(candidate, "title must not be null");
        String trimmed = candidate.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException(
                    "Recipe title must be between 1 and " + MAX_TITLE_LENGTH + " characters");
        }
        return trimmed;
    }

    private static int requireValidServings(int candidate) {
        if (candidate < 1 || candidate > MAX_SERVINGS) {
            throw new IllegalArgumentException(
                    "Servings must be between 1 and " + MAX_SERVINGS + ", was " + candidate);
        }
        return candidate;
    }

    public RecipeId id() {
        return new RecipeId(id);
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String title() {
        return title;
    }

    public int servings() {
        return servings;
    }

    public RecipeStatus status() {
        return status;
    }

    public List<RecipeLine> lines() {
        return Collections.unmodifiableList(lines);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /** @return null while the recipe has not been published */
    public Instant publishedAt() {
        return publishedAt;
    }

    /** @return null while the recipe has not been archived */
    public Instant archivedAt() {
        return archivedAt;
    }

    public RecipeSummary toSummary() {
        return new RecipeSummary(id(), title, servings, status);
    }
}
