package com.codewithsavage.majipoa.shared.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * An order placed by a customer with a specific vendor.
 *
 * Lifecycle (see OrderStatus enum):
 *   NEW → PREPARING → READY → OUT_FOR_DELIVERY → COMPLETED
 *                                               ↘ CANCELLED (from any state)
 *
 * Key relationships:
 *  vendor  – the VendorProfile that owns and fulfils this order
 *  lines   – one or more OrderLine items (products × quantities)
 *  rider   – assigned when status moves to OUT_FOR_DELIVERY
 */
@Entity
@Table(name = "vendor_orders")
@Getter @Setter @NoArgsConstructor
public class VendorOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable reference shown in the UI, e.g. "#WM10262" */
    @Column(unique = true, nullable = false)
    private String orderRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private VendorProfile vendor;

    private String customerName;
    private String customerPhone;
    private String area;
    private double distanceKm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PAID;

    private String riderName;

    @Enumerated(EnumType.STRING)
    private RiderKind riderKind;

    @Enumerated(EnumType.STRING)
    private DeliveryStage deliveryStage;

    private Instant placedAt    = Instant.now();
    private Instant acceptedAt;
    private Instant deliveredAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLine> lines = new ArrayList<>();

    // ─── Status enums ────────────────────────────────────────────────────────

    /** Tracks where the order is in its fulfilment journey. */
    public enum OrderStatus {
        NEW,              // Just placed — vendor hasn't responded yet
        PREPARING,        // Vendor accepted and is filling the jugs
        READY,            // Ready for pickup by the rider
        OUT_FOR_DELIVERY, // Rider has collected and is on the way
        COMPLETED,        // Delivered and confirmed
        CANCELLED         // Declined by vendor or cancelled by customer
    }

    public enum PaymentStatus { PAID, INVOICED, REFUNDED }

    /** Whether the rider is from the platform pool or the vendor's own staff. */
    public enum RiderKind { PLATFORM, MY_STAFF }

    /** More granular status while OUT_FOR_DELIVERY. */
    public enum DeliveryStage {
        EN_ROUTE,
        NEAR_CUSTOMER,
        CUSTOMER_NOT_REACHABLE
    }
}
