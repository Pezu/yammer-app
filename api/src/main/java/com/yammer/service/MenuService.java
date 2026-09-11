package com.yammer.service;

import com.yammer.dto.MenuItemNode;
import com.yammer.dto.MenuRequest;
import com.yammer.dto.MenuResponse;
import com.yammer.entity.MenuEntity;
import com.yammer.entity.MenuItemEntity;
import com.yammer.entity.ProductEntity;
import com.yammer.repository.MenuItemRepository;
import com.yammer.repository.MenuRepository;
import com.yammer.repository.ProductRepository;
import com.yammer.security.AccessGuard;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class MenuService {

    /** Object-storage prefix for menu item images. */
    public static final String IMAGE_PREFIX = "menu-items";

    private final MenuRepository menuRepository;
    private final MenuItemRepository menuItemRepository;
    private final ProductRepository productRepository;
    private final AccessGuard accessGuard;
    private final StorageService storageService;

    /** Stores a menu-item image and returns its object-storage key (admin upload). */
    public String uploadImage(MultipartFile file) {
        return storageService.uploadImage(IMAGE_PREFIX, file);
    }

    @Transactional(readOnly = true)
    public List<MenuResponse> listByLocation(UUID locationId) {
        accessGuard.requireAccessibleLocation(locationId);
        return menuRepository.findByLocationIdOrderByName(locationId).stream()
                .map(MenuResponse::from)
                .toList();
    }

    public MenuResponse create(MenuRequest request) {
        accessGuard.requireAccessibleLocation(request.locationId());
        MenuEntity entity = new MenuEntity();
        entity.setLocationId(request.locationId());
        entity.setName(request.name().trim());
        return MenuResponse.from(menuRepository.save(entity));
    }

    public void delete(UUID menuId) {
        requireAccessibleMenu(menuId);
        menuRepository.deleteById(menuId); // cascades to menu_item rows
    }

    @Transactional(readOnly = true)
    public List<MenuItemNode> getTree(UUID menuId) {
        MenuEntity menu = requireAccessibleMenu(menuId);
        return buildTree(menuItemRepository.findByMenuIdOrderBySortOrder(menuId), productsOf(menu.getLocationId()));
    }

    /** The tree without a tenant check — for the PUBLIC customer page (reached via a printed QR). */
    @Transactional(readOnly = true)
    public List<MenuItemNode> getTreeUnchecked(UUID menuId) {
        MenuEntity menu = menuRepository.findById(menuId).orElse(null);
        return menu == null
                ? List.of()
                : buildTree(
                        menuItemRepository.findByMenuIdOrderBySortOrder(menuId),
                        productsOf(menu.getLocationId()));
    }

    /**
     * Saves the tree by reconciling against the existing rows, preserving node ids:
     * nodes that carry a known id are updated in place, new nodes (null/unknown id) are
     * inserted, and rows no longer present are deleted. Keeping ids stable means edits
     * (e.g. a price change) don't disturb anything that references a menu_item id —
     * notably the waiter's drill-down path.
     */
    public List<MenuItemNode> saveTree(UUID menuId, List<MenuItemNode> nodes) {
        MenuEntity menu = requireAccessibleMenu(menuId);
        Map<UUID, ProductEntity> products = productsOf(menu.getLocationId());

        Map<UUID, MenuItemEntity> existing = new HashMap<>();
        for (MenuItemEntity e : menuItemRepository.findByMenuIdOrderBySortOrder(menuId)) {
            existing.put(e.getId(), e);
        }

        Set<UUID> kept = new HashSet<>();
        reconcile(menuId, null, nodes, existing, kept, products);

        // rows that survived the reconcile but aren't in the incoming tree are gone;
        // a single batch delete is safe — parent_id is ON DELETE CASCADE and every
        // removed descendant is included in the set.
        List<UUID> toDelete = existing.keySet().stream().filter(id -> !kept.contains(id)).toList();
        if (!toDelete.isEmpty()) {
            menuItemRepository.deleteAllByIdInBatch(toDelete);
        }

        return buildTree(menuItemRepository.findByMenuIdOrderBySortOrder(menuId), products);
    }

    // --- helpers ---

    private void reconcile(
            UUID menuId,
            UUID parentId,
            List<MenuItemNode> nodes,
            Map<UUID, MenuItemEntity> existing,
            Set<UUID> kept,
            Map<UUID, ProductEntity> products) {
        if (nodes == null) {
            return;
        }
        int order = 0;
        for (MenuItemNode node : nodes) {
            MenuItemEntity entity = node.id() == null ? null : existing.get(node.id());
            if (entity == null) {
                entity = new MenuItemEntity();
                entity.setMenuId(menuId);
            }
            entity.setParentId(parentId);
            entity.setOrderable(node.orderable());
            if (node.orderable()) {
                ProductEntity product = node.productId() == null ? null : products.get(node.productId());
                if (product == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "A product node must reference a product of the menu's location");
                }
                entity.setProductId(product.getId());
                entity.setName(product.getName()); // display snapshot — re-derived on every read
                entity.setPrice(node.price()); // the price ON THIS MENU
                entity.setImageObject(null);
            } else {
                entity.setProductId(null);
                entity.setName(node.name() == null ? "" : node.name().trim());
                entity.setPrice(null);
                entity.setImageObject(node.imageObject());
            }
            entity.setSortOrder(order++);
            UUID id = menuItemRepository.save(entity).getId();
            kept.add(id);
            reconcile(menuId, id, node.children(), existing, kept, products);
        }
    }

    private List<MenuItemNode> buildTree(List<MenuItemEntity> all, Map<UUID, ProductEntity> products) {
        Map<UUID, List<MenuItemEntity>> byParent = new LinkedHashMap<>();
        for (MenuItemEntity e : all) {
            byParent.computeIfAbsent(e.getParentId(), k -> new ArrayList<>()).add(e);
        }
        return toNodes(byParent.get(null), byParent, products);
    }

    private List<MenuItemNode> toNodes(
            List<MenuItemEntity> items,
            Map<UUID, List<MenuItemEntity>> byParent,
            Map<UUID, ProductEntity> products) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(e -> {
                    ProductEntity product = e.getProductId() == null ? null : products.get(e.getProductId());
                    return new MenuItemNode(
                            e.getId(),
                            product != null ? product.getName() : e.getName(),
                            e.isOrderable(),
                            e.getProductId(),
                            e.getPrice(),
                            product != null ? product.getVatTypeId() : null,
                            product != null ? product.getImageObject() : e.getImageObject(),
                            product != null ? product.getDescription() : null,
                            toNodes(byParent.get(e.getId()), byParent, products));
                })
                .toList();
    }

    private Map<UUID, ProductEntity> productsOf(UUID locationId) {
        return productRepository.findByLocationIdOrderByName(locationId).stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));
    }

    private MenuEntity requireAccessibleMenu(UUID menuId) {
        MenuEntity menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu not found: " + menuId));
        accessGuard.requireAccessibleLocation(menu.getLocationId());
        return menu;
    }
}
