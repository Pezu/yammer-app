package com.yammer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/** One row of a product's recipe: a component product + the fraction consumed. */
public record RecipeComponentRequest(
        @NotNull UUID componentProductId,
        @NotNull @Positive BigDecimal quantity) {
}
