package com.codewithsavage.majipoa.shared.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * A sale that happened outside the MajiSafi platform
 * (walk-in at the shop, standing office client, another delivery app, etc.).
 *
 * Vendors log these manually so their total revenue reporting is complete,
 * even for orders that didn't go through the app.
 * Only available on the PRO subscription plan.
 */
@Entity
@Table(name = "offline_sales")
@Getter @Setter @NoArgsConstructor
public class OfflineSale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private VendorProfile vendor;

    @Column(nullable = false)
    private LocalDate saleDate;

    @Column(nullable = false)
    private String product;

    private int qty;
    private int amount;   // KES total (not per unit)

    /** e.g. "Walk-in customer", "Standing office client", "Another delivery app" */
    private String channel;
}
