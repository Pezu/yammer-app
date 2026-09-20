package com.yammer.service;

import com.yammer.dto.DashboardResponse.FinalRow;
import com.yammer.dto.ShiftCloseResponse;
import com.yammer.dto.ShiftResponse;
import com.yammer.entity.IntegrationEntity;
import com.yammer.entity.LocationEntity;
import com.yammer.entity.LocationShiftEntity;
import com.yammer.entity.PaymentEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.OrderPointTypeEntity;
import com.yammer.entity.PaymentTypeEntity;
import com.yammer.entity.UserEntity;
import com.yammer.repository.IntegrationRepository;
import com.yammer.repository.LocationShiftRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderPointTypeRepository;
import com.yammer.repository.PaymentRepository;
import com.yammer.repository.PaymentTypeRepository;
import com.yammer.repository.UserRepository;
import com.yammer.security.AccessGuard;
import com.yammer.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The location's service day. A manager opens it and closes it in the backoffice; closing
 * prints one cash-up slip per waiter (the same thermal slip as the dashboard's final report)
 * and stamps the day shut.
 *
 * <p>Every order and payment is stamped with the open shift's id ({@link #requireOpenShiftId}),
 * and every report matches on that id — never on a time window. That is why the waiter screens
 * have no date picker: a waiter sees "this service", never a calendar day. Serving with no day
 * open would leave rows unattributed, so the first order or payment opens one implicitly.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ShiftService {

    private static final DateTimeFormatter SLIP_DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final LocationShiftRepository shiftRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentTypeRepository paymentTypeRepository;
    private final IntegrationRepository integrationRepository;
    private final OrderPointRepository orderPointRepository;
    private final OrderPointTypeRepository orderPointTypeRepository;
    private final TableRoundService roundService;
    private final UserRepository userRepository;
    private final BridgeService bridgeService;
    private final AccessGuard accessGuard;
    private final CurrentUserProvider currentUser;

    /** The period a waiter's own figures cover: the open shift, else the current business day. */
    public record Window(LocalDateTime from, LocalDateTime to) {
    }

    /** The location's open shift, or the last closed one, or null if it never had any. */
    @Transactional(readOnly = true)
    public ShiftResponse current(UUID locationId) {
        accessGuard.requireAccessibleLocation(locationId);
        return shiftRepository.findByLocationIdAndClosedAtIsNull(locationId)
                .or(() -> shiftRepository.findFirstByLocationIdOrderByOpenedAtDesc(locationId))
                .map(ShiftService::toResponse)
                .orElse(null);
    }

    /** One service day, or 404 (tenant-checked). */
    @Transactional(readOnly = true)
    public ShiftResponse get(UUID shiftId) {
        return toResponse(require(shiftId));
    }

    /** The window a report covers when it is scoped to one service day; an open day runs up to now. */
    @Transactional(readOnly = true)
    public Window windowOf(UUID shiftId) {
        LocationShiftEntity shift = require(shiftId);
        return new Window(shift.getOpenedAt(),
                shift.getClosedAt() == null ? LocalDateTime.now() : shift.getClosedAt());
    }

    /** How a service day is named on a slip or a PDF: its nickname, else when it opened. */
    public static String label(ShiftResponse shift) {
        return shift.nickname() != null && !shift.nickname().isBlank()
                ? shift.nickname()
                : SLIP_DAY.format(shift.openedAt());
    }

    private LocationShiftEntity require(UUID shiftId) {
        LocationShiftEntity shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found: " + shiftId));
        accessGuard.requireAccessibleLocation(shift.getLocationId());
        return shift;
    }

    /** The location's service days, newest first (the backoffice Shifts page). */
    @Transactional(readOnly = true)
    public List<ShiftResponse> list(UUID locationId) {
        accessGuard.requireAccessibleLocation(locationId);
        return shiftRepository.findByLocationIdOrderByOpenedAtDesc(locationId).stream()
                .map(ShiftService::toResponse)
                .toList();
    }

    /**
     * The tables that still owe something, by name — what keeps the day from closing.
     * Empty means every table is settled and the day can be cashed up.
     */
    @Transactional(readOnly = true)
    public List<String> openTables(UUID locationId) {
        accessGuard.requireAccessibleLocation(locationId);
        Map<UUID, String> serviceTypes = orderPointTypeRepository.findAll().stream()
                .collect(Collectors.toMap(OrderPointTypeEntity::getId, OrderPointTypeEntity::getType));
        Map<UUID, String> names = orderPointRepository.findByLocationIdOrderByName(locationId).stream()
                .filter(op -> !"SERVICE".equals(serviceTypes.get(op.getTypeId()))) // stations are not tables
                .collect(Collectors.toMap(OrderPointEntity::getId, OrderPointEntity::getName));
        if (names.isEmpty()) {
            return List.of();
        }
        return roundService.pointsWithUnsettled(names.keySet()).stream()
                .map(names::get)
                .filter(java.util.Objects::nonNull)
                .sorted(OrderPointService::compareNames)
                .toList();
    }

    /** Rename a service day (the manager's own label); blank clears it. */
    public ShiftResponse rename(UUID shiftId, String nickname) {
        LocationShiftEntity shift = require(shiftId);
        shift.setNickname(nickname == null || nickname.isBlank() ? null : nickname.trim());
        return toResponse(shiftRepository.saveAndFlush(shift));
    }

    /** Open the service day (409 if one is already running). */
    public ShiftResponse open(UUID locationId, String nickname) {
        accessGuard.requireAccessibleLocation(locationId);
        if (shiftRepository.findByLocationIdAndClosedAtIsNull(locationId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A service day is already open here");
        }
        LocationShiftEntity shift = new LocationShiftEntity();
        shift.setLocationId(locationId);
        shift.setNickname(nickname == null || nickname.isBlank() ? null : nickname.trim());
        shift.setOpenedAt(LocalDateTime.now());
        shift.setOpenedBy(currentUser.require().username());
        LocationShiftEntity saved = shiftRepository.saveAndFlush(shift);
        log.info("Service day opened at location {} by {}", locationId, saved.getOpenedBy());
        return toResponse(saved);
    }

    /**
     * Close the service day and cash up. Refused (409) while any table still has an open
     * session — every table is settled and closed first. The per-waiter takings come back, and —
     * when a printer is named — one slip per waiter goes to it. A print failure does NOT keep
     * the day open; the response says what went wrong and the same slips can be re-printed
     * from the dashboard's final report.
     */
    public ShiftCloseResponse close(UUID locationId, UUID printerId) {
        LocationEntity location = accessGuard.requireAccessibleLocation(locationId);
        LocationShiftEntity shift = shiftRepository.findByLocationIdAndClosedAtIsNull(locationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No service day is open here"));
        // A day cannot be cashed up over tables that are still trading: their bills would
        // land in the next service and the slips printed here would be wrong.
        List<String> stillOpen = openTables(locationId);
        if (!stillOpen.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Close these tables first: " + String.join(", ", stillOpen));
        }
        LocalDateTime closedAt = LocalDateTime.now();
        List<FinalRow> rows = cashUp(shift.getId());

        shift.setClosedAt(closedAt);
        shift.setClosedBy(currentUser.require().username());
        LocationShiftEntity saved = shiftRepository.saveAndFlush(shift);
        log.info("Service day at location {} closed by {} ({} waiter(s) cashed up)",
                locationId, saved.getClosedBy(), rows.size());

        String printError = printerId == null ? null : printSlips(location, printerId, shift, rows);
        return new ShiftCloseResponse(toResponse(saved), rows, printerId != null && printError == null, printError);
    }

    /** The location's open service day, if one is running (a pure read — never opens one). */
    @Transactional(readOnly = true)
    public Optional<UUID> openShiftId(UUID locationId) {
        return locationId == null
                ? Optional.empty()
                : shiftRepository.findByLocationIdAndClosedAtIsNull(locationId).map(LocationShiftEntity::getId);
    }

    /**
     * The shift to stamp on an order or a payment. Serving with no day open would leave rows
     * unattributed — and nobody could cash them up — so one is opened implicitly here, recorded
     * against whoever rang the sale up; a manager closes it like any other.
     */
    public UUID requireOpenShiftId(UUID locationId, String openedBy) {
        return shiftRepository.findByLocationIdAndClosedAtIsNull(locationId)
                .map(LocationShiftEntity::getId)
                .orElseGet(() -> {
                    LocationShiftEntity shift = new LocationShiftEntity();
                    shift.setLocationId(locationId);
                    shift.setOpenedAt(LocalDateTime.now());
                    shift.setOpenedBy(openedBy);
                    UUID id = shiftRepository.saveAndFlush(shift).getId();
                    log.info("Service day at location {} opened implicitly by {}", locationId, openedBy);
                    return id;
                });
    }

    /** One row per waiter who took money during this service day: card/cash, tips, comped, total. */
    @Transactional(readOnly = true)
    public List<FinalRow> cashUp(UUID shiftId) {
        Map<UUID, String> typeName = paymentTypeRepository.findAll().stream()
                .collect(Collectors.toMap(PaymentTypeEntity::getId, PaymentTypeEntity::getType));
        List<PaymentEntity> payments = paymentRepository.findByShiftId(shiftId).stream()
                .filter(p -> !"FAILED".equals(p.getStatus()))
                .toList();
        if (payments.isEmpty()) {
            return List.of();
        }
        Map<String, String> displayNames = displayNames(payments);

        Map<String, BigDecimal[]> agg = new LinkedHashMap<>(); // [card, cash, tipCard, tipCash, protocol]
        for (PaymentEntity p : payments) {
            String waiter = displayNames.getOrDefault(p.getCreatedBy(), p.getCreatedBy() == null ? "—" : p.getCreatedBy());
            BigDecimal[] a = agg.computeIfAbsent(waiter, k -> {
                BigDecimal[] z = new BigDecimal[5];
                java.util.Arrays.fill(z, BigDecimal.ZERO);
                return z;
            });
            BigDecimal amount = p.getAmount() == null ? BigDecimal.ZERO : p.getAmount();
            BigDecimal tip = p.getTip() == null ? BigDecimal.ZERO : p.getTip();
            String type = typeName.getOrDefault(p.getPaymentTypeId(), "").toUpperCase();
            if (NotPaidReportService.isNotPaid(type)) {
                a[4] = a[4].add(amount);
            } else if ("CASH".equals(type)) {
                a[1] = a[1].add(amount);
                a[3] = a[3].add(tip);
            } else if ("CARD".equals(type) || "ONLINE".equals(type)) {
                a[0] = a[0].add(amount);
                a[2] = a[2].add(tip);
            }
        }
        List<FinalRow> rows = new ArrayList<>(agg.size());
        agg.forEach((waiter, a) -> rows.add(new FinalRow(waiter, a[0], a[1], a[2], a[3], a[4],
                a[0].add(a[1]).add(a[2]).add(a[3]))));
        rows.sort(Comparator.comparing(FinalRow::waiter));
        return rows;
    }

    /** Best-effort thermal print of the cash-up slips; returns null on success, else the reason. */
    private String printSlips(LocationEntity location, UUID printerId, LocationShiftEntity shift, List<FinalRow> rows) {
        if (rows.isEmpty()) {
            return "Nothing to print — no takings in this service day";
        }
        Optional<IntegrationEntity> printer = integrationRepository.findById(printerId)
                .filter(i -> i.getLocationId().equals(location.getId()));
        if (printer.isEmpty()) {
            return "Printer not found at this location";
        }
        String title = location.getName() + " " + SLIP_DAY.format(shift.getOpenedAt());
        try {
            bridgeService.sendWaiterReport(printer.get(), title, rows);
            return null;
        } catch (RuntimeException e) {
            log.warn("Cash-up slips for location {} could not be printed: {}", location.getId(), e.toString());
            return e instanceof ResponseStatusException rse && rse.getReason() != null
                    ? rse.getReason()
                    : "The printer did not accept the job";
        }
    }

    private Map<String, String> displayNames(List<PaymentEntity> payments) {
        Set<String> usernames = payments.stream()
                .map(PaymentEntity::getCreatedBy)
                .filter(u -> u != null && !u.isBlank())
                .collect(Collectors.toSet());
        if (usernames.isEmpty()) {
            return Map.of();
        }
        return userRepository.findByUsernameIn(List.copyOf(usernames)).stream()
                .collect(Collectors.toMap(UserEntity::getUsername,
                        u -> u.getName() == null || u.getName().isBlank() ? u.getUsername() : u.getName(),
                        (a, b) -> a));
    }

    private static ShiftResponse toResponse(LocationShiftEntity s) {
        return new ShiftResponse(s.getId(), s.getLocationId(), s.getNickname(), s.getOpenedBy(),
                s.getOpenedAt(), s.getClosedBy(), s.getClosedAt(), s.getClosedAt() == null);
    }
}
