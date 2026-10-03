package com.codewithsavage.majipoa.shared.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * A compliance document uploaded by a vendor during onboarding or renewal.
 *
 * Examples:
 *  - Business registration certificate
 *  - KEBS standardisation mark
 *  - Water abstraction permit (WRA)
 *  - Laboratory test result (e.g. from SGS)
 *  - Public health certificate
 *
 * Platform admins review these and update the status from NOT_SUBMITTED → APPROVED.
 * The overall vendor verificationStatus in VendorProfile is derived from
 * whether all required documents are APPROVED.
 */
@Entity
@Table(name = "vendor_documents")
@Getter @Setter @NoArgsConstructor
public class VendorDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private VendorProfile vendor;

    @Column(nullable = false)
    private String name;

    private String note;
    private String reference;

    private LocalDate submittedOn;
    private LocalDate validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocStatus status = DocStatus.NOT_SUBMITTED;

    public enum DocStatus { APPROVED, EXPIRING, NOT_SUBMITTED }
}
