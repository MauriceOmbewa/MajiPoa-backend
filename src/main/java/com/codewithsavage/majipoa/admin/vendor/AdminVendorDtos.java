package com.codewithsavage.majipoa.admin.vendor;

import java.time.Instant;
import java.util.List;

/**
 * Response shapes for the admin Vendors page.
 * Used by AdminVendorService (builds them) and AdminVendorController (returns them as JSON).
 */
public final class AdminVendorDtos {

    private AdminVendorDtos() {}

    /** Whole page payload: filtered table rows + the tab badges. */
    public record VendorsPageResponse(
            List<VendorRowDto> vendors,
            int total,
            TabCountsDto counts
    ) {}

    /** The "Active (214) / Awaiting review (…)" tab badges. Counts ignore the other filters. */
    public record TabCountsDto(
            long active,
            long awaitingReview,
            long suspended,
            long rejected
    ) {}

    /** One row of the vendors table. */
    public record VendorRowDto(
            Long id,
            String businessName,
            String ownerName,
            Instant joinedAt,
            long orders30d,
            Double rating,           // null until a rating source exists
            DocSummaryDto documents,
            String plan,             // FREE | PRO
            String status            // ACTIVE | AWAITING_REVIEW | SUSPENDED | REJECTED
    ) {}

    /** Documents column: state is used for filtering, label is what the table displays. */
    public record DocSummaryDto(
            String state,            // COMPLETE | PENDING | EXPIRING
            String label             // e.g. "Complete", "2 expiring", "KRA PIN pending"
    ) {}
}