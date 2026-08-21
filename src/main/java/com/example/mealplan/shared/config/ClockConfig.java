package com.example.mealplan.shared.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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

    /**
     * Truncated to microseconds, which is the resolution of a {@code timestamptz} column.
     *
     * <p>Without this, an instant taken in memory carries nanoseconds that the database rounds on
     * the way in, so the response to the request that creates a row reports a timestamp that
     * differs, in its last digits, from the one every later read returns. Setting a pantry amount
     * twice with the same body then answers two different {@code updatedAt} values although nothing
     * was written the second time, which contradicts the idempotence the endpoint promises.
     */
    @Bean
    public Clock clock() {
        return new MicrosecondClock(ZoneOffset.UTC);
    }

    private static final class MicrosecondClock extends Clock {

        private final ZoneId zone;

        private MicrosecondClock(ZoneId zone) {
            this.zone = zone;
        }

        @Override
        public Instant instant() {
            return Clock.system(zone).instant().truncatedTo(ChronoUnit.MICROS);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId other) {
            return new MicrosecondClock(other);
        }
    }
}
