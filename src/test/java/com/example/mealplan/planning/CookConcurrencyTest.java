package com.example.mealplan.planning;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mealplan.planning.api.PlanEntryId;
import com.example.mealplan.planning.api.PlanEntryStatus;
import com.example.mealplan.planning.application.CookPlanEntryService;
import com.example.mealplan.planning.application.PlanEntryService;
import com.example.mealplan.shared.domain.Dimension;
import com.example.mealplan.shared.domain.Unit;
import com.example.mealplan.shared.domain.UserId;
import com.example.mealplan.support.AbstractIntegrationTest;
import com.example.mealplan.support.BarrierConfig;
import com.example.mealplan.support.TestBarrier;
import com.example.mealplan.support.TestFixtures;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The test that justifies half the decisions in this application: two transactions cooking at the
 * same time, against a real PostgreSQL, with real threads.
 *
 * <p>Three races, and two different guards. The same entry cooked twice, and cooking against
 * cancelling, are held by the version of the plan entry; two different entries that share an
 * ingredient are held by the version of the pantry row. Neither guard replaces the other, and the
 * scenario that proves it is the third one: without a version on the entry, a cancelled entry could
 * end up with its stock already gone.
 */
@Import(BarrierConfig.class)
class CookConcurrencyTest extends AbstractIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
    private static final long ONE_KILO = 1_000_000L;

    @Autowired
    private CookPlanEntryService cooking;

    @Autowired
    private PlanEntryService entries;

    @Autowired
    private TestBarrier barrier;

    @Autowired
    private JdbcTemplate jdbc;

    private String token;
    private UserId owner;
    private UUID flour;

    @BeforeEach
    void createScenario() throws Exception {
        token = fixtures.registerAndLogin("chef@example.com");
        owner = new UserId(jdbc.queryForObject(
                "select id from app_user where email = ?", UUID.class, "chef@example.com"));
        flour = fixtures.createIngredient(token, "Flour", Dimension.MASS);
        fixtures.setPantryAmount(token, flour, "1", Unit.KILOGRAM);
    }

    @AfterEach
    void disarmBarrier() {
        // The bean is shared by the three scenarios: leaving it armed would hang the next one.
        barrier.disarm();
    }

    @Test
    @DisplayName("the same plan entry cooked twice at once is cooked once")
    void refusesToCookTheSameEntryTwice() throws Exception {
        UUID recipe = publishedRecipeUsingFlour("Pancakes");
        UUID entry = fixtures.createPlanEntry(token, recipe, TODAY, 4);
        barrier.armForParties(2);

        List<Outcome> outcomes = runTogether(
                () -> cooking.cook(owner, new PlanEntryId(entry)),
                () -> cooking.cook(owner, new PlanEntryId(entry)));

        assertExactlyOneWonAndTheOtherLostTheVersionCheck(outcomes);
        assertThat(statusOf(entry)).isEqualTo(PlanEntryStatus.COOKED.name());
        assertThat(cookedAtOf(entry)).isNotNull();
        assertThat(stockOfFlour()).isEqualTo(650_000L);
        assertNoNegativeStock();
    }

    @Test
    @DisplayName("two entries sharing an ingredient subtract it once, held only by the pantry version")
    void refusesToSubtractTheSameStockTwice() throws Exception {
        UUID pancakes = publishedRecipeUsingFlour("Pancakes");
        UUID crepes = publishedRecipeUsingFlour("Crepes");
        UUID first = fixtures.createPlanEntry(token, pancakes, TODAY, 4);
        UUID second = fixtures.createPlanEntry(token, crepes, TODAY, 4);
        barrier.armForParties(2);

        List<Outcome> outcomes = runTogether(
                () -> cooking.cook(owner, new PlanEntryId(first)),
                () -> cooking.cook(owner, new PlanEntryId(second)));

        assertExactlyOneWonAndTheOtherLostTheVersionCheck(outcomes);
        assertThat(stockOfFlour()).isEqualTo(650_000L);
        assertNoNegativeStock();
    }

    @Test
    @DisplayName("cooking loses against a cancel that commits first, and gives the stock back")
    void refusesToCookAnEntryCancelledMeanwhile() throws Exception {
        UUID recipe = publishedRecipeUsingFlour("Pancakes");
        UUID entry = fixtures.createPlanEntry(token, recipe, TODAY, 4);
        barrier.armForHandoff();

        List<Outcome> outcomes = runTogether(
                () -> cooking.cook(owner, new PlanEntryId(entry)),
                () -> {
                    // Waits until cooking has read the entry and changed the pantry in memory.
                    barrier.awaitArrival();
                    Object cancelled = entries.cancel(owner, new PlanEntryId(entry));
                    barrier.release();
                    return cancelled;
                });

        assertThat(outcomes.get(0).failure()).isNotNull();
        assertThat(rootCauseOf(outcomes.get(0).failure()))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(outcomes.get(1).failure()).isNull();

        assertThat(statusOf(entry)).isEqualTo(PlanEntryStatus.CANCELLED.name());
        assertThat(stockOfFlour()).isEqualTo(ONE_KILO);
        assertNoNegativeStock();
    }

    private void assertExactlyOneWonAndTheOtherLostTheVersionCheck(List<Outcome> outcomes) {
        List<Outcome> succeeded = outcomes.stream().filter(outcome -> outcome.failure() == null).toList();
        List<Outcome> failed = outcomes.stream().filter(outcome -> outcome.failure() != null).toList();

        assertThat(succeeded).hasSize(1);
        assertThat(failed).hasSize(1);
        // Called directly, the failure usually arrives unwrapped; through a proxy it can be nested.
        assertThat(rootCauseOf(failed.get(0).failure()))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    private static Throwable rootCauseOf(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) {
            if (current instanceof OptimisticLockingFailureException) {
                return current;
            }
            current = current.getCause();
        }
        return current;
    }

    /** Runs both calls on real threads and returns what each one produced, in the order given. */
    private List<Outcome> runTogether(Callable<Object> first, Callable<Object> second) throws Exception {
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = List.of(threads.submit(first), threads.submit(second));
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                try {
                    outcomes.add(new Outcome(future.get(20, TimeUnit.SECONDS), null));
                } catch (java.util.concurrent.ExecutionException failure) {
                    outcomes.add(new Outcome(null, failure.getCause()));
                }
            }
            return outcomes;
        } finally {
            threads.shutdownNow();
        }
    }

    private record Outcome(Object result, Throwable failure) {
    }

    private UUID publishedRecipeUsingFlour(String title) throws Exception {
        return fixtures.createPublishedRecipe(token, title, 4,
                List.of(new TestFixtures.Line(flour, "350", Unit.GRAM)));
    }

    private Long stockOfFlour() {
        return jdbc.queryForObject(
                "select amount_milli from pantry_item where ingredient_id = ?", Long.class, flour);
    }

    private String statusOf(UUID entry) {
        return jdbc.queryForObject("select status from plan_entry where id = ?", String.class, entry);
    }

    private Object cookedAtOf(UUID entry) {
        return jdbc.queryForObject("select cooked_at from plan_entry where id = ?", Object.class, entry);
    }

    /**
     * Redundant with the check constraint on the table, and here on purpose: if that constraint ever
     * disappeared, this assertion would still catch it.
     */
    private void assertNoNegativeStock() {
        Long negatives = jdbc.queryForObject(
                "select count(*) from pantry_item where amount_milli < 0", Long.class);
        assertThat(negatives).isZero();
    }
}
