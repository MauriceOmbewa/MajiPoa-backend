package com.codewithsavage.majipoa.shared.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Extra details for a user who operates as a VENDOR.
 *
 * Relationship: one-to-one with AppUser.
 * Created automatically by VendorService.getOrCreateProfile() the first time
 * a vendor accesses their portal — no manual setup step required.
 *
 * Contains:
 *  - businessName       : displayed in the vendor sidebar and the customer-facing shop
 *  - waterSource / treatment / phValue / tdsValue / about : water quality info shown in the
 *                         customer-facing "Water Passport" for transparency
 *  - verificationStatus : set by platform admins after reviewing submitted documents
 *  - plan               : FREE or PRO subscription tier
 */
@Entity
@Table(name = "vendor_profiles")
@Getter @Setter @NoArgsConstructor
public class VendorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(nullable = false)
    private String businessName = "My Water Business";

    private String waterSource;
    private String treatment;
    private String phValue;
    private String tdsValue;

    @Column(length = 1000)
    private String about;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionPlan plan = SubscriptionPlan.FREE;

    private Instant createdAt = Instant.now();

    public enum VerificationStatus { PENDING, APPROVED, SUSPENDED }
    public enum SubscriptionPlan   { FREE, PRO }
}
