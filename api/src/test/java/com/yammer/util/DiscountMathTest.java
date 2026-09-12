package com.yammer.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DiscountMathTest {

    @Test
    void discountsPerUnitRoundedToCents() {
        assertEquals(new BigDecimal("37.80"), DiscountMath.discountedUnit(new BigDecimal("42.00"), new BigDecimal("10")));
        assertEquals(new BigDecimal("18.70"), DiscountMath.discountedUnit(new BigDecimal("22.00"), new BigDecimal("15")));
        // 19.99 × 0.85 = 16.9915 → 16.99 (HALF_UP on the third decimal)
        assertEquals(new BigDecimal("16.99"), DiscountMath.discountedUnit(new BigDecimal("19.99"), new BigDecimal("15")));
    }

    @Test
    void noDiscountLeavesThePriceAlone() {
        assertEquals(new BigDecimal("42.00"), DiscountMath.discountedUnit(new BigDecimal("42.00"), null));
        assertEquals(new BigDecimal("42.00"), DiscountMath.discountedUnit(new BigDecimal("42.00"), BigDecimal.ZERO));
        assertEquals(0, DiscountMath.discountedUnit(null, new BigDecimal("10")).compareTo(BigDecimal.ZERO));
    }
}
