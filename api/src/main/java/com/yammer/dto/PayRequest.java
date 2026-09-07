package com.yammer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A payment against the order point's running bill, with one of its accepted payment
 * types. {@code amount} applies to {@link PaymentMode#AMOUNT} (capped at the unpaid
 * total); {@code items} applies to {@link PaymentMode#PARTIAL}.
 */
public record PayRequest(
        @NotNull UUID orderPointId,
        @NotNull UUID paymentTypeId,
        @NotNull PaymentMode mode,
        @PositiveOrZero BigDecimal tip,
        @Positive BigDecimal amount,
        @Valid List<PayItem> items) {

    /** {@code price} narrows the target to lines with that exact unit price (so a split
     *  remainder can be paid separately from the product's regular lines); null = any. */
    public record PayItem(@NotNull UUID menuItemId, @Min(1) int quantity, @PositiveOrZero BigDecimal price) {
    }
}
