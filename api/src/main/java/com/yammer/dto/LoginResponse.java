package com.yammer.dto;

import java.util.List;
import java.util.UUID;

public record LoginResponse(
        String token,
        String username,
        String name,
        List<String> roles,
        UUID clientId,
        UUID locationId) {
}
