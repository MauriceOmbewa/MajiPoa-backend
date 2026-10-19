package com.codewithsavage.majipoa.shared.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "customer_profiles")
@Getter @Setter @NoArgsConstructor
public class CustomerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private Long userId;

    private String name;
    private String phone;
    private String area;

    private int totalOrders = 0;
    private long totalSpend = 0;

    private Instant lastOrderAt;
    private Double ratingGiven;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomerStatus status = CustomerStatus.ACTIVE;

    private String note;
    private Instant blockedAt;
    private String blockedBy;

    private Instant createdAt = Instant.now();

    public enum CustomerStatus { ACTIVE, AT_RISK, BLOCKED }
}
