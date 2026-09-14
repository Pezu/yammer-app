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
import com.yammer.dto.NotPaidReportRow;
import com.yammer.entity.LocationEntity;
import com.yammer.entity.OrderItemEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.PaymentEntity;
import com.yammer.entity.PaymentTypeEntity;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotPaidReportServiceTest {

    private final UUID location = UUID.randomUUID();
    private final UUID t1 = UUID.randomUUID();
    private final UUID t5 = UUID.randomUUID();
    private final UUID cash = UUID.randomUUID();
    private final UUID protocol = UUID.randomUUID();
    private final UUID po = UUID.randomUUID();

    @Test
    void listsProtocolAndPoSettlementsWithMergedLines() throws Exception {
        LocalDate day = LocalDate.of(2026, 9, 12);
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID paid = UUID.randomUUID();
        List<PaymentEntity> payments = List.of(
                payment(p1, t1, protocol, "eduard", "66", day.atTime(20, 15)),
                payment(p2, t5, po, "ana", "25", day.atTime(22, 11)),
                payment(paid, t1, cash, "ana", "50", day.atTime(21, 0)));
        List<OrderItemEntity> items = List.of(
                item("Acqua Panna 0.5 L", "22", 2, p1), item("Acqua Panna 0.5 L", "22", 1, p1),
                item("Ramazzotti Amaro", "25", 1, p2));

        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        when(paymentRepo.findByLocationIdAndCreatedAtBetween(eq(location), any(), any())).thenReturn(payments);
        OrderItemRepository itemRepo = mock(OrderItemRepository.class);
        when(itemRepo.findByPaymentIdIn(any())).thenReturn(items);
        OrderPointRepository points = mock(OrderPointRepository.class);
        when(points.findByLocationIdOrderByName(location)).thenReturn(List.of(point(t1, "T1.1", "Fereastra"), point(t5, "T5", null)));
        PaymentTypeRepository types = mock(PaymentTypeRepository.class);
        when(types.findAll()).thenReturn(List.of(type(cash, "CASH"), type(protocol, "PROTOCOL"), type(po, "PO")));
        UserRepository users = mock(UserRepository.class);
        when(users.findByUsernameIn(any())).thenReturn(List.of());
        AccessGuard guard = mock(AccessGuard.class);
        LocationEntity loc = new LocationEntity();
        loc.setName("Rendezvous");
        when(guard.requireAccessibleLocation(location)).thenReturn(loc);

        NotPaidReportService service = new NotPaidReportService(paymentRepo, itemRepo, points, types, users, guard);
        List<NotPaidReportRow> rows = service.report(location, day, day);
        assertEquals(2, rows.size());
        assertEquals("T5", rows.get(0).orderPointName()); // newest first
        assertEquals("PO", rows.get(0).paymentType());
        assertEquals("Fereastra", rows.get(1).nickname());
        assertEquals(1, rows.get(1).items().size()); // two orders of the same product → one line
        assertEquals(3, rows.get(1).items().get(0).quantity());
        assertEquals(new BigDecimal("66"), rows.get(1).items().get(0).total());

        byte[] pdf = service.pdf(location, day, day);
        Files.write(Path.of(System.getProperty("java.io.tmpdir"), "not-paid-test.pdf"), pdf);
        String text;
        try (PdfDocument doc = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            text = PdfTextExtractor.getTextFromPage(doc.getPage(1));
        }
        assertTrue(text.contains("Not paid (Protocol / PO)"), text);
        assertTrue(text.contains("Acqua Panna 0.5 L 3 22.00 66.00"), text);
        assertTrue(text.contains("T1.1 (Fereastra)   12.09 20:15"), text);
        assertTrue(text.contains("PO 1 25.00") && text.contains("PROTOCOL 1 66.00"), text);
        assertTrue(!text.contains("50.00"), "the CASH payment leaked: " + text);
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

    private static PaymentEntity payment(UUID id, UUID point, UUID type, String by, String amount, LocalDateTime at) {
        PaymentEntity p = new PaymentEntity();
        p.setId(id);
        p.setOrderPointId(point);
        p.setPaymentTypeId(type);
        p.setCreatedBy(by);
        p.setAmount(new BigDecimal(amount));
        p.setCreatedAt(at);
        return p;
    }

    private static PaymentTypeEntity type(UUID id, String name) {
        PaymentTypeEntity t = new PaymentTypeEntity();
        t.setId(id);
        t.setType(name);
        return t;
    }

    private static OrderPointEntity point(UUID id, String name, String nickname) {
        OrderPointEntity op = new OrderPointEntity();
        op.setId(id);
        op.setName(name);
        op.setNickname(nickname);
        return op;
    }
}
