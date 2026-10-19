package com.codewithsavage.majipoa.admin.verification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Response shapes for the admin Verification page. */
public final class AdminVerificationDtos {

    private AdminVerificationDtos() {}

    /** Left-hand queue + the "11 waiting" and "Average review time" badges. */
    public record QueueResponse(
            List<QueueItemDto> items,
            int waiting,
            Double averageReviewHours      // null until at least one application has been decided
    ) {}

    public record QueueItemDto(
            Long vendorId,
            String businessName,
            String area,                   // first area served, may be null
            String kind,                   // APPLICATION | RENEWAL
            String summary,                // "New application" or e.g. "Lab result renewal"
            Instant submittedAt
    ) {}

    /** Everything the right-hand side of the page shows for one vendor. */
    public record DetailDto(
            Long vendorId,
            String businessName,
            String ownerName,
            String phone,
            Instant appliedAt,
            String status,                 // UNDER_REVIEW | APPROVED | SUSPENDED | CHANGES_REQUESTED | REJECTED
            String source,
            String treatment,
            String products,
            String areas,
            String settlement,
            String registration,           // reference of the business registration document
            List<DocumentDto> documents,
            String note,                   // the admin's latest note to the vendor
            boolean canApprove             // true only when every document is approved and valid
    ) {}

    public record DocumentDto(
            Long id,
            String name,
            String reference,
            LocalDate submittedOn,
            LocalDate expiresOn,
            String state,                  // APPROVED | AWAITING_DECISION | QUERIED | EXPIRING | EXPIRED | MISSING
            String check,                  // short label for the "Check" column
            String note
    ) {}

    /** Body for query / request / request-changes / reject / approve. */
    public record DecisionRequest(String note) {}
}