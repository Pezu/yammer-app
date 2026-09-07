package com.yammer.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One node of a menu tree, used both ways. On read, {@code id} is populated and product
 * nodes are enriched (name/price/vatTypeId/imageObject come from the referenced catalog
 * product). On write (save-the-tree) a known {@code id} updates that node in place while
 * a null/unknown id inserts a new one — node ids stay stable across edits. A category has
 * {@code orderable=false} (name + own image); a product node has {@code orderable=true}
 * and MUST reference a catalog {@code productId}.
 */
public record MenuItemNode(
        UUID id,
        String name,
        boolean orderable,
        UUID productId,
        BigDecimal price,
        UUID vatTypeId,
        String imageObject,
        List<MenuItemNode> children) {
}
