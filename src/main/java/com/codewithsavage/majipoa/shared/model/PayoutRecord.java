package com.codewithsavage.majipoa.shared.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * A historical payout record — one row per weekly settlement disbursed to a vendor.
 *
 * The platform collects all payments from customers, deducts commission (8%) and
 * delivery fees, then transfers the net amount to the vendor's M-Pesa account weekly.
 * Each such transfer is recorded here with a unique reference number.
 */
@Entity
@Table(name = "payout_records")
@Getter @Setter @NoArgsConstructor
public class PayoutRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private VendorProfile vendor;

    private LocalDate paidOn;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    private int orders;
    private int gross;       // KES total sales in the period
    private int deductions;  // KES commission + delivery fees + refunds
    private int paid;        // KES net transferred = gross - deductions

    private String reference; // e.g. "SJ11P0K4TR" — M-Pesa transaction ID
}
