package com.yammer.dto;

import jakarta.validation.constraints.NotBlank;

public record QrLoginRequest(@NotBlank String token) {
}
