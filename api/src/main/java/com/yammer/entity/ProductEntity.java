package com.yammer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "product")
@Getter
@Setter
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "vat_type_id")
    private UUID vatTypeId;

    /** Object-storage key of the product's image, or null. */
    @Column(name = "image_object")
    private String imageObject;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
