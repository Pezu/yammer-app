package com.yammer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yammer.dto.DashboardResponse;
import com.yammer.entity.LocationEntity;
import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderItemEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.PaymentEntity;
import com.yammer.entity.PaymentTypeEntity;
import com.yammer.entity.UserEntity;
import com.yammer.repository.OrderItemRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderRepository;
import com.yammer.repository.PaymentRepository;
import com.yammer.repository.PaymentTypeRepository;
import com.yammer.repository.UserRepository;
import com.yammer.security.AccessGuard;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DashboardReportServiceTest {

    private final UUID location = UUID.randomUUID();
    private final UUID t1 = UUID.randomUUID();
    private final UUID b1 = UUID.randomUUID();
    private final UUID cash = UUID.randomUUID();
    private final UUID card = UUID.randomUUID();

    @Test
    void aggregatesADayOfOrdersAndPayments() {
        LocalDate day = LocalDate.of(2026, 9, 12);
        OrderEntity o1 = order(t1, "ana", day.atTime(20, 5));     // 2 × 42 + 1 × 22 = 106, paid by card
        OrderEntity o2 = order(t1, "ana", day.atTime(21, 40));    // 1 × 42, unpaid
        OrderEntity o3 = order(b1, "bob", day.atTime(22, 10));    // 3 × 25 = 75, paid cash at the bar
        OrderEntity draft = order(t1, "ana", day.atTime(22, 30));
        draft.setStatus("DRAFT");

        UUID pCard = UUID.randomUUID();
        UUID pCash = UUID.randomUUID();
        List<OrderItemEntity> items = List.of(
                item(o1, "Beefeater & Tonic", 42, 2, pCard), item(o1, "Coca Cola", 22, 1, pCard),
                item(o2, "Beefeater & Tonic", 42, 1, null),
                item(o3, "Ramazzotti Amaro", 25, 3, pCash),
                item(draft, "Hugo", 49, 1, null));
        List<PaymentEntity> payments = List.of(
                payment(pCard, t1, card, "ana", 106, 10, day.atTime(21, 0)),
                payment(pCash, b1, cash, "bob", 75, 5, day.atTime(22, 11)));

        OrderRepository orders = mock(OrderRepository.class);
        when(orders.findByLocationIdAndCreatedAtBetween(eq(location), any(), any()))
                .thenReturn(List.of(o1, o2, o3, draft));
        OrderItemRepository itemRepo = mock(OrderItemRepository.class);
        when(itemRepo.findByOrderIdIn(any())).thenReturn(items);
        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        when(paymentRepo.findByLocationIdAndCreatedAtBetween(eq(location), any(), any())).thenReturn(payments);
        PaymentTypeRepository types = mock(PaymentTypeRepository.class);
        when(types.findAll()).thenReturn(List.of(type(cash, "CASH"), type(card, "CARD")));
        OrderPointRepository points = mock(OrderPointRepository.class);
        when(points.findByLocationIdOrderByName(location)).thenReturn(List.of(point(t1, "T1.1"), point(b1, "B1")));
        UserRepository users = mock(UserRepository.class);
        when(users.findByUsernameIn(any())).thenReturn(List.of(user("ana", "Ana"), user("bob", null)));
        AccessGuard guard = mock(AccessGuard.class);
        when(guard.requireAccessibleLocation(location)).thenReturn(new LocationEntity());

        DashboardResponse d = new DashboardReportService(orders, itemRepo, paymentRepo, types, points, users, guard)
                .dashboard(location, day, day);

        // summary: the DRAFT never counts
        assertEquals(new BigDecimal("223"), d.summary().ordered());
        assertEquals(new BigDecimal("181"), d.summary().paid());
        assertEquals(new BigDecimal("15"), d.summary().tips());
        assertEquals(new BigDecimal("42"), d.summary().remaining());
        assertEquals(3, d.summary().orders());
        assertEquals(new BigDecimal("74.33"), d.summary().averageOrder());

        // one day → 15-minute buckets, gapless
        assertEquals(15, d.bucketMinutes());
        assertEquals(96, d.series().size());
        assertEquals(new BigDecimal("106"), d.series().get(20 * 4).ordered()); // 20:00 bucket
        assertEquals(new BigDecimal("106"), d.series().get(21 * 4).paid());    // 21:00 bucket

        // tables in natural order, unpaid flagged
        assertEquals(List.of("B1", "T1.1"), d.tables().stream().map(DashboardResponse.TableRow::table).toList());
        DashboardResponse.TableRow table = d.tables().get(1);
        assertEquals(new BigDecimal("148"), table.ordered());
        assertEquals(new BigDecimal("106"), table.paidCard());
        assertEquals(new BigDecimal("42"), table.remaining());

        // products by quantity
        assertEquals("Beefeater & Tonic", d.products().get(0).product());
        assertEquals(3, d.products().get(0).quantity());

        // waiters: display name, takings by the payment's creator; final report mirrors it
        DashboardResponse.WaiterRow ana = d.waiters().get(0);
        assertEquals("Ana", ana.waiter());
        assertEquals(2, ana.orders());
        assertEquals(new BigDecimal("106"), ana.paidCard());
        assertEquals(new BigDecimal("10"), ana.tipsCard());
        assertEquals(new BigDecimal("42"), ana.unsettled());
        DashboardResponse.FinalRow bob = d.finalReport().stream().filter(r -> r.waiter().equals("bob")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("75"), bob.paidCash());
        assertEquals(new BigDecimal("80"), bob.total());
    }

    private OrderEntity order(UUID point, String by, LocalDateTime at) {
        OrderEntity o = new OrderEntity();
        o.setId(UUID.randomUUID());
        o.setOrderPointId(point);
        o.setCreatedBy(by);
        o.setCreatedAt(at);
        o.setStatus("ORDERED");
        return o;
    }

    private static OrderItemEntity item(OrderEntity o, String name, int price, int qty, UUID paymentId) {
        OrderItemEntity i = new OrderItemEntity();
        i.setId(UUID.randomUUID());
        i.setOrderId(o.getId());
        i.setName(name);
        i.setPrice(BigDecimal.valueOf(price));
        i.setQuantity(qty);
        i.setPaymentId(paymentId);
        return i;
    }

    private static PaymentEntity payment(UUID id, UUID point, UUID type, String by, int amount, int tip, LocalDateTime at) {
        PaymentEntity p = new PaymentEntity();
        p.setId(id);
        p.setOrderPointId(point);
        p.setPaymentTypeId(type);
        p.setCreatedBy(by);
        p.setAmount(BigDecimal.valueOf(amount));
        p.setTip(BigDecimal.valueOf(tip));
        p.setCreatedAt(at);
        return p;
    }

    private static PaymentTypeEntity type(UUID id, String name) {
        PaymentTypeEntity t = new PaymentTypeEntity();
        t.setId(id);
        t.setType(name);
        return t;
    }

    private static OrderPointEntity point(UUID id, String name) {
        OrderPointEntity op = new OrderPointEntity();
        op.setId(id);
        op.setName(name);
        return op;
    }

    private static UserEntity user(String username, String name) {
        UserEntity u = new UserEntity();
        u.setUsername(username);
        u.setName(name);
        return u;
    }
}
