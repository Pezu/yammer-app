package com.yammer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A frame the order-point QR sheets are printed on: a background image plus where the QR
 * code and the order point's name sit on it. All positions/sizes are fractions of the
 * image — x and sizes of its width, y of its height — so any resolution works.
 */
@Entity
@Table(name = "qr_template")
@Getter
@Setter
public class QrTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    /** Object key of the frame image in cloud storage, {@code classpath:...} for bundled art, or null. */
    @Column(name = "image_object")
    private String imageObject;

    /** Left edge of the QR code. */
    @Column(name = "qr_x", nullable = false)
    private BigDecimal qrX = new BigDecimal("0.3");

    /** Top edge of the QR code. */
    @Column(name = "qr_y", nullable = false)
    private BigDecimal qrY = new BigDecimal("0.3");

    /** Side of the (square) QR code. */
    @Column(name = "qr_size", nullable = false)
    private BigDecimal qrSize = new BigDecimal("0.4");

    /** Colour of the QR modules, {@code #RRGGBB} (the background stays transparent). */
    @Column(name = "qr_color", nullable = false)
    private String qrColor = "#000000";

    /** Optional title printed on every card (venue name), same size/colour as the label; null = none. */
    @Column(name = "title")
    private String title;

    /** Baseline of the title (centred horizontally). */
    @Column(name = "title_y", nullable = false)
    private BigDecimal titleY = new BigDecimal("0.1");

    /** Baseline of the order point's name (centred horizontally). */
    @Column(name = "label_y", nullable = false)
    private BigDecimal labelY = new BigDecimal("0.9");

    /** Font size of the name. */
    @Column(name = "label_size", nullable = false)
    private BigDecimal labelSize = new BigDecimal("0.08");

    /** Name colour, {@code #RRGGBB}. */
    @Column(name = "label_color", nullable = false)
    private String labelColor = "#FFFFFF";
}
