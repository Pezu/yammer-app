package com.yammer.dto;

import jakarta.validation.constraints.NotBlank;

public record PaymentTypeRequest(@NotBlank String type) {
}
