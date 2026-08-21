package com.example.mealplan.pantry.infrastructure;

import com.example.mealplan.pantry.domain.PantryItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Rows are addressed by owner and ingredient, never by their own identifier. */
public interface PantryItemJpaRepository extends JpaRepository<PantryItem, UUID> {

    Optional<PantryItem> findByOwnerIdAndIngredientId(UUID ownerId, UUID ingredientId);

    List<PantryItem> findByOwnerIdAndIngredientIdIn(UUID ownerId, Collection<UUID> ingredientIds);

    List<PantryItem> findByOwnerId(UUID ownerId);
}
