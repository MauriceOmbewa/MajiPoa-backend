package com.codewithsavage.majipoa.shared.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "refund_cases")
@Getter @Setter @NoArgsConstructor
public class RefundCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String orderId;
    private String customerName;
    private String vendorName;
    private long amount;
    private String tag;

    @Column(length = 1000)
    private String description;

    private Instant openedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status = RefundStatus.OPEN;

    private String bornBy;
    private String mpesaRef;

    private Instant resolvedAt;
    private String resolvedBy;

    public enum RefundStatus { OPEN, APPROVED, REJECTED }
}
