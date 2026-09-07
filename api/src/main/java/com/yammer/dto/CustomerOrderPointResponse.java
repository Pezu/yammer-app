package com.yammer.dto;

import java.util.List;
import java.util.UUID;

/**
 * Public, customer-facing view of an order point reached by scanning its QR:
 * the table, its client (for the logo), and its menu tree. The menu is always
 * browsable; ordering additionally needs {@code sessionOpen}, a self-order mode
 * other than DISALLOW, and an APPROVED {@code customerStatus} (the assigned
 * waiter approves each device's first join).
 */
public record CustomerOrderPointResponse(
        UUID id,
        String name,
        UUID clientId,
        boolean sessionOpen,
        String selfOrderMode,
        String customerStatus,
        /* ONLINE self-pay table: ordering goes through the Netopia gateway */
        boolean selfPayOnline,
        List<MenuItemNode> menu) {
}
