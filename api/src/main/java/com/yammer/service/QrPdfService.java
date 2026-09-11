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
import org.springframework.data.domain.Sort;
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
 * <p>Every sheet is one framed card — the QR template's image with the QR code (in the
 * template's colour, transparent background) and the label placed where the template says —
 * tiled three per row under the location name, exactly like the plain grid. The location's
 * own template wins; otherwise the catalog's first template is used; the plain black-on-white
 * grid only remains for a catalog with no frames at all. ZXing renders the QR images (via
 * {@link QrCodeService}), iText lays out the PDF.
 */
@Service
@RequiredArgsConstructor
public class QrPdfService {

    /** The point types customers can sit and order at. */
    private static final Set<String> CUSTOMER_TYPES = Set.of("TABLE", "BAR");

    /** Framed-card layout: A4 portrait, the location name on top, then three cards per row. */
    private static final int COLUMNS = 3;
    private static final float PAGE_MARGIN = 36f;
    private static final float CARD_GAP = 12f;
    private static final float TITLE_HEIGHT = 34f;

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
        QrTemplateEntity template = templateFor(location);
        if (template != null) {
            return framedPdf(location, template, sheets);
        }
        return plainGridPdf(location, sheets);
    }

    /** The location's frame, else the catalog's first frame with an image, else null. */
    private QrTemplateEntity templateFor(LocationEntity location) {
        if (location.getQrTemplateId() != null) {
            QrTemplateEntity own = qrTemplateRepository.findById(location.getQrTemplateId()).orElse(null);
            if (own != null && own.getImageObject() != null) {
                return own;
            }
        }
        return qrTemplateRepository.findAll(Sort.by("name")).stream()
                .filter(t -> t.getImageObject() != null)
                .findFirst()
                .orElse(null);
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

    private byte[] framedPdf(LocationEntity location, QrTemplateEntity template, List<Sheet> sheets) {
        ImageData frame = ImageDataFactory.create(qrTemplateService.getImage(template).data());
        Rectangle page = PageSize.A4;
        float cardWidth = (page.getWidth() - 2 * PAGE_MARGIN - (COLUMNS - 1) * CARD_GAP) / COLUMNS;
        float cardHeight = cardWidth * frame.getHeight() / frame.getWidth();
        PdfFont font;
        try {
            font = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "PDF font unavailable", e);
        }
        DeviceRgb labelColor = rgb(template.getLabelColor());
        int qrArgb = 0xFF000000 | rgbInt(template.getQrColor());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
        try {
            PdfImageXObject frameObject = new PdfImageXObject(frame); // embedded once, drawn per card
            float gridTop = page.getHeight() - PAGE_MARGIN - TITLE_HEIGHT;
            int rows = Math.max(1, (int) ((gridTop - PAGE_MARGIN + CARD_GAP) / (cardHeight + CARD_GAP)));
            int perPage = COLUMNS * rows;
            PdfCanvas canvas = null;

            for (int i = 0; i < sheets.size(); i++) {
                int slot = i % perPage;
                if (slot == 0) {
                    PdfPage p = pdf.addNewPage(PageSize.A4);
                    canvas = new PdfCanvas(p);
                    drawTitle(canvas, font, location.getName(), page);
                }
                float left = PAGE_MARGIN + (slot % COLUMNS) * (cardWidth + CARD_GAP);
                float bottom = gridTop - (slot / COLUMNS + 1) * cardHeight - (slot / COLUMNS) * CARD_GAP;
                drawCard(canvas, template, frameObject, font, labelColor, qrArgb, sheets.get(i),
                        left, bottom, cardWidth, cardHeight);
            }
            if (sheets.isEmpty()) {
                pdf.addNewPage(PageSize.A4); // an empty PDF is invalid — leave one blank page
            }
        } finally {
            pdf.close();
        }
        return baos.toByteArray();
    }

    /** The location name, centred at the top of a page (same as the plain grid's heading). */
    private void drawTitle(PdfCanvas canvas, PdfFont font, String title, Rectangle page) {
        float size = 18f;
        float width = font.getWidth(title, size);
        canvas.beginText()
                .setFontAndSize(font, size)
                .setFillColor(ColorConstants.BLACK)
                .moveText((page.getWidth() - width) / 2, page.getHeight() - PAGE_MARGIN - size)
                .showText(title)
                .endText();
    }

    /** One framed card: background, QR in its slot, the sheet label centred at its baseline. */
    private void drawCard(PdfCanvas canvas, QrTemplateEntity t, PdfImageXObject frame, PdfFont font,
                          DeviceRgb labelColor, int qrArgb, Sheet sheet,
                          float left, float bottom, float width, float height) {
        canvas.addXObjectFittedIntoRectangle(frame, new Rectangle(left, bottom, width, height));

        String url = baseUrl + "/customer/order-point/" + sheet.target().getId();
        float qrSize = t.getQrSize().floatValue() * width;
        float qrLeft = left + t.getQrX().floatValue() * width;
        float qrBottom = bottom + height - t.getQrY().floatValue() * height - qrSize;
        // Modules in the template's colour on a transparent background: the frame shows through.
        ImageData qr = ImageDataFactory.create(qrCodeService.png(url, 600, qrArgb, 0x00000000));
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
        int v = rgbInt(hex);
        return new DeviceRgb((v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF);
    }

    /** {@code #RRGGBB} → 0xRRGGBB; white when malformed. */
    private static int rgbInt(String hex) {
        try {
            return Integer.parseInt(hex.substring(1), 16) & 0xFFFFFF;
        } catch (RuntimeException e) {
            return 0xFFFFFF;
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
