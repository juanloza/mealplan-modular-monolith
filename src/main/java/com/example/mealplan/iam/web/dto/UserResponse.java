package com.example.mealplan.iam.web.dto;

import com.example.mealplan.iam.application.UserView;
import java.util.UUID;

/** Everything a caller learns about the account it just created. The hash is never part of it. */
public record UserResponse(UUID id, String email) {

    public static UserResponse of(UserView view) {
        return new UserResponse(view.id(), view.email());
    }
}
