package com.example.mealplan.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the single {@link Clock} of the application.
 *
 * <p>This is the only place in {@code src/main} that reads the system time. Everything else
 * receives the clock through its constructor, including the expiry validation of the access token:
 * if any piece read the wall clock on its own, a test with a fixed clock would see some components
 * in one instant and the rest in another.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
