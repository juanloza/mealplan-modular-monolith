package com.example.mealplan.iam.domain;

import com.example.mealplan.shared.domain.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A registered account.
 *
 * <p>The entity knows nothing about hashing or normalising: the email arrives already trimmed and
 * lowercased, and the password already hashed. Both are the service's job, because both need
 * collaborators the domain does not have.
 *
 * <p>There is no {@code toView()} here, unlike other modules: the view type lives in the
 * application layer, and this module has no public contract package to put it in, so returning one
 * would make the domain depend on the layer above it.
 */
@Entity
@Table(name = "app_user")
public class User {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected User() {
        // Required by JPA.
    }

    public User(String normalizedEmail, String passwordHash, Instant now) {
        this.id = UUID.randomUUID();
        this.email = Objects.requireNonNull(normalizedEmail, "email must not be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
    }

    public UserId id() {
        return new UserId(id);
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
