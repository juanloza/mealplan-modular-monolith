package com.example.mealplan.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mealplan.catalog.api.IngredientId;
import com.example.mealplan.catalog.api.RecipeStatus;
import com.example.mealplan.shared.domain.Dimension;
import com.example.mealplan.shared.domain.DomainException;
import com.example.mealplan.shared.domain.ErrorCode;
import com.example.mealplan.shared.domain.Quantity;
import com.example.mealplan.shared.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The state machine, covered cell by cell, with no Spring context and no database.
 *
 * <p>Nothing here needs a clock either: the entity never reads the time, it receives the instant
 * already resolved. That is what makes this test a plain constructor call.
 */
class RecipeTest {

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-06-15T13:00:00Z");
    private static final IngredientId FLOUR = new IngredientId(UUID.randomUUID());
    private static final IngredientId MILK = new IngredientId(UUID.randomUUID());

    private static Recipe draft() {
        return new Recipe(OWNER, "Pancakes", 4, NOW);
    }

    private static Recipe draftWithLines() {
        Recipe recipe = draft();
        recipe.replaceContent("Pancakes", 4, List.of(flourLine()), NOW);
        return recipe;
    }

    private static Recipe published() {
        Recipe recipe = draftWithLines();
        recipe.publish(NOW);
        return recipe;
    }

    private static Recipe archived() {
        Recipe recipe = published();
        recipe.archive(NOW);
        return recipe;
    }

    private static LineSpec flourLine() {
        return new LineSpec(FLOUR, Quantity.ofMilli(350_000L, Dimension.MASS));
    }

    private static void assertCode(ThrowingCall call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(DomainException.class)
                .extracting(thrown -> ((DomainException) thrown).code())
                .isEqualTo(expected);
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }

    @Nested
    @DisplayName("a recipe in DRAFT")
    class FromDraft {

        @Test
        void isBornThere() {
            Recipe recipe = draft();

            assertThat(recipe.status()).isEqualTo(RecipeStatus.DRAFT);
            assertThat(recipe.lines()).isEmpty();
            assertThat(recipe.publishedAt()).isNull();
            assertThat(recipe.archivedAt()).isNull();
        }

        @Test
        void publishes() {
            Recipe recipe = draftWithLines();

            recipe.publish(LATER);

            assertThat(recipe.status()).isEqualTo(RecipeStatus.PUBLISHED);
            assertThat(recipe.publishedAt()).isEqualTo(LATER);
            assertThat(recipe.updatedAt()).isEqualTo(LATER);
        }

        @Test
        void cannotBeArchived() {
            assertCode(() -> draftWithLines().archive(LATER), ErrorCode.INVALID_RECIPE_TRANSITION);
        }

        @Test
        void acceptsNewContent() {
            Recipe recipe = draftWithLines();

            recipe.replaceContent("Crepes", 2, List.of(flourLine(),
                    new LineSpec(MILK, Quantity.ofMilli(500_000L, Dimension.VOLUME))), LATER);

            assertThat(recipe.title()).isEqualTo("Crepes");
            assertThat(recipe.servings()).isEqualTo(2);
            assertThat(recipe.lines()).extracting(RecipeLine::position).containsExactly(0, 1);
            assertThat(recipe.lines()).extracting(RecipeLine::ingredientId).containsExactly(FLOUR, MILK);
            assertThat(recipe.updatedAt()).isEqualTo(LATER);
        }

        @Test
        void isDeletable() {
            draftWithLines().requireDeletable();
        }
    }

    @Nested
    @DisplayName("a recipe in PUBLISHED")
    class FromPublished {

        @Test
        void cannotBePublishedAgain() {
            assertCode(() -> published().publish(LATER), ErrorCode.INVALID_RECIPE_TRANSITION);
        }

        @Test
        void archives() {
            Recipe recipe = published();

            recipe.archive(LATER);

            assertThat(recipe.status()).isEqualTo(RecipeStatus.ARCHIVED);
            assertThat(recipe.archivedAt()).isEqualTo(LATER);
            assertThat(recipe.publishedAt()).isNotNull();
            assertThat(recipe.updatedAt()).isEqualTo(LATER);
        }

        @Test
        void refusesNewContent() {
            assertCode(() -> published().replaceContent("Crepes", 2, List.of(flourLine()), LATER),
                    ErrorCode.RECIPE_NOT_EDITABLE);
        }

        @Test
        void isNotDeletable() {
            assertCode(() -> published().requireDeletable(), ErrorCode.RECIPE_NOT_DELETABLE);
        }
    }

    @Nested
    @DisplayName("a recipe in ARCHIVED")
    class FromArchived {

        @Test
        void cannotBePublished() {
            assertCode(() -> archived().publish(LATER), ErrorCode.INVALID_RECIPE_TRANSITION);
        }

        @Test
        void cannotBeArchivedAgain() {
            assertCode(() -> archived().archive(LATER), ErrorCode.INVALID_RECIPE_TRANSITION);
        }

        @Test
        void refusesNewContent() {
            assertCode(() -> archived().replaceContent("Crepes", 2, List.of(flourLine()), LATER),
                    ErrorCode.RECIPE_NOT_EDITABLE);
        }

        @Test
        void isNotDeletable() {
            assertCode(() -> archived().requireDeletable(), ErrorCode.RECIPE_NOT_DELETABLE);
        }
    }

    @Nested
    @DisplayName("what the lines themselves have to satisfy")
    class Lines {

        @Test
        void publishingWithoutLinesIsRefusedAndChangesNothing() {
            Recipe recipe = draft();

            assertCode(() -> recipe.publish(LATER), ErrorCode.RECIPE_HAS_NO_LINES);

            assertThat(recipe.status()).isEqualTo(RecipeStatus.DRAFT);
            assertThat(recipe.publishedAt()).isNull();
        }

        @Test
        void theSameIngredientTwiceIsRefused() {
            Recipe recipe = draft();
            List<LineSpec> twice = List.of(flourLine(), flourLine());

            assertCode(() -> recipe.replaceContent("Pancakes", 4, twice, LATER),
                    ErrorCode.DUPLICATE_RECIPE_LINE);
        }

        @Test
        void anAmountOfZeroIsRefused() {
            Recipe recipe = draft();
            List<LineSpec> zero = List.of(new LineSpec(FLOUR, Quantity.zero(Dimension.MASS)));

            assertCode(() -> recipe.replaceContent("Pancakes", 4, zero, LATER),
                    ErrorCode.AMOUNT_NOT_POSITIVE);
        }

        @Test
        void aRefusedReplacementLeavesTheOldContentAlone() {
            Recipe recipe = draftWithLines();
            List<LineSpec> twice = List.of(flourLine(), flourLine());

            assertCode(() -> recipe.replaceContent("Crepes", 2, twice, LATER),
                    ErrorCode.DUPLICATE_RECIPE_LINE);

            assertThat(recipe.title()).isEqualTo("Pancakes");
            assertThat(recipe.lines()).hasSize(1);
            assertThat(recipe.updatedAt()).isEqualTo(NOW);
        }

        @Test
        void theListOfLinesCannotBeChangedFromOutside() {
            Recipe recipe = draftWithLines();

            assertThatThrownBy(() -> recipe.lines().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
