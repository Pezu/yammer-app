package com.yammer.dto;

import jakarta.validation.constraints.Email;
import java.util.List;
import java.util.UUID;

public record UserRequest(
        // Optional: on create a blank username falls back to a random UUID;
        // on update blank means "keep current". A create needs a name or a username.
        String username,
        String name,
        // Optional: blank on create generates a strong random password (QR-only sign-in);
        // blank on update means "keep current password".
        String password,
        String phone,
        @Email String email,
        List<String> roles,
        // Required for non-SUPER users; ignored (cleared) for SUPER users.
        UUID clientId,
        // Required for client-scoped users; must be one of the client's locations.
        UUID locationId) {
}
