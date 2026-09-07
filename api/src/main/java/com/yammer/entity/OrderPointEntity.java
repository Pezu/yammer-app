package com.yammer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "order_point")
@Getter
@Setter
public class OrderPointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(nullable = false)
    private String name;

    /** The order point's kind, from the order_point_type catalog (SERVICE/BAR/TABLE/…). */
    @Column(name = "type_id", nullable = false)
    private UUID typeId;

    /** How a customer self-pays here (ONLINE/CHECK), from the self_pay_type catalog. */
    @Column(name = "self_pay_type_id")
    private UUID selfPayTypeId;

    /** ALLOW (customer orders go straight in) / CONFIRM (waiter approves each order) / DISALLOW (menu only). */
    @Column(name = "self_order_mode", nullable = false)
    private String selfOrderMode = "CONFIRM";

    /** Whether several users may work this point at the same time. */
    @Column(name = "allow_multiple_users", nullable = false)
    private boolean allowMultipleUsers;

    /** Accepted payment types, from the payment_type catalog (CARD/CASH/PROTOCOL/PO/…); empty = none set. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "payment_type_ids", columnDefinition = "uuid[]", nullable = false)
    private List<UUID> paymentTypeIds = new ArrayList<>();

    /** Menu served here — target table not ported yet, plain UUID for now. */
    @Column(name = "menu_id")
    private UUID menuId;

    /** The SERVICE-type point that serves this one. */
    @Column(name = "service_order_point_id")
    private UUID serviceOrderPointId;

    /** Printer integration — target table not ported yet, plain UUID for now. */
    @Column(name = "printer_id")
    private UUID printerId;

    /** Cash register integration — target table not ported yet, plain UUID for now. */
    @Column(name = "cash_register_id")
    private UUID cashRegisterId;
}
