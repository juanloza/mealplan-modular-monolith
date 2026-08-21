package com.example.mealplan.pantry.application;

import com.example.mealplan.catalog.api.IngredientAmount;
import com.example.mealplan.catalog.api.IngredientDirectory;
import com.example.mealplan.catalog.api.IngredientId;
import com.example.mealplan.catalog.api.IngredientView;
import com.example.mealplan.pantry.api.PantryStock;
import com.example.mealplan.pantry.domain.PantryItem;
import com.example.mealplan.pantry.infrastructure.PantryItemJpaRepository;
import com.example.mealplan.shared.domain.DomainException;
import com.example.mealplan.shared.domain.ErrorCode;
import com.example.mealplan.shared.domain.Quantity;
import com.example.mealplan.shared.domain.Unit;
import com.example.mealplan.shared.domain.UserId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock, and the invariant that cannot be broken: no amount ever goes below zero, and cooking
 * subtracts everything or nothing.
 *
 * <p>The dimension of every amount comes from the catalogue, through its public contract. This
 * module never reads the ingredient table itself, which is what keeps the dependency one way.
 */
@Service
@Transactional
public class PantryService implements PantryStock {

    private final PantryItemJpaRepository items;
    private final IngredientDirectory ingredients;
    private final Clock clock;

    public PantryService(PantryItemJpaRepository items,
                         IngredientDirectory ingredients,
                         Clock clock) {
        this.items = items;
        this.ingredients = ingredients;
        this.clock = clock;
    }

    /**
     * Sets the amount, creating the row if there was none.
     *
     * <p>It sets a value, it does not add to one, and that is what makes it idempotent: repeating it
     * with the same body leaves the same state, down to {@code updatedAt} and the version.
     *
     * @throws DomainException {@code INGREDIENT_NOT_FOUND}, {@code UNIT_DIMENSION_MISMATCH},
     *                         {@code AMOUNT_OUT_OF_RANGE}
     */
    public PantryItemView setAmount(UserId owner, IngredientId ingredientId, BigDecimal amount, Unit unit) {
        IngredientView ingredient = ingredients.require(owner, ingredientId);
        requireSameDimension(ingredient, unit);

        // Zero is allowed and means "I have run out", which is not the same as having no row.
        Quantity quantity = Quantity.of(amount, unit);
        Instant now = clock.instant();

        PantryItem item = items.findByOwnerIdAndIngredientId(owner.value(), ingredientId.value())
                .orElse(null);
        if (item == null) {
            item = items.save(new PantryItem(owner, ingredientId, quantity, now));
        } else {
            item.setAmount(quantity, now);
        }
        return toView(item, ingredient);
    }

