package com.codewithsavage.majipoa.vendor.dto;

import java.time.LocalDate;
import java.util.List;

public record PayoutsResponse(
        long nextPayout,
        long pendingClearance,
        long paidThisMonth,
        long heldForDisputes,
        List<SettlementLine>  settlement,
        long payable,
        List<PayoutRecordDto> history
) {
    public record SettlementLine(String label, long amount) {}

    public record PayoutRecordDto(
            Long      id,
            LocalDate paidOn,
            LocalDate periodStart,
            LocalDate periodEnd,
            int       orders,
            int       gross,
            int       deductions,
            int       paid,
            String    reference
    ) {}
}
