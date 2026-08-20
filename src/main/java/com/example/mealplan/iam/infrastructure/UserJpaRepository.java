package com.example.mealplan.iam.infrastructure;

import com.example.mealplan.iam.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<User, UUID> {

    /** The email is stored already normalised, so this is a plain lookup and not a case fold. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
