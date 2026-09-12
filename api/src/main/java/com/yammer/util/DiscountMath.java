package com.yammer.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The one place a discount is computed: per UNIT price, rounded to 2 decimals, then multiplied
 * by the quantity — exactly what the fiscal register does with the 2-decimal unit prices it is
 * sent, so the printed total equals the stored payment amount.
 */
public final class DiscountMath {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private DiscountMath() {
    }

    public static BigDecimal discountedUnit(BigDecimal unitPrice, BigDecimal discountPercent) {
        BigDecimal price = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        if (discountPercent == null || discountPercent.signum() <= 0) {
            return price;
        }
        return price.multiply(HUNDRED.subtract(discountPercent)).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
