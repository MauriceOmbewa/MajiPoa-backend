package com.codewithsavage.majipoa.admin.rider;

import java.time.LocalDateTime;
import java.util.List;

/** Shapes returned to the admin Riders page. */
public final class AdminRiderDtos {

    private AdminRiderDtos() {}

    /** status is one of: online, offline, suspended, applicants. */
    public record AdminRiderRow(
            Long id,
            String name,
            String phone,
            String area,
            String vehicle,
            LocalDateTime joinedAt,
            int deliveriesToday,
            Double rating,
            Integer onTimeRate,
            String status,
            String note) {}

    public record TabCounts(long online, long offline, long suspended, long applicants) {}

    public record AdminRiderSummary(
            long activeDeliveries,
            long deliveriesToday,
            Long avgDeliveryMinutes,
            long lateOrFailedToday,
            double lateOrFailedPercent) {}

    public record AdminRidersResponse(
            AdminRiderSummary summary,
            TabCounts counts,
            List<AdminRiderRow> riders) {}
}