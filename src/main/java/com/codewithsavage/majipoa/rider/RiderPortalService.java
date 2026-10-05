package com.codewithsavage.majipoa.rider;

import com.codewithsavage.majipoa.rider.RiderDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Read models and profile actions behind the Dashboard, History, Earnings and Profile screens. */
@Service
@Transactional
public class RiderPortalService {

    private static final BigDecimal BONUS_PER_TEN_ON_TIME = new BigDecimal("100");
    private static final String BONUS_RULE =
            "Bonus: KSh 100 for every 10 deliveries completed on time in a day.";
    private static final String NEXT_PAYOUT = "Friday 6 pm";

    private final RiderRepository riders;
    private final DeliveryRepository deliveries;
    private final PayoutRepository payouts;
    private final DeliveryService deliveryService;

    public RiderPortalService(RiderRepository riders,
                              DeliveryRepository deliveries,
                              PayoutRepository payouts,
                              DeliveryService deliveryService) {
        this.riders = riders;
        this.deliveries = deliveries;
        this.payouts = payouts;
        this.deliveryService = deliveryService;
    }

    // ---------------------------------------------------------------- dashboard

    public DashboardDto dashboard(Long riderId) {
        Rider r = getRider(riderId);

        LocalDateTime start = LocalDate.now().atStartOfDay();
        List<Delivery> today = deliveries.findByRiderIdAndStatusAndDeliveredAtBetweenOrderByDeliveredAtDesc(
                riderId, DeliveryStatus.DELIVERED, start, start.plusDays(1));

        List<Delivery> rated = deliveries.findTop50ByRiderIdAndStatusAndRatingIsNotNullOrderByDeliveredAtDesc(
                riderId, DeliveryStatus.DELIVERED);
        Double rating = rated.isEmpty()
                ? null
                : DeliveryService.round1(rated.stream().mapToInt(Delivery::getRating).average().orElse(0));

        long onlineMinutes = r.getOnlineSince() == null
                ? 0
                : Duration.between(r.getOnlineSince(), LocalDateTime.now()).toMinutes();

        boolean online = r.getStatus() != RiderStatus.OFFLINE;
        OfferDto offer = online ? deliveryService.currentOffer(riderId).orElse(null) : null;
        ActiveDeliveryDto active = deliveryService.findActive(riderId)
                .map(deliveryService::toActiveDto).orElse(null);

        return new DashboardDto(
                r.getStatus(),
                online,
                r.getFullName(),
                sumFees(today),
                today.size(),
                onlineMinutes,
                r.getOnlineSince(),
                rating,
                rated.size(),
                offer,
                active,
                today.stream().map(deliveryService::toCompletedDto).toList());
    }

    /** The Go online / Go offline button and the Online switch on the profile. */
    public ProfileDto setOnline(Long riderId, boolean online) {
        Rider r = getRider(riderId);
        boolean hasActive = deliveryService.findActive(riderId).isPresent();
        if (!online && hasActive) {
            throw new InvalidDeliveryStateException("Finish your active delivery before going offline");
        }
        r.setStatus(online ? (hasActive ? RiderStatus.BUSY : RiderStatus.AVAILABLE) : RiderStatus.OFFLINE);
        return toProfile(r);
    }

    // ------------------------------------------------------------------ history

    public HistoryDto history(Long riderId, String period, String search) {
        getRider(riderId);
        String q = search == null ? "" : search.trim();
        List<Delivery> list = deliveries.searchHistory(
                riderId, DeliveryStatus.DELIVERED, historyStart(period), q);

        Double avg = list.stream().filter(d -> d.getRating() != null).count() == 0
                ? null
                : DeliveryService.round1(list.stream()
                        .filter(d -> d.getRating() != null)
                        .mapToInt(Delivery::getRating)
                        .average().orElse(0));

        return new HistoryDto(
                list.size(),
                sumFees(list),
                avg,
                list.stream().map(deliveryService::toCompletedDto).toList());
    }

    private LocalDateTime historyStart(String period) {
        LocalDate today = LocalDate.now();
        String p = period == null ? "week" : period.toLowerCase();
        return switch (p) {
            case "today" -> today.atStartOfDay();
            case "week" -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
            case "month" -> today.withDayOfMonth(1).atStartOfDay();
            case "all" -> LocalDateTime.of(2000, 1, 1, 0, 0);
            default -> throw new IllegalArgumentException(
                    "Unknown period '" + period + "'. Use today, week, month or all");
        };
    }

    // ----------------------------------------------------------------- earnings

