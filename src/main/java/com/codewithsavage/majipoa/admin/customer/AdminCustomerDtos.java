package com.codewithsavage.majipoa.admin.customer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminCustomerDtos {

    private AdminCustomerDtos() {}

    public record CustomersPageResponse(
            List<CustomerRowDto> customers,
            int total,
            TabCountsDto counts,
            List<String> areas
    ) {}

    public record TabCountsDto(long all, long newLast7d, long atRisk, long blocked) {}

    /** Same shape as GET /api/customer (CustomerResponse) */
    public record AddressDto(
            String county,
            String subCounty,
            String area,
            String street,
            String building,
            String landmark,
            String pinLocation
    ) {}

    /**
     * Customer fields mirror GET /api/customer exactly, followed by
     * admin-only fields computed from the customer's real orders.
     */
    public record CustomerRowDto(
            // ── same as GET /api/customer ──
            String customerId,
            String email,
            String firstName,
            String lastName,
            String userName,
            String pictureUrl,
            String role,
            String phoneNumber,
            AddressDto address,
            // ── admin-only, computed ──
            LocalDateTime joinedAt,
            long orders,
            BigDecimal totalSpend,
            LocalDateTime lastOrderAt,
            String status // ACTIVE | AT_RISK | BLOCKED
    ) {}

    public record StatsDto(
            long activeCustomers30d,
            long newCustomers30d,
            long ordersToday,
            long uniqueCustomersToday,
            int repeatRate30d,
            long blockedAccounts
    ) {}

    public record TopCustomerDto(
            int rank,
            String customerId,
            String firstName,
            String lastName,
            String userName,
            String area,
            BigDecimal totalSpend
    ) {}

    public record CustomerDetailDto(
            CustomerRowDto customer,
            String blockedReason,
            LocalDateTime blockedAt,
            String blockedBy
    ) {}

    public record BlockRequest(String reason) {}
}