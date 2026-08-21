package com.example.mealplan.catalog.infrastructure;

import com.example.mealplan.catalog.domain.Ingredient;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every lookup by identifier filters by owner as well. There is no bare {@code findById} anywhere in
 * this application: that is what makes "does not exist" and "is not yours" the same code path, and
 * therefore the same 404.
 */
public interface IngredientJpaRepository extends JpaRepository<Ingredient, UUID> {

    Optional<Ingredient> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Ingredient> findByOwnerIdOrderByNameAsc(UUID ownerId);

    List<Ingredient> findByOwnerIdAndIdIn(UUID ownerId, Collection<UUID> ids);

    /** For creating: there is no row of its own to exclude yet. */
    boolean existsByOwnerIdAndNameIgnoreCase(UUID ownerId, String name);

    /**
     * For renaming: excludes the ingredient itself, because renaming it to what it is already
     * called is not a conflict. Without the exclusion it would find its own row and refuse.
     */
    boolean existsByOwnerIdAndNameIgnoreCaseAndIdNot(UUID ownerId, String name, UUID id);
}
