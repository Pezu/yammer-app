package com.yammer.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject;
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
import com.yammer.entity.QrTemplateEntity;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderPointTypeRepository;
import com.yammer.repository.QrTemplateRepository;
import com.yammer.security.AccessGuard;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Builds a printable PDF of QR codes — one per customer-facing order point (TABLE/BAR)
 * of a location. Each QR encodes the customer ordering URL
 * {@code <app.base-url>/customer/order-point/{opId}}.
 *
 * <p>A table's split slots (T1.1, T1.2, …) are one physical table, so they collapse into a
 * single sheet labelled "T1" whose QR opens the lowest slot — guests scan the table, not a
 * seat. Other points (B1, …) are one sheet each.
 *
 * <p>When the location has a QR template, every sheet is rendered as one framed card: the
 * template's background image with the QR code and the label placed where the template says
 * (two per A4 page, ready to cut). Without a template the old plain 3-column grid is
 * produced. ZXing renders the QR images (via {@link QrCodeService}), iText lays out the PDF.
 */
@Service
@RequiredArgsConstructor
public class QrPdfService {

    /** The point types customers can sit and order at. */
    private static final Set<String> CUSTOMER_TYPES = Set.of("TABLE", "BAR");

    /** Framed-card layout: A4 portrait filled left-to-right, top-to-bottom with cards this wide (~10.6 cm: two per page). */
    private static final float CARD_WIDTH = 300f;
    private static final float PAGE_MARGIN = 40f;
    private static final float CARD_GAP = 25f;

    private final OrderPointRepository orderPointRepository;
    private final OrderPointTypeRepository orderPointTypeRepository;
    private final QrTemplateRepository qrTemplateRepository;
    private final QrTemplateService qrTemplateService;
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

