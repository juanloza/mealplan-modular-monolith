package com.example.mealplan.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The metadata and the security scheme of the published contract.
 *
 * <p>The bearer scheme is applied globally, so every operation documents that it needs a token. The
 * two public routes cancel that requirement with an annotation on their own controller, which is
 * the only place where the exception belongs.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI mealplanOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mealplan API")
                        .version("1.0.0")
                        .description("Meal planning with pantry control. Cooking a plan entry scales "
                                + "the recipe to the planned servings and subtracts the result from "
                                + "the pantry, in a single transaction.")
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .servers(List.of(new Server()
                        .url("http://localhost:8080")
                        .description("Local development")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
