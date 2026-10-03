package com.codewithsavage.majipoa.vendor.dto;

import java.util.List;

/** Response for GET /api/vendor/dashboard — the vendor's real-time overview. */
public record VendorDashboardResponse(
        long salesToday,
        long ordersToday,
        long completedToday,
        int  jugsInStock,
        int  jugsCapacity,
        List<IncomingOrderDto> incoming,   // NEW orders awaiting accept/decline
        List<ActiveOrderDto>   active      // PREPARING / READY / OUT_FOR_DELIVERY orders
) {
    public record IncomingOrderDto(
            Long   id,
            String orderRef,
            String items,
            String area,
            double distanceKm,
            long   total,
            long   minutesAgo
    ) {}

    public record ActiveOrderDto(
            Long   id,
            String orderRef,
            String items,
            String area,
            String stage   // "Preparing" | "Rider assigned" | "On the way"
    ) {}
}
