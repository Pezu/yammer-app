package com.yammer.service;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One waiter's statement for a period, as a printable A4 PDF: every payment they took, grouped by
 * table (the dashboard's natural order — B1, T1, T2, …, T10), each with its time, payment type, amount, tip and discount,
 * followed by the products that payment settled. A per-payment-type summary closes the document.
 *
 * <p>Only settled money is listed — an order that is still unpaid has no payment type yet, so it does
 * not appear here (the dashboard's "unsettled" column covers it). FAILED payments are skipped.
 */
@Service
@RequiredArgsConstructor
public class WaiterStatementPdfService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private static final DeviceRgb HEADER_BG = new DeviceRgb(240, 242, 248);
    static final DeviceRgb MUTED = new DeviceRgb(110, 110, 120);

    private final PaymentRepository paymentRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderPointRepository orderPointRepository;
    private final PaymentTypeRepository paymentTypeRepository;
    private final UserRepository userRepository;
    private final AccessGuard accessGuard;

    @Transactional(readOnly = true)
    public byte[] statement(UUID locationId, String username, LocalDate from, LocalDate to) {
        LocationEntity location = accessGuard.requireAccessibleLocation(locationId);
        if (to.isBefore(from)) {
            LocalDate t = to;
            to = from;
            from = t;
        }
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        List<PaymentEntity> payments = paymentRepository.findByLocationIdAndCreatedAtBetween(locationId, start, end)
                .stream()
                .filter(p -> username.equals(p.getCreatedBy()) && !"FAILED".equals(p.getStatus()))
                .sorted(Comparator.comparing(PaymentEntity::getCreatedAt))
                .toList();
        Map<UUID, List<OrderItemEntity>> itemsByPayment = payments.isEmpty() ? Map.of()
                : orderItemRepository.findByPaymentIdIn(payments.stream().map(PaymentEntity::getId).toList())
                        .stream().collect(Collectors.groupingBy(OrderItemEntity::getPaymentId));
        Map<UUID, String> pointName = orderPointRepository.findByLocationIdOrderByName(locationId).stream()
                .collect(Collectors.toMap(OrderPointEntity::getId, OrderPointEntity::getName));
        Map<UUID, String> typeName = paymentTypeRepository.findAll().stream()
                .collect(Collectors.toMap(PaymentTypeEntity::getId, PaymentTypeEntity::getType));
        String waiter = userRepository.findByUsername(username)
                .map(UserEntity::getName).filter(n -> n != null && !n.isBlank()).orElse(username);

        // group by table, tables in natural order, payments in time order within a table
        Map<String, List<PaymentEntity>> byTable = new TreeMap<>(OrderPointService::compareNames);
        for (PaymentEntity p : payments) {
            byTable.computeIfAbsent(pointName.getOrDefault(p.getOrderPointId(), "?"), k -> new ArrayList<>()).add(p);
        }

        PdfFont regular;
        PdfFont bold;
        try {
            // CP1250 covers Romanian (cedilla forms; comma-below letters are mapped to them in text())
            regular = PdfFontFactory.createFont(StandardFonts.HELVETICA, PdfEncodings.CP1250);
            bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD, PdfEncodings.CP1250);
        } catch (IOException e) {
            throw new IllegalStateException("PDF fonts unavailable", e);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
        try (Document doc = new Document(pdf, PageSize.A4)) {
            doc.setMargins(36, 36, 36, 36);
            doc.setFont(regular).setFontSize(9.5f);

            doc.add(new Paragraph(text("Waiter statement — " + waiter)).setFont(bold).setFontSize(16));
            String period = from.equals(to) ? from.toString() : from + " – " + to;
            doc.add(new Paragraph(text(location.getName() + "  ·  " + period))
                    .setFontColor(MUTED).setMarginBottom(12));

            if (payments.isEmpty()) {
                doc.add(new Paragraph("No payments taken in this period."));
            }

            BigDecimal grandAmount = BigDecimal.ZERO;
            BigDecimal grandTip = BigDecimal.ZERO;
            Map<String, BigDecimal[]> byType = new TreeMap<>(); // [count, amount, tip]
            for (Map.Entry<String, List<PaymentEntity>> table : byTable.entrySet()) {
                doc.add(new Paragraph(text(table.getKey())).setFont(bold).setFontSize(12).setMarginTop(10));
                for (PaymentEntity p : table.getValue()) {
                    String type = typeName.getOrDefault(p.getPaymentTypeId(), "?");
                    BigDecimal amount = nz(p.getAmount());
                    BigDecimal tip = nz(p.getTip());
                    grandAmount = grandAmount.add(amount);
                    grandTip = grandTip.add(tip);
                    BigDecimal[] acc = byType.computeIfAbsent(type, k -> new BigDecimal[] {
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
                    acc[0] = acc[0].add(BigDecimal.ONE);
                    acc[1] = acc[1].add(amount);
                    acc[2] = acc[2].add(tip);

                    StringBuilder head = new StringBuilder()
                            .append(p.getCreatedAt().format(TIME)).append("   ").append(type)
                            .append("   Amount ").append(money(amount));
                    if (tip.signum() != 0) {
                        head.append("   Tip ").append(money(tip));
                    }
                    if (p.getDiscountPercent() != null && p.getDiscountPercent().signum() > 0) {
                        head.append("   Discount ").append(p.getDiscountPercent().stripTrailingZeros().toPlainString())
                                .append("% (-").append(money(nz(p.getDiscountAmount()))).append(')');
                    }
                    if (p.getReceiptNumber() != null && !p.getReceiptNumber().isBlank()) {
                        head.append("   Receipt ").append(p.getReceiptNumber());
                    }
                    doc.add(new Paragraph(text(head.toString())).setFont(bold).setMarginTop(6).setMarginBottom(2));
                    doc.add(productTable(itemsByPayment.getOrDefault(p.getId(), List.of()), regular, bold));
                }
            }

            if (!payments.isEmpty()) {
                doc.add(new Paragraph("Summary by payment type").setFont(bold).setFontSize(12).setMarginTop(16));
                Table summary = new Table(UnitValue.createPercentArray(new float[] {40, 15, 22, 23}))
                        .setWidth(UnitValue.createPercentValue(100));
                summary.addHeaderCell(header("Payment type", bold, TextAlignment.LEFT));
                summary.addHeaderCell(header("Payments", bold, TextAlignment.RIGHT));
                summary.addHeaderCell(header("Amount", bold, TextAlignment.RIGHT));
                summary.addHeaderCell(header("Tips", bold, TextAlignment.RIGHT));
                for (Map.Entry<String, BigDecimal[]> e : byType.entrySet()) {
                    summary.addCell(cell(text(e.getKey()), TextAlignment.LEFT));
                    summary.addCell(cell(e.getValue()[0].toPlainString(), TextAlignment.RIGHT));
                    summary.addCell(cell(money(e.getValue()[1]), TextAlignment.RIGHT));
                    summary.addCell(cell(money(e.getValue()[2]), TextAlignment.RIGHT));
                }
                summary.addCell(cell("Total", TextAlignment.LEFT).setFont(bold));
                summary.addCell(cell(String.valueOf(payments.size()), TextAlignment.RIGHT).setFont(bold));
                summary.addCell(cell(money(grandAmount), TextAlignment.RIGHT).setFont(bold));
                summary.addCell(cell(money(grandTip), TextAlignment.RIGHT).setFont(bold));
                doc.add(summary);
            }
        }
        return baos.toByteArray();
    }

    /** The same product at the same unit price, ordered several times, is one line with the quantities summed. */
    static List<OrderItemEntity> merged(List<OrderItemEntity> items) {
        Map<String, OrderItemEntity> byKey = new LinkedHashMap<>();
        for (OrderItemEntity i : items) {
            String name = BridgeService.plainName(i.getName());
            BigDecimal unit = nz(i.getPrice()).stripTrailingZeros();
            OrderItemEntity acc = byKey.computeIfAbsent(name + "|" + unit.toPlainString(), k -> {
                OrderItemEntity m = new OrderItemEntity();
                m.setName(name);
                m.setPrice(nz(i.getPrice()));
                m.setQuantity(0);
                return m;
            });
            acc.setQuantity(acc.getQuantity() + (i.getQuantity() == null ? 0 : i.getQuantity()));
        }
        return new ArrayList<>(byKey.values());
    }

    /** Product | Qty | Unit | Total for the items one payment settled, with the lines' sum underneath. */
    static Table productTable(List<OrderItemEntity> items, PdfFont regular, PdfFont bold) {
        Table t = new Table(UnitValue.createPercentArray(new float[] {58, 10, 16, 16}))
                .setWidth(UnitValue.createPercentValue(100)).setFont(regular);
        t.addHeaderCell(header("Product", bold, TextAlignment.LEFT));
        t.addHeaderCell(header("Qty", bold, TextAlignment.RIGHT));
        t.addHeaderCell(header("Unit", bold, TextAlignment.RIGHT));
        t.addHeaderCell(header("Total", bold, TextAlignment.RIGHT));
        if (items.isEmpty()) {
            t.addCell(cell("(no product lines — a fixed-amount payment)", TextAlignment.LEFT)
                    .setFontColor(MUTED));
            for (int i = 0; i < 3; i++) {
                t.addCell(cell("", TextAlignment.RIGHT));
            }
            return t;
        }
        BigDecimal sum = BigDecimal.ZERO;
        List<OrderItemEntity> sorted = merged(items).stream()
                .sorted(Comparator.comparing((OrderItemEntity i) -> BridgeService.plainName(i.getName()),
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
        for (OrderItemEntity i : sorted) {
            BigDecimal qty = BigDecimal.valueOf(i.getQuantity() == null ? 0 : i.getQuantity());
            BigDecimal unit = nz(i.getPrice());
            BigDecimal line = unit.multiply(qty);
            sum = sum.add(line);
            t.addCell(cell(text(BridgeService.plainName(i.getName())), TextAlignment.LEFT));
            t.addCell(cell(qty.toPlainString(), TextAlignment.RIGHT));
            t.addCell(cell(money(unit), TextAlignment.RIGHT));
            t.addCell(cell(money(line), TextAlignment.RIGHT));
        }
        t.addCell(cell("Products", TextAlignment.LEFT).setFont(bold));
        t.addCell(cell("", TextAlignment.RIGHT));
        t.addCell(cell("", TextAlignment.RIGHT));
        t.addCell(cell(money(sum), TextAlignment.RIGHT).setFont(bold));
        return t;
    }

    static Cell header(String label, PdfFont bold, TextAlignment align) {
        return new Cell().add(new Paragraph(label).setFont(bold).setTextAlignment(align))
                .setBackgroundColor(HEADER_BG).setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                .setPaddingTop(3).setPaddingBottom(3).setPaddingLeft(4).setPaddingRight(4);
    }

    static Cell cell(String value, TextAlignment align) {
        return new Cell().add(new Paragraph(value).setTextAlignment(align))
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                .setPaddingTop(2).setPaddingBottom(2).setPaddingLeft(4).setPaddingRight(4);
    }

    /** Helvetica/CP1250 has no comma-below ș/ț: fold them onto the cedilla forms it does have. */
    static String text(String s) {
        return s == null ? "" : s.replace('ș', 'ş').replace('ț', 'ţ').replace('Ș', 'Ş').replace('Ț', 'Ţ');
    }

    static String money(BigDecimal v) {
        return nz(v).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
