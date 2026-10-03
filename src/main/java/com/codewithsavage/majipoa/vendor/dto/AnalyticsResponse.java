package com.codewithsavage.majipoa.vendor.dto;

import java.util.List;

public record AnalyticsResponse(
        String period,
        long   grossSales,
        long   netAfterFees,
        long   commissionPaid,
        long   orders,
        long   avgOrder,
        List<DayBar>   salesByDay,
        List<HourBar>  busiestHours,
        List<ShareBar> topProducts,
        List<ShareBar> areas,
        long   newCustomers,
        long   returningCustomers,
        int    repeatRate,
        long   recurringCustomers,
        long   lostCustomers,
        int    acceptanceRate,
        int    avgPrepTime,
        int    avgDeliveryTime,
        double cancellationRate
) {
    public record DayBar (String day,   int pct, boolean peak) {}
    public record HourBar(String label, int pct, int orders)   {}
    public record ShareBar(String label, int pct, String value) {}
}
