package com.yammer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yammer.dto.DashboardResponse.FinalRow;
import com.yammer.entity.LocationEntity;
import com.yammer.entity.LocationShiftEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.OrderPointTypeEntity;
import com.yammer.entity.PaymentEntity;
import com.yammer.entity.PaymentTypeEntity;
import com.yammer.entity.UserEntity;
import com.yammer.repository.IntegrationRepository;
import com.yammer.repository.LocationShiftRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderPointTypeRepository;
import com.yammer.repository.PaymentRepository;
import com.yammer.repository.PaymentTypeRepository;
import com.yammer.service.TableRoundService;
import com.yammer.repository.UserRepository;
import com.yammer.security.AccessGuard;
import com.yammer.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ShiftServiceTest {

    private final UUID location = UUID.randomUUID();
    private final UUID table = UUID.randomUUID();
    private final UUID cash = UUID.randomUUID();
    private final UUID card = UUID.randomUUID();
    private final UUID protocol = UUID.randomUUID();

    private final LocationShiftRepository shifts = mock(LocationShiftRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentTypeRepository types = mock(PaymentTypeRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final OrderPointRepository points = mock(OrderPointRepository.class);
    private final OrderPointTypeRepository pointTypes = mock(OrderPointTypeRepository.class);
    private final TableRoundService rounds = mock(TableRoundService.class);
    private final AccessGuard guard = mock(AccessGuard.class);

    private ShiftService service() {
        return new ShiftService(shifts, payments, types, mock(IntegrationRepository.class), points,
                pointTypes, rounds, users, mock(BridgeService.class), guard,
                mock(CurrentUserProvider.class));
    }

    @Test
    void anOpenDayIsWhatNewRowsAreStampedWith() {
        UUID open = UUID.randomUUID();
        when(shifts.findByLocationIdAndClosedAtIsNull(location)).thenReturn(Optional.of(shiftWithId(open)));

        assertEquals(Optional.of(open), service().openShiftId(location));
        // the write path reuses it instead of opening a second one
        assertEquals(open, service().requireOpenShiftId(location, "ana"));
    }

    @Test
    void servingWithNoDayOpenOpensOneSoNothingIsUnattributed() {
        UUID fresh = UUID.randomUUID();
        when(shifts.findByLocationIdAndClosedAtIsNull(location)).thenReturn(Optional.empty());
        when(shifts.saveAndFlush(any(LocationShiftEntity.class))).thenAnswer(inv -> {
            LocationShiftEntity saved = inv.getArgument(0);
            saved.setId(fresh);
            return saved;
        });

        assertEquals(fresh, service().requireOpenShiftId(location, "ana"));
        // a read never opens one
        assertEquals(Optional.empty(), service().openShiftId(location));
    }

    @Test
    void cashUpSplitsCardCashTipsAndProtocolPerWaiter() {
        UUID shiftId = UUID.randomUUID();
        LocalDateTime at = LocalDate.of(2026, 9, 12).atTime(21, 0);
        when(types.findAll()).thenReturn(List.of(type(cash, "CASH"), type(card, "CARD"), type(protocol, "PROTOCOL")));
        when(users.findByUsernameIn(any())).thenReturn(List.of(user("ana", "Ana"), user("bob", null)));
        when(payments.findByShiftId(shiftId)).thenReturn(List.of(
                payment(card, "ana", 100, 10, at),
                payment(cash, "ana", 40, 5, at.plusMinutes(10)),
                payment(protocol, "ana", 70, 0, at.plusMinutes(20)), // comped: no money, no tip
                payment(cash, "bob", 25, 0, at.plusMinutes(30))));

        List<FinalRow> rows = service().cashUp(shiftId);

        assertEquals(2, rows.size());
        FinalRow ana = rows.get(0);
        assertEquals("Ana", ana.waiter());
        assertEquals(new BigDecimal("100"), ana.paidCard());
        assertEquals(new BigDecimal("40"), ana.paidCash());
        assertEquals(new BigDecimal("10"), ana.tipCard());
        assertEquals(new BigDecimal("5"), ana.tipCash());
        assertEquals(new BigDecimal("70"), ana.protocol());
        assertEquals(new BigDecimal("155"), ana.total()); // protocol is NOT money owed
        assertEquals("bob", rows.get(1).waiter()); // no display name → the username
        assertEquals(new BigDecimal("25"), rows.get(1).paidCash());
    }

    @Test
    void declinedPaymentsNeverReachTheCashUp() {
        UUID shiftId = UUID.randomUUID();
        PaymentEntity declined = payment(card, "ana", 100, 0, LocalDate.of(2026, 9, 12).atTime(21, 0));
        declined.setStatus("FAILED");
        when(types.findAll()).thenReturn(List.of(type(card, "CARD")));
        when(payments.findByShiftId(shiftId)).thenReturn(List.of(declined));

        assertTrue(service().cashUp(shiftId).isEmpty());
    }

    @Test
    void theDayCannotCloseWhileATableIsStillOpen() {
        UUID tableType = UUID.randomUUID();
        UUID station = UUID.randomUUID();
        UUID serviceType = UUID.randomUUID();
        when(guard.requireAccessibleLocation(location)).thenReturn(new LocationEntity());
        when(shifts.findByLocationIdAndClosedAtIsNull(location))
                .thenReturn(Optional.of(shift(LocalDateTime.now().minusHours(6), null)));
        when(pointTypes.findAll()).thenReturn(List.of(pointType(tableType, "TABLE"), pointType(serviceType, "SERVICE")));
        when(points.findByLocationIdOrderByName(location))
                .thenReturn(List.of(point(table, "T7", tableType), point(station, "Bar station", serviceType)));
        when(rounds.pointsWithUnsettled(any())).thenReturn(Set.of(table));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service().close(location, null));

        assertEquals(409, e.getStatusCode().value());
        assertTrue(e.getReason().contains("T7"), e.getReason());
    }

    @Test
    void serviceStationsDoNotBlockTheClose() {
        UUID station = UUID.randomUUID();
        UUID serviceType = UUID.randomUUID();
        when(pointTypes.findAll()).thenReturn(List.of(pointType(serviceType, "SERVICE")));
        when(points.findByLocationIdOrderByName(location))
                .thenReturn(List.of(point(station, "Bar station", serviceType)));

        // the only point is a station: nothing to close, so the list of blockers is empty
        assertTrue(service().openTables(location).isEmpty());
    }

    private static OrderPointEntity point(UUID id, String name, UUID typeId) {
        OrderPointEntity op = new OrderPointEntity();
        op.setId(id);
        op.setName(name);
        op.setTypeId(typeId);
        return op;
    }

    private static OrderPointTypeEntity pointType(UUID id, String type) {
        OrderPointTypeEntity t = new OrderPointTypeEntity();
        t.setId(id);
        t.setType(type);
        return t;
    }

    private LocationShiftEntity shiftWithId(UUID id) {
        LocationShiftEntity s = shift(LocalDateTime.now().minusHours(3), null);
        s.setId(id);
        return s;
    }

    private LocationShiftEntity shift(LocalDateTime openedAt, LocalDateTime closedAt) {
        LocationShiftEntity s = new LocationShiftEntity();
        s.setId(UUID.randomUUID());
        s.setLocationId(location);
        s.setOpenedAt(openedAt);
        s.setClosedAt(closedAt);
        return s;
    }

    private PaymentEntity payment(UUID type, String by, int amount, int tip, LocalDateTime at) {
        PaymentEntity p = new PaymentEntity();
        p.setId(UUID.randomUUID());
        p.setOrderPointId(table);
        p.setPaymentTypeId(type);
        p.setCreatedBy(by);
        p.setAmount(BigDecimal.valueOf(amount));
        p.setTip(BigDecimal.valueOf(tip));
        p.setCreatedAt(at);
        return p;
    }

    private static PaymentTypeEntity type(UUID id, String name) {
        PaymentTypeEntity t = new PaymentTypeEntity();
        t.setId(id);
        t.setType(name);
        return t;
    }

    private static UserEntity user(String username, String name) {
        UserEntity u = new UserEntity();
        u.setUsername(username);
        u.setName(name);
        return u;
    }
}
