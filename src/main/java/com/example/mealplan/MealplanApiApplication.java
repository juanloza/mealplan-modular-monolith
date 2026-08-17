package com.example.mealplan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point. Component scanning from this package is the whole wiring story: there is no manual
 * composition file, and every collaborator is injected through a constructor.
 *
 * <p>{@code @ConfigurationPropertiesScan} is what registers the typed configuration records without
 * having to enumerate them in an {@code @EnableConfigurationProperties}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MealplanApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MealplanApiApplication.class, args);
    }
}
