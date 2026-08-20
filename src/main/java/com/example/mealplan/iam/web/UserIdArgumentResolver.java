package com.example.mealplan.iam.web;

import com.example.mealplan.shared.domain.UserId;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Supplies the caller identity to any controller that declares a {@link UserId} parameter.
 *
 * <p>This is what lets the controllers of the other modules take {@code UserId owner} without
 * importing anything from this module: the type lives in the shared package, so no boundary is
 * crossed.
 */
@Component
public class UserIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return UserId.class.equals(parameter.getParameterType());
    }

    /**
     * A missing authentication here is not a business case and deliberately not a 401: it means a
     * {@code UserId} parameter was placed on a public route, which is a wiring bug that should be
     * visible immediately rather than disguised as a rejected request.
     */
    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new IllegalStateException(
                    "No authenticated JWT in the security context while resolving a UserId parameter");
        }
        return UserId.of(token.getToken().getSubject());
    }
}