    /** Sorted by ingredient name ascending, with every name resolved in a single call. */
    @Transactional(readOnly = true)
    public List<PantryItemView> list(UserId owner) {
        List<PantryItem> rows = items.findByOwnerId(owner.value());
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<IngredientId, IngredientView> byId = ingredients.findAllById(owner,
                rows.stream().map(PantryItem::ingredientId).toList());

        return rows.stream()
                .map(row -> toView(row, requireLoaded(byId, row.ingredientId())))
                .sorted(Comparator.comparing(PantryItemView::ingredientName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Checks the ingredient first and the row second, which is why it can answer two different
     * codes: one identifier is not an ingredient of yours, the other is, but you are not keeping
     * track of it. Deleting something that is not there answers 404 rather than 204, because a
     * silent success would hide a mistyped identifier.
     *
     * @throws DomainException {@code INGREDIENT_NOT_FOUND}, {@code PANTRY_ITEM_NOT_FOUND}
     */
    public void remove(UserId owner, IngredientId ingredientId) {
        ingredients.require(owner, ingredientId);

        PantryItem item = items.findByOwnerIdAndIngredientId(owner.value(), ingredientId.value())
                .orElseThrow(() -> new DomainException(ErrorCode.PANTRY_ITEM_NOT_FOUND,
                        "You are not keeping track of that ingredient."));
        items.delete(item);
    }

    /**
     * Checks every line before writing anything, which is not a matter of elegance: it is what
     * guarantees that the reported shortfalls are the complete list, and that a failure leaves no
     * half applied subtraction even inside the transaction.
     */
    @Override
    public void consume(UserId owner, List<IngredientAmount> amounts) {
        if (amounts.isEmpty()) {
            return;
        }

        // The order is fixed here and nowhere else: the catalogue returns the lines in recipe order
        // and promises nothing more. What actually orders the statements against the database is
        // hibernate.order_updates, by primary key; this order is what makes the shortfall list
        // deterministic and keeps any future explicit flush from depending on chance.
        List<IngredientAmount> ordered = amounts.stream()
                .sorted(Comparator.comparing(amount -> amount.ingredientId().value()))
                .toList();

        Set<UUID> rawIds = ordered.stream()
                .map(amount -> amount.ingredientId().value())
                .collect(Collectors.toSet());

        Map<UUID, PantryItem> rows = new LinkedHashMap<>();
        for (PantryItem row : items.findByOwnerIdAndIngredientIdIn(owner.value(), rawIds)) {
            rows.put(row.ingredientId().value(), row);
        }

        List<Map<String, Object>> shortfalls = new ArrayList<>();
        for (IngredientAmount required : ordered) {
            Quantity available = availableFor(rows, required);
            if (available.isLessThan(required.quantity())) {
                shortfalls.add(shortfallOf(required, available));
            }
        }
        if (!shortfalls.isEmpty()) {
            throw new DomainException(ErrorCode.INSUFFICIENT_STOCK,
                    "Not enough stock to cook this plan entry.",
                    Map.of("shortfalls", shortfalls));
        }

        Instant now = clock.instant();
        for (IngredientAmount required : ordered) {
            rows.get(required.ingredientId().value()).consume(required.quantity(), now);
        }
    }

    private static Quantity availableFor(Map<UUID, PantryItem> rows, IngredientAmount required) {
        PantryItem row = rows.get(required.ingredientId().value());
        return row == null
                ? Quantity.zero(required.quantity().dimension())
                : row.amount(required.quantity().dimension());
    }

    /**
     * Values ready for JSON and never domain objects: a {@code Quantity} dropped in here would
     * serialise as its internal shape, which is not what the API promises. The conversion belongs
     * to whoever knows the data, so that the error factory has to know no type of any module.
     */
    private static Map<String, Object> shortfallOf(IngredientAmount required, Quantity available) {
        return Map.of(
                "ingredientId", required.ingredientId().value().toString(),
                "ingredientName", required.ingredientName(),
                "required", required.quantity().toCanonicalString(),
                "available", available.toCanonicalString(),
                "unit", required.quantity().dimension().canonicalUnit().name());
    }

    private static void requireSameDimension(IngredientView ingredient, Unit unit) {
        if (unit.dimension() != ingredient.dimension()) {
            throw new DomainException(ErrorCode.UNIT_DIMENSION_MISMATCH,
                    "That unit does not measure what this ingredient is measured in.",
                    Map.of("expectedDimension", ingredient.dimension().name(),
                           "actualDimension", unit.dimension().name()));
        }
    }

    private static PantryItemView toView(PantryItem item, IngredientView ingredient) {
        return new PantryItemView(item.ingredientId(), ingredient.name(),
                item.amount(ingredient.dimension()), item.updatedAt());
    }

    /**
     * A row whose ingredient is gone is impossible while the foreign key cascades the delete, so it
     * is treated as the bug it would be rather than dressed up as a business case.
     */
    private static IngredientView requireLoaded(Map<IngredientId, IngredientView> byId, IngredientId id) {
        IngredientView ingredient = byId.get(id);
        if (ingredient == null) {
            throw new IllegalStateException("Pantry row refers to an ingredient that is not in the catalogue: " + id);
        }
        return ingredient;
    }
}
