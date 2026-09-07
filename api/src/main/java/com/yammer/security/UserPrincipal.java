package com.yammer.security;

import java.util.List;
import java.util.UUID;

/**
 * The authenticated caller, built from the JWT. {@code clientId} and {@code locationId}
 * are null for SUPER users (who are not scoped to any single client).
 */
public record UserPrincipal(String username, UUID clientId, UUID locationId, List<String> roles) {

    public static final String SUPER = "SUPER";

    public boolean isSuper() {
        return roles != null && roles.contains(SUPER);
    }
}