        List<Sheet> sheets = sheetsOf(orderPoints);
        QrTemplateEntity template = location.getQrTemplateId() == null
                ? null
                : qrTemplateRepository.findById(location.getQrTemplateId()).orElse(null);
        if (template != null && template.getImageObject() != null) {
            return framedPdf(template, sheets);
        }
        return plainGridPdf(location, sheets);
    }

    /** One printed sheet: the label guests read and the order point its QR opens. */
    record Sheet(String label, OrderPointEntity target) {
    }

    private static final Pattern SPLIT_NAME = Pattern.compile("^(.+?)\\.(\\d+)$");

    /**
     * Collapse split slots into one sheet per table: T1.1/T1.2/… → "T1" pointing at the lowest
     * slot (the input is already name-sorted, so the first slot seen is the lowest). Points
     * without a ".n" suffix are one sheet each under their own name.
     */
    static List<Sheet> sheetsOf(List<OrderPointEntity> orderPoints) {
        Map<String, Sheet> byLabel = new LinkedHashMap<>();
        for (OrderPointEntity op : orderPoints) {
            Matcher m = SPLIT_NAME.matcher(op.getName());
            String label = m.matches() ? m.group(1) : op.getName();
            byLabel.putIfAbsent(label, new Sheet(label, op));
        }
        return new ArrayList<>(byLabel.values());
    }

    // ---- framed cards (QR template) ----

    private byte[] framedPdf(QrTemplateEntity template, List<Sheet> sheets) {
        ImageData frame = ImageDataFactory.create(qrTemplateService.getImage(template).data());
        float cardHeight = CARD_WIDTH * frame.getHeight() / frame.getWidth();
        PdfFont font;
        try {
            font = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "PDF font unavailable", e);
        }
        DeviceRgb labelColor = rgb(template.getLabelColor());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
        try {
            PdfImageXObject frameObject = new PdfImageXObject(frame); // embedded once, drawn per card
            Rectangle page = PageSize.A4;
            int columns = Math.max(1, (int) ((page.getWidth() - 2 * PAGE_MARGIN + CARD_GAP) / (CARD_WIDTH + CARD_GAP)));
            int rows = Math.max(1, (int) ((page.getHeight() - 2 * PAGE_MARGIN + CARD_GAP) / (cardHeight + CARD_GAP)));
            float gridWidth = columns * CARD_WIDTH + (columns - 1) * CARD_GAP;
            float gridLeft = (page.getWidth() - gridWidth) / 2;
            float gridTop = page.getHeight() - PAGE_MARGIN;
            int perPage = columns * rows;
            PdfCanvas canvas = null;

            for (int i = 0; i < sheets.size(); i++) {
                int slot = i % perPage;
                if (slot == 0) {
                    PdfPage p = pdf.addNewPage(PageSize.A4);
                    canvas = new PdfCanvas(p);
                }
                float left = gridLeft + (slot % columns) * (CARD_WIDTH + CARD_GAP);
                float bottom = gridTop - (slot / columns + 1) * cardHeight - (slot / columns) * CARD_GAP;
                drawCard(canvas, template, frameObject, font, labelColor, sheets.get(i), left, bottom, cardHeight);
            }
            if (sheets.isEmpty()) {
                pdf.addNewPage(PageSize.A4); // an empty PDF is invalid — leave one blank page
            }
        } finally {
            pdf.close();
        }
        return baos.toByteArray();
    }

    /** One framed card: background, QR in its slot, the sheet label centred at its baseline. */
    private void drawCard(PdfCanvas canvas, QrTemplateEntity t, PdfImageXObject frame, PdfFont font,
                          DeviceRgb labelColor, Sheet sheet, float left, float bottom, float height) {
        float width = CARD_WIDTH;
        canvas.addXObjectFittedIntoRectangle(frame, new Rectangle(left, bottom, width, height));

        String url = baseUrl + "/customer/order-point/" + sheet.target().getId();
        float qrSize = t.getQrSize().floatValue() * width;
        float qrLeft = left + t.getQrX().floatValue() * width;
        float qrBottom = bottom + height - t.getQrY().floatValue() * height - qrSize;
        ImageData qr = ImageDataFactory.create(qrCodeService.png(url, 600));
        canvas.addImageFittedIntoRectangle(qr, new Rectangle(qrLeft, qrBottom, qrSize, qrSize), false);

        String name = sheet.label();
        float fontSize = t.getLabelSize().floatValue() * width;
        float maxWidth = width * 0.9f;
        float textWidth = font.getWidth(name, fontSize);
        if (textWidth > maxWidth) { // long names shrink to fit the card
            fontSize *= maxWidth / textWidth;
            textWidth = maxWidth;
        }
        float baseline = bottom + height - t.getLabelY().floatValue() * height;
        canvas.beginText()
                .setFontAndSize(font, fontSize)
                .setFillColor(labelColor)
                .moveText(left + (width - textWidth) / 2, baseline)
                .showText(name)
                .endText();
    }

    private static DeviceRgb rgb(String hex) {
        try {
            int v = Integer.parseInt(hex.substring(1), 16);
            return new DeviceRgb((v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF);
        } catch (RuntimeException e) {
            return new DeviceRgb(255, 255, 255);
        }
    }

    // ---- plain grid (no template) ----

    private byte[] plainGridPdf(LocationEntity location, List<Sheet> sheets) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
        Document document = new Document(pdf);
        try {
            document.add(new Paragraph(location.getName())
                    .setFontSize(20).setBold().setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph(" "));

            Table table = new Table(UnitValue.createPercentArray(new float[]{33.33f, 33.33f, 33.33f}));
            table.setWidth(UnitValue.createPercentValue(100));

            for (Sheet sheet : sheets) {
                String url = baseUrl + "/customer/order-point/" + sheet.target().getId();
                Image image = new Image(ImageDataFactory.create(qrCodeService.png(url, 300)))
                        .setWidth(150)
                        .setHorizontalAlignment(HorizontalAlignment.CENTER);
                Cell cell = new Cell();
                cell.add(new Paragraph(sheet.label())
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
            int remainder = sheets.size() % 3;
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
