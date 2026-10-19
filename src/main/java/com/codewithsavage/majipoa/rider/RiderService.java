package com.codewithsavage.majipoa.rider;

import com.codewithsavage.majipoa.rider.RiderDtos.*;
import com.codewithsavage.majipoa.shared.model.AppUser;
import com.codewithsavage.majipoa.shared.model.Role;
import com.codewithsavage.majipoa.shared.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
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
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Everything about riders in one place:
 *  1. admin CRUD (used by RiderController)
 *  2. working out which rider the logged-in user is (used by RiderPortalController)
 *  3. the Dashboard, History, Earnings and Profile screens of the rider portal
 */
@Service
@Transactional
public class RiderService {

    private static final BigDecimal BONUS_PER_TEN_ON_TIME = new BigDecimal("100");
    private static final String BONUS_RULE =
            "Bonus: KSh 100 for every 10 deliveries completed on time in a day.";
    private static final String NEXT_PAYOUT = "Friday 6 pm";

    private final RiderRepository riders;
    private final DeliveryRepository deliveries;
    private final PayoutRepository payouts;
    private final DeliveryService deliveryService;
    private final UserRepository users;

    public RiderService(RiderRepository riders,
                        DeliveryRepository deliveries,
                        PayoutRepository payouts,
                        DeliveryService deliveryService,
                        UserRepository users) {
        this.riders = riders;
        this.deliveries = deliveries;
        this.payouts = payouts;
        this.deliveryService = deliveryService;
        this.users = users;
    }

    // =====================================================================
    // 1. Admin CRUD
    // =====================================================================

    public RiderResponse create(RiderRequest request) {
        if (riders.existsByPhone(request.getPhone())) {
            throw new DuplicateRiderException("A rider with this phone number already exists");
        }
        if (notBlank(request.getEmail()) && riders.existsByEmail(request.getEmail())) {
            throw new DuplicateRiderException("A rider with this email already exists");
        }
        if (notBlank(request.getPlateNumber()) && riders.existsByPlateNumber(request.getPlateNumber())) {
            throw new DuplicateRiderException("A rider with this plate number already exists");
        }

        Rider rider = new Rider();
        apply(rider, request);
        return RiderResponse.from(riders.save(rider));
    }

    @Transactional(readOnly = true)
    public List<RiderResponse> findAll(RiderStatus status) {
        List<Rider> list = (status == null) ? riders.findAll() : riders.findByStatus(status);
        return list.stream().map(RiderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RiderResponse findById(Long id) {
        return RiderResponse.from(getRider(id));
    }

    public RiderResponse update(Long id, RiderRequest request) {
        Rider rider = getRider(id);

        if (!request.getPhone().equals(rider.getPhone()) && riders.existsByPhone(request.getPhone())) {
            throw new DuplicateRiderException("A rider with this phone number already exists");
        }
        if (notBlank(request.getEmail())
                && !request.getEmail().equals(rider.getEmail())
                && riders.existsByEmail(request.getEmail())) {
            throw new DuplicateRiderException("A rider with this email already exists");
        }
        if (notBlank(request.getPlateNumber())
                && !request.getPlateNumber().equals(rider.getPlateNumber())
                && riders.existsByPlateNumber(request.getPlateNumber())) {
            throw new DuplicateRiderException("A rider with this plate number already exists");
        }

        apply(rider, request);
        return RiderResponse.from(riders.save(rider));
    }

    public RiderResponse updateStatus(Long id, RiderStatus status) {
        Rider rider = getRider(id);
        rider.setStatus(status);
        return RiderResponse.from(riders.save(rider));
    }

    public RiderResponse updateLocation(Long id, RiderLocationRequest location) {
        Rider rider = getRider(id);
        rider.setLatitude(location.getLatitude());
        rider.setLongitude(location.getLongitude());
        return RiderResponse.from(riders.save(rider));
    }

    public void delete(Long id) {
        riders.delete(getRider(id));
    }

    private void apply(Rider rider, RiderRequest request) {
        rider.setFullName(request.getFullName());
        rider.setPhone(request.getPhone());
        rider.setEmail(blankToNull(request.getEmail()));
        rider.setVehicleType(request.getVehicleType());
        rider.setPlateNumber(blankToNull(request.getPlateNumber()));
    }

    // =====================================================================
    // 2. The logged-in rider (no rider id in any rider-portal URL)
    // =====================================================================

    public Long currentRiderId(Authentication auth) {
        return currentRider(auth).getId();
    }

    public Rider currentRider(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof OidcUser oidc)) {
            throw new AccessDeniedException("Not signed in");
        }
        AppUser user = users.findByGoogleSub(oidc.getSubject())
                .orElseThrow(() -> new AccessDeniedException("Unknown account"));

        // Checked against the database, not the session, so switching role takes effect
        // immediately instead of only after the next Google login.
        if (user.getRole() != Role.RIDER) {
            throw new AccessDeniedException("This account is not a rider");
        }

        return riders.findByUserId(user.getId()).orElseGet(() -> linkOrCreate(user));
    }

    /** First time this rider opens the portal: link an admin-created profile, or create one. */
    private Rider linkOrCreate(AppUser user) {
        Optional<Rider> byEmail = riders.findByEmailIgnoreCase(user.getEmail());

        if (byEmail.isPresent() && byEmail.get().getUser() == null) {
            Rider existing = byEmail.get();
            existing.setUser(user);
            return riders.save(existing);
        }

        Rider rider = new Rider();
        rider.setUser(user);
        rider.setFullName(user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getEmail());
        if (byEmail.isEmpty()) {
            rider.setEmail(user.getEmail());
        }
        return riders.save(rider);
    }

    // =====================================================================
    // 3. Dashboard
    // =====================================================================

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

    // =====================================================================
    // 4. History
    // =====================================================================

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

    // =====================================================================
    // 5. Earnings
    // =====================================================================

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

        // Each payout also carries how many deliveries fell inside its period.
        List<PayoutDto> past = payouts.findByRiderIdOrderByPeriodEndDesc(riderId).stream()
                .map(x -> new PayoutDto(
                        x.getId(),
                        x.getPeriodStart(),
                        x.getPeriodEnd(),
                        countDelivered(riderId, x.getPeriodStart(), x.getPeriodEnd()),
                        x.getAmount(),
                        x.getStatus(),
                        x.getPaidAt(),
                        x.getMpesaReference()))
                .toList();

        return new EarningsDto(p, from, to, days, total, NEXT_PAYOUT, BONUS_RULE, past);
    }

    private int countDelivered(Long riderId, LocalDate from, LocalDate to) {
        if (from == null || to == null) return 0;
        return deliveries.findByRiderIdAndStatusAndDeliveredAtBetweenOrderByDeliveredAtDesc(
                riderId, DeliveryStatus.DELIVERED, from.atStartOfDay(), to.plusDays(1).atStartOfDay()).size();
    }

    // =====================================================================
    // 6. Profile
    // =====================================================================

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

    // =====================================================================
    // Helpers
    // =====================================================================

    private Rider getRider(Long riderId) {
        return riders.findById(riderId).orElseThrow(() -> new RiderNotFoundException(riderId));
    }

    private BigDecimal sumFees(List<Delivery> list) {
        return list.stream().map(Delivery::getFee).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String blankToNull(String s) {
        return notBlank(s) ? s.trim() : null;
    }
}