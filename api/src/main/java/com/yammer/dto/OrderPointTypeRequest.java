package com.yammer.dto;

import jakarta.validation.constraints.NotBlank;

public record OrderPointTypeRequest(@NotBlank String type) {
}
