package com.yammer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.OrderPointTypeEntity;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderPointTypeRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class OrderPointServiceSplitTest {

    private final UUID location = UUID.randomUUID();
    private final UUID tableType = UUID.randomUUID();
    private final UUID barType = UUID.randomUUID();
    private final OrderPointRepository points = mock(OrderPointRepository.class);
    private final OrderPointTypeRepository types = mock(OrderPointTypeRepository.class);
    private final OrderPointService service =
            new OrderPointService(points, types, null, null, null, null, null, null, null, null);

    OrderPointServiceSplitTest() {
        when(types.findById(tableType)).thenReturn(Optional.of(type("TABLE")));
        when(types.findById(barType)).thenReturn(Optional.of(type("BAR")));
        when(points.save(any(OrderPointEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void splitCreatesTheNextSlotWithTheSameConfiguration() {
        OrderPointEntity t12_1 = point("T12.1", tableType);
        t12_1.setMenuId(UUID.randomUUID());
        t12_1.setPrinterId(UUID.randomUUID());
        t12_1.setCashRegisterId(UUID.randomUUID());
        t12_1.setPaymentTypeIds(List.of(UUID.randomUUID(), UUID.randomUUID()));
        t12_1.setSelfOrderMode("ALLOW");
        t12_1.setAllowMultipleUsers(true);
        when(points.findByLocationIdOrderByName(location))
                .thenReturn(List.of(point("T1.1", tableType), t12_1, point("T12.3", tableType), point("T2.1", tableType)));

        OrderPointEntity slot = service.splitEntity(t12_1);

        assertEquals("T12.4", slot.getName()); // next after the highest existing slot, not "first gap"
        assertEquals(location, slot.getLocationId());
        assertEquals(tableType, slot.getTypeId());
        assertEquals(t12_1.getMenuId(), slot.getMenuId());
        assertEquals(t12_1.getPrinterId(), slot.getPrinterId());
        assertEquals(t12_1.getCashRegisterId(), slot.getCashRegisterId());
        assertEquals(t12_1.getPaymentTypeIds(), slot.getPaymentTypeIds());
        assertEquals("ALLOW", slot.getSelfOrderMode());
        assertEquals(true, slot.isAllowMultipleUsers());
    }

    @Test
    void onlyTablesNamedLikeSlotsCanBeSplit() {
        assertThrows(ResponseStatusException.class, () -> service.splitEntity(point("B1", barType)));
        assertThrows(ResponseStatusException.class, () -> service.splitEntity(point("Terrace", tableType)));
    }

    private OrderPointEntity point(String name, UUID typeId) {
        OrderPointEntity op = new OrderPointEntity();
        op.setId(UUID.randomUUID());
        op.setLocationId(location);
        op.setName(name);
        op.setTypeId(typeId);
        return op;
    }

    private static OrderPointTypeEntity type(String name) {
        OrderPointTypeEntity t = new OrderPointTypeEntity();
        t.setId(UUID.randomUUID());
        t.setType(name);
        return t;
    }
}
