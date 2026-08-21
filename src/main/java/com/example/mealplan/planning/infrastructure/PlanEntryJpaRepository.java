package com.example.mealplan.planning.infrastructure;

import com.example.mealplan.planning.domain.PlanEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * No query parameter here is nullable, on purpose. The {@code (:param is null or field = :param)}
 * pattern is convenient, but against PostgreSQL it can fail with "could not determine data type of
 * parameter" depending on how Hibernate infers the type of the null. The service substitutes two
 * open constants for the missing ends of the range instead.
 */
public interface PlanEntryJpaRepository extends JpaRepository<PlanEntry, UUID> {

    Optional<PlanEntry> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<PlanEntry> findByOwnerIdAndPlannedForBetweenOrderByPlannedForAscCreatedAtAsc(
            UUID ownerId, LocalDate from, LocalDate to);
}
