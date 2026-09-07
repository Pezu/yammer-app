package com.yammer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yammer.service.OrderService.Piece;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure test of the bill's split-unit re-join (no Spring context). */
class OrderServiceBillMergeTest {

    private static final UUID AP = UUID.randomUUID();
    private static final UUID SP = UUID.randomUUID();

    private static Piece piece(UUID id, String price, long qty, boolean paid, String original) {
        return new Piece(id, id == AP ? "Aqua Panna" : "San Pellegrino", new BigDecimal(price), qty, paid,
                original == null ? null : new BigDecimal(original));
    }

    private static BigDecimal value(List<Piece> pieces) {
        return pieces.stream()
                .map(p -> p.price().multiply(BigDecimal.valueOf(p.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void legacyPaidHalfWithoutMarkerIsJoinedWithMarkedHalf() {
        // the T1.1 screenshot: 10 + (11 unmarked) + (11 marked 22) -> 11 whole units
        List<Piece> in = List.of(
                piece(AP, "22.00", 10, true, null),
                piece(AP, "11.00", 1, true, null),
                piece(SP, "22.00", 8, true, null),
                piece(AP, "11.00", 1, true, "22.00"));
        List<Piece> out = OrderService.mergeSettledSplits(in);

        assertTrue(out.stream().noneMatch(p -> p.originalPrice() != null), "no partial line left");
        long apWhole = out.stream()
                .filter(p -> p.menuItemId().equals(AP) && p.price().compareTo(new BigDecimal("22.00")) == 0)
                .mapToLong(Piece::quantity).sum();
        assertEquals(11, apWhole);
        assertEquals(0, value(in).compareTo(value(out)), "money is only regrouped, never changed");
    }

    @Test
    void twoMarkedPaidHalvesBecomeOneUnit() {
        List<Piece> out = OrderService.mergeSettledSplits(List.of(
                piece(AP, "11.00", 1, true, "22.00"),
                piece(AP, "11.00", 1, true, "22.00")));
        assertEquals(1, out.size());
        assertEquals(1, out.get(0).quantity());
        assertEquals(0, out.get(0).price().compareTo(new BigDecimal("22.00")));
        assertNull(out.get(0).originalPrice());
    }

    @Test
    void paidHalfStaysPartialWhileRemainderIsUnpaid() {
        List<Piece> out = OrderService.mergeSettledSplits(List.of(
                piece(AP, "11.00", 1, true, "22.00"),
                piece(AP, "11.00", 1, false, "22.00")));
        assertEquals(2, out.size());
        Piece paid = out.stream().filter(Piece::paid).findFirst().orElseThrow();
        assertEquals(0, paid.price().compareTo(new BigDecimal("11.00")));
        assertEquals(0, paid.originalPrice().compareTo(new BigDecimal("22.00")));
        Piece unpaid = out.stream().filter(p -> !p.paid()).findFirst().orElseThrow();
        assertEquals(0, unpaid.originalPrice().compareTo(new BigDecimal("22.00")));
    }

    @Test
    void unevenFragmentsKeepOnlyTheFractionPartial() {
        // 15 + 7 + 22 marked paid on 22-unit bottles = 2 whole units, nothing left over
        List<Piece> out = OrderService.mergeSettledSplits(List.of(
                piece(AP, "15.00", 1, true, "22.00"),
                piece(AP, "7.00", 1, true, "22.00"),
                piece(AP, "22.00", 1, true, null)));
        long whole = out.stream().filter(p -> p.originalPrice() == null).mapToLong(Piece::quantity).sum();
        assertEquals(2, whole);
        assertTrue(out.stream().noneMatch(p -> p.originalPrice() != null));
    }
}
