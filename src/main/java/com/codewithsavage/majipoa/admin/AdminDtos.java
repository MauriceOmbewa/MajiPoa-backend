package com.codewithsavage.majipoa.admin;

import java.time.Instant;
import java.util.List;

/**
 * All DTOs (Java records) used by the admin API. Nested inside this class for
 * a clean single import: import com.codewithsavage.majipoa.admin.AdminDtos;
 */
public final class AdminDtos {

    private AdminDtos() {}

    // ─── Dashboard ────────────────────────────────────────────────────────────

    public record DashboardResponse(
            KpiDto kpis,
            List<OrdersPerHourDto> ordersPerHour,
            List<TopVendorDto> topVendors,
            List<DemandGapDto> demandGaps,
            List<AttentionItemDto> attentionItems
    ) {}

    public record KpiDto(
            long ordersToday,
            long activeRiders,
            long pendingVerifications,
            long openRefunds,
            long unmatchedPayments,
            long totalVendors,
            long totalCustomers,
            long totalRiders
    ) {}

    public record OrdersPerHourDto(int hour, long count) {}

    public record TopVendorDto(Long vendorId, String businessName, long ordersToday, long revenue) {}

    public record DemandGapDto(String area, long orders, long vendors) {}

    public record AttentionItemDto(String type, String label, String link) {}

    // ─── Orders ───────────────────────────────────────────────────────────────

    public record AdminOrderDto(
            Long id,
            String orderRef,
            String customerName,
            String customerPhone,
            String vendorName,
            String area,
            String status,
            String paymentStatus,
            String riderName,
            Instant placedAt,
            Instant acceptedAt,
            Instant deliveredAt,
            List<OrderLineDto> lines
    ) {}

    public record OrderLineDto(String productName, int qty, int unitPrice) {}

    public record OrdersKpiResponse(
            long total,
            long pending,
            long preparing,
            long outForDelivery,
            long completed,
            long cancelled,
            long stuck
    ) {}

    public record InterveneRequest(String action, String riderName) {}

    // ─── Customers ────────────────────────────────────────────────────────────

    public record CustomerDto(
            Long id,
            Long userId,
            String name,
            String phone,
            String area,
            int totalOrders,
            long totalSpend,
            Instant lastOrderAt,
            Double ratingGiven,
            String status,
            String note,
            Instant blockedAt,
            String blockedBy,
            Instant createdAt
    ) {}

    public record CustomersResponse(List<CustomerDto> customers, long total) {}

    public record BlockRequest(String reason) {}

    // ─── Vendors ──────────────────────────────────────────────────────────────

    public record AdminVendorDto(
            Long id,
            Long userId,
            String businessName,
            String verificationStatus,
            String plan,
            Double latitude,
            Double longitude,
            Instant createdAt
    ) {}

    public record SuspendRequest(String reason) {}

    // ─── Riders ───────────────────────────────────────────────────────────────

    public record AdminRiderDto(
            Long id,
            String fullName,
            String phone,
            String email,
            String vehicleType,
            String plateNumber,
            boolean verified,
            boolean adminSuspended,
            String status,
            Double latitude,
            Double longitude
    ) {}

    public record RidersResponse(List<AdminRiderDto> riders, long total) {}

    public record RiderSuspendRequest(String reason) {}

    public record RiderSummaryDto(Long id, String fullName, String phone, double distanceKm) {}

    // ─── Verification ─────────────────────────────────────────────────────────

    public record VerificationQueueItem(
            Long vendorId,
            String businessName,
            String verificationStatus,
            Instant createdAt
    ) {}

    public record VerificationDetail(
            Long vendorId,
            String businessName,
            String verificationStatus,
            List<DocDto> documents,
            String note
    ) {}

    public record DocDto(
            Long id,
            String name,
            String status,
            String reference,
            String note
    ) {}

    public record VerificationNoteRequest(String note) {}

    public record DocDecisionRequest(String note) {}

    // ─── Subscriptions ────────────────────────────────────────────────────────

    public record SubscriptionDto(
            Long vendorId,
            String businessName,
            String plan,
            Instant createdAt
    ) {}

    public record SubscriptionsResponse(List<SubscriptionDto> subscriptions, long total) {}

    // ─── Payouts ──────────────────────────────────────────────────────────────

    public record AdminPayoutDto(
            Long id,
            String recipientName,
            String recipientType,
            String period,
            long amount,
            String status,
            String mpesaRef
    ) {}

    public record PayoutsResponse(List<AdminPayoutDto> payouts, long total, NextSettlementDto nextSettlement) {}

    public record NextSettlementDto(String scheduledFor, long estimatedTotal) {}

    public record HoldRequest(String reason) {}

    // ─── Payments ────────────────────────────────────────────────────────────

    public record PaymentDto(
            Long id,
            String mpesaCode,
            String phone,
            long amount,
            Instant receivedAt,
            String matchedOrderRef,
            String status,
            String failureReason
    ) {}

    public record PaymentsResponse(List<PaymentDto> payments, long total, MoneyLineDto moneyLine) {}

    public record MoneyLineDto(long matched, long unmatched, long failed) {}

    public record FailureReasonDto(String reason) {}

    // ─── Refunds ──────────────────────────────────────────────────────────────

    public record RefundCaseDto(
            Long id,
            String orderId,
            String customerName,
            String vendorName,
            long amount,
            String tag,
            String description,
            Instant openedAt,
            String status,
            String bornBy,
            String mpesaRef,
            Instant resolvedAt,
            String resolvedBy
    ) {}

    public record CaseActionDto(String note) {}

    public record SettledCaseDto(String mpesaRef, String bornBy) {}

    public record RefundsResponse(List<RefundCaseDto> cases, long openCount, long totalRefunded) {}

    public record ApproveRefundRequest(String mpesaRef, String bornBy) {}
}
