package com.example.mealplan.support;

import com.example.mealplan.pantry.api.PantryStock;
import com.example.mealplan.pantry.application.PantryService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Wraps the real pantry so that a test can stop a transaction at the one instant that matters.
 *
 * <p>It is a top level class in this package and not a class nested inside the test, which is what
 * makes the reasoning behind {@code @TestConfiguration} apply at all: this package falls inside the
 * component scan of the application, so as a plain {@code @Configuration} it would be picked up by
 * every context and leave the barrier in place where nothing expects it. A nested class would not
 * be scanned and that argument would not hold.
 */
@TestConfiguration(proxyBeanMethods = false)
public class BarrierConfig {

    /**
     * The decorator stops after the delegate has read and changed the pantry rows in memory and
     * before the transaction commits. Anywhere earlier and the two transactions would not yet have
     * read the same version; anywhere later and one of them would already have written.
     */
    @Bean
    @Primary
    PantryStock barrierPantryStock(PantryService delegate, TestBarrier barrier) {
        return (owner, amounts) -> {
            delegate.consume(owner, amounts);
            barrier.arriveAndWait();
        };
    }

    @Bean
    TestBarrier testBarrier() {
        return new TestBarrier();
    }
}
