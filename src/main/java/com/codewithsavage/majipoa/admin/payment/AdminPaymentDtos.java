package com.codewithsavage.majipoa.admin.payment;

import java.time.Instant;
import java.util.List;

/** Response shapes for the admin Payments page. */
public final class AdminPaymentDtos {

    private AdminPaymentDtos() {}

    /** Table rows + tab badges. */
    public record PaymentsPageResponse(
            List<PaymentRowDto> payments,
            int total,
            TabCountsDto counts
    ) {}

    /** "Unreconciled (14)  All payments  Failed (38)  Refunds". */
    public record TabCountsDto(long unreconciled, long all, long failed, long refunds) {}

    public record PaymentRowDto(
            Long id,
            String code,               // M Pesa code (or refund reference on the Refunds tab)
            Instant receivedAt,        // time received (refund resolved time on the Refunds tab)
            String phone,              // null on the Refunds tab (refunds don't store a phone)
            long amount,               // KSh
            String matchedOrder,       // null when there is no matched order
            String status,             // MATCHED | UNMATCHED | FAILED | REFUNDED
            String label               // "Problem" text for unreconciled rows, "Status" text otherwise
    ) {}

    /** The four KPI cards at the top. */
    public record SummaryDto(
            long collectedToday,           // KSh actually received on M Pesa today
            long successfulPaymentsToday,
            long failedToday,
            long unreconciledCallbacks,    // all unmatched payments, not just today
            long refundedToday,            // KSh, approved refunds resolved today
            long refundCountToday
    ) {}

    /** One row of "Failures by reason" (last 7 days). */
    public record FailureReasonDto(
            String reason,
            long count,
            int sharePercent,
            int recoveredPercent
    ) {}

    public record MatchRequest(String orderRef) {}
}