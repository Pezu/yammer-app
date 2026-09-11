package com.yammer.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/** Geometry fields are fractions of the frame image; null keeps the current value (or the default on create). */
public record QrTemplateRequest(
        @NotBlank String name,
        @DecimalMin("0") @DecimalMax("1") BigDecimal qrX,
        @DecimalMin("0") @DecimalMax("1") BigDecimal qrY,
        @DecimalMin("0.01") @DecimalMax("1") BigDecimal qrSize,
        @Pattern(regexp = "#[0-9a-fA-F]{6}") String qrColor,
        @DecimalMin("0") @DecimalMax("1") BigDecimal labelY,
        @DecimalMin("0.005") @DecimalMax("1") BigDecimal labelSize,
        @Pattern(regexp = "#[0-9a-fA-F]{6}") String labelColor) {
}
