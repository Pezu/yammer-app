package com.yammer.service;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.yammer.dto.NotPaidReportRow;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reports → Not paid: the consumption closed without money in a period — every settlement taken with
 * the PROTOCOL or PO payment types, newest first, each with the product lines it covered. The
 * amount is what those lines were worth; nothing was collected for them.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotPaidReportService {

    private final PaymentRepository paymentRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderPointRepository orderPointRepository;
    private final PaymentTypeRepository paymentTypeRepository;
    private final UserRepository userRepository;
    private final AccessGuard accessGuard;

    /** The payment types that close consumption without collecting money. */
    static boolean isNotPaid(String type) {
        return "PROTOCOL".equalsIgnoreCase(type) || "PO".equalsIgnoreCase(type);
    }

    public List<NotPaidReportRow> report(UUID locationId, LocalDate from, LocalDate to) {
        return rows(locationId, from, to);
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    /** The same report as an A4 PDF: one block per settlement (table, time, type, waiter) with its product lines. */
    public byte[] pdf(UUID locationId, LocalDate from, LocalDate to) {
        LocationEntity location = accessGuard.requireAccessibleLocation(locationId);
        if (to.isBefore(from)) {
            LocalDate t = to;
            to = from;
            from = t;
        }
        List<NotPaidReportRow> rows = rows(locationId, from, to);
        PdfFont regular;
        PdfFont bold;
        try {
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
            doc.add(new Paragraph("Not paid (Protocol / PO)").setFont(bold).setFontSize(16));
            String period = from.equals(to) ? from.toString() : from + " – " + to;
            doc.add(new Paragraph(WaiterStatementPdfService.text(location.getName() + "  ·  " + period))
                    .setFontColor(WaiterStatementPdfService.MUTED).setMarginBottom(12));
            if (rows.isEmpty()) {
                doc.add(new Paragraph("Nothing closed as protocol or PO in this period."));
            }
            Map<String, BigDecimal[]> byType = new TreeMap<>(); // [count, amount]
            BigDecimal total = BigDecimal.ZERO;
            for (NotPaidReportRow r : rows) {
                BigDecimal[] acc = byType.computeIfAbsent(r.paymentType(), k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
                acc[0] = acc[0].add(BigDecimal.ONE);
                acc[1] = acc[1].add(r.amount());
                total = total.add(r.amount());
                String when = r.at() == null ? "" : r.at().atZone(ZoneId.systemDefault()).format(TIME);
                doc.add(new Paragraph(WaiterStatementPdfService.text(r.orderPointName() + "   " + when + "   "
                        + r.paymentType() + "   " + r.waiter() + "   Amount " + WaiterStatementPdfService.money(r.amount())))
                        .setFont(bold).setMarginTop(8).setMarginBottom(2));
                doc.add(linesTable(r, regular, bold));
            }
            if (!rows.isEmpty()) {
                doc.add(new Paragraph("Summary").setFont(bold).setFontSize(12).setMarginTop(16));
                Table summary = new Table(UnitValue.createPercentArray(new float[] {50, 20, 30}))
                        .setWidth(UnitValue.createPercentValue(100));
                summary.addHeaderCell(WaiterStatementPdfService.header("Payment type", bold, TextAlignment.LEFT));
                summary.addHeaderCell(WaiterStatementPdfService.header("Settlements", bold, TextAlignment.RIGHT));
                summary.addHeaderCell(WaiterStatementPdfService.header("Amount", bold, TextAlignment.RIGHT));
                for (Map.Entry<String, BigDecimal[]> e : byType.entrySet()) {
                    summary.addCell(WaiterStatementPdfService.cell(WaiterStatementPdfService.text(e.getKey()), TextAlignment.LEFT));
                    summary.addCell(WaiterStatementPdfService.cell(e.getValue()[0].toPlainString(), TextAlignment.RIGHT));
                    summary.addCell(WaiterStatementPdfService.cell(WaiterStatementPdfService.money(e.getValue()[1]), TextAlignment.RIGHT));
                }
                summary.addCell(WaiterStatementPdfService.cell("Total", TextAlignment.LEFT).setFont(bold));
                summary.addCell(WaiterStatementPdfService.cell(String.valueOf(rows.size()), TextAlignment.RIGHT).setFont(bold));
                summary.addCell(WaiterStatementPdfService.cell(WaiterStatementPdfService.money(total), TextAlignment.RIGHT).setFont(bold));
                doc.add(summary);
            }
        }
        return baos.toByteArray();
    }

    private static Table linesTable(NotPaidReportRow r, PdfFont regular, PdfFont bold) {
        Table t = new Table(UnitValue.createPercentArray(new float[] {58, 10, 16, 16}))
                .setWidth(UnitValue.createPercentValue(100)).setFont(regular);
        t.addHeaderCell(WaiterStatementPdfService.header("Product", bold, TextAlignment.LEFT));
        t.addHeaderCell(WaiterStatementPdfService.header("Qty", bold, TextAlignment.RIGHT));
        t.addHeaderCell(WaiterStatementPdfService.header("Unit", bold, TextAlignment.RIGHT));
        t.addHeaderCell(WaiterStatementPdfService.header("Total", bold, TextAlignment.RIGHT));
        if (r.items().isEmpty()) {
            t.addCell(WaiterStatementPdfService.cell("(no product lines — a fixed-amount settlement)", TextAlignment.LEFT)
                    .setFontColor(WaiterStatementPdfService.MUTED));
            for (int i = 0; i < 3; i++) {
                t.addCell(WaiterStatementPdfService.cell("", TextAlignment.RIGHT));
            }
            return t;
        }
        for (NotPaidReportRow.Line line : r.items()) {
            t.addCell(WaiterStatementPdfService.cell(WaiterStatementPdfService.text(line.name()), TextAlignment.LEFT));
            t.addCell(WaiterStatementPdfService.cell(String.valueOf(line.quantity()), TextAlignment.RIGHT));
            t.addCell(WaiterStatementPdfService.cell(WaiterStatementPdfService.money(line.price()), TextAlignment.RIGHT));
            t.addCell(WaiterStatementPdfService.cell(WaiterStatementPdfService.money(line.total()), TextAlignment.RIGHT));
        }
        return t;
    }

    private List<NotPaidReportRow> rows(UUID locationId, LocalDate from, LocalDate to) {
        accessGuard.requireAccessibleLocation(locationId);
        if (to.isBefore(from)) {
            LocalDate t = to;
            to = from;
            from = t;
        }
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        Map<UUID, String> typeName = paymentTypeRepository.findAll().stream()
                .collect(Collectors.toMap(PaymentTypeEntity::getId, PaymentTypeEntity::getType));
        Set<UUID> protocolTypes = typeName.entrySet().stream()
                .filter(e -> isNotPaid(e.getValue()))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        if (protocolTypes.isEmpty()) {
            return List.of();
        }
        List<PaymentEntity> payments = paymentRepository.findByLocationIdAndCreatedAtBetween(locationId, start, end)
                .stream()
                .filter(p -> protocolTypes.contains(p.getPaymentTypeId()) && !"FAILED".equals(p.getStatus()))
                .sorted(Comparator.comparing(PaymentEntity::getCreatedAt).reversed())
                .toList();
        if (payments.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<OrderItemEntity>> itemsByPayment = orderItemRepository
                .findByPaymentIdIn(payments.stream().map(PaymentEntity::getId).toList())
                .stream().collect(Collectors.groupingBy(OrderItemEntity::getPaymentId));
        Map<UUID, String> pointName = orderPointRepository.findByLocationIdOrderByName(locationId).stream()
                .collect(Collectors.toMap(OrderPointEntity::getId, OrderPointEntity::getName));
        Map<String, String> waiterName = userRepository.findByUsernameIn(payments.stream()
                        .map(PaymentEntity::getCreatedBy).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(UserEntity::getUsername,
                        u -> u.getName() == null || u.getName().isBlank() ? u.getUsername() : u.getName(),
                        (a, b) -> a));

        return payments.stream().map(p -> new NotPaidReportRow(
                p.getId(),
                pointName.getOrDefault(p.getOrderPointId(), "?"),
                p.getCreatedBy() == null ? "—" : waiterName.getOrDefault(p.getCreatedBy(), p.getCreatedBy()),
                p.getCreatedAt() == null ? null : p.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant(),
                typeName.getOrDefault(p.getPaymentTypeId(), "PROTOCOL"),
                p.getAmount() == null ? BigDecimal.ZERO : p.getAmount(),
                WaiterStatementPdfService.merged(itemsByPayment.getOrDefault(p.getId(), List.of())).stream()
                        .sorted(Comparator.comparing((OrderItemEntity i) -> BridgeService.plainName(i.getName()),
                                String.CASE_INSENSITIVE_ORDER))
                        .map(i -> {
                            int qty = i.getQuantity() == null ? 0 : i.getQuantity();
                            BigDecimal price = i.getPrice() == null ? BigDecimal.ZERO : i.getPrice();
                            return new NotPaidReportRow.Line(BridgeService.plainName(i.getName()), qty, price,
                                    price.multiply(BigDecimal.valueOf(qty)));
                        })
                        .toList()))
                .toList();
    }
}
