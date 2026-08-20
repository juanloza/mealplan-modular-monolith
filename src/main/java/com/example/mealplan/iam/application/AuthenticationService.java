package com.example.mealplan.iam.application;

import com.example.mealplan.iam.domain.User;
import com.example.mealplan.iam.infrastructure.UserJpaRepository;
import com.example.mealplan.shared.domain.DomainException;
import com.example.mealplan.shared.domain.ErrorCode;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthenticationService {

    /**
     * Compared against when the account does not exist, so that a failed login costs the same
     * whether or not the email is registered. Computed once, on a constant.
     */
    private final String decoyHash;

    private final UserJpaRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtAccessTokenIssuer tokenIssuer;
    private final Clock clock;

    public AuthenticationService(UserJpaRepository users,
                                 PasswordEncoder passwordEncoder,
                                 JwtAccessTokenIssuer tokenIssuer,
                                 Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
        this.decoyHash = passwordEncoder.encode("decoy-password-for-constant-time-login");
    }

    /**
     * The pre-check gives a clean error in the ordinary case; the caught violation is what actually
     * guarantees uniqueness, because two simultaneous registrations both pass the pre-check. This
     * is one of only two rules in the application where the database decides and the domain follows.
     *
     * @throws DomainException {@code EMAIL_ALREADY_REGISTERED}
     */
    public UserView register(String email, String rawPassword) {
        String normalized = normalize(email);
        if (users.existsByEmail(normalized)) {
            throw emailTaken();
        }
        try {
            User saved = users.saveAndFlush(
                    new User(normalized, passwordEncoder.encode(rawPassword), clock.instant()));
            return new UserView(saved.id().value(), saved.email());
        } catch (DataIntegrityViolationException ex) {
            throw emailTaken();
        }
    }

    /**
     * Both failure branches produce the same code, the same status and the same body, so an unknown
     * email and a wrong password are indistinguishable from outside. They are also indistinguishable
     * in time: without the decoy comparison, a missing account would answer in microseconds and an
     * existing one in the time of a BCrypt match, turning login into an oracle for enumerating
     * registered accounts.
     *
     * @throws DomainException {@code INVALID_CREDENTIALS}
     */
    @Transactional(readOnly = true)
    public AccessToken login(String email, String rawPassword) {
        Optional<User> found = users.findByEmail(normalize(email));

        if (found.isEmpty()) {
            passwordEncoder.matches(rawPassword, decoyHash);
            throw invalidCredentials();
        }
        User user = found.get();
        if (!passwordEncoder.matches(rawPassword, user.passwordHash())) {
            throw invalidCredentials();
        }
        return tokenIssuer.issue(user);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static DomainException emailTaken() {
        return new DomainException(ErrorCode.EMAIL_ALREADY_REGISTERED, "That email is already registered.");
    }

    private static DomainException invalidCredentials() {
        return new DomainException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password.");
    }
}
