package com.example.mealplan.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mealplan.catalog.api.RecipeId;
import com.example.mealplan.planning.api.PlanEntryStatus;
import com.example.mealplan.shared.domain.DomainException;
import com.example.mealplan.shared.domain.ErrorCode;
import com.example.mealplan.shared.domain.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The state machine of a plan entry, cell by cell, with no Spring context and no database.
 *
 * <p>Both terminal states are covered separately because they differ in exactly one place: only a
 * cooked entry refuses to be deleted.
 */
class PlanEntryTest {

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final RecipeId RECIPE = new RecipeId(UUID.randomUUID());
    private static final LocalDate MONDAY = LocalDate.of(2026, 6, 15);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 6, 16);
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-06-15T13:00:00Z");

    private static PlanEntry planned() {
        return new PlanEntry(OWNER, RECIPE, MONDAY, 4, NOW);
    }

    private static PlanEntry cooked() {
        PlanEntry entry = planned();
        entry.markCooked(NOW);
        return entry;
    }

    private static PlanEntry cancelled() {
        PlanEntry entry = planned();
        entry.cancel(NOW);
        return entry;
    }

    private static void assertCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(DomainException.class)
                .extracting(thrown -> ((DomainException) thrown).code())
                .isEqualTo(expected);
    }

    @Nested
    @DisplayName("an entry in PLANNED")
    class FromPlanned {

        @Test
        void isBornThere() {
            PlanEntry entry = planned();

            assertThat(entry.status()).isEqualTo(PlanEntryStatus.PLANNED);
            assertThat(entry.cookedAt()).isNull();
            assertThat(entry.cancelledAt()).isNull();
            assertThat(entry.createdAt()).isEqualTo(entry.updatedAt());
        }

        @Test
        void cooks() {
            PlanEntry entry = planned();

            entry.markCooked(LATER);

            assertThat(entry.status()).isEqualTo(PlanEntryStatus.COOKED);
            assertThat(entry.cookedAt()).isEqualTo(LATER);
            assertThat(entry.updatedAt()).isEqualTo(LATER);
            assertThat(entry.cancelledAt()).isNull();
        }

        @Test
        void cancels() {
            PlanEntry entry = planned();

            entry.cancel(LATER);

            assertThat(entry.status()).isEqualTo(PlanEntryStatus.CANCELLED);
            assertThat(entry.cancelledAt()).isEqualTo(LATER);
            assertThat(entry.updatedAt()).isEqualTo(LATER);
            assertThat(entry.cookedAt()).isNull();
        }

        @Test
        void reschedules() {
            PlanEntry entry = planned();

            entry.reschedule(TUESDAY, 2, LATER);

            assertThat(entry.plannedFor()).isEqualTo(TUESDAY);
            assertThat(entry.servings()).isEqualTo(2);
            assertThat(entry.updatedAt()).isEqualTo(LATER);
        }

        @Test
        void isDeletable() {
            planned().requireDeletable();
        }
    }

    @Nested
    @DisplayName("an entry in COOKED")
    class FromCooked {

        @Test
        void cannotBeCookedAgain() {
            assertCode(() -> cooked().markCooked(LATER), ErrorCode.PLAN_ENTRY_NOT_PLANNED);
        }

        @Test
        void cannotBeCancelled() {
            assertCode(() -> cooked().cancel(LATER), ErrorCode.PLAN_ENTRY_NOT_PLANNED);
        }

        @Test
        void cannotBeRescheduled() {
            assertCode(() -> cooked().reschedule(TUESDAY, 2, LATER), ErrorCode.PLAN_ENTRY_NOT_PLANNED);
        }

        @Test
        void cannotBeDeleted() {
            assertCode(() -> cooked().requireDeletable(), ErrorCode.PLAN_ENTRY_NOT_DELETABLE);
        }
    }

    @Nested
    @DisplayName("an entry in CANCELLED")
    class FromCancelled {

        @Test
        void cannotBeCooked() {
            assertCode(() -> cancelled().markCooked(LATER), ErrorCode.PLAN_ENTRY_NOT_PLANNED);
        }

        @Test
        void cannotBeCancelledAgain() {
            assertCode(() -> cancelled().cancel(LATER), ErrorCode.PLAN_ENTRY_NOT_PLANNED);
        }

        @Test
        void cannotBeRescheduled() {
            assertCode(() -> cancelled().reschedule(TUESDAY, 2, LATER), ErrorCode.PLAN_ENTRY_NOT_PLANNED);
        }

        @Test
        void isDeletable() {
            cancelled().requireDeletable();
        }
    }

    @Nested
    @DisplayName("rescheduling that changes nothing")
    class PointlessReschedule {

        @Test
        void withBothArgumentsNullLeavesNoTrace() {
            PlanEntry entry = planned();

            entry.reschedule(null, null, LATER);

            assertThat(entry.plannedFor()).isEqualTo(MONDAY);
            assertThat(entry.servings()).isEqualTo(4);
            assertThat(entry.updatedAt()).isEqualTo(NOW);
        }

        @Test
        void withTheValuesItAlreadyHasLeavesNoTrace() {
            PlanEntry entry = planned();

            entry.reschedule(MONDAY, 4, LATER);

            assertThat(entry.updatedAt()).isEqualTo(NOW);
        }

        @Test
        void movingOnlyTheDateLeavesTheServingsAlone() {
            PlanEntry entry = planned();

            entry.reschedule(TUESDAY, null, LATER);

            assertThat(entry.plannedFor()).isEqualTo(TUESDAY);
            assertThat(entry.servings()).isEqualTo(4);
            assertThat(entry.updatedAt()).isEqualTo(LATER);
        }

        @Test
        void servingsOutOfRangeIsABugAndNotABusinessCase() {
            PlanEntry entry = planned();

            assertThatThrownBy(() -> entry.reschedule(null, 51, LATER))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
