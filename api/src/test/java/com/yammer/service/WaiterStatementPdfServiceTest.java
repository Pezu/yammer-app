package com.yammer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.yammer.entity.LocationEntity;
import com.yammer.entity.OrderItemEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.PaymentEntity;
import com.yammer.entity.PaymentTypeEntity;
import com.yammer.entity.UserEntity;
import com.yammer.repository.OrderItemRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.PaymentRepository;
import com.yammer.repository.PaymentTypeRepository;
import com.yammer.repository.UserRepository;
import com.yammer.security.AccessGuard;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WaiterStatementPdfServiceTest {

    private final UUID location = UUID.randomUUID();
    private final UUID t1 = UUID.randomUUID();
    private final UUID t10 = UUID.randomUUID();
    private final UUID b1 = UUID.randomUUID();
    private final UUID cash = UUID.randomUUID();
    private final UUID card = UUID.randomUUID();

    @Test
    void listsTheWaitersPaymentsByTableWithTheirProducts() throws Exception {
        LocalDate day = LocalDate.of(2026, 9, 12);
        UUID p1 = UUID.randomUUID(); // T1.1 card, discounted
        UUID p2 = UUID.randomUUID(); // T10 cash with tip
        UUID p3 = UUID.randomUUID(); // B1 cash, fixed amount (no lines)
        UUID other = UUID.randomUUID(); // someone else's — must not show
        PaymentEntity d = payment(p1, t1, card, "eduard", "95.40", "0", day.atTime(20, 15));
        d.setDiscountPercent(new BigDecimal("10"));
        d.setDiscountAmount(new BigDecimal("10.60"));
        d.setReceiptNumber("0001234");
        List<PaymentEntity> payments = List.of(
                payment(p2, t10, cash, "eduard", "75", "5", day.atTime(22, 11)),
                d,
                payment(p3, b1, cash, "eduard", "20", "0", day.atTime(23, 0)),
                payment(other, t1, cash, "ana", "50", "0", day.atTime(21, 0)));
        List<OrderItemEntity> items = List.of(
                item("<b>Beefeater</b> & Tonic", "37.80", 2, p1), item("Coca Cola", "19.80", 1, p1),
                item("Ramazzotti Amaro", "25", 3, p2));

        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        when(paymentRepo.findByLocationIdAndCreatedAtBetween(eq(location), any(), any())).thenReturn(payments);
        OrderItemRepository itemRepo = mock(OrderItemRepository.class);
        when(itemRepo.findByPaymentIdIn(any())).thenReturn(items);
        OrderPointRepository points = mock(OrderPointRepository.class);
        when(points.findByLocationIdOrderByName(location))
                .thenReturn(List.of(point(t1, "T1.1"), point(t10, "T10"), point(b1, "B1")));
        PaymentTypeRepository types = mock(PaymentTypeRepository.class);
        when(types.findAll()).thenReturn(List.of(type(cash, "CASH"), type(card, "CARD")));
        UserRepository users = mock(UserRepository.class);
        UserEntity u = new UserEntity();
        u.setUsername("eduard");
        u.setName("Eduard Popescu");
        when(users.findByUsername("eduard")).thenReturn(Optional.of(u));
        AccessGuard guard = mock(AccessGuard.class);
        LocationEntity loc = new LocationEntity();
        loc.setName("Rendezvous");
        when(guard.requireAccessibleLocation(location)).thenReturn(loc);

        byte[] pdf = new WaiterStatementPdfService(paymentRepo, itemRepo, points, types, users, guard)
                .statement(location, "eduard", day, day);
        Files.write(Path.of(System.getProperty("java.io.tmpdir"), "waiter-statement-test.pdf"), pdf);

        String text;
        try (PdfDocument doc = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            assertEquals(1, doc.getNumberOfPages());
            text = PdfTextExtractor.getTextFromPage(doc.getPage(1));
        }
        assertTrue(text.contains("Eduard Popescu"), text);
        assertTrue(text.contains("Rendezvous"), text);
        // tables in the dashboard's natural order: B1, then T1.1 before T10
        assertTrue(text.indexOf("B1") < text.indexOf("T1.1") && text.indexOf("T1.1") < text.indexOf("T10"), text);
        assertTrue(text.contains("CARD   Amount 95.40   Discount 10% (-10.60)   Receipt 0001234"), text);
        assertTrue(text.contains("Beefeater & Tonic") && text.contains("37.80") && text.contains("75.60"), text);
        assertTrue(text.contains("CASH   Amount 75.00   Tip 5.00"), text);
        assertTrue(text.contains("fixed-amount payment"), text);
        assertTrue(!text.contains("50.00"), "ana's payment leaked: " + text);
        assertTrue(text.contains("CASH 2 95.00 5.00") && text.contains("190.40"), text);
    }

    private static OrderItemEntity item(String name, String price, int qty, UUID paymentId) {
        OrderItemEntity i = new OrderItemEntity();
        i.setId(UUID.randomUUID());
        i.setOrderId(UUID.randomUUID());
        i.setName(name);
        i.setPrice(new BigDecimal(price));
        i.setQuantity(qty);
        i.setPaymentId(paymentId);
        return i;
    }

    private static PaymentEntity payment(UUID id, UUID point, UUID type, String by, String amount, String tip,
                                         LocalDateTime at) {
        PaymentEntity p = new PaymentEntity();
        p.setId(id);
        p.setOrderPointId(point);
        p.setPaymentTypeId(type);
        p.setCreatedBy(by);
        p.setAmount(new BigDecimal(amount));
        p.setTip(new BigDecimal(tip));
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
}