    public EarningsDto earnings(Long riderId, String period) {
        getRider(riderId);
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        String p = period == null ? "this-week" : period.toLowerCase();

        LocalDate from;
        LocalDate to;
        switch (p) {
            case "this-week" -> { from = monday; to = monday.plusDays(6); }
            case "last-week" -> { from = monday.minusWeeks(1); to = monday.minusDays(1); }
            case "this-month" -> { from = today.withDayOfMonth(1); to = today.with(TemporalAdjusters.lastDayOfMonth()); }
            default -> throw new IllegalArgumentException(
                    "Unknown period '" + period + "'. Use this-week, last-week or this-month");
        }

        List<Delivery> list = deliveries.findByRiderIdAndStatusAndDeliveredAtBetweenOrderByDeliveredAtDesc(
                riderId, DeliveryStatus.DELIVERED, from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        Map<LocalDate, List<Delivery>> byDay = list.stream().collect(
                Collectors.groupingBy(d -> d.getDeliveredAt().toLocalDate(), TreeMap::new, Collectors.toList()));

        List<EarningsDayDto> days = byDay.entrySet().stream().map(e -> {
            List<Delivery> day = e.getValue();
            BigDecimal base = sumFees(day);
            long onTime = day.stream().filter(d -> Boolean.TRUE.equals(d.getOnTime())).count();
            BigDecimal bonus = BONUS_PER_TEN_ON_TIME.multiply(BigDecimal.valueOf(onTime / 10));
            double km = day.stream()
                    .mapToDouble(d -> d.getDistanceToPickupKm() + d.getPickupToDropoffKm()).sum();
            return new EarningsDayDto(e.getKey(), day.size(), Math.round(km), base, bonus, base.add(bonus));
        }).toList();

        BigDecimal total = days.stream().map(EarningsDayDto::total).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PayoutDto> past = payouts.findByRiderIdOrderByPeriodEndDesc(riderId).stream()
                .map(x -> new PayoutDto(x.getId(), x.getPeriodStart(), x.getPeriodEnd(), x.getAmount(),
                        x.getStatus(), x.getPaidAt(), x.getMpesaReference()))
                .toList();

        return new EarningsDto(p, from, to, days, total, NEXT_PAYOUT, BONUS_RULE, past);
    }

    // ------------------------------------------------------------------ profile

    public ProfileDto profile(Long riderId) {
        return toProfile(getRider(riderId));
    }

    public ProfileDto updateProfile(Long riderId, ProfileUpdateRequest req) {
        Rider r = getRider(riderId);

        String phone = req.phone().replaceAll("\\s", "");
        if (!phone.equals(r.getPhone()) && riders.existsByPhone(phone)) {
            throw new DuplicateRiderException("A rider with this phone number already exists");
        }
        String email = blankToNull(req.email());
        if (email != null && !email.equals(r.getEmail()) && riders.existsByEmail(email)) {
            throw new DuplicateRiderException("A rider with this email already exists");
        }
        String plate = req.plateNumber().trim().toUpperCase();
        if (!plate.equals(r.getPlateNumber()) && riders.existsByPlateNumber(plate)) {
            throw new DuplicateRiderException("A rider with this number plate already exists");
        }

        r.setFullName(req.fullName().trim());
        r.setPhone(phone);
        r.setEmail(email);
        r.setVehicleType(req.vehicleType());
        r.setPlateNumber(plate);
        r.setCapacity(req.capacity());
        r.setMpesaNumber(req.mpesaNumber() == null ? null : req.mpesaNumber().replaceAll("\\s", ""));
        r.setNotifyNewOrders(req.notifyNewOrders());
        r.setNotifyPayouts(req.notifyPayouts());
        r.setNotifySms(req.notifySms());
        return toProfile(r);
    }

    private ProfileDto toProfile(Rider r) {
        long total = deliveries.countByRiderIdAndStatus(r.getId(), DeliveryStatus.DELIVERED);
        long onTime = deliveries.countByRiderIdAndStatusAndOnTimeTrue(r.getId(), DeliveryStatus.DELIVERED);
        Double avg = deliveries.averageRating(r.getId());
        return new ProfileDto(
                r.getId(),
                String.format("RDR-%04d", r.getId()),
                r.getFullName(),
                r.getPhone(),
                r.getEmail(),
                r.getVehicleType(),
                r.getPlateNumber(),
                r.getCapacity(),
                r.getMpesaNumber(),
                r.isVerified(),
                r.getStatus() != RiderStatus.OFFLINE,
                r.getStatus(),
                r.getCreatedAt() == null ? null : r.getCreatedAt().toLocalDate(),
                avg == null ? null : DeliveryService.round1(avg),
                total,
                total == 0 ? 0 : (int) Math.round(onTime * 100.0 / total),
                r.isNotifyNewOrders(),
                r.isNotifyPayouts(),
                r.isNotifySms());
    }

    // ------------------------------------------------------------------ helpers

    private Rider getRider(Long riderId) {
        return riders.findById(riderId).orElseThrow(() -> new RiderNotFoundException(riderId));
    }

    private BigDecimal sumFees(List<Delivery> list) {
        return list.stream().map(Delivery::getFee).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
