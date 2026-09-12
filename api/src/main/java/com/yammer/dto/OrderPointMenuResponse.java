package com.yammer.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * An order point plus its (default) menu tree, for the waiter ordering screen.
 *
 * @param menuId   the order point's default menu (the initially-loaded one)
 * @param items    the default menu's tree
 * @param menus    every menu of the order point's location, so the waiter can switch
 * @param products every orderable product across the location's menus, for the search box
 */
public record OrderPointMenuResponse(
        UUID orderPointId,
        String orderPointName,
        UUID menuId,
        List<MenuItemNode> items,
        List<MenuOption> menus,
        List<ProductOption> products,
        /* runs a tab (pay later); false = pay as you order — the order screen asks for the payment */
        boolean keepOpen,
        /* payment types accepted at this point (the pay-now sheet's buttons) */
        List<UUID> paymentTypeIds,
        /* the table's discount, percent; null = none */
        BigDecimal discountPercent) {

    /** A selectable menu (id + display name) for the order screen's menu switcher. */
    public record MenuOption(UUID id, String name) {
    }

    /** A searchable product (a menu item with orderable=true) and the menu it comes from. */
    public record ProductOption(UUID id, String name, String description, BigDecimal price, UUID menuId,
                                String menuName) {
    }
}
