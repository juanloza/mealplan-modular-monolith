package com.example.mealplan.shared.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * The only Jackson configuration class of the application, and it exists for exactly one reason.
 *
 * <p>Everything else is expressed as properties, but the ban on coercion cannot be. By default
 * Jackson turns a JSON number into a {@code String} field without complaining, so
 * {@code {"amount": 350}} would arrive as {@code "350"}, satisfy the pattern of the amount field,
 * and defeat the decision that amounts travel as text. With this, the same body is rejected as
 * unreadable and comes back as a 400.
 *
 * <p>Spring Boot 4 autoconfigures a Jackson 3 {@code JsonMapper}, so the extension point is
 * {@code JsonMapperBuilderCustomizer}. Anything copied from a Spring Boot 3 example that mentions
 * {@code ObjectMapper} does not compile here.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer strictCoercion() {
        return builder -> builder.withCoercionConfig(LogicalType.Textual, config -> config
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }
}
