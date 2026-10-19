package com.codewithsavage.majipoa.admin.refund;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One decision or note recorded against a refund case. */
@Entity
@Table(name = "refund_case_actions")
@Getter @Setter @NoArgsConstructor
public class RefundCaseAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long refundCaseId;

    @Column(nullable = false)
    private String action;

    private String bornBy;

    @Column(length = 1000)
    private String note;

    private String performedBy;

    private Instant createdAt = Instant.now();
}