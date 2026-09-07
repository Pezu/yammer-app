package com.yammer.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** One row of a product's recipe, with the component's display name resolved. */
public record RecipeComponentResponse(
        UUID id,
        UUID componentProductId,
        String componentName,
        BigDecimal quantity) {
}
