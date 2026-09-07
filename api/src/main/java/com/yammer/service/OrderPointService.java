package com.yammer.service;

import com.yammer.dto.CreateOrderPointsBatchRequest;
import com.yammer.dto.MenuItemNode;
import com.yammer.dto.OrderPointMenuResponse;
import com.yammer.dto.OrderPointRequest;
import com.yammer.dto.OrderPointResponse;
import com.yammer.entity.MenuEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.OrderPointTypeEntity;
import com.yammer.entity.ProductEntity;
import com.yammer.repository.MenuItemRepository;
import com.yammer.repository.MenuRepository;
import com.yammer.repository.ProductRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderPointTypeRepository;
import com.yammer.repository.PaymentTypeRepository;
import com.yammer.repository.SelfPayTypeRepository;
import com.yammer.security.AccessGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class OrderPointService {

    private final OrderPointRepository orderPointRepository;
    private final OrderPointTypeRepository orderPointTypeRepository;
    private final PaymentTypeRepository paymentTypeRepository;
    private final SelfPayTypeRepository selfPayTypeRepository;
    private final MenuRepository menuRepository;
    private final MenuItemRepository menuItemRepository;
    private final ProductRepository productRepository;
    private final MenuService menuService;
    private final AccessGuard accessGuard;

    /** The order point plus its menu tree (for the waiter ordering screen). */
    @Transactional(readOnly = true)
    public OrderPointMenuResponse getMenu(UUID id) {
        OrderPointEntity op = accessGuard.requireAccessibleOrderPoint(id);
        List<MenuItemNode> items =
                op.getMenuId() == null ? List.of() : menuService.getTree(op.getMenuId());

        List<MenuEntity> locationMenus = menuRepository.findByLocationIdOrderByName(op.getLocationId());
        List<OrderPointMenuResponse.MenuOption> menus = locationMenus.stream()
                .map(m -> new OrderPointMenuResponse.MenuOption(m.getId(), m.getName()))
                .toList();

        // Every orderable product across the location's menus, for the search box.
        // Price/name come from the referenced catalog product.
        Map<UUID, String> menuNameById = locationMenus.stream()
                .collect(Collectors.toMap(MenuEntity::getId, MenuEntity::getName));
        Map<UUID, ProductEntity> productsById = productRepository
                .findByLocationIdOrderByName(op.getLocationId()).stream()
                .collect(Collectors.toMap(ProductEntity::getId, p -> p));
        List<UUID> menuIds = locationMenus.stream().map(MenuEntity::getId).toList();
        List<OrderPointMenuResponse.ProductOption> products = menuIds.isEmpty()
                ? List.of()
                : menuItemRepository.findByMenuIdInAndOrderableTrueOrderByName(menuIds).stream()
                        .map(mi -> {
                            ProductEntity p = mi.getProductId() == null
                                    ? null
                                    : productsById.get(mi.getProductId());
                            return new OrderPointMenuResponse.ProductOption(
                                    mi.getId(),
                                    p != null ? p.getName() : mi.getName(),
                                    mi.getPrice(), // the price on that menu
                                    mi.getMenuId(),
                                    menuNameById.get(mi.getMenuId()));
                        })
                        .toList();

        return new OrderPointMenuResponse(
                op.getId(), op.getName(), op.getMenuId(), items, menus, products);
    }

    /** Order points of one location (tenant-checked via the location), naturally ordered. */
    public List<OrderPointResponse> list(UUID locationId) {
        accessGuard.requireAccessibleLocation(locationId);
        return orderPointRepository.findByLocationIdOrderByName(locationId).stream()
                .sorted((a, b) -> compareNames(a.getName(), b.getName()))
                .map(OrderPointResponse::from)
                .toList();
    }

    private static final Pattern SORT_NAME = Pattern.compile("^([A-Za-z]+)(\\d+)(?:\\.(\\d+))?$");

    /** B1…Bn, S1…Sn, T1.1…Tn.1 — prefix groups alphabetically, numbers numerically. */
    public static int compareNames(String a, String b) {
        Matcher ma = SORT_NAME.matcher(a);
        Matcher mb = SORT_NAME.matcher(b);
        if (ma.matches() && mb.matches()) {
            int byPrefix = ma.group(1).compareToIgnoreCase(mb.group(1));
            if (byPrefix != 0) {
                return byPrefix;
            }
            int byNumber = Integer.compare(Integer.parseInt(ma.group(2)), Integer.parseInt(mb.group(2)));
            if (byNumber != 0) {
                return byNumber;
            }
            return Integer.compare(splitSlot(ma), splitSlot(mb));
        }
        return a.compareToIgnoreCase(b);
    }

    private static int splitSlot(Matcher m) {
        return m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
    }

    /** Creates {@code count} order points at once, auto-naming them by type (T1…, B1…, S1…). */
    public List<OrderPointResponse> createBatch(CreateOrderPointsBatchRequest request) {
        accessGuard.requireAccessibleLocation(request.locationId());
        OrderPointTypeEntity type = orderPointTypeRepository.findById(request.typeId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Unknown order point type: " + request.typeId()));
        UUID serviceId = resolveServicePoint(request.locationId(), request.serviceOrderPointId());

        List<OrderPointEntity> existing = orderPointRepository.findByLocationIdOrderByName(request.locationId());
        List<String> names = generateNames(existing, request.count(), type.getType());

        List<UUID> paymentTypeIds = resolvePaymentTypes(request.paymentTypeIds());
        UUID selfPayTypeId = resolveSelfPayType(request.selfPayTypeId());
        List<OrderPointEntity> toCreate = names.stream().map(name -> {
            OrderPointEntity op = new OrderPointEntity();
            op.setLocationId(request.locationId());
            op.setName(name);
            op.setTypeId(request.typeId());
            op.setSelfPayTypeId(selfPayTypeId);
            op.setAllowMultipleUsers(request.allowMultipleUsers());
            op.setPaymentTypeIds(new ArrayList<>(paymentTypeIds));
            op.setMenuId(request.menuId());
            op.setServiceOrderPointId(serviceId);
            op.setPrinterId(request.printerId());
            op.setCashRegisterId(request.cashRegisterId());
            return op;
        }).toList();
        return orderPointRepository.saveAll(toCreate).stream().map(OrderPointResponse::from).toList();
    }

    public OrderPointResponse update(UUID id, OrderPointRequest request) {
        OrderPointEntity entity = accessGuard.requireAccessibleOrderPoint(id);
        if (Objects.equals(request.serviceOrderPointId(), id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An order point cannot serve itself");
        }
        UUID serviceId = resolveServicePoint(entity.getLocationId(), request.serviceOrderPointId());
        entity.setName(request.name().trim());
        entity.setSelfPayTypeId(resolveSelfPayType(request.selfPayTypeId()));
        entity.setAllowMultipleUsers(request.allowMultipleUsers());
        entity.setPaymentTypeIds(resolvePaymentTypes(request.paymentTypeIds()));
        entity.setMenuId(request.menuId());
        entity.setServiceOrderPointId(serviceId);
        entity.setPrinterId(request.printerId());
        entity.setCashRegisterId(request.cashRegisterId());
        return OrderPointResponse.from(orderPointRepository.save(entity));
    }

    public void delete(UUID id) {
        OrderPointEntity entity = accessGuard.requireAccessibleOrderPoint(id);
        orderPointRepository.delete(entity);
    }

    /** The self pay type, if set, must exist in the catalog; null → none. */
    private UUID resolveSelfPayType(UUID requested) {
        if (requested != null && !selfPayTypeRepository.existsById(requested)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown self pay type: " + requested);
        }
        return requested;
    }

    /** Every requested payment type must exist in the catalog; null → none. */
    private List<UUID> resolvePaymentTypes(List<UUID> requested) {
        List<UUID> ids = requested == null ? List.of() : requested.stream().distinct().toList();
        for (UUID id : ids) {
            if (!paymentTypeRepository.existsById(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown payment type: " + id);
            }
        }
        return new ArrayList<>(ids);
    }

    /** The serving point must exist in the same location (the UI offers only SERVICE-type points). */
    private UUID resolveServicePoint(UUID locationId, UUID requested) {
        if (requested == null) {
            return null;
        }
        OrderPointEntity servicePoint = orderPointRepository.findById(requested)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Unknown service point: " + requested));
        if (!Objects.equals(servicePoint.getLocationId(), locationId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service point is not in this location");
        }
        return requested;
    }

    /**
     * Names are the type's first letter plus a number (BAR → B1, SERVICE → S1), numbering
     * continuing past the location's current max for that prefix. TABLE points are born as
     * T{n}.1 — the ".1" is the first split slot, because tables get split (T{n}.2, …) later,
     * exactly like the old project's pay-later M{n}.1 scheme.
     */
    private List<String> generateNames(List<OrderPointEntity> existing, int count, String typeName) {
        String prefix = typeName.substring(0, 1).toUpperCase();
        boolean split = "TABLE".equalsIgnoreCase(typeName);
        Pattern pattern = split
                ? Pattern.compile("^" + Pattern.quote(prefix) + "(\\d+)\\.\\d+$")
                : Pattern.compile("^" + Pattern.quote(prefix) + "(\\d+)$");
        int max = existing.stream()
                .map(op -> pattern.matcher(op.getName()))
                .filter(Matcher::matches)
                .mapToInt(m -> Integer.parseInt(m.group(1)))
                .max()
                .orElse(0);
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> prefix + (max + i) + (split ? ".1" : ""))
                .toList();
    }
}
