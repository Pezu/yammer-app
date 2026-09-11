package com.yammer.dto;

import com.yammer.entity.QrTemplateEntity;
import java.math.BigDecimal;
import java.util.UUID;

public record QrTemplateResponse(
        UUID id,
        String name,
        boolean hasImage,
        BigDecimal qrX,
        BigDecimal qrY,
        BigDecimal qrSize,
        String qrColor,
        BigDecimal labelY,
        BigDecimal labelSize,
        String labelColor) {

    public static QrTemplateResponse from(QrTemplateEntity e) {
        return new QrTemplateResponse(e.getId(), e.getName(), e.getImageObject() != null,
                e.getQrX(), e.getQrY(), e.getQrSize(), e.getQrColor(), e.getLabelY(), e.getLabelSize(), e.getLabelColor());
    }
}
