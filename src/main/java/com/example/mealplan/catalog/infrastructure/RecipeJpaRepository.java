package com.example.mealplan.catalog.infrastructure;

import com.example.mealplan.catalog.api.RecipeStatus;
import com.example.mealplan.catalog.domain.Recipe;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The queries that bring the lines use {@code join fetch} for two reasons: with
 * {@code open-in-view: false} the lines have to be loaded inside the transaction, and without the
 * fetch the recipe listing would be an obvious N+1. The {@code distinct} is required when fetching
 * a collection.
 *
 * <p>The status filter is two derived queries rather than one with a nullable parameter. The
 * {@code (:param is null or field = :param)} pattern is convenient, but against PostgreSQL it can
 * fail with "could not determine data type of parameter" depending on how Hibernate infers the type
 * of the null, and it fails in the first integration test rather than at compile time.
 */
public interface RecipeJpaRepository extends JpaRepository<Recipe, UUID> {

    /** Without the lines: enough to decide on the status or to title an entry. */
    Optional<Recipe> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Query("select distinct r from Recipe r left join fetch r.lines where r.id = :id and r.ownerId = :ownerId")
    Optional<Recipe> findWithLinesByIdAndOwnerId(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    @Query("select distinct r from Recipe r left join fetch r.lines where r.ownerId = :ownerId")
    List<Recipe> findAllWithLinesByOwner(@Param("ownerId") UUID ownerId);

    @Query("select distinct r from Recipe r left join fetch r.lines "
         + "where r.ownerId = :ownerId and r.status = :status")
    List<Recipe> findAllWithLinesByOwnerAndStatus(@Param("ownerId") UUID ownerId,
                                                  @Param("status") RecipeStatus status);

    /**
     * Filters by owner as well, even though the ingredient identifier was already narrowed to the
     * owner by the check before it: the rule that every query filters by owner admits no exception
     * that has to be justified by reading the service that calls it.
     */
    @Query("select count(distinct l.recipe.id) from RecipeLine l "
         + "where l.ingredientId = :ingredientId and l.recipe.ownerId = :ownerId")
    long countRecipesUsingIngredient(@Param("ownerId") UUID ownerId,
                                     @Param("ingredientId") UUID ingredientId);

    /** Bulk resolution of titles for planning, without bringing the lines. */
    List<Recipe> findByIdInAndOwnerId(Collection<UUID> ids, UUID ownerId);
}
