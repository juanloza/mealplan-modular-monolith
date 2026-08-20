package com.example.mealplan.iam.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The cost is configurable for one reason only: at the production cost of 10, every encode and
 * every match takes on the order of 100 ms, and an integration suite that registers dozens of
 * accounts would take minutes. The low cost lives in the test profile and nowhere else.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder(SecurityProperties properties) {
        return new BCryptPasswordEncoder(properties.bcryptStrength());
    }
}
