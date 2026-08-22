package com.example.mealplan.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A clock frozen at a known instant, for the tests whose expectations depend on the date.
 *
 * <p>It is a {@code @TestConfiguration} and not a plain {@code @Configuration} because this package
 * falls inside the component scan of the application: as a normal configuration it would be picked
 * up by every context and freeze the clock of the whole suite. Only the classes that import it get
 * it.
 *
 * <p>That this works at all depends on the token validator receiving this same clock. If it read
 * the wall clock instead, every token issued here would be born expired and every authenticated
 * request of these classes would answer 401.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfig {

    public static final Instant FIXED_INSTANT = Instant.parse("2026-06-15T12:00:00Z");

    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    }
}
