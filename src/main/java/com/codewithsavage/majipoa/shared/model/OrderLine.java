package com.codewithsavage.majipoa.shared.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * A single line item inside a VendorOrder, e.g. "2 × 20L refill at KSh 250 each".
 *
 * productName and unitPrice are snapshotted from the Product at the time the order
 * is placed so that later price changes don't affect historical records.
 */
@Entity
@Table(name = "order_lines")
@Getter @Setter @NoArgsConstructor
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private VendorOrder order;

    @Column(nullable = false)
    private String productName;

    private String note;
    private int qty;

    /** Price per unit in KES at the time the order was placed. */
    private int unitPrice;
}
