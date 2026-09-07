package com.yammer.service;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Link;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.yammer.entity.LocationEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.OrderPointTypeEntity;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderPointTypeRepository;
import com.yammer.security.AccessGuard;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds a printable PDF of QR codes — one per customer-facing order point (TABLE/BAR)
 * of a location. Each QR encodes the customer ordering URL
 * {@code <app.base-url>/customer/order-point/{opId}} (the page itself is a future port —
 * old yammer generated these per EVENT; here menus and points hang off the location).
 * ZXing renders the QR images (via {@link QrCodeService}), iText lays out the PDF.
 */
@Service
@RequiredArgsConstructor
public class QrPdfService {

    /** The point types customers can sit and order at. */
    private static final Set<String> CUSTOMER_TYPES = Set.of("TABLE", "BAR");

    private final OrderPointRepository orderPointRepository;
    private final OrderPointTypeRepository orderPointTypeRepository;
    private final QrCodeService qrCodeService;
    private final AccessGuard accessGuard;

    /** Web app base URL the QR codes point at (APP_URL, same as the login QRs). */
    @Value("${app.base-url}")
    private String appBaseUrl;

    private String baseUrl;

    @PostConstruct
    void init() {
        // Trim a trailing slash so URL building is clean.
        this.baseUrl = appBaseUrl.endsWith("/")
                ? appBaseUrl.substring(0, appBaseUrl.length() - 1)
                : appBaseUrl;
    }

    @Transactional(readOnly = true)
    public byte[] generateOrderPointsQrPdf(UUID locationId) {
        LocationEntity location = accessGuard.requireAccessibleLocation(locationId);
        Map<UUID, String> typeById = orderPointTypeRepository.findAll().stream()
                .collect(Collectors.toMap(OrderPointTypeEntity::getId, OrderPointTypeEntity::getType));
        List<OrderPointEntity> orderPoints = orderPointRepository.findByLocationIdOrderByName(locationId)
                .stream()
                .filter(op -> CUSTOMER_TYPES.contains(typeById.getOrDefault(op.getTypeId(), "")))
                .sorted((a, b) -> OrderPointService.compareNames(a.getName(), b.getName()))
                .toList();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
        Document document = new Document(pdf);
        try {
            document.add(new Paragraph(location.getName())
                    .setFontSize(20).setBold().setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph(" "));

            Table table = new Table(UnitValue.createPercentArray(new float[]{33.33f, 33.33f, 33.33f}));
            table.setWidth(UnitValue.createPercentValue(100));

            for (OrderPointEntity op : orderPoints) {
                String url = baseUrl + "/customer/order-point/" + op.getId();
                Image image = new Image(ImageDataFactory.create(qrCodeService.png(url, 300)))
                        .setWidth(150)
                        .setHorizontalAlignment(HorizontalAlignment.CENTER);
                Cell cell = new Cell();
                cell.add(new Paragraph(op.getName())
                        .setFontSize(10).setBold().setTextAlignment(TextAlignment.CENTER));
                cell.add(image);
                // TODO: testing aid — clickable link under each QR; remove for production.
                Link link = new Link(url, PdfAction.createURI(url));
                link.setFontColor(ColorConstants.BLUE);
                cell.add(new Paragraph(link).setFontSize(6).setTextAlignment(TextAlignment.CENTER));
                cell.setTextAlignment(TextAlignment.CENTER);
                table.addCell(cell);
            }
            // pad the last row so the grid stays aligned
            int remainder = orderPoints.size() % 3;
            if (remainder != 0) {
                for (int i = 0; i < 3 - remainder; i++) {
                    table.addCell(new Cell());
                }
            }
            document.add(table);
        } finally {
            document.close();
        }
        return baos.toByteArray();
    }
}
