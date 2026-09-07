package com.yammer.dto;

import jakarta.validation.constraints.NotBlank;

public record SelfPayTypeRequest(@NotBlank String type) {
}
