package com.codewithsavage.majipoa.admin.order;

import java.time.Instant;
import java.util.List;

/** Shapes returned to the admin Orders page. */
public final class AdminOrderDtos {

    private AdminOrderDtos() {}

    /**
     * status: new | stuck | live | completed | cancelled | refunded. tone: warn | ok | bad.
     * items is what the customer ordered, e.g. "2 × 20L Refill, 1 × 500ml Bottle".
     * area is the customer's delivery area from their saved address, or null if not set.
     */
    public record AdminOrderRow(
            String id,
            String orderRef,
            Instant placedAt,
            String customer,
            String phone,
            String area,
            String items,
            String vendor,
            String rider,
            String stage,
            String tone,
            boolean severe,
            Long durationMinutes,
            long value,
            String status,
            String mpesaCode) {}

    public record OrderTabCounts(long newOrders, long stuck, long live, long completed, long cancelled, long refunded) {}

    public record AdminOrdersSummary(
            long cancelledToday,
            double cancelledTodayPercent,
            Long avgDeliveryMinutes,
            Long avgDeliveryDeltaMinutes) {}

    public record AdminOrdersResponse(
            AdminOrdersSummary summary,
            OrderTabCounts counts,
            List<AdminOrderRow> orders) {}

    /** A vendor the admin can dispatch to. distanceKm is null when coordinates are unknown. */
    public record VendorOption(Long id, String name, Double distanceKm) {}

    public record DispatchRequest(Long vendorId) {}
}