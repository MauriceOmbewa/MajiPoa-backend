package com.codewithsavage.majipoa.admin.refund;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

public final class RefundDtos {

    private RefundDtos() {}

    /** status is OPEN, APPROVED or REJECTED. lastAction is the most recent action code logged on the case. */
    public record RefundCaseView(
            Long id,
            String caseRef,
            String orderRef,
            String customer,
            String vendor,
            long amount,
            String tag,
            String description,
            Instant openedAt,
            String status,
            String bornBy,
            String mpesaRef,
            Instant resolvedAt,
            String lastAction,
            Instant lastActionAt) {}

    public record RefundStats(
            double refundRatePercent,
            long borneByVendors,
            long borneByRiders,
            long borneByPlatform,
            Long avgSettleMinutes) {}

    public record RefundPageResponse(
            List<RefundCaseView> waiting,
            List<RefundCaseView> settled,
            RefundStats stats) {}

    /**
     * action: REFUND_FULL, REFUND_REPLACE, REFUND_CREDIT, ASK_VENDOR, HOLD_BATCH, REASSIGN or REJECT.
     * bornBy (optional): VENDOR, RIDER or PLATFORM. When missing it is chosen from the case tag.
     */
    public record DecisionRequest(@NotBlank String action, String bornBy, String note) {}

    public record MpesaRefRequest(@NotBlank String mpesaRef) {}
}