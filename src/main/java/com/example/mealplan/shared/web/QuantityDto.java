package com.example.mealplan.shared.web;

import com.example.mealplan.shared.domain.Quantity;
import com.example.mealplan.shared.domain.Unit;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/**
 * How an amount crosses the wire, in both directions.
 *
 * <p>{@code amount} is a string and not a JSON number. A number would pass through a {@code double}
 * somewhere in the stack; a string reaches {@code BigDecimal} intact. What stops a JSON number from
 * being quietly coerced into this string is the mapper configuration, not the pattern below: by
 * default Jackson would convert {@code 350} into {@code "350"} without complaining, and the pattern
 * would then happily accept it.
 *
 * <p>On the way out the amount is always in the canonical unit of its dimension, with exactly three
 * decimals. The response therefore does not preserve the unit the caller wrote, which buys the
 * property that any two amounts are comparable without looking at the unit field.
 */
public record QuantityDto(
        @NotNull
        @Pattern(regexp = "^(0|[1-9][0-9]{0,6})(\\.[0-9]{1,3})?$",
                 message = "must be a decimal string with at most 3 decimals")
        String amount,

        @NotNull
        Unit unit) {

    public BigDecimal amountAsDecimal() {
        return new BigDecimal(amount);
    }

    public static QuantityDto canonicalOf(Quantity quantity) {
        return new QuantityDto(quantity.toCanonicalString(), quantity.dimension().canonicalUnit());
    }
}
