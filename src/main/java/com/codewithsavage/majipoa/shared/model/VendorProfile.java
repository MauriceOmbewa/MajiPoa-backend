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
 *  - phone / areas / productsDeclared / settlement : application details reviewed by admins
 *  - verificationStatus : set by platform admins after reviewing submitted documents
 *  - verificationNote   : the admin's latest note to the vendor (shown on their dashboard)
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

    private Double latitude;
    private Double longitude;

    // ── Application details (new) ─────────────────────────────────────────────
    private String phone;                       // business contact number

    @Column(length = 500)
    private String areas;                       // comma separated, e.g. "Rongai, Kiserian, Ongata"

    @Column(length = 500)
    private String productsDeclared;            // e.g. "20L refill, 10L, 500ml case"

    private String settlement;                  // e.g. "Paybill 4471020"

    // ── Admin review (new) ────────────────────────────────────────────────────
    @Column(length = 1000)
    private String verificationNote;            // latest note to the vendor, shown on their dashboard

    private String reviewedBy;
    private Instant reviewedAt;                 // set when approved or rejected

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionPlan plan = SubscriptionPlan.FREE;

    private Instant createdAt = Instant.now();

    /**
     * PENDING           -> waiting for an admin (shown as "Under review")
     * APPROVED          -> live on the marketplace
     * SUSPENDED         -> hidden, reversible
     * CHANGES_REQUESTED -> sent back to the vendor with a note
     * REJECTED          -> application declined
     */
    public enum VerificationStatus { PENDING, APPROVED, SUSPENDED, CHANGES_REQUESTED, REJECTED }
    public enum SubscriptionPlan   { FREE, PRO }
}