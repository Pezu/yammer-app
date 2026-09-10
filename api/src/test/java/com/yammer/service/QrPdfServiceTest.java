package com.yammer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.yammer.entity.LocationEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.OrderPointTypeEntity;
import com.yammer.entity.QrTemplateEntity;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderPointTypeRepository;
import com.yammer.repository.QrTemplateRepository;
import com.yammer.security.AccessGuard;
import com.yammer.service.QrPdfService.Sheet;
import com.yammer.service.StorageService.StoredObject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class QrPdfServiceTest {

    @Test
    void splitSlotsCollapseIntoOneSheetPerTable() {
        List<OrderPointEntity> points = List.of(
                point("B1"), point("B2"), point("T1.1"), point("T1.2"), point("T1.3"), point("T2.1"));

        List<Sheet> sheets = QrPdfService.sheetsOf(points);

        assertEquals(List.of("B1", "B2", "T1", "T2"), sheets.stream().map(Sheet::label).toList());
        assertEquals("T1.1", sheets.get(2).target().getName()); // the QR opens the lowest slot
        assertEquals("T2.1", sheets.get(3).target().getName());
    }

    @Test
    void framedPdfRendersTwoCardsPerPage() throws Exception {
        UUID locationId = UUID.randomUUID();
        UUID tableType = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();

        LocationEntity location = new LocationEntity();
        location.setId(locationId);
        location.setName("Rendezvous");
        location.setQrTemplateId(templateId);

        OrderPointTypeEntity type = new OrderPointTypeEntity();
        type.setId(tableType);
        type.setType("TABLE");

        List<OrderPointEntity> points = List.of(
                point("T1.1", tableType), point("T1.2", tableType), point("T2.1", tableType),
                point("T3.1", tableType), point("T10.1", tableType));

        QrTemplateEntity template = new QrTemplateEntity();
        template.setId(templateId);
        template.setName("Rendezvous");
        template.setImageObject("classpath:qr-templates/rendezvous.png");
        template.setQrX(new BigDecimal("0.2634"));
        template.setQrY(new BigDecimal("0.2179"));
        template.setQrSize(new BigDecimal("0.4732"));
        template.setLabelY(new BigDecimal("0.7714"));
        template.setLabelSize(new BigDecimal("0.0786"));
        template.setLabelColor("#080814");

        byte[] frame;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("qr-templates/rendezvous.png")) {
            frame = in.readAllBytes();
        }

        AccessGuard guard = mock(AccessGuard.class);
        when(guard.requireAccessibleLocation(locationId)).thenReturn(location);
        OrderPointTypeRepository types = mock(OrderPointTypeRepository.class);
        when(types.findAll()).thenReturn(List.of(type));
        OrderPointRepository pointsRepo = mock(OrderPointRepository.class);
        when(pointsRepo.findByLocationIdOrderByName(locationId)).thenReturn(points);
        QrTemplateRepository templates = mock(QrTemplateRepository.class);
        when(templates.findById(templateId)).thenReturn(Optional.of(template));
        QrTemplateService templateService = mock(QrTemplateService.class);
        when(templateService.getImage(any(QrTemplateEntity.class))).thenReturn(new StoredObject(frame, "image/png"));

        QrPdfService service = new QrPdfService(pointsRepo, types, templates, templateService, new QrCodeService(), guard);
        ReflectionTestUtils.setField(service, "appBaseUrl", "https://yammer.ro/");
        service.init();

        byte[] pdf = service.generateOrderPointsQrPdf(locationId);

        assertTrue(pdf.length > 100_000, "PDF should embed the frame image");
        try (PdfDocument doc = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            assertEquals(2, doc.getNumberOfPages()); // 4 sheets (T1, T2, T3, T10), two per page
        }
        Path out = Path.of("target", "qr-framed-sample.pdf");
        Files.createDirectories(out.getParent());
        Files.write(out, pdf);
    }

    private static OrderPointEntity point(String name) {
        return point(name, UUID.randomUUID());
    }

    private static OrderPointEntity point(String name, UUID typeId) {
        OrderPointEntity op = new OrderPointEntity();
        op.setId(UUID.randomUUID());
        op.setName(name);
        op.setTypeId(typeId);
        return op;
    }
}
