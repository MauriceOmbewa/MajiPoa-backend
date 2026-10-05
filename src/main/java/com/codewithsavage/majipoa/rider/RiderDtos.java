package com.codewithsavage.majipoa.rider;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Request/response shapes used by the rider screens. */
public final class RiderDtos {

    private RiderDtos() {}

    /** Kenyan mobile number; spaces allowed because the UI shows "0798 221 004". */
    public static final String PHONE_REGEX = "^(\\+254|0)\\s?[17]\\d{2}\\s?\\d{3}\\s?\\d{3}$";

    // ------------------------------------------------------------------ shared

    public record ItemDto(Long id, int quantity, String name, String note, boolean checked) {}

    // ------------------------------------------------------------ offer + active

    /** "New delivery offer" card on the dashboard. */
    public record OfferDto(
            Long deliveryId,
            String orderNumber,
            BigDecimal fee,
            double totalDistanceKm,
            int estimatedMinutes,
            boolean heavyLoad,
            List<ItemDto> items,
            String vendorName,
            String pickupAddress,
            double distanceToPickupKm,
            String customerName,
            String dropoffArea,
            double pickupToDropoffKm,
            LocalDateTime expiresAt,
            long secondsLeft) {}

    /** state is one of: done, current, pending. */
    public record TimelineStepDto(String key, String label, LocalDateTime at, String detail, String state) {}

    /** Everything the Active delivery screen and the dashboard "current delivery" card need. */
    public record ActiveDeliveryDto(
            Long id,
            String orderNumber,
            DeliveryStatus status,
            String statusLabel,
            String vendorName,
            String pickupAddress,
            Double pickupLat,
            Double pickupLng,
            String customerName,
            String customerPhone,
            String dropoffAddress,
            String dropoffUnit,
            String dropoffArea,
            String dropoffNotes,
            Double dropoffLat,
            Double dropoffLng,
            Double riderLat,
            Double riderLng,
            String destinationLabel,
            String destinationAddress,
            double distanceRemainingKm,
            int etaMinutes,
            BigDecimal fee,
            boolean paid,
            List<ItemDto> items,
            List<TimelineStepDto> timeline,
            DeliveryStatus nextStatus,
            String nextActionLabel) {}

    // ---------------------------------------------------------------- dashboard

    public record CompletedDto(
            Long id,
            LocalDateTime deliveredAt,
            String orderNumber,
            String vendorName,
            String dropoffArea,
            double distanceKm,
            int durationMinutes,
            BigDecimal earned,
            Integer rating) {}

    public record DashboardDto(
            RiderStatus status,
            boolean online,
            String riderName,
            BigDecimal earnedToday,
            int deliveriesToday,
            long onlineMinutes,
            LocalDateTime onlineSince,
            Double rating,
            int ratingSampleSize,
            OfferDto currentOffer,
            ActiveDeliveryDto currentDelivery,
            List<CompletedDto> completedToday) {}

    // ------------------------------------------------------------------ history

    public record HistoryDto(
            int deliveries,
            BigDecimal totalEarned,
            Double averageRating,
            List<CompletedDto> items) {}

    // ----------------------------------------------------------------- earnings

    public record EarningsDayDto(
            LocalDate date,
            int deliveries,
            double distanceKm,
            BigDecimal base,
            BigDecimal bonus,
            BigDecimal total) {}

    public record PayoutDto(
            Long id,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal amount,
            PayoutStatus status,
            LocalDateTime paidAt,
            String mpesaReference) {}

    public record EarningsDto(
            String period,
            LocalDate from,
            LocalDate to,
            List<EarningsDayDto> days,
            BigDecimal total,
            String nextPayout,
            String bonusRule,
            List<PayoutDto> pastPayouts) {}

    // ------------------------------------------------------------------ profile

    public record ProfileDto(
            Long id,
            String riderCode,
            String fullName,
            String phone,
            String email,
            String vehicleType,
            String plateNumber,
            Integer capacity,
            String mpesaNumber,
            boolean verified,
            boolean online,
            RiderStatus status,
            LocalDate memberSince,
            Double rating,
            long totalDeliveries,
            int onTimeRate,
            boolean notifyNewOrders,
            boolean notifyPayouts,
            boolean notifySms) {}

    public record ProfileUpdateRequest(
            @NotBlank(message = "Full name is required") String fullName,
            @NotBlank(message = "Phone number is required")
            @Pattern(regexp = PHONE_REGEX, message = "Enter a valid Kenyan phone number") String phone,
            @Email(message = "Enter a valid email") String email,
            @NotBlank(message = "Vehicle type is required") String vehicleType,
            @NotBlank(message = "Number plate is required") String plateNumber,
            @NotNull @Min(value = 1, message = "Capacity must be at least 1")
            @Max(value = 50, message = "Capacity looks too high") Integer capacity,
            @Pattern(regexp = PHONE_REGEX, message = "Enter a valid M-Pesa number") String mpesaNumber,
            boolean notifyNewOrders,
            boolean notifyPayouts,
            boolean notifySms) {}

    // ----------------------------------------------- dispatch hook (order side)

    public record ItemRequest(
            @Min(1) int quantity,
            @NotBlank String name,
            String note) {}

    /** Called by the customer/order side (or by you while testing) to offer a delivery to a rider. */
    public record CreateOfferRequest(
            @NotNull Long riderId,
            @NotBlank String vendorName,
            @NotBlank String pickupAddress,
            Double pickupLat,
            Double pickupLng,
            @NotBlank String customerName,
            String customerPhone,
            @NotBlank String dropoffAddress,
            String dropoffUnit,
            String dropoffArea,
            Double dropoffLat,
            Double dropoffLng,
            String dropoffNotes,
            @PositiveOrZero double distanceToPickupKm,
            @Positive double pickupToDropoffKm,
            @Positive int durationMin,
            @NotNull @Positive BigDecimal fee,
            boolean paid,
            boolean heavyLoad,
            @Min(10) @Max(300) Integer offerSeconds,
            @NotEmpty @Valid List<ItemRequest> items) {}

    public record RatingRequest(@Min(1) @Max(5) int rating) {}
}
