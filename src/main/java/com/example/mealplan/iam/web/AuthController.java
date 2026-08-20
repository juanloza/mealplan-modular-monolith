package com.example.mealplan.iam.web;

import com.example.mealplan.iam.application.AuthenticationService;
import com.example.mealplan.iam.web.dto.LoginRequest;
import com.example.mealplan.iam.web.dto.RegisterRequest;
import com.example.mealplan.iam.web.dto.TokenResponse;
import com.example.mealplan.iam.web.dto.UserResponse;
import com.example.mealplan.shared.domain.DomainException;
import com.example.mealplan.shared.domain.ErrorCode;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two public routes of the API.
 *
 * <p>{@code @SecurityRequirements} without arguments cancels the global bearer requirement of the
 * OpenAPI document for this class only, which is why these two operations are documented as taking
 * no token while every other one does.
 */
@RestController
@RequestMapping("/api/auth")
@SecurityRequirements
public class AuthController {

    /**
     * BCrypt only considers the first 72 bytes of a password, and the encoder rejects anything
     * longer by throwing. Bean Validation counts characters, not bytes, so a password of 40 emoji
     * passes {@code @Size(max = 72)} and would still reach the encoder.
     */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final AuthenticationService authentication;

    public AuthController(AuthenticationService authentication) {
        this.authentication = authentication;
    }

    /**
     * Answers 201 without a {@code Location} header, the single declared exception to the rule:
     * there is no endpoint to read an account back from, so there is nothing to point at.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        requirePasswordFitsBcrypt(request.password());
        return UserResponse.of(authentication.register(request.email(), request.password()));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        requirePasswordFitsBcrypt(request.password());
        return TokenResponse.of(authentication.login(request.email(), request.password()));
    }

    /**
     * The only place in the application where a controller throws a domain exception. The shape is
     * copied deliberately from what Bean Validation produces, so that a caller cannot tell which of
     * the two limits on the password rejected the request.
     *
     * @throws DomainException {@code VALIDATION_FAILED}
     */
    private static void requirePasswordFitsBcrypt(String password) {
        if (password != null && password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "The request body is not valid.",
                    Map.of("errors", List.of(Map.of(
                            "field", "password",
                            "message", "must be at most 72 bytes when encoded as UTF-8"))));
        }
    }
}
