package com.codewithsavage.majipoa.shared.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "mpesa_payments")
@Getter @Setter @NoArgsConstructor
public class MpesaPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String mpesaCode;

    private String phone;
    private long amount;

    private Instant receivedAt = Instant.now();

    private String matchedOrderRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.UNMATCHED;

    private String failureReason;

    public enum PaymentStatus { MATCHED, UNMATCHED, FAILED }
}
